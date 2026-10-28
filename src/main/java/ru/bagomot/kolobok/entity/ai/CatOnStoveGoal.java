package ru.bagomot.kolobok.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ru.bagomot.kolobok.block.StoveBlockEntity;
import ru.bagomot.kolobok.block.StovePart;
import ru.bagomot.kolobok.block.StovePattern;
import ru.bagomot.kolobok.entity.Hop;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * A tame cat now and then takes a free place on top of a warm Russian stove, as vanilla cats sit on
 * a lit furnace: it walks up beside the stove, springs two blocks up onto the place, sits there for
 * a while facing the room and jumps down again. A hurt Kolobok who finds no other place free shoos
 * it off ({@link #shoo}).
 *
 * <p>The spring is scripted, as Kolobok's hops are, and only ever onto the stove, from the ground
 * beside it, at most {@link #MAX_JUMP} blocks up. Nothing of it is saved with the cat: a cat unloaded
 * mid-jump just falls when it is back.
 *
 * <p>Added beside the cat's own goals when it is loaded; nothing of vanilla's is changed.
 */
public final class CatOnStoveGoal extends Goal {

    /** Below vanilla's sitting on chests, beds and furnaces, so those come first. */
    public static final int PRIORITY = 7;
    private static final int SEARCH_RANGE = 10;
    /** A cat thinks of the stove once every one to three minutes. */
    private static final int CHECK_MIN = 1200;
    private static final int CHECK_SPREAD = 2400;
    private static final int WALK_TICKS = 200;
    private static final double WALK_SPEED = 0.9;
    /** Within this of the ground's middle, the cat stands on it… */
    private static final double ARRIVED = 0.3;
    /** …and within this it walks the last steps straight, since a path counts as done half a block short. */
    private static final double LAST_STEPS = 1.5;
    /** The highest the cat springs up. */
    private static final double MAX_JUMP = 2.0;
    private static final int JUMP_TICKS = 12;
    private static final int SIT_MIN = 600;
    private static final int SIT_SPREAD = 1800;
    /** A place is taken by anyone standing this close to its middle. */
    private static final double PLACE_RADIUS = 0.35;
    /** A cat this close to a place's middle is on it. */
    private static final double ON_PLACE = 0.5;

    private enum Phase { WALK, UP, SIT, DOWN, DONE }

    private final Cat cat;
    private int nextCheck;
    private Phase phase = Phase.DONE;
    private int timer;
    private @Nullable Hop hop;
    private BlockPos mouth = BlockPos.ZERO;
    private Direction facing = Direction.NORTH;
    private Vec3 place = Vec3.ZERO;
    private BlockPos launch = BlockPos.ZERO;
    private @Nullable Path path;
    private boolean shooed;

    private CatOnStoveGoal(Cat cat) {
        this.cat = cat;
        this.nextCheck = cat.getRandom().nextInt(CHECK_SPREAD);
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
    }

    /**
     * Gives a cat the goal, once.
     */
    public static void teach(Cat cat) {
        Goals.addOnce(Goals.goals(cat), PRIORITY, new CatOnStoveGoal(cat));
    }

    /**
     * @return the cat's own goal, found among its goals: a map from cats to their goals would keep
     *         every cat, since the goal holds its cat
     */
    private static @Nullable CatOnStoveGoal of(Cat cat) {
        for (WrappedGoal wrapped : Goals.goals(cat).getAvailableGoals()) {
            if (wrapped.getGoal() instanceof CatOnStoveGoal goal) {
                return goal;
            }
        }
        return null;
    }

    /**
     * Sends the cat off the stove: down the way it came if it is sitting there by this goal, or about
     * to, otherwise a push off the edge, away from that point.
     */
    public static void shoo(Cat cat, Vec3 from) {
        CatOnStoveGoal goal = of(cat);
        if (goal != null && (goal.phase == Phase.UP || goal.phase == Phase.SIT)) {
            goal.shooed = true;
            return;
        }
        Vec3 away = cat.position().subtract(from).multiply(1.0, 0.0, 1.0);
        if (away.lengthSqr() < 1.0E-4) {
            away = Vec3.directionFromRotation(0.0F, cat.getYRot());
        }
        away = away.normalize().scale(0.45);
        cat.setInSittingPose(false);
        cat.setDeltaMovement(away.x, 0.35, away.z);
        cat.syncVelocity = true;
    }

    /**
     * @return whether anyone but {@code self} stands on that place
     */
    public static boolean isTaken(Level level, Vec3 place, @Nullable LivingEntity self) {
        return !sittingOn(level, place, self).isEmpty();
    }

    /**
     * @return everyone but {@code self} who stands on that place
     */
    public static List<LivingEntity> sittingOn(Level level, Vec3 place, @Nullable LivingEntity self) {
        AABB box = new AABB(place.x - PLACE_RADIUS, place.y - 0.1, place.z - PLACE_RADIUS,
                place.x + PLACE_RADIUS, place.y + 0.6, place.z + PLACE_RADIUS);
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e != self && e.isAlive() && !e.isSpectator());
    }

    @Override
    public boolean canUse() {
        if (cat.tickCount < nextCheck || !cat.isTame() || cat.isOrderedToSit() || cat.isLeashed() || cat.isPassenger()
                || !cat.onGround()) {
            return false;
        }
        nextCheck = cat.tickCount + CHECK_MIN + cat.getRandom().nextInt(CHECK_SPREAD);
        for (StoveBlockEntity stove : StovePattern.nearby(cat.level(), cat.blockPosition(), SEARCH_RANGE)) {
            if (stove.isWarm() && pickPlace(stove)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds a free place on that stove with free ground beside it that the cat can walk onto.
     */
    private boolean pickPlace(StoveBlockEntity stove) {
        BlockPos at = stove.getBlockPos();
        Direction dir = stove.facing();
        if (!StovePattern.isIntact(cat.level(), at, dir)) {
            return false;
        }
        List<Vec3> places = StovePattern.topSlots(at, dir);
        for (int i = 0; i < places.size(); i++) {
            Vec3 candidate = places.get(i);
            if (isTaken(cat.level(), candidate, cat)) {
                continue;
            }
            StovePart top = StovePattern.TOP.get(i);
            for (BlockPos ground : StovePattern.beside(at, dir, top)) {
                if (!cat.level().noCollision(cat, cat.getDimensions(cat.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(ground)))
                        || !cat.level().getBlockState(ground.below()).isFaceSturdy(cat.level(), ground.below(), Direction.UP)) {
                    continue;
                }
                // To the block itself: moveTo(x, y, z) would stop a block short of it.
                Path found = cat.getNavigation().createPath(ground, 0);
                if (found != null && found.canReach()) {
                    mouth = at;
                    facing = dir;
                    place = candidate;
                    launch = ground;
                    path = found;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void start() {
        phase = Phase.WALK;
        timer = WALK_TICKS;
        shooed = false;
        cat.setInSittingPose(false);
        cat.getNavigation().moveTo(path, WALK_SPEED);
        path = null;
    }

    @Override
    public boolean canContinueToUse() {
        return phase != Phase.DONE && cat.isTame() && !cat.isLeashed()
                && (phase != Phase.SIT || !cat.isOrderedToSit());
    }

    /** Not in the air: a jump, once begun, ends where it was going. */
    @Override
    public boolean isInterruptable() {
        return phase != Phase.UP && phase != Phase.DOWN;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        switch (phase) {
            case WALK -> walk();
            case UP, DOWN -> jump();
            case SIT -> sit();
            case DONE -> {
            }
        }
    }

    /**
     * Onto the ground beside the stove: by path while it is far, then the last steps straight, as
     * Kolobok walks up to his own hops. Standing there, the cat springs up.
     */
    private void walk() {
        Vec3 target = Vec3.atBottomCenterOf(launch);
        double flat = cat.position().multiply(1, 0, 1).distanceToSqr(target.multiply(1, 0, 1));
        boolean level = Math.abs(cat.getY() - target.y) < 0.5;
        if (cat.onGround() && level && flat < ARRIVED * ARRIVED) {
            if (isTaken(cat.level(), place, cat) || !StovePattern.isIntact(cat.level(), mouth, facing)
                    || place.y - cat.getY() > MAX_JUMP) {
                phase = Phase.DONE;
                return;
            }
            startJump(Phase.UP, cat.position(), place);
            return;
        }
        if (--timer <= 0) {
            phase = Phase.DONE;
        } else if (level && flat < LAST_STEPS * LAST_STEPS) {
            cat.getNavigation().stop();
            cat.getMoveControl().setWantedPosition(target.x, target.y, target.z, WALK_SPEED);
        } else if (cat.getNavigation().isDone() && timer % 10 == 0) {
            cat.getNavigation().moveTo(cat.getNavigation().createPath(launch, 0), WALK_SPEED);
        }
    }

    /**
     * The jump is placed a tick at a time, with no motion of its own, so gravity has nothing to pull
     * at: the cat's NoGravity, which is saved with it, is never touched.
     */
    private void startJump(Phase next, Vec3 from, Vec3 to) {
        phase = next;
        timer = 0;
        hop = new Hop(from, to, JUMP_TICKS, 0.4);
        cat.getNavigation().stop();
        cat.noPhysics = true;
        cat.setInSittingPose(false);
        Vec3 d = to.subtract(from);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        cat.setYRot(yaw);
        cat.setYBodyRot(yaw);
        cat.setYHeadRot(yaw);
    }

    private void jump() {
        if (hop == null) {
            phase = Phase.DONE;
            return;
        }
        hop.place(cat, ++timer);
        if (!hop.done(timer)) {
            return;
        }
        land();
        if (phase == Phase.UP) {
            phase = Phase.SIT;
            timer = SIT_MIN + cat.getRandom().nextInt(SIT_SPREAD);
            float yaw = facing.toYRot();
            cat.setYRot(yaw);
            cat.setYBodyRot(yaw);
            cat.setYHeadRot(yaw);
        } else {
            phase = Phase.DONE;
        }
    }

    private void land() {
        hop = null;
        cat.noPhysics = false;
    }

    private void sit() {
        cat.setInSittingPose(true);
        Player player = cat.level().getNearestPlayer(cat, 6.0);
        if (player != null && cat.getRandom().nextInt(40) == 0) {
            cat.getLookControl().setLookAt(player);
        }
        boolean gone = cat.tickCount % 20 == 0 && !StovePattern.isIntact(cat.level(), mouth, facing);
        if (gone) {
            phase = Phase.DONE;
            return;
        }
        if (--timer <= 0 || shooed) {
            BlockPos down = landing();
            if (down == null) {
                // Nowhere to jump to: it walks off by itself once the goal lets go.
                phase = Phase.DONE;
                return;
            }
            startJump(Phase.DOWN, cat.position(), Vec3.atBottomCenterOf(down));
        }
    }

    /**
     * @return free ground beside the stove under the place the cat is on, to jump down to: where it
     *         came from, if still free; {@code null} if there is none or the cat is off its place
     */
    private @Nullable BlockPos landing() {
        StovePart top = StovePattern.nearestTop(mouth, facing, cat.position(), ON_PLACE);
        if (top == null) {
            return null;
        }
        List<BlockPos> grounds = new ArrayList<>(List.of(launch));
        grounds.addAll(StovePattern.beside(mouth, facing, top));
        for (BlockPos ground : grounds) {
            if (cat.level().noCollision(cat, cat.getDimensions(cat.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(ground)))) {
                return ground;
            }
        }
        return null;
    }

    @Override
    public void stop() {
        if (hop != null) {
            land();
        }
        if (!cat.isOrderedToSit()) {
            cat.setInSittingPose(false);
        }
        cat.getNavigation().stop();
        phase = Phase.DONE;
        shooed = false;
        path = null;
    }
}
