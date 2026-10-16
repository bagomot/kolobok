package ru.bagomot.kolobok.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ru.bagomot.kolobok.block.StoveBlockEntity;

import java.util.Set;

public final class KBlockEntities {

    public static final KRegistry<BlockEntityType<?>> REGISTRY = new KRegistry<>(Registries.BLOCK_ENTITY_TYPE);

    /** Only the stove's mouth has one. */
    public static final KRegistry.Entry<BlockEntityType<?>, BlockEntityType<StoveBlockEntity>> STOVE = REGISTRY.register(
            "stove",
            key -> new BlockEntityType<>(StoveBlockEntity::new, Set.of(KBlocks.STOVE_PART.get())));

    private KBlockEntities() {
    }

    /** Loads the class, which declares the entries. */
    public static void init() {
    }
}
