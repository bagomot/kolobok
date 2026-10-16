package ru.bagomot.kolobok.entity.song;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sets syllables to notes: a children's song in 2/4, two bars to a line, C major from G4 to G5.
 *
 * <p>A line-for-line port of {@code fitLine()} and {@code songTimeline()} from the songbook
 * prototype. Syllables before the first stress are the upbeat; the first stress falls on the strong
 * beat of the first bar, the last syllable on the strong beat of the second bar and holds until the
 * next line's upbeat. Lines that rhyme in words rhyme in the melody too: the same rhythm and a
 * similar turn at the end.
 *
 * <p>Times and lengths are in eighths. Pitches are written MIDI notes; the samples are recorded on
 * C4, so written C5 (72) plays them at pitch 1 and the tune sounds an octave below the page.
 */
public final class Melody {

    /** Highest written note, G5: an ornament never climbs past it. */
    static final int TOP = 79;
    private static final int[] SCALE = {0, 2, 4, 5, 7, 9, 11};

    /**
     * The tune of one line: the approach to the first stress, the path from it to the next-to-last
     * note, and the last note.
     */
    public enum Template {
        /** соль-до-ре | МИ ми ре | ДО */
        TITLE(new int[]{67, 72, 74}, new int[]{76, 76, 74}, 72),
        /** ми фа | СОЛЬ ми до | РЕ — a question. */
        Q1(new int[]{76, 77}, new int[]{79, 76, 72}, 74),
        /** The same a step lower, answered on C. */
        A1(new int[]{74, 76}, new int[]{77, 74, 71}, 72),
        Q2(new int[]{76, 77}, new int[]{79, 77, 76}, 74),
        /** «В печку» goes down. */
        DOWN(new int[]{69, 71}, new int[]{72, 71, 69}, 67),
        /** «На окошко» goes up. */
        UP(new int[]{67, 69}, new int[]{71, 72, 74}, 76),
        /** «ушёл» as a question. */
        Q3(new int[]{67, 67}, new int[]{72, 72, 74, 76}, 74),
        /** «ушёл» as the answer. */
        A3(new int[]{67, 67}, new int[]{76, 76, 74, 71}, 72),
        /** The third mob climbs towards the end. */
        CLIMB(new int[]{67, 69}, new int[]{74, 76, 77}, 79),
        /** «и от те-БЯ у-ДУ!» */
        END(new int[]{76, 76, 76}, new int[]{79, 74}, 72);

        private final int[] pick;
        private final int[] path;
        private final int fin;

        Template(int[] pick, int[] path, int fin) {
            this.pick = pick;
            this.path = path;
            this.fin = fin;
        }
    }

    /**
     * One sung note.
     *
     * @param syllable what is sung; for the held half of an ornament, the bare vowel
     * @param midi     written pitch
     * @param start    onset in eighths from the start of the song
     * @param length   length in eighths
     * @param tie      the held half of an ornament: the voice glides into it from {@code fromMidi}
     * @param fromMidi where a tie glides from; equal to {@code midi} otherwise
     */
    public record Note(Syllable syllable, int midi, double start, double length, boolean tie, int fromMidi) {

        public double end() {
            return start + length;
        }
    }

    /**
     * One line set to music.
     *
     * @param text     the words
     * @param start    onset of its first note, in eighths
     * @param downbeat the strong beat of its first bar, in eighths
     * @param notes    its notes in order
     */
    public record Line(SongLine text, double start, double downbeat, List<Note> notes) {

        public double end() {
            return notes.getLast().end();
        }
    }

    private enum Part {
        PICK, MID, FIN
    }

    /** A note while the line is still being laid out. */
    private static final class Draft {
        Syllable syllable;
        int midi;
        double length;
        Part part;
        boolean tie;
        int fromMidi;

        Draft(Syllable syllable, int midi, double length, Part part) {
            this.syllable = syllable;
            this.midi = midi;
            this.length = length;
            this.part = part;
            this.fromMidi = midi;
        }
    }

    private Melody() {
    }

