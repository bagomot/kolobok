package ru.bagomot.kolobok.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import ru.bagomot.kolobok.config.KConfig;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.entity.SongWords;
import ru.bagomot.kolobok.entity.song.Song;
import ru.bagomot.kolobok.entity.song.SongLine;
import ru.bagomot.kolobok.registry.KSounds;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Plays the songs on the client, frame by frame. Ticks are too coarse: a sixteenth of Колобочек is
 * 80 ms and a tick is 50, so every frame plays the samples whose moment has come. No packets: the
 * song is composed here from the synced start, seed and verse.
 */
public final class SongPlayer {

    private static final float VOLUME = 0.8F;
    /** His voice is gone this far away and at full volume this close. */
    private static final double HEARD_RANGE = 16.0;
    private static final double FULL_RANGE = 2.0;
    /**
     * A late joiner starts with the note being sung, not with the whole song in one burst; after a
     * frozen frame or a jump in game time the singing goes on the same way.
     */
    private static final double CATCH_UP_SECONDS = 0.05;
    /** The newest note due is still sung this late, so that a slow frame never leaves him silent. */
    private static final double SLOW_FRAME_SECONDS = 0.25;

    private static final Set<KolobokEntity> SINGING = Collections.newSetFromMap(new WeakHashMap<>());
    /**
     * The subtitle each singer shows, by UUID: made when his line changes rather than every frame.
     * Only words, nothing that holds a level.
     */
    private static final Map<UUID, Subtitle> SUBTITLES = new HashMap<>();
    private static final KolobokClient.LevelWatch SUBTITLES_LEVEL = new KolobokClient.LevelWatch();

    /** The subtitle of a line, and which song and which of its lines it is. */
    private record Subtitle(int songStart, int line, Component text) {
    }

    private SongPlayer() {
    }

    /**
     * Called from the entity's client tick: notices a song starting or ending.
     */
    static void tick(KolobokEntity entity) {
        int start = entity.getSingStart();
        if (start == 0) {
            if (entity.clientSong != null) {
                entity.clientSong = null;
                entity.clientSongLine = null;
            }
            return;
        }
        if (entity.clientSong == null || entity.clientSongStart != start) {
            Song song = entity.composeSong();
            entity.clientSong = song;
            entity.clientSongStart = start;
            double now = elapsed(entity, 0.0F);
            int next = 0;
            while (next < song.samples().size() && song.samples().get(next).time() < now - CATCH_UP_SECONDS) {
                next++;
            }
            entity.clientSongNext = next;
            SINGING.add(entity);
        }
    }

    /**
     * Called once per rendered frame by the loader's frame event.
     */
    public static void frame() {
        if (SUBTITLES_LEVEL.changed()) {
            SUBTITLES.clear();
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || SINGING.isEmpty()) {
            SINGING.clear();
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Iterator<KolobokEntity> it = SINGING.iterator();
        while (it.hasNext()) {
            KolobokEntity entity = it.next();
            Song song = entity.clientSong;
            if (song == null || entity.isRemoved() || entity.level() != level) {
                it.remove();
                SUBTITLES.remove(entity.getUUID());
                continue;
            }
            double now = elapsed(entity, partialTick);
            List<Song.Sample> samples = song.samples();
            int due = entity.clientSongNext;
            while (due < samples.size() && samples.get(due).time() <= now) {
                due++;
            }
            // Notes long overdue, after a frozen frame or a jump in game time, are dropped rather than
            // sung all at once.
            for (int i = entity.clientSongNext; i < due; i++) {
                double late = now - samples.get(i).time();
                if (late <= CATCH_UP_SECONDS || i == due - 1 && late <= SLOW_FRAME_SECONDS) {
                    play(entity, samples.get(i));
                }
            }
            entity.clientSongNext = due;
            int line = now < song.seconds() ? song.lineAt(now) : -1;
            entity.clientSongLine = line >= 0 ? subtitle(entity, song, line) : null;
            if (due >= samples.size() && now >= song.seconds()) {
                it.remove();
                SUBTITLES.remove(entity.getUUID());
            }
        }
    }

    /**
     * @return the subtitle of that line of his song, the same object for as long as he sings it
     */
    private static Component subtitle(KolobokEntity entity, Song song, int line) {
        Subtitle shown = SUBTITLES.get(entity.getUUID());
        if (shown == null || shown.songStart() != entity.clientSongStart || shown.line() != line) {
            shown = new Subtitle(entity.clientSongStart, line, subtitle(song.lines().get(line).text()));
            SUBTITLES.put(entity.getUUID(), shown);
        }
        return shown.text();
    }

    /**
     * @return seconds since his song began
     */
    static double elapsed(KolobokEntity entity, float partialTick) {
        return (entity.level().getGameTime() - entity.clientSongStart + partialTick) / 20.0;
    }

    private static void play(KolobokEntity entity, Song.Sample sample) {
        entity.clientMouthTicks = 3;
        float volume = VOLUME * sample.volume() * KConfig.client().voiceVolume;
        if (volume <= 0.0F || entity.isSilent()) {
            return;
        }
        float pitch = Mth.clamp(sample.pitch() * entity.voice(), 0.5F, 2.0F);
        voice(entity, KSounds.syllable(sample.syllable()), volume, pitch);
    }

    /**
     * Plays a syllable of his, sung or spoken. The game fades a sound out over sixteen blocks only
     * at its quietest, so the song carried across half a village; his voice is full within a couple
     * of blocks and gone at sixteen. The game's own fading is off for it so that only this curve
     * counts; the sound still comes from where he is.
     */
    static void voice(KolobokEntity entity, SoundEvent sound, float volume, float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 at = entity.position().add(0.0, 0.3, 0.0);
        float loud = volume * falloff(minecraft.getSoundManager().getListenerTransform().position().distanceTo(at));
        if (loud <= 0.0F) {
            return;
        }
        minecraft.getSoundManager().play(new SimpleSoundInstance(sound.location(), SoundSource.NEUTRAL, loud, pitch,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, at.x, at.y, at.z, false));
    }

    /**
     * @return how loud he is that far away, from 1 to 0: full close by, then falling off as a square
     */
    public static float falloff(double distance) {
        if (distance <= FULL_RANGE) {
            return 1.0F;
        }
        if (distance >= HEARD_RANGE) {
            return 0.0F;
        }
        double near = (HEARD_RANGE - distance) / (HEARD_RANGE - FULL_RANGE);
        return (float) (near * near);
    }

    /**
     * The line in the player's language for the bubble. The song itself is Russian everywhere.
     */
    static Component subtitle(SongLine line) {
        return Component.literal("♪ ").append(SongWords.line(line));
    }
}
