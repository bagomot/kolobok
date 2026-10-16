package ru.bagomot.kolobok.entity.song;

/**
 * How Kolobok sings, apart from how high: the pitch comes from {@link
 * ru.bagomot.kolobok.entity.KolobokEntity#voice()}. In game a sample pitched down also sounds darker,
 * so a low voice is a bass and Колобочек rings.
 *
 * @param tempo    length of an eighth relative to 0.2 s; above 1 is slower
 * @param accent   volume of a stressed syllable relative to the others
 * @param volume   overall volume
 * @param echo     every note sounds again 0.21 s later, quieter: humming inside a helmet
 * @param ornament the last syllable of a line is sung as two notes, from above
 * @param wobble   every other note is off by this fraction, up and down: a soggy voice floats
 */
public record Voice(float tempo, float accent, float volume, boolean echo, boolean ornament, float wobble) {

    public static final Voice NORMAL = new Voice(1.0F, 1.15F, 1.0F, false, false, 0.0F);
    /** Bass, slow, humming in the helmet. */
    public static final Voice HELMET = new Voice(1.25F, 1.2F, 1.0F, true, false, 0.0F);
    /** Grand, with a flourish at the end of each line. */
    public static final Voice CROWN = new Voice(1.2F, 1.25F, 1.0F, false, true, 0.0F);
    /** Squeaks and hurries. */
    public static final Voice MINI = new Voice(0.8F, 1.15F, 1.0F, false, false, 0.0F);
    /** Floats. */
    public static final Voice SOGGY = new Voice(1.0F, 1.1F, 1.0F, false, false, 0.03F);
    /** Grumbles evenly, under his breath. */
    public static final Voice STALE = new Voice(1.15F, 1.0F, 0.75F, false, false, 0.0F);

    /** Seconds between a note and its echo. */
    public static final double ECHO_DELAY = 0.21;
    /** Volume of the echo against the note. */
    public static final float ECHO_VOLUME = 0.35F;
}
