package ru.bagomot.kolobok.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.fox.Fox;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.entity.ai.CatOnStoveGoal;
import ru.bagomot.kolobok.entity.ai.Goals;

/**
 * Common handlers that each loader calls from its own events.
 */
public final class KolobokEvents {

    /** As a fox goes for a chicken or a rabbit. */
    private static final int FOX_HUNT_PRIORITY = 4;
    /** A fox looks round for Kolobok about once in this many ticks. */
    private static final int FOX_HUNT_INTERVAL = 40;

    private KolobokEvents() {
    }

    /**
     * A mob is loaded into a server level: foxes learn to hunt Kolobok, tame cats to sit on the stove.
     */
    public static void onEntityLoad(Entity entity) {
        if (entity instanceof Fox fox) {
            Goals.addOnce(Goals.targets(fox), FOX_HUNT_PRIORITY, new FoxHuntGoal(fox));
        } else if (entity instanceof Cat cat) {
            CatOnStoveGoal.teach(cat);
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
