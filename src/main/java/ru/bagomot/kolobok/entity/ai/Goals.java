package ru.bagomot.kolobok.entity.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
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
     * Holds the mob still for a tick: its goals stop walking it about and turning its head, so that
     * where it is sent and what it is told to look at hold. The mob gives the flags back to its goals
     * by itself every five ticks, so whoever holds it does so every tick.
     */
    public static void hold(Mob mob) {
        GoalSelector selector = goals(mob);
        for (WrappedGoal goal : selector.getAvailableGoals()) {
            if (goal.isRunning() && (goal.getFlags().contains(Goal.Flag.MOVE) || goal.getFlags().contains(Goal.Flag.LOOK))) {
                goal.stop();
            }
        }
        selector.disableControlFlag(Goal.Flag.MOVE);
        selector.disableControlFlag(Goal.Flag.LOOK);
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
