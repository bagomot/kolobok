package ru.bagomot.kolobok.entity.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import ru.bagomot.kolobok.mixin.MobAccessor;

/**
 * A vanilla mob's goals, through the accessor mixin.
 */
public final class Goals {

    private Goals() {
    }

    public static GoalSelector goals(Mob mob) {
        return ((MobAccessor) mob).kolobok$goalSelector();
    }

    public static GoalSelector targets(Mob mob) {
        return ((MobAccessor) mob).kolobok$targetSelector();
    }

    /**
     * @return whether the mob is driven by goals at all. Villagers, piglins, the warden and others
     *         run on a brain instead; Kolobok leaves those alone.
     */
    public static boolean runsOnGoals(Mob mob) {
        return !goals(mob).getAvailableGoals().isEmpty();
    }

    /**
     * Adds the goal unless one of its class is there already: an entity can be loaded again.
     */
    public static void addOnce(GoalSelector selector, int priority, Goal goal) {
        boolean present = selector.getAvailableGoals().stream().anyMatch(wrapped -> wrapped.getGoal().getClass() == goal.getClass());
        if (!present) {
            selector.addGoal(priority, goal);
        }
    }
}
