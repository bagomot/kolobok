package ru.bagomot.kolobok.entity;

/**
 * The dough Kolobok is baked from. States such as stale or burnt are flags,
 * not variants.
 */
public enum Variant {
    CLASSIC("classic", 1.0F),
    BORODINSKY("borodinsky", 1.0F),
    GOLDEN("golden", 1.0F),
    MOON("moon", 1.0F),
    /** Колобочек: from a single wheat, smaller and higher-voiced. */
    MINI("mini", 1.33F);

    private static final Variant[] VALUES = values();

    private final String name;
    private final float voice;

    Variant(String name, float voice) {
        this.name = name;
        this.voice = voice;
    }

    public String serializedName() {
        return name;
    }

    /**
     * @return the dough's factor in the voice pitch
     */
    public float voice() {
        return voice;
    }

    public static Variant byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : CLASSIC;
    }

    public static Variant byName(String name) {
        for (Variant v : VALUES) {
            if (v.name.equals(name)) {
                return v;
            }
        }
        return CLASSIC;
    }
}
