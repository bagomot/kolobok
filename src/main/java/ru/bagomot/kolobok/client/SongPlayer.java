package ru.bagomot.kolobok.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import ru.bagomot.kolobok.config.KConfig;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.entity.song.Song;
import ru.bagomot.kolobok.entity.song.SongLine;
import ru.bagomot.kolobok.registry.KSounds;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Plays the songs on the client, frame by frame. Ticks are too coarse: a sixteenth of Колобочек is
 * 80 ms and a tick is 50, so every frame plays the samples whose moment has come. No packets: the
 * song is composed here from the synced start, seed and verse.
 */
public final class SongPlayer {

    private static final float VOLUME = 0.8F;
    /** A late joiner starts with the note being sung, not with the whole song in one burst. */
    private static final double CATCH_UP_SECONDS = 0.05;

    private static final Set<KolobokEntity> SINGING = Collections.newSetFromMap(new WeakHashMap<>());

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
                continue;
            }
            double now = elapsed(entity, partialTick);
            List<Song.Sample> samples = song.samples();
            while (entity.clientSongNext < samples.size() && samples.get(entity.clientSongNext).time() <= now) {
                play(entity, samples.get(entity.clientSongNext++));
            }
            int line = song.lineAt(now);
            entity.clientSongLine = line >= 0 && now < song.seconds() ? subtitle(song.lines().get(line).text()) : null;
            if (entity.clientSongNext >= samples.size() && now >= song.seconds()) {
                it.remove();
            }
        }
    }

    private static double elapsed(KolobokEntity entity, float partialTick) {
        return (entity.level().getGameTime() - entity.clientSongStart + partialTick) / 20.0;
    }

    private static void play(KolobokEntity entity, Song.Sample sample) {
        entity.clientMouthTicks = 3;
        float volume = VOLUME * sample.volume() * KConfig.client().voiceVolume;
        if (volume <= 0.0F || entity.isSilent()) {
            return;
        }
        float pitch = Mth.clamp(sample.pitch() * entity.voice(), 0.5F, 2.0F);
        entity.level().playLocalSound(entity.getX(), entity.getY() + 0.3, entity.getZ(),
                KSounds.syllable(sample.syllable()), SoundSource.NEUTRAL, volume, pitch, false);
    }

    /**
     * The line in the player's language for the bubble. The song itself is Russian everywhere.
     */
    static Component subtitle(SongLine line) {
        Component text;
        if (line.mob() == null) {
            text = Component.translatable(line.subtitle());
        } else {
            Identifier id = Identifier.parse(line.mob());
            String key = "kolobok.verse." + id.getNamespace() + "." + id.getPath();
            Component mob = Language.getInstance().has(key)
                    ? Component.translatable(key)
                    : BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(type -> type.getDescription())
                    .orElse(Component.literal(id.getPath()));
            text = Component.translatable(line.subtitle(), mob);
        }
        return Component.literal("♪ ").append(text);
    }
}