    /**
     * Lays the lines out in time.
     *
     * @param ornament the crown's grand manner: the last syllable of a line is sung as two notes,
     *                 a step up and back down
     */
    public static List<Line> timeline(List<SongLine> lines, boolean ornament) {
        List<List<Draft>> fitted = new ArrayList<>(lines.size());
        for (SongLine line : lines) {
            fitted.add(fit(Phonetics.syllables(line.sung()), line.template()));
        }
        List<Line> out = new ArrayList<>(lines.size());
        double down = sum(fitted.getFirst(), Part.PICK);
        for (int i = 0; i < lines.size(); i++) {
            List<Draft> notes = fitted.get(i);
            List<Draft> next = i + 1 < lines.size() ? fitted.get(i + 1) : null;
            int gap = lines.get(i).breath() ? 2 : 0;
            double midLength = sum(notes, Part.MID);
            double finLength = next != null ? Math.max(1, 4 + gap - sum(next, Part.PICK)) : 6;
            Draft fin = notes.removeLast();
            fin.length = finLength;
            if (ornament && finLength >= 2) {
                int up = fin.midi < TOP ? stepToward(fin.midi, fin.midi + 12) : stepToward(fin.midi, fin.midi - 12);
                Draft high = new Draft(fin.syllable, up, 1, Part.FIN);
                Draft held = new Draft(new Syllable(Syllable.NO_ONSET, fin.syllable.vowel(), false), fin.midi,
                        finLength - 1, Part.FIN);
                held.tie = true;
                held.fromMidi = up;
                notes.add(high);
                notes.add(held);
            } else {
                notes.add(fin);
            }
            double t = down - sum(notes, Part.PICK);
            double start = t;
            List<Note> timed = new ArrayList<>(notes.size());
            for (Draft d : notes) {
                timed.add(new Note(d.syllable, d.midi, t, d.length, d.tie, d.fromMidi));
                t += d.length;
            }
            out.add(new Line(lines.get(i), start, down, Collections.unmodifiableList(timed)));
            down += midLength + 4 + gap;
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Fits one line's syllables to its template. Without a stress mark, or with only the last
     * syllable stressed, the stress goes three syllables from the end.
     */
    private static List<Draft> fit(List<Syllable> syllables, Template template) {
        int n = syllables.size();
        if (n == 0) {
            throw new IllegalArgumentException("A line with nothing to sing");
        }
        int s1 = -1;
        for (int i = 0; i < n; i++) {
            if (syllables.get(i).stressed()) {
                s1 = i;
                break;
            }
        }
        if (s1 < 0 || s1 >= n - 1) {
            s1 = Math.max(0, n - 3);
        }
        int pickCount = s1;
        int midCount = n - 1 - s1;
        double[] pickLengths = pickLengths(pickCount);
        double[] midLengths = midLengths(midCount);
        int last = template.path.length - 1;
        List<Draft> out = new ArrayList<>(n + 1);
        for (int i = 0; i < pickCount; i++) {
            int m = template.pick[Math.max(0, template.pick.length - pickCount + i)];
            out.add(new Draft(syllables.get(i), m, pickLengths[i], Part.PICK));
        }
        for (int j = 0; j < midCount; j++) {
            int m = template.path[midCount == 1 ? 0 : (int) Math.round((double) j * last / (midCount - 1))];
            out.add(new Draft(syllables.get(s1 + j), m, midLengths[j], Part.MID));
        }
        out.add(new Draft(syllables.get(n - 1), template.fin, 4, Part.FIN));
        return out;
    }

    /** One upbeat syllable is an eighth, two are two eighths, three an eighth and two sixteenths. */
    private static double[] pickLengths(int count) {
        return switch (count) {
            case 1 -> new double[]{1};
            case 2 -> new double[]{1, 1};
            case 3 -> new double[]{1, 0.5, 0.5};
            default -> filled(count, 0.5);
        };
    }

    /** The syllables from the first stress to the last fill one bar. */
    private static double[] midLengths(int count) {
        return switch (count) {
            case 1 -> new double[]{4};
            case 2 -> new double[]{2, 2};
            case 3 -> new double[]{2, 1, 1};
            case 4 -> new double[]{1, 1, 1, 1};
            case 5 -> new double[]{1, 1, 1, 0.5, 0.5};
            case 6 -> new double[]{1, 1, 0.5, 0.5, 0.5, 0.5};
            case 7 -> new double[]{1, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5};
            default -> filled(count, 0.5);
        };
    }

    private static double[] filled(int count, double value) {
        double[] a = new double[count];
        java.util.Arrays.fill(a, value);
        return a;
    }

    private static double sum(List<Draft> notes, Part part) {
        double total = 0;
        for (Draft d : notes) {
            if (d.part == part) {
                total += d.length;
            }
        }
        return total;
    }

    /**
     * @return the next note of C major from {@code midi} in the direction of {@code toward}
     */
    static int stepToward(int midi, int toward) {
        int step = Integer.signum(toward - midi);
        int n = midi + step;
        while (!inScale(n)) {
            n += step;
        }
        return n;
    }

    private static boolean inScale(int midi) {
        int degree = Math.floorMod(midi, 12);
        for (int s : SCALE) {
            if (s == degree) {
                return true;
            }
        }
        return false;
    }
}
