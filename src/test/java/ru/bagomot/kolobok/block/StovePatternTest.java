package ru.bagomot.kolobok.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StovePatternTest {

    private static final BlockPos MOUTH = new BlockPos(10, 64, -7);

    @Test
    void facingNorthTheChimneyIsOverTheBackRightCorner() {
        // In front of a north-facing stove one looks south; the right hand is west, the back south.
        assertEquals(new BlockPos(9, 66, -6), StovePattern.posOf(MOUTH, Direction.NORTH, StovePart.CHIMNEY));
        assertEquals(new BlockPos(11, 64, -7), StovePattern.posOf(MOUTH, Direction.NORTH, StovePart.FRONT_LEFT));
        assertEquals(new BlockPos(10, 65, -6), StovePattern.posOf(MOUTH, Direction.NORTH, StovePart.UPPER_BACK_MIDDLE));
    }

    @Test
    void facingEastTheRightHandIsNorth() {
        assertEquals(new BlockPos(9, 66, -8), StovePattern.posOf(MOUTH, Direction.EAST, StovePart.CHIMNEY));
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "EAST", "SOUTH", "WEST"})
    void thirteenBlocksInAThreeByTwoByTwoBoxAndTheChimneyAbove(Direction facing) {
        Set<BlockPos> cells = new HashSet<>();
        for (StovePart part : StovePart.values()) {
            BlockPos pos = StovePattern.posOf(MOUTH, facing, part);
            assertTrue(cells.add(pos), "two parts in one place: " + part);
            assertEquals(MOUTH, StovePattern.mouthOf(pos, facing, part), "the way back to the mouth: " + part);
            // Nothing stands in front of the furnace's face.
            BlockPos fromMouth = pos.subtract(MOUTH);
            assertTrue(fromMouth.getX() * facing.getStepX() + fromMouth.getZ() * facing.getStepZ() <= 0, part.name());
        }
        assertEquals(13, cells.size());
        var box = StovePattern.bounds(MOUTH, facing);
        assertEquals(3.0, box.getYsize(), "two layers and the chimney");
        assertEquals(6.0, box.getXsize() * box.getZsize());
        assertEquals(MOUTH.above(2).relative(facing.getOpposite()).relative(facing.getCounterClockWise()),
                StovePattern.posOf(MOUTH, facing, StovePart.CHIMNEY));
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "EAST", "SOUTH", "WEST"})
    void theBenchIsOnTopBetweenTheRowsAndClearOfTheChimney(Direction facing) {
        var bench = StovePattern.bench(MOUTH, facing);
        var box = StovePattern.bounds(MOUTH, facing);
        assertEquals(MOUTH.getY() + 2, bench.y, 1e-9, "on the top of the two layers");
        BlockPos front = StovePattern.posOf(MOUTH, facing, StovePart.UPPER_FRONT_MIDDLE);
        BlockPos back = StovePattern.posOf(MOUTH, facing, StovePart.UPPER_BACK_MIDDLE);
        assertEquals((front.getX() + back.getX()) / 2.0 + 0.5, bench.x, 1e-9);
        assertEquals((front.getZ() + back.getZ()) / 2.0 + 0.5, bench.z, 1e-9);
        assertTrue(box.contains(bench.x, bench.y - 0.01, bench.z), "above the stove, not beside it");
    }
}
