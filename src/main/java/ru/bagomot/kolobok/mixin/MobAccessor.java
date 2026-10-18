package ru.bagomot.kolobok.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The only mixin: reaches a vanilla mob's goals, so that a fox can hunt Kolobok and a cat can sit
 * on the stove. Nothing of vanilla is changed; goals are added beside its own.
 */
@Mixin(Mob.class)
public interface MobAccessor {

    @Accessor("goalSelector")
    GoalSelector kolobok$goalSelector();

    @Accessor("targetSelector")
    GoalSelector kolobok$targetSelector();
}
