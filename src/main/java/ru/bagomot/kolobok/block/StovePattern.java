package ru.bagomot.kolobok.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.bagomot.kolobok.registry.KBlocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the thirteen blocks of the stove stand, and turning vanilla blocks into the stove and back.
 *
 * <p>The stove faces where the furnace faced. Everything is placed relative to the mouth (the
 * furnace's spot): {@code right} is the right hand of someone standing in front, looking at it.
 */
public final class StovePattern {

    // The front of the stove in pixels of the mouth's block, as tools/stove draws it: x across the
    // front from the onlooker's right, y up, z into the stove from its front face.
    /** The подпечье, where the firewood lies. */
    public static final int PODPECHYE_X0 = 2, PODPECHYE_X1 = 14, PODPECHYE_Y0 = 2, PODPECHYE_Y1 = 10;
    public static final int PODPECHYE_DEPTH = 13;
    /** The под, the горнило's floor, level with the шесток. */
    public static final int GORNILO_FLOOR = 13;
    /** The горнило's opening, across and up from the под. */
    public static final int GORNILO_X0 = 1, GORNILO_X1 = 15, GORNILO_HEIGHT = 12;
    /** Where bread stands in the горнило, its middle this deep from the front face; the coals lie behind. */
    public static final int GORNILO_SPOT = 9;
    /** The coals start this deep. */
    public static final int COALS_FROM = 17;
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
     * Turns the checked furnace and bricks into the stove, facing where the furnace faced, and puts it
     * on the server's list of stoves with every place on top free.
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
        StoveRegistry.of(level.getServer()).add(level.dimension(), furnacePos.immutable(), facing);
    }

    /**
     * Called when one part has gone, however it went: every other part turns back into the block it
     * was built from, without dropping anything, and the stove comes off the server's list. The part
     * that went drops its own block through its loot table.
     */
    static void disassemble(ServerLevel level, BlockPos removedAt, BlockState removed) {
        if (rebuilding) {
            return;
        }
        Direction facing = removed.getValue(StovePartBlock.FACING);
        BlockPos mouth = mouthOf(removedAt, facing, removed.getValue(StovePartBlock.PART));
        StoveRegistry.of(level.getServer()).remove(level.dimension(), mouth);
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

    /**
     * Checks a stove from {@link StoveRegistry} where it can be seen without loading anything. One
     * damaged by something that skipped the removal hooks, such as a command, comes apart now and off
     * the list.
     *
     * @return whether it stands whole, or is out of sight and so stands as the list has it
     */
    public static boolean checkListed(ServerLevel level, BlockPos mouth, Direction facing) {
        for (StovePart part : StovePart.values()) {
            if (!level.isLoaded(posOf(mouth, facing, part))) {
                return true;
            }
        }
        if (isIntact(level, mouth, facing)) {
            return true;
        }
        BlockState state = level.getBlockState(mouth);
        if (state.getBlock() instanceof StovePartBlock && state.getValue(StovePartBlock.PART) == StovePart.MOUTH) {
            disassembleDamaged(level, mouth, state);
        } else {
            StoveRegistry.of(level.getServer()).remove(level.dimension(), mouth);
        }
        return false;
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
     * @return a point of the mouth's block given in the pixels above, turned to the stove's facing
     */
    public static Vec3 local(BlockPos mouth, Direction facing, double x, double y, double z) {
        Direction back = facing.getOpposite();
        Direction left = back.getCounterClockWise();
        // From the block's middle: x counts from the onlooker's right edge, z from the front face.
        double across = x / 16.0 - 0.5;
        double in = z / 16.0 - 0.5;
        return new Vec3(mouth.getX() + 0.5 + left.getStepX() * across + back.getStepX() * in,
                mouth.getY() + y / 16.0,
                mouth.getZ() + 0.5 + left.getStepZ() * across + back.getStepZ() * in);
    }

    /**
     * @return where bread bakes and a Kolobok sits in the горнило: on the под, at its front, with the
     *         coals behind
     */
    public static Vec3 gornilo(BlockPos mouth, Direction facing) {
        return local(mouth, facing, 8, GORNILO_FLOOR, GORNILO_SPOT);
    }

    /**
     * @return the space at the front of the горнило where a Kolobok fits
     */
    public static AABB gorniloSpace(BlockPos mouth, Direction facing) {
        Vec3 spot = gornilo(mouth, facing);
        return new AABB(spot.x - 0.35, spot.y, spot.z - 0.35, spot.x + 0.35, spot.y + GORNILO_HEIGHT / 16.0, spot.z + 0.35);
    }

    /**
     * @return the block in front of the mouth, from which one hops into the горнило and onto which
     *         one hops out
     */
    public static BlockPos front(BlockPos mouth, Direction facing) {
        return mouth.relative(facing);
    }

    /** The parts whose tops are the places on the stove: all but the one under the chimney. */
    public static final List<StovePart> TOP = List.of(StovePart.UPPER_FRONT_MIDDLE, StovePart.UPPER_FRONT_LEFT,
            StovePart.UPPER_FRONT_RIGHT, StovePart.UPPER_BACK_MIDDLE, StovePart.UPPER_BACK_LEFT);

    /**
     * @return the five places on top of the stove, the middle of each free top block, front middle
     *         first. A «killed» Kolobok rests on one of them, and a cat may take one.
     */
    public static List<Vec3> topSlots(BlockPos mouth, Direction facing) {
        List<Vec3> slots = new ArrayList<>(TOP.size());
        for (StovePart part : TOP) {
            slots.add(Vec3.atBottomCenterOf(posOf(mouth, facing, part).above()));
        }
        return slots;
    }

    /**
     * @return the ground beside the stove from which one jumps onto that top block: the outside
     *         neighbours of the block below it, at floor level
     */
    public static List<BlockPos> beside(BlockPos mouth, Direction facing, StovePart top) {
        BlockPos below = posOf(mouth, facing, top).below();
        List<BlockPos> out = new ArrayList<>(2);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos pos = below.relative(side);
            if (!isFootprint(mouth, facing, pos)) {
                out.add(pos);
            }
        }
        return out;
    }

    /**
     * @return the loaded stoves whose mouths are within that many blocks
     */
    public static List<StoveBlockEntity> nearby(Level level, BlockPos centre, int radius) {
        List<StoveBlockEntity> out = new ArrayList<>();
        int minX = SectionPos.blockToSectionCoord(centre.getX() - radius);
        int maxX = SectionPos.blockToSectionCoord(centre.getX() + radius);
        int minZ = SectionPos.blockToSectionCoord(centre.getZ() - radius);
        int maxZ = SectionPos.blockToSectionCoord(centre.getZ() + radius);
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                for (BlockEntity blockEntity : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (blockEntity instanceof StoveBlockEntity stove && !stove.isRemoved()
                            && stove.getBlockPos().closerThan(centre, radius)) {
                        out.add(stove);
                    }
                }
            }
        }
        return out;
    }

    private static boolean isFootprint(BlockPos mouth, Direction facing, BlockPos pos) {
        for (StovePart part : StovePart.values()) {
            if (part.layer() == 0 && posOf(mouth, facing, part).equals(pos)) {
                return true;
            }
        }
        return false;
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
