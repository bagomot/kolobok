package ru.bagomot.kolobok.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void fivePlacesOnTopInTheMiddlesOfTheBlocksAndNoneUnderTheChimney(Direction facing) {
        List<Vec3> places = StovePattern.topSlots(MOUTH, facing);
        assertEquals(5, places.size());
        assertEquals(5, new HashSet<>(places).size(), "two places in one");
        BlockPos chimney = StovePattern.posOf(MOUTH, facing, StovePart.CHIMNEY);
        AABB box = StovePattern.bounds(MOUTH, facing);
        for (Vec3 place : places) {
            assertEquals(MOUTH.getY() + 2, place.y, 1e-9, "on the top of the two layers");
            assertEquals(0.5, place.x - Math.floor(place.x), 1e-9, "in the middle of a block");
            assertEquals(0.5, place.z - Math.floor(place.z), 1e-9, "in the middle of a block");
            assertTrue(box.contains(place.x, place.y - 0.01, place.z), "above the stove, not beside it");
            assertNotEquals(chimney, BlockPos.containing(place), "in the chimney");
        }
        assertEquals(Vec3.atBottomCenterOf(MOUTH.above(2)), places.getFirst(), "front middle first");
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "EAST", "SOUTH", "WEST"})
    void theNearestTopPlaceIsTheOneSatOnAndNoneFromTheGround(Direction facing) {
        List<Vec3> places = StovePattern.topSlots(MOUTH, facing);
        for (int i = 0; i < places.size(); i++) {
            Vec3 aside = places.get(i).add(0.2, 0.0, -0.1);
            assertEquals(StovePattern.TOP.get(i), StovePattern.nearestTop(MOUTH, facing, aside, 0.5));
        }
        assertNull(StovePattern.nearestTop(MOUTH, facing, Vec3.atBottomCenterOf(StovePattern.front(MOUTH, facing)), 0.5),
                "in front of the stove");
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "EAST", "SOUTH", "WEST"})
    void pixelsCountFromTheOnlookersRightAndInwardFromTheFrontFace(Direction facing) {
        Vec3 bottom = Vec3.atBottomCenterOf(MOUTH);
        Vec3 out = Vec3.atLowerCornerOf(facing.getUnitVec3i()).scale(0.5);
        assertVec(bottom.add(out), StovePattern.local(MOUTH, facing, 8, 0, 0), "middle of the front face");
        assertVec(bottom.subtract(out), StovePattern.local(MOUTH, facing, 8, 0, 16), "middle of the back face");
        // Facing the stove, one's left hand is the stove's clockwise side.
        Vec3 left = Vec3.atLowerCornerOf(facing.getClockWise().getUnitVec3i()).scale(0.5);
        assertVec(bottom.add(out).add(left), StovePattern.local(MOUTH, facing, 16, 0, 0), "front left corner");
        assertVec(bottom.add(0, 1, 0), StovePattern.local(MOUTH, facing, 8, 16, 8), "top middle");
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "EAST", "SOUTH", "WEST"})
    void theKolobokSitsInTheGorniloAboveThePodpechyeWithTheCoalsBehindHim(Direction facing) {
        Vec3 spot = StovePattern.gornilo(MOUTH, facing);
        assertEquals(MOUTH, BlockPos.containing(spot), "in the mouth's block");
        assertTrue(spot.y > MOUTH.getY() + StovePattern.PODPECHYE_Y1 / 16.0, "above the подпечье");
        Vec3 coals = StovePattern.local(MOUTH, facing, 8, StovePattern.GORNILO_FLOOR, StovePattern.COALS_FROM);
        Vec3 back = Vec3.atLowerCornerOf(facing.getOpposite().getUnitVec3i());
        assertTrue(coals.subtract(spot).dot(back) > 0, "the coals are deeper in");
        AABB space = StovePattern.gorniloSpace(MOUTH, facing);
        assertTrue(space.contains(spot.add(0, 0.01, 0)), "his space is where he sits");
        assertEquals(StovePattern.front(MOUTH, facing), MOUTH.relative(facing));
    }

    private static void assertVec(Vec3 expected, Vec3 actual, String what) {
        assertEquals(0.0, expected.distanceTo(actual), 1e-9, what + ": " + actual);
    }
}
