package ru.bagomot.kolobok.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.bagomot.kolobok.registry.KBlocks;

/**
 * Where the thirteen blocks of the stove stand, and turning vanilla blocks into the stove and back.
 *
 * <p>The stove faces where the furnace faced. Everything is placed relative to the mouth (the
 * furnace's spot): {@code right} is the right hand of someone standing in front, looking at it.
 */
public final class StovePattern {

    /** Height of the hearth inside the mouth's niche, in pixels. */
    static final int HEARTH = 9;
    /** How far into the niche a fresh Kolobok sits, in pixels from the front face. */
    private static final int HEARTH_DEPTH = 5;
    /** Glass this close to the stove is a window: a fresh Kolobok cools on it four times faster. */
    private static final int WINDOW_REACH = 2;

    private static final TagKey<Block> GLASS_BLOCKS =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "glass_blocks"));
    private static final TagKey<Block> GLASS_PANES =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "glass_panes"));

    /** Set while the stove itself rebuilds blocks, so that their removal hooks stay quiet. */
    private static boolean rebuilding;

    /** Why a furnace did not become a stove. */
    public enum Check {
        OK, NOT_A_FURNACE, MISSING_BRICKS, BURNING, NOT_EMPTY
    }

    private StovePattern() {
    }

    public static BlockPos posOf(BlockPos mouth, Direction facing, StovePart part) {
        Direction back = facing.getOpposite();
        Direction right = back.getClockWise();
        return mouth.relative(right, part.column()).relative(back, part.row()).above(part.layer());
    }

    public static BlockPos mouthOf(BlockPos pos, Direction facing, StovePart part) {
        Direction back = facing.getOpposite();
        Direction right = back.getClockWise();
        return pos.relative(right, -part.column()).relative(back, -part.row()).below(part.layer());
    }

    /**
     * Checks the vanilla layout around a furnace: 11 bricks around it in a 3×2×2 block and one more
     * above the back right corner, the future chimney. The furnace must be cold and empty, so
     * nothing in it is lost.
     */
    public static Check check(LevelReader level, BlockPos furnacePos) {
        BlockState furnace = level.getBlockState(furnacePos);
        if (!furnace.is(Blocks.FURNACE)) {
            return Check.NOT_A_FURNACE;
        }
        Direction facing = furnace.getValue(AbstractFurnaceBlock.FACING);
        for (StovePart part : StovePart.values()) {
            if (part != StovePart.MOUTH && !level.getBlockState(posOf(furnacePos, facing, part)).is(Blocks.BRICKS)) {
                return Check.MISSING_BRICKS;
            }
        }
        if (furnace.getValue(AbstractFurnaceBlock.LIT)) {
            return Check.BURNING;
        }
        if (level.getBlockEntity(furnacePos) instanceof AbstractFurnaceBlockEntity contents && !contents.isEmpty()) {
            return Check.NOT_EMPTY;
        }
        return Check.OK;
    }

    /**
     * Turns the checked furnace and bricks into the stove, facing where the furnace faced.
     */
    public static void assemble(ServerLevel level, BlockPos furnacePos) {
        Direction facing = level.getBlockState(furnacePos).getValue(AbstractFurnaceBlock.FACING);
        StovePartBlock block = KBlocks.STOVE_PART.get();
        rebuilding = true;
        try {
            for (StovePart part : StovePart.values()) {
                BlockState state = block.defaultBlockState()
                        .setValue(StovePartBlock.PART, part)
                        .setValue(StovePartBlock.FACING, facing);
                level.setBlock(posOf(furnacePos, facing, part), state, Block.UPDATE_ALL);
            }
        } finally {
            rebuilding = false;
        }
    }

    /**
     * Called when one part has gone, however it went: every other part turns back into the block it
     * was built from, without dropping anything. The part that went drops its own block through
     * its loot table.
     */
    static void disassemble(ServerLevel level, BlockPos removedAt, BlockState removed) {
        if (rebuilding) {
            return;
        }
        Direction facing = removed.getValue(StovePartBlock.FACING);
        BlockPos mouth = mouthOf(removedAt, facing, removed.getValue(StovePartBlock.PART));
        rebuilding = true;
        try {
            for (StovePart part : StovePart.values()) {
                BlockPos pos = posOf(mouth, facing, part);
                if (isPart(level.getBlockState(pos), part, facing)) {
                    level.setBlock(pos, original(part, facing), Block.UPDATE_ALL);
                }
            }
        } finally {
            rebuilding = false;
        }
    }

    /**
     * Takes a damaged stove apart: one whose blocks were changed by something that skips the removal
     * hook, such as a command.
     */
    static void disassembleDamaged(ServerLevel level, BlockPos mouth, BlockState mouthState) {
        disassemble(level, mouth, mouthState);
    }

    public static boolean isIntact(LevelReader level, BlockPos mouth, Direction facing) {
        for (StovePart part : StovePart.values()) {
            if (!isPart(level.getBlockState(posOf(mouth, facing, part)), part, facing)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Lights or puts out every part at once: the mouth glows and the chimney smokes. Two state
     * changes per fire, never one per tick.
     */
    static void setLit(Level level, BlockPos mouth, Direction facing, boolean lit) {
        for (StovePart part : StovePart.values()) {
            BlockPos pos = posOf(mouth, facing, part);
            BlockState state = level.getBlockState(pos);
            if (isPart(state, part, facing) && state.getValue(StovePartBlock.LIT) != lit) {
                level.setBlock(pos, state.setValue(StovePartBlock.LIT, lit), Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * @return where a freshly baked Kolobok sits: on the hearth inside the mouth's niche
     */
    public static Vec3 hearth(BlockPos mouth, Direction facing) {
        double out = 0.5 - HEARTH_DEPTH / 16.0;
        return new Vec3(mouth.getX() + 0.5 + facing.getStepX() * out, mouth.getY() + HEARTH / 16.0,
                mouth.getZ() + 0.5 + facing.getStepZ() * out);
    }

    /**
     * @return the лежанка, where a «killed» Kolobok rests: the middle of the stove's top, between
     *         the front and back rows
     */
    public static Vec3 bench(BlockPos mouth, Direction facing) {
        Direction back = facing.getOpposite();
        return new Vec3(mouth.getX() + 0.5 + back.getStepX() * 0.5, mouth.getY() + 2,
                mouth.getZ() + 0.5 + back.getStepZ() * 0.5);
    }

    /**
     * @return the middle of the chimney's top, where the smoke comes out
     */
    public static Vec3 chimneyTop(BlockPos mouth, Direction facing) {
        return Vec3.atBottomCenterOf(posOf(mouth, facing, StovePart.CHIMNEY).above());
    }

    /**
     * @return the box all thirteen blocks fill
     */
    public static AABB bounds(BlockPos mouth, Direction facing) {
        AABB box = new AABB(mouth);
        for (StovePart part : StovePart.values()) {
            box = box.minmax(new AABB(posOf(mouth, facing, part)));
        }
        return box;
    }

    /**
     * @return whether there is a window by the stove: glass or a glass pane within two blocks
     */
    public static boolean hasWindow(LevelReader level, BlockPos mouth, Direction facing) {
        AABB box = bounds(mouth, facing).inflate(WINDOW_REACH);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(GLASS_BLOCKS) || state.is(GLASS_PANES)
                    || state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE) || state.is(Blocks.TINTED_GLASS)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPart(BlockState state, StovePart part, Direction facing) {
        return state.getBlock() instanceof StovePartBlock
                && state.getValue(StovePartBlock.PART) == part
                && state.getValue(StovePartBlock.FACING) == facing;
    }

    /**
     * @return the block a part was built from: the furnace for the mouth, bricks for the rest
     */
    static BlockState original(StovePart part, Direction facing) {
        return part == StovePart.MOUTH
                ? Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, facing)
                : Blocks.BRICKS.defaultBlockState();
    }
}
