package ru.bagomot.kolobok.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * «Покажи дорогу»: where the nearest village is. The game's structure search is costly — it may
 * have to work out structure starts in chunks nobody has seen yet — so an answer is kept a game day
 * for its whole area of 4×4 chunks, and the server makes at most one new search a second, whoever
 * asks. The search stays on the server thread: it reads, and may generate, chunks, which no other
 * thread may touch.
 */
final class VillageFinder {

    private static final int CELL_SHIFT = 2;
    private static final int CELL_HALF_CHUNKS = 1 << (CELL_SHIFT - 1);
    private static final long KEEP_TICKS = 24000L;
    private static final int MAX_ANSWERS = 256;
    private static final int SEARCH_GAP = 20;

    /** Keyed by the dimension's key rather than the level, so that no closed world is held on to. */
    private static final Map<Area, Answer> ANSWERS = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Area, Answer> eldest) {
            return size() > MAX_ANSWERS;
        }
    };
    /** Server tick of the next search allowed. */
    private static int nextSearch;

    private VillageFinder() {
    }

    /**
     * @param village the nearest village within reach, or {@code null}
     * @param busy    whether the server was busy with another search, and there is no answer yet
     */
    record Result(@Nullable BlockPos village, boolean busy) {
    }

    /**
     * @return the nearest village to that point within that many chunks
     */
    static Result find(ServerLevel level, Vec3 from, int chunks) {
        ChunkPos chunk = ChunkPos.containing(BlockPos.containing(from));
        Area area = new Area(level.dimension(), chunk.x() >> CELL_SHIFT, chunk.z() >> CELL_SHIFT);
        long now = level.getGameTime();
        Answer answer = ANSWERS.get(area);
        if (answer == null || answer.until() <= now || answer.chunks() != chunks) {
            int tick = level.getServer().getTickCount();
            if (tick < nextSearch) {
                return new Result(null, true);
            }
            nextSearch = tick + SEARCH_GAP;
            answer = new Answer(search(level, area.centre(from.y), chunks + CELL_HALF_CHUNKS), chunks, now + KEEP_TICKS);
            ANSWERS.put(area, answer);
        }
        double range = chunks * 16.0;
        BlockPos village = answer.village();
        return new Result(village != null && Vec3.atBottomCenterOf(village).subtract(from).horizontalDistanceSqr() <= range * range
                ? village : null, false);
    }

    /** The server stopped: the next world's villages are elsewhere. */
    static void clear() {
        ANSWERS.clear();
        nextSearch = 0;
    }

    /**
     * @return the nearest village within that many chunks, or {@code null}. The game searches in steps
     *         of the villages' placement grid, 34 chunks for vanilla ones, not in chunks: the search is
     *         as many steps as cover the distance, and a village found beyond it does not count.
     */
    private static @Nullable BlockPos search(ServerLevel level, BlockPos from, int chunks) {
        int spacing = Integer.MAX_VALUE;
        for (Holder<Structure> village : level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getTagOrEmpty(StructureTags.VILLAGE)) {
            for (StructurePlacement placement : level.getChunkSource().getGeneratorState().getPlacementsForStructure(village)) {
                if (placement instanceof RandomSpreadStructurePlacement spread) {
                    spacing = Math.min(spacing, Math.max(1, spread.spacing()));
                }
            }
        }
        int steps = spacing == Integer.MAX_VALUE ? 1 : Math.max(1, (chunks + spacing - 1) / spacing);
        BlockPos village = level.findNearestMapStructure(StructureTags.VILLAGE, from, steps, false);
        double range = chunks * 16.0;
        return village != null && Vec3.atBottomCenterOf(village).subtract(Vec3.atBottomCenterOf(from)).horizontalDistanceSqr() <= range * range
                ? village : null;
    }

    private record Area(ResourceKey<Level> dimension, int x, int z) {

        BlockPos centre(double y) {
            int size = 16 << CELL_SHIFT;
            return BlockPos.containing(x * size + size / 2.0, y, z * size + size / 2.0);
        }
    }

    private record Answer(@Nullable BlockPos village, int chunks, long until) {
    }
}
