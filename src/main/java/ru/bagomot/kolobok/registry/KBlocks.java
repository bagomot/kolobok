package ru.bagomot.kolobok.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import ru.bagomot.kolobok.block.StovePart;
import ru.bagomot.kolobok.block.StovePartBlock;

public final class KBlocks {

    public static final KRegistry<Block> REGISTRY = new KRegistry<>(Registries.BLOCK);

    /**
     * The Russian stove, the mod's only block. No item: it is assembled from bricks and a furnace
     * and turns back into them. As hard as bricks; a burning mouth gives light like a furnace.
     */
    public static final KRegistry.Entry<Block, StovePartBlock> STOVE_PART = REGISTRY.register(
            "stove_part",
            key -> new StovePartBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.QUARTZ)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.STONE)
                    .pushReaction(PushReaction.IMMOVEABLE)
                    .lightLevel(state -> state.getValue(StovePartBlock.LIT)
                            && state.getValue(StovePartBlock.PART) == StovePart.MOUTH ? 13 : 0)));

    private KBlocks() {
    }

    /** Loads the class, which declares the entries. */
    public static void init() {
    }
}
