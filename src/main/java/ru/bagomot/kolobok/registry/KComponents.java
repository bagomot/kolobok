package ru.bagomot.kolobok.registry;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import ru.bagomot.kolobok.entity.Variant;

public final class KComponents {

    public static final KRegistry<DataComponentType<?>> REGISTRY = new KRegistry<>(Registries.DATA_COMPONENT_TYPE);

    /**
     * What the dough is: on the dough and on the raw Kolobok rolled from it. Absent means classic.
     * Recipes set it, and rolling copies it over.
     */
    public static final KRegistry.Entry<DataComponentType<?>, DataComponentType<Variant>> DOUGH = REGISTRY.register(
            "dough",
            key -> DataComponentType.<Variant>builder()
                    .persistent(Codec.STRING.xmap(Variant::byName, Variant::serializedName))
                    .networkSynchronized(ByteBufCodecs.VAR_INT.map(Variant::byId, Variant::ordinal))
                    .build());

    private KComponents() {
    }

    /** Loads the class, which declares the entries. */
    public static void init() {
    }
}
