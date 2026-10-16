package ru.bagomot.kolobok.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import ru.bagomot.kolobok.entity.song.Syllable;

import java.util.HashMap;
import java.util.Map;

/**
 * Sound events. The voice is 49 syllable samples recorded on C4; song and speech both
 * play them at a pitch.
 *
 * <p>Each sample is registered twice, as {@code sing.<syllable>} and {@code mumble.<syllable>}. Both
 * point at the same file in sounds.json; they differ only in the subtitle, «Колобок поёт» against
 * «Колобок бормочет».
 */
public final class KSounds {

    public static final KRegistry<SoundEvent> REGISTRY = new KRegistry<>(Registries.SOUND_EVENT);

    private static final Map<String, KRegistry.Entry<SoundEvent, SoundEvent>> SING = new HashMap<>();
    private static final Map<String, KRegistry.Entry<SoundEvent, SoundEvent>> MUMBLE = new HashMap<>();

    static {
        for (String name : Syllable.allSampleNames()) {
            SING.put(name, event("sing." + name));
        }
        for (String name : Syllable.allSampleNames()) {
            MUMBLE.put(name, event("mumble." + name));
        }
    }

    private KSounds() {
    }

    private static KRegistry.Entry<SoundEvent, SoundEvent> event(String name) {
        return REGISTRY.register(name, key -> SoundEvent.createVariableRangeEvent(key.identifier()));
    }

    /**
     * @return the sung sample for a syllable; every {@link Syllable} has one
     */
    public static SoundEvent syllable(Syllable syllable) {
        return SING.get(syllable.sampleName()).get();
    }

    /**
     * @return the same sample under the mumbling subtitle
     */
    public static SoundEvent mumble(Syllable syllable) {
        return MUMBLE.get(syllable.sampleName()).get();
    }

    /** Loads the class, which declares the entries. */
    public static void init() {
    }
}
