package ru.bagomot.kolobok.block;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoveRegistryTest {

    private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    private static final ResourceKey<Level> NETHER = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_nether"));
    private static final BlockPos MOUTH = new BlockPos(10, 64, -7);

    private static List<UUID> kolobki(int count) {
        List<UUID> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(UUID.randomUUID());
        }
        return out;
    }

    @Test
    void fivePlacesTakenInOrderAndNoMore() {
        StoveRegistry registry = new StoveRegistry();
        registry.add(OVERWORLD, MOUTH, Direction.SOUTH);
        List<UUID> five = kolobki(StoveRegistry.PLACES);
        for (int i = 0; i < five.size(); i++) {
            assertEquals(i, registry.adopt(OVERWORLD, MOUTH, five.get(i)));
        }
        assertTrue(registry.isFull(OVERWORLD, MOUTH));
        assertEquals(-1, registry.adopt(OVERWORLD, MOUTH, UUID.randomUUID()));
        // One who has a place keeps it.
        assertEquals(2, registry.adopt(OVERWORLD, MOUTH, five.get(2)));
    }

    @Test
    void oneGoneForGoodFreesHisPlaceAndTheOthersKeepTheirs() {
        StoveRegistry registry = new StoveRegistry();
        registry.add(OVERWORLD, MOUTH, Direction.SOUTH);
        List<UUID> five = kolobki(StoveRegistry.PLACES);
        five.forEach(k -> registry.adopt(OVERWORLD, MOUTH, k));
        registry.forget(five.get(1));
        assertEquals(4, registry.taken(OVERWORLD, MOUTH));
        assertFalse(registry.remembers(OVERWORLD, MOUTH, five.get(1)));
        UUID next = UUID.randomUUID();
        assertEquals(1, registry.adopt(OVERWORLD, MOUTH, next));
        assertEquals(3, registry.adopt(OVERWORLD, MOUTH, five.get(3)));
    }

    @Test
    void withNoStoveThereIsNoPlace() {
        StoveRegistry registry = new StoveRegistry();
        UUID kolobok = UUID.randomUUID();
        assertNull(registry.facing(OVERWORLD, MOUTH));
        assertEquals(-1, registry.adopt(OVERWORLD, MOUTH, kolobok));
        registry.add(OVERWORLD, MOUTH, Direction.EAST);
        assertEquals(0, registry.adopt(OVERWORLD, MOUTH, kolobok));
        // The same spot in the Nether is another place, with no stove.
        assertEquals(-1, registry.adopt(NETHER, MOUTH, kolobok));
        registry.remove(OVERWORLD, MOUTH);
        assertNull(registry.facing(OVERWORLD, MOUTH));
        assertEquals(-1, registry.adopt(OVERWORLD, MOUTH, kolobok));
    }

    @Test
    void builtAgainItStartsEmpty() {
        StoveRegistry registry = new StoveRegistry();
        registry.add(OVERWORLD, MOUTH, Direction.SOUTH);
        UUID old = UUID.randomUUID();
        registry.adopt(OVERWORLD, MOUTH, old);
        registry.remove(OVERWORLD, MOUTH);
        registry.add(OVERWORLD, MOUTH, Direction.WEST);
        assertEquals(Direction.WEST, registry.facing(OVERWORLD, MOUTH));
        assertEquals(0, registry.taken(OVERWORLD, MOUTH));
        // His home stands again: it takes him back in.
        assertEquals(0, registry.adopt(OVERWORLD, MOUTH, old));
    }

    @Test
    void aStoveFoundUnlistedKeepsTheKolobkiItRemembered() {
        StoveRegistry registry = new StoveRegistry();
        List<UUID> two = kolobki(2);
        registry.addIfMissing(OVERWORLD, MOUTH, Direction.NORTH, two);
        assertEquals(Direction.NORTH, registry.facing(OVERWORLD, MOUTH));
        assertEquals(0, registry.adopt(OVERWORLD, MOUTH, two.get(0)));
        assertEquals(1, registry.adopt(OVERWORLD, MOUTH, two.get(1)));
        // Listed already: found again, it changes nothing.
        registry.addIfMissing(OVERWORLD, MOUTH, Direction.SOUTH, kolobki(3));
        assertEquals(Direction.NORTH, registry.facing(OVERWORLD, MOUTH));
        assertEquals(2, registry.taken(OVERWORLD, MOUTH));
        // More than it has room for: the first five.
        List<UUID> seven = kolobki(7);
        registry.addIfMissing(NETHER, MOUTH, Direction.NORTH, seven);
        assertTrue(registry.isFull(NETHER, MOUTH));
        assertFalse(registry.remembers(NETHER, MOUTH, seven.get(5)));
    }

    @Test
    void placesSurviveSavingAndLoading() {
        StoveRegistry registry = new StoveRegistry();
        registry.add(OVERWORLD, MOUTH, Direction.SOUTH);
        registry.add(NETHER, MOUTH, Direction.WEST);
        List<UUID> three = kolobki(3);
        three.forEach(k -> registry.adopt(OVERWORLD, MOUTH, k));
        registry.forget(three.getFirst());
        UUID inNether = UUID.randomUUID();
        registry.adopt(NETHER, MOUTH, inNether);

        JsonElement json = StoveRegistry.CODEC.encodeStart(JsonOps.INSTANCE, registry).getOrThrow();
        StoveRegistry loaded = StoveRegistry.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(Direction.SOUTH, loaded.facing(OVERWORLD, MOUTH));
        assertEquals(Direction.WEST, loaded.facing(NETHER, MOUTH));
        assertEquals(2, loaded.taken(OVERWORLD, MOUTH));
        assertEquals(1, loaded.adopt(OVERWORLD, MOUTH, three.get(1)));
        assertEquals(2, loaded.adopt(OVERWORLD, MOUTH, three.get(2)));
        // The hole the first one left is still free, and first.
        assertEquals(0, loaded.adopt(OVERWORLD, MOUTH, UUID.randomUUID()));
        assertEquals(0, loaded.adopt(NETHER, MOUTH, inNether));
    }
}
