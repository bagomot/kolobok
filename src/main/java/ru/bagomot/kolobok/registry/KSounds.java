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

    /** The Head from «Руслан и Людмила» asleep: a soft hum in and out, no rasp. */
    public static final KRegistry.Entry<SoundEvent, SoundEvent> SNORE = event("snore");
    /** The Head tickled: «а-а-апчхи», sung like everything else he says. */
    public static final KRegistry.Entry<SoundEvent, SoundEvent> SNEEZE = event("sneeze");
    /** Not his voice: a chicken's clucks, with his subtitle «ЧИКЕН-ЖОКЕЙ!». */
    public static final KRegistry.Entry<SoundEvent, SoundEvent> CHICKEN_JOCKEY = event("chicken_jockey");
    /** Soggy, he sneezes flour: the Head's sneeze, higher, under its own subtitle. */
    public static final KRegistry.Entry<SoundEvent, SoundEvent> SNEEZE_FLOUR = event("sneeze_flour");
    /** Rolling, his crust crunches: crisply when fresh, dry as a rusk when stale. Vanilla samples. */
    public static final KRegistry.Entry<SoundEvent, SoundEvent> CRUNCH = event("crunch");
    public static final KRegistry.Entry<SoundEvent, SoundEvent> CRUNCH_STALE = event("crunch_stale");
    /** «Боинг» as he jumps and «шлёп» as he lands. Vanilla samples. */
    public static final KRegistry.Entry<SoundEvent, SoundEvent> BOING = event("boing");
    public static final KRegistry.Entry<SoundEvent, SoundEvent> SPLAT = event("splat");
    /** Tickled with a feather, he squeaks: his own «и», high. */
    public static final KRegistry.Entry<SoundEvent, SoundEvent> SQUEAK = event("squeak");

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
