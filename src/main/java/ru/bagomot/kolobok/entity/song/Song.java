package ru.bagomot.kolobok.entity.song;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A song ready to be played: every sample with its moment, relative pitch and volume.
 *
 * <p>The server and the clients compose the same song from the same synced inputs. The server needs
 * only its length, to know when he is done; each client plays the notes as their moment comes.
 */
public final class Song {

    /** An eighth lasts this long at tempo 1. */
    public static final double EIGHTH_SECONDS = 0.2;
    /** The last sample keeps ringing this long after its note ends. */
    private static final double TAIL_SECONDS = 0.3;

    /**
     * One sample to play.
     *
     * @param syllable which sample
     * @param time     seconds from the start of the song
     * @param pitch    relative pitch; the singer's voice multiplies it
     * @param volume   relative volume, accents and echoes included
     * @param line     index of the line the note belongs to
     */
    public record Sample(Syllable syllable, double time, float pitch, float volume, int line) {
    }

    private final List<Melody.Line> lines;
    private final List<Sample> samples;
    private final double[] lineStarts;
    private final double seconds;

    private Song(List<Melody.Line> lines, List<Sample> samples, double[] lineStarts, double seconds) {
        this.lines = lines;
        this.samples = samples;
        this.lineStarts = lineStarts;
        this.seconds = seconds;
    }

    /**
     * @param seed      the singing's seed: which mobs, where Herobrine goes
     * @param part      0 for the whole song, otherwise one verse, counted from 1
     * @param mobs      every mob he has a verse about, in the order he got them
     * @param newest    the verse just earned, if he sings about it
     * @param herobrine whether Herobrine sneaks in
     */
    public static Song compose(int seed, int part, List<String> mobs, @Nullable String newest, boolean herobrine,
                               Voice voice) {
        List<SongLine> text = SongText.part(SongText.lines(seed, mobs, newest, herobrine), part);
        List<Melody.Line> lines = Melody.timeline(text, voice.ornament());
        double eighth = EIGHTH_SECONDS * voice.tempo();
        List<Sample> samples = new ArrayList<>();
        double[] lineStarts = new double[lines.size()];
        int k = 0;
        double end = 0;
        for (int li = 0; li < lines.size(); li++) {
            Melody.Line line = lines.get(li);
            lineStarts[li] = line.start() * eighth;
            for (Melody.Note note : line.notes()) {
                float wobble = voice.wobble() == 0 ? 1.0F : (k++ % 2 == 1 ? 1 + voice.wobble() : 1 - voice.wobble());
                float pitch = (float) Math.pow(2.0, (note.midi() - 72) / 12.0) * wobble;
                float volume = voice.volume() * (note.syllable().stressed() ? voice.accent() : 1.0F);
                double time = note.start() * eighth;
                samples.add(new Sample(note.syllable(), time, pitch, volume, li));
                if (voice.echo()) {
                    samples.add(new Sample(note.syllable(), time + Voice.ECHO_DELAY, pitch,
                            volume * Voice.ECHO_VOLUME, li));
                }
                end = Math.max(end, note.end() * eighth);
            }
        }
        samples.sort(Comparator.comparingDouble(Sample::time));
        return new Song(lines, Collections.unmodifiableList(samples), lineStarts, end + TAIL_SECONDS);
    }

    public List<Melody.Line> lines() {
        return lines;
    }

    /**
     * @return every sample in order of time
     */
    public List<Sample> samples() {
        return samples;
    }

    public double seconds() {
        return seconds;
    }

    /**
     * @return the song's length in game ticks, rounded up
     */
    public int ticks() {
        return (int) Math.ceil(seconds * 20.0);
    }

    /**
     * @return the line being sung at that moment, or -1 before the first one starts
     */
    public int lineAt(double time) {
        int current = -1;
        for (int i = 0; i < lineStarts.length; i++) {
            if (lineStarts[i] <= time) {
                current = i;
            }
        }
        return current;
    }
}
