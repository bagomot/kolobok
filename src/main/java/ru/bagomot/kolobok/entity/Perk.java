package ru.bagomot.kolobok.entity;

import org.jspecify.annotations.Nullable;

/**
 * What Kolobok learns by getting away from a mob: every verse of the song about a vanilla mob that
 * has a perk brings it, and the fox's, which is never sung about, comes of being rescued from one.
 * The effects live in {@link KolobokEntity}; this is only the list, free of game classes so that
 * tests can read it.
 */
public enum Perk {
    /** «Заячья прыть»: +10 % speed. */
    RABBIT("minecraft:rabbit"),
    /** «Ну, погоди!»: wolves no longer scare him. */
    WOLF("minecraft:wolf"),
    /** «Косолапый»: +4 health. */
    POLAR_BEAR("minecraft:polar_bear"),
    /** «Невкусный»: +2 armour. */
    ZOMBIE("minecraft:zombie"),
    /** «Сухарь»: every other arrow bounces off. */
    SKELETON("minecraft:skeleton"),
    /** «Сссдобный»: explosions do nothing. */
    CREEPER("minecraft:creeper"),
    /** «Липкое тесто»: climbs walls. */
    SPIDER("minecraft:spider"),
    /** «Тут как тут»: left far behind, he turns up next to his owner. */
    ENDERMAN("minecraft:enderman"),
    /** «Не та бабка»: a witch's potions do nothing. */
    WITCH("minecraft:witch"),
    /** «Непотопляемый»: never gets soggy (sogginess arrives with the other states). */
    DROWNED("minecraft:drowned"),
    /** «Бессонница»: warns an owner who has not slept for three nights. */
    PHANTOM("minecraft:phantom"),
    /** «Ипотека не догонит»: cannot be knocked back. */
    RAVAGER("minecraft:ravager"),
    /** «Пропечённый»: fire and lava do not burn. */
    BLAZE("minecraft:blaze"),
    /** «Тише воды»: makes no vibrations. */
    WARDEN("minecraft:warden"),
    /** «Кощеева закалка»: withering does nothing. */
    WITHER("minecraft:wither"),
    /** «Легенда»: a golden crust. */
    ENDER_DRAGON("minecraft:ender_dragon"),
    /** «Не на ту напала»: foxes no longer hunt him. Not a verse: berries out of a fox's mouth bring it. */
    FOX("minecraft:fox");

    private static final Perk[] VALUES = values();

    private final String mob;

    Perk(String mob) {
        this.mob = mob;
    }

    /**
     * @return the id of the mob whose verse brings it
     */
    public String mob() {
        return mob;
    }

    /**
     * @return the mob id's path, which names the perk in lang keys and modifier ids
     */
    public String path() {
        return mob.substring(mob.indexOf(':') + 1);
    }

    /**
     * @return the lang key of its name; {@code .desc} after it is the description
     */
    public String key() {
        return "kolobok.perk." + path();
    }

    /**
     * @return the perk a verse about that mob brings, or {@code null}
     */
    public static @Nullable Perk forMob(String mob) {
        for (Perk perk : VALUES) {
            if (perk.mob.equals(mob)) {
                return perk;
            }
        }
        return null;
    }
}
