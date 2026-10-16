package ru.bagomot.kolobok.entity;

/**
 * The face Kolobok shows. Blinking is not a mood: the renderer closes the
 * eyes of whatever face is shown for a few frames every 3–5 seconds.
 */
public enum Mood {
    HAPPY(0.0F),
    SING(0.0F),
    SCARED(0.12F),
    DIZZY(0.0F),
    SLEEP(0.0F),
    SLY(0.0F),
    ANGRY(0.0F),
    SAD(0.0F),
    PUFF(0.0F),
    GOLOVA(0.0F),
    GOLOVA_SLEEP(0.0F);

    private static final Mood[] VALUES = values();

    private final float voiceRaise;

    Mood(float voiceRaise) {
        this.voiceRaise = voiceRaise;
    }

    /**
     * @return the mood's factor in the voice pitch; a scared Kolobok squeaks ×1.12
     */
    public float voice() {
        return 1.0F + voiceRaise;
    }

    public static Mood byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : HAPPY;
    }
}
