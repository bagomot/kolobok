package ru.bagomot.kolobok.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;
import ru.bagomot.kolobok.Kolobok;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every assembled Russian stove on the server and which of its five places on top belongs to which
 * Kolobok. Kept with the world, not in the stoves' chunks: a Kolobok «killed» far from his stove,
 * even in another dimension, finds his place on it without loading a chunk.
 *
 * <p>A stove goes on the list when it is assembled, or when its block entity turns up in a level
 * unlisted (placed from a structure, or saved before there was a list), and comes off it when the
 * stove comes apart. A Kolobok takes a place when he is baked or when a rebuilt stove takes him in,
 * and frees it when he is gone for good.
 */
public final class StoveRegistry extends SavedData {

    /** The places on top, as {@link StovePattern#topSlots} lists them. */
    public static final int PLACES = StovePattern.TOP.size();

    private record Place(UUID kolobok, int index) {
        static final Codec<Place> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("kolobok").forGetter(Place::kolobok),
                Codec.intRange(0, PLACES - 1).fieldOf("place").forGetter(Place::index)
        ).apply(i, Place::new));
    }

    /**
     * Where a stove's mouth stands. Not {@link net.minecraft.core.GlobalPos}, whose codec pulls in the
     * whole of {@link Level} and so keeps this class out of plain unit tests.
     */
    private record Spot(ResourceKey<Level> dimension, BlockPos mouth) {
    }

    private record Entry(Spot spot, Direction facing, List<Place> places) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(e -> e.spot().dimension()),
                BlockPos.CODEC.fieldOf("pos").forGetter(e -> e.spot().mouth()),
                Direction.CODEC.fieldOf("facing").forGetter(Entry::facing),
                Place.CODEC.listOf().optionalFieldOf("places", List.of()).forGetter(Entry::places)
        ).apply(i, (dimension, mouth, facing, places) -> new Entry(new Spot(dimension, mouth), facing, places)));
    }

    static final Codec<StoveRegistry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.listOf().optionalFieldOf("stoves", List.of()).forGetter(StoveRegistry::entries)
    ).apply(i, StoveRegistry::new));

    // Command storage is free-form NBT that no datafixer touches; so is this.
    private static final SavedDataType<StoveRegistry> TYPE = new SavedDataType<>(
            Kolobok.id("stoves"), StoveRegistry::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    /** A listed stove: which way it faces and who has each place, {@code null} for a free one. */
    private static final class Stove {
        final Direction facing;
        final @Nullable UUID[] places = new UUID[PLACES];

        Stove(Direction facing) {
            this.facing = facing;
        }
    }

    private final Map<Spot, Stove> stoves = new HashMap<>();

    public StoveRegistry() {
    }

    private StoveRegistry(List<Entry> entries) {
        for (Entry entry : entries) {
            Stove stove = new Stove(entry.facing());
            for (Place place : entry.places()) {
                stove.places[place.index()] = place.kolobok();
            }
            stoves.put(entry.spot(), stove);
        }
    }

    public static StoveRegistry of(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    private List<Entry> entries() {
        List<Entry> out = new ArrayList<>(stoves.size());
        stoves.forEach((spot, stove) -> {
            List<Place> places = new ArrayList<>();
            for (int i = 0; i < PLACES; i++) {
                UUID kolobok = stove.places[i];
                if (kolobok != null) {
                    places.add(new Place(kolobok, i));
                }
            }
            out.add(new Entry(spot, stove.facing, places));
        });
        return out;
    }

    // ------------------------------------------------------------------------------------ stoves

    /**
     * A stove has just been assembled there: listed with every place free.
     */
    public void add(ResourceKey<Level> dimension, BlockPos mouth, Direction facing) {
        stoves.put(new Spot(dimension, mouth), new Stove(facing));
        setDirty();
    }

    /**
     * Lists a stove found standing that is not listed yet, with the Kolobki it remembered itself
     * before there was a list, as many as there are places.
     */
    public void addIfMissing(ResourceKey<Level> dimension, BlockPos mouth, Direction facing, List<UUID> kolobki) {
        Spot spot = new Spot(dimension, mouth);
        if (stoves.containsKey(spot)) {
            return;
        }
        Stove stove = new Stove(facing);
        for (int i = 0; i < Math.min(PLACES, kolobki.size()); i++) {
            stove.places[i] = kolobki.get(i);
        }
        stoves.put(spot, stove);
        setDirty();
    }

    /**
     * The stove there came apart. Its Kolobki keep it as their home: if it is built again in the same
     * place, it takes them back in while it has room.
     */
    public void remove(ResourceKey<Level> dimension, BlockPos mouth) {
        if (stoves.remove(new Spot(dimension, mouth)) != null) {
            setDirty();
        }
    }

    /**
     * @return which way the stove there faces, or {@code null} if no stove stands there
     */
    public @Nullable Direction facing(ResourceKey<Level> dimension, BlockPos mouth) {
        Stove stove = stoves.get(new Spot(dimension, mouth));
        return stove == null ? null : stove.facing;
    }

    // ------------------------------------------------------------------------------------ places

    /**
     * @return how many places on that stove are taken
     */
    public int taken(ResourceKey<Level> dimension, BlockPos mouth) {
        Stove stove = stoves.get(new Spot(dimension, mouth));
        int taken = 0;
        if (stove != null) {
            for (UUID kolobok : stove.places) {
                if (kolobok != null) {
                    taken++;
                }
            }
        }
        return taken;
    }

    public boolean isFull(ResourceKey<Level> dimension, BlockPos mouth) {
        return taken(dimension, mouth) >= PLACES;
    }

    public boolean remembers(ResourceKey<Level> dimension, BlockPos mouth, UUID kolobok) {
        return placeOf(dimension, mouth, kolobok) >= 0;
    }

    /**
     * Gives that Kolobok a place on the stove, the first free one, unless he has one already.
     *
     * @return his place, counted as {@link StovePattern#topSlots} lists them, or -1 if no stove stands
     *         there or it has no room
     */
    public int adopt(ResourceKey<Level> dimension, BlockPos mouth, UUID kolobok) {
        int place = placeOf(dimension, mouth, kolobok);
        Stove stove = stoves.get(new Spot(dimension, mouth));
        if (place >= 0 || stove == null) {
            return place;
        }
        for (int i = 0; i < PLACES; i++) {
            if (stove.places[i] == null) {
                stove.places[i] = kolobok;
                setDirty();
                return i;
            }
        }
        return -1;
    }

    /**
     * He is gone for good: his place, wherever it was, is free, and that stove can bake another.
     */
    public void forget(UUID kolobok) {
        for (Stove stove : stoves.values()) {
            for (int i = 0; i < PLACES; i++) {
                if (kolobok.equals(stove.places[i])) {
                    stove.places[i] = null;
                    setDirty();
                }
            }
        }
    }

    private int placeOf(ResourceKey<Level> dimension, BlockPos mouth, UUID kolobok) {
        Stove stove = stoves.get(new Spot(dimension, mouth));
        if (stove != null) {
            for (int i = 0; i < PLACES; i++) {
                if (kolobok.equals(stove.places[i])) {
                    return i;
                }
            }
        }
        return -1;
    }
}
