package ru.bagomot.kolobok.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import ru.bagomot.kolobok.entity.KolobokEntity;

public final class KEntities {

    public static final KRegistry<EntityType<?>> REGISTRY = new KRegistry<>(Registries.ENTITY_TYPE);

    /** Model «Б» is Ø10 px, so the box is 0.6 × 0.6; Колобочек scales it to 0.45. */
    public static final KRegistry.Entry<EntityType<?>, EntityType<KolobokEntity>> KOLOBOK = REGISTRY.register(
            "kolobok",
            key -> EntityType.Builder.<KolobokEntity>of(KolobokEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 0.6F)
                    .eyeHeight(0.4F)
                    .clientTrackingRange(10)
                    .build(key));

    private KEntities() {
    }

    /** Loads the class, which declares the entries. */
    public static void init() {
    }
}
