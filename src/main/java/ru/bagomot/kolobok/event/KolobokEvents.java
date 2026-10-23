package ru.bagomot.kolobok.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.entity.ai.CatOnStoveGoal;
import ru.bagomot.kolobok.entity.ai.Goals;
import ru.bagomot.kolobok.item.BindleItem;

import java.util.List;

/**
 * Common handlers that each loader calls from its own events.
 */
public final class KolobokEvents {

    /** As a fox goes for a chicken or a rabbit. */
    private static final int FOX_HUNT_PRIORITY = 4;
    /** A fox looks round for Kolobok about once in this many ticks. */
    private static final int FOX_HUNT_INTERVAL = 40;
    /** «Репка»: the dog, the cat and Kolobok must be this close to the beetroot. */
    private static final double REPKA_RANGE = 6.0;
    private static final int REPKA_BEETROOTS = 6;

    private KolobokEvents() {
    }

    /**
     * An entity is loaded into a server level: foxes learn to hunt Kolobok, tame cats to sit on the
     * stove, and a bindle with a Kolobok in it, dropped or thrown, never despawns.
     */
    public static void onEntityLoad(Entity entity) {
        if (entity instanceof ItemEntity item) {
            if (BindleItem.isFull(item.getItem())) {
                item.setUnlimitedLifetime();
            }
        } else if (entity instanceof Fox fox) {
            Goals.addOnce(Goals.targets(fox), FOX_HUNT_PRIORITY, new FoxHuntGoal(fox));
        } else if (entity instanceof Cat cat) {
            CatOnStoveGoal.teach(cat);
        }
    }

    /**
     * A living entity is about to be hurt.
     *
     * @return whether the damage goes ahead: a Kolobok on a player's head takes a falling anvil's bonk
     */
    public static boolean allowDamage(LivingEntity entity, DamageSource source) {
        return !KolobokEntity.bonk(entity, source);
    }

    /**
     * «Тянут-потянут»: a player picks ripe beetroot with his own dog, cat and Kolobok close by, and six
     * more beetroots come up with it, as the turnip did for the whole family.
     */
    public static void onBlockBroken(Level level, Player player, BlockPos pos, BlockState state) {
        if (level.isClientSide() || !(state.getBlock() instanceof BeetrootBlock crop) || !crop.isMaxAge(state)) {
            return;
        }
        AABB around = new AABB(pos).inflate(REPKA_RANGE);
        boolean dog = !level.getEntitiesOfClass(Wolf.class, around, wolf -> wolf.isTame() && wolf.isOwnedBy(player)).isEmpty();
        boolean cat = !level.getEntitiesOfClass(Cat.class, around, c -> c.isTame() && c.isOwnedBy(player)).isEmpty();
        List<KolobokEntity> kolobki = level.getEntitiesOfClass(KolobokEntity.class, around,
                kolobok -> kolobok.isTame() && kolobok.isOwnedBy(player));
        if (dog && cat && !kolobki.isEmpty()) {
            Block.popResource(level, pos, new ItemStack(Items.BEETROOT, REPKA_BEETROOTS));
            kolobki.getFirst().say("repka");
        }
    }

    /**
     * The fox's own hunting goal, aimed at Kolobok. Its own class so that it is added only once.
     */
    private static final class FoxHuntGoal extends NearestAttackableTargetGoal<KolobokEntity> {

        FoxHuntGoal(Fox fox) {
            super(fox, KolobokEntity.class, FOX_HUNT_INTERVAL, true, false,
                    (target, level) -> target instanceof KolobokEntity kolobok && kolobok.foxMayHunt());
        }
    }
}
