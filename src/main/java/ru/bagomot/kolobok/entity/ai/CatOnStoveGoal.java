package ru.bagomot.kolobok.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
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
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A tame cat now and then takes a free place on top of a warm Russian stove, as vanilla cats sit on
 * a lit furnace: it walks up beside the stove, springs two blocks up onto the place, sits there for
 * a while facing the room and jumps down again. A Kolobok coming home to a stove with no other
 * place free shoos it off ({@link #shoo}).
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
    private static final int JUMP_TICKS = 12;
    private static final int SIT_MIN = 600;
    private static final int SIT_SPREAD = 1800;
    /** A place is taken by anyone standing this close to its middle. */
    private static final double PLACE_RADIUS = 0.35;

    private static final Map<Cat, CatOnStoveGoal> GOALS = Collections.synchronizedMap(new WeakHashMap<>());

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
        CatOnStoveGoal goal = new CatOnStoveGoal(cat);
        Goals.addOnce(Goals.goals(cat), PRIORITY, goal);
        GOALS.putIfAbsent(cat, goal);
    }

    /**
     * Sends the cat off the stove: down the way it came if it is sitting there by this goal,
     * otherwise a push off the edge, away from that point.
     */
    public static void shoo(Cat cat, Vec3 from) {
        CatOnStoveGoal goal = GOALS.get(cat);
        if (goal != null && goal.phase == Phase.SIT) {
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
        AABB box = new AABB(place.x - PLACE_RADIUS, place.y - 0.1, place.z - PLACE_RADIUS,
                place.x + PLACE_RADIUS, place.y + 0.6, place.z + PLACE_RADIUS);
        return !level.getEntitiesOfClass(LivingEntity.class, box, e -> e != self && e.isAlive() && !e.isSpectator()).isEmpty();
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
     * Finds a free place on that stove with free ground beside it that the cat can walk to.
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
                Path path = cat.getNavigation().createPath(ground, 0);
                if (path != null && path.canReach()) {
                    mouth = at;
                    facing = dir;
                    place = candidate;
                    launch = ground;
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
        Vec3 target = Vec3.atBottomCenterOf(launch);
        cat.getNavigation().moveTo(target.x, target.y, target.z, 0.9);
    }

    @Override
    public boolean canContinueToUse() {
        return phase != Phase.DONE && cat.isTame() && !cat.isLeashed()
                && (phase != Phase.SIT || !cat.isOrderedToSit());
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

    private void walk() {
        Vec3 target = Vec3.atBottomCenterOf(launch);
        if (cat.position().multiply(1, 0, 1).distanceToSqr(target.multiply(1, 0, 1)) < 0.35 * 0.35 && cat.onGround()) {
            if (isTaken(cat.level(), place, cat) || !StovePattern.isIntact(cat.level(), mouth, facing)) {
                phase = Phase.DONE;
                return;
            }
            startJump(Phase.UP, cat.position(), place);
            return;
        }
        if (--timer <= 0 || cat.getNavigation().isDone() && cat.position().distanceToSqr(target) > 2.0) {
            phase = Phase.DONE;
        }
    }

    private void startJump(Phase next, Vec3 from, Vec3 to) {
        phase = next;
        timer = 0;
        hop = new Hop(from, to, JUMP_TICKS, 0.4);
        cat.getNavigation().stop();
        cat.noPhysics = true;
        cat.setNoGravity(true);
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
        cat.setNoGravity(false);
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
     * @return free ground beside the stove to jump down to: where the cat came from, if still free
     */
    private @Nullable BlockPos landing() {
        int index = 0;
        for (int i = 0; i < StovePattern.TOP.size(); i++) {
            if (StovePattern.topSlots(mouth, facing).get(i).distanceToSqr(place) < 1.0E-4) {
                index = i;
            }
        }
        List<BlockPos> grounds = new ArrayList<>(List.of(launch));
        grounds.addAll(StovePattern.beside(mouth, facing, StovePattern.TOP.get(index)));
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
    }
}
