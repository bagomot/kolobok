package ru.bagomot.kolobok.registry;

import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import ru.bagomot.kolobok.advancement.TaleTrigger;

public final class KTriggers {

    public static final KRegistry<CriterionTrigger<?>> REGISTRY = new KRegistry<>(Registries.TRIGGER_TYPE);

    /** Every advancement of «Сказка о Колобке» that vanilla has no trigger for. */
    public static final KRegistry.Entry<CriterionTrigger<?>, TaleTrigger> TALE = REGISTRY.register("tale", key -> new TaleTrigger());

    private KTriggers() {
    }

    /** Loads the class, which declares the entries. */
    public static void init() {
    }
}
