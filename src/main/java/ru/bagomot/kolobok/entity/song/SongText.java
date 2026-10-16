package ru.bagomot.kolobok.entity.song;

import org.jspecify.annotations.Nullable;
import ru.bagomot.kolobok.entity.song.Melody.Template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The words of the song: a children's song of three verses of four lines.
 *
 * <ol>
 *   <li>«Я Колобок, Колобок! / По амбару метён, / По сусекам скребён, / На сметане мешён,»</li>
 *   <li>«В печку сажён, / На окошке стужён. / Я от дедушки ушёл, / Я от бабушки ушёл,»</li>
 *   <li>up to three lines about mobs he got away from, as in the tale («Я от… / Я от… / От… ушёл,»),
 *       and always «И от тебя уйду!»</li>
 * </ol>
 *
 * <p>The words are Russian in every game language, because the tale is; only the subtitles are
 * translated. A port of {@code songLines()} and {@code pickMobs()} from the songbook prototype.
 */
public final class SongText {

    /** Not a mob: the rare night line «Я и от Херобрина ушёл». No perk, never in the songbook. */
    public static final String HEROBRINE = "herobrine";
    /** The song has at most this many lines about mobs. */
    public static final int MOB_LINES = 3;

    private static final List<SongLine> HEAD = List.of(
            new SongLine("Я Колобо'к, Колобо'к!", Template.TITLE, false, "kolobok.song.0"),
            new SongLine("По амба'ру метё'н,", Template.Q1, false, "kolobok.song.1"),
            new SongLine("По сусе'кам скребё'н,", Template.A1, false, "kolobok.song.2"),
            new SongLine("На смета'не мешё'н,", Template.Q2, true, "kolobok.song.3"),
            new SongLine("В пе'чку сажё'н,", Template.DOWN, false, "kolobok.song.4"),
            new SongLine("На око'шке стужё'н.", Template.UP, false, "kolobok.song.5"),
            new SongLine("Я от де'душки ушё'л,", Template.Q3, false, "kolobok.song.6"),
            new SongLine("Я от ба'бушки ушё'л,", Template.A3, true, "kolobok.song.7"));
    /** «Я от # ушёл, / Я от # ушёл, / От # ушёл,» — the third one shorter, as in the tale. */
    private static final String[] MOB_SUNG = {"Я от # ушё'л,", "Я от # ушё'л,", "От # ушё'л,"};
    private static final Template[] MOB_TUNE = {Template.Q3, Template.A3, Template.CLIMB};
    private static final String HEROBRINE_SUNG = "Я и от Херобри'на ушё'л,";
    private static final SongLine FINALE = new SongLine("И от тебя' уйду'!", Template.END, false, "kolobok.song.end");

    /** Genitive, stressed, of the vanilla mobs a verse can be about. */
    private static final Map<String, String> GENITIVE = Map.ofEntries(
            Map.entry("minecraft:rabbit", "за'йца"),
            Map.entry("minecraft:wolf", "во'лка"),
            Map.entry("minecraft:polar_bear", "медве'дя"),
            Map.entry("minecraft:zombie", "зо'мби"),
            Map.entry("minecraft:skeleton", "скеле'та"),
            Map.entry("minecraft:creeper", "кри'пера"),
            Map.entry("minecraft:spider", "паука'"),
            Map.entry("minecraft:enderman", "эндерме'на"),
            Map.entry("minecraft:witch", "Ба'бы-Яги'"),
            Map.entry("minecraft:drowned", "уто'пленника"),
            Map.entry("minecraft:phantom", "фанто'ма"),
            Map.entry("minecraft:ravager", "разори'теля"),
            Map.entry("minecraft:blaze", "ифри'та"),
            Map.entry("minecraft:warden", "Храни'теля"),
            Map.entry("minecraft:wither", "Иссуши'теля"),
            Map.entry("minecraft:ender_dragon", "драко'на"));

    private SongText() {
    }

    /**
     * @param seed      chooses the three mobs and Herobrine's place; one seed, one song
     * @param mobs      ids of every mob he has a verse about, in the order he got them
     * @param newest    the verse just earned, which must be sung; {@code null} if none
     * @param herobrine whether Herobrine sneaks into the song tonight
     * @return every line of the song in order
     */
    public static List<SongLine> lines(int seed, List<String> mobs, @Nullable String newest, boolean herobrine) {
        List<SongLine> out = new ArrayList<>(HEAD);
        List<String> sung = new ArrayList<>(pickMobs(mobs, seed, newest));
        if (herobrine) {
            if (sung.size() < MOB_LINES) {
                sung.add(HEROBRINE);
            } else {
                sung.set((int) Math.floor(new Rng(seed * 7 + 3).next() * MOB_LINES), HEROBRINE);
            }
        }
        for (int i = 0; i < sung.size(); i++) {
            String id = sung.get(i);
            if (HEROBRINE.equals(id)) {
                out.add(new SongLine(HEROBRINE_SUNG, MOB_TUNE[i], false, "kolobok.song.herobrine"));
            } else {
                out.add(new SongLine(MOB_SUNG[i].replace("#", genitive(id)), MOB_TUNE[i], false,
                        "kolobok.song.from." + i, id));
            }
        }
        out.add(FINALE);
        return Collections.unmodifiableList(out);
    }

    /**
     * Splits the song into verses after each breath. A last verse that is only «И от тебя уйду!» (no
     * mobs yet) joins the one before it: «…я от бабушки ушёл, и от тебя уйду!».
     */
    public static List<List<SongLine>> verses(List<SongLine> lines) {
        List<List<SongLine>> verses = new ArrayList<>();
        List<SongLine> current = new ArrayList<>();
        for (SongLine line : lines) {
            current.add(line);
            if (line.breath()) {
                verses.add(current);
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            if (current.size() == 1 && !verses.isEmpty()) {
                verses.getLast().addAll(current);
            } else {
                verses.add(current);
            }
        }
        return verses;
    }

    /**
     * @param part 0 for the whole song, otherwise the verse to sing, counted from 1 and wrapping
     *             around
     */
    public static List<SongLine> part(List<SongLine> lines, int part) {
        if (part <= 0) {
            return lines;
        }
        List<List<SongLine>> verses = verses(lines);
        return List.copyOf(verses.get((part - 1) % verses.size()));
    }

    /**
     * @return the number of verses the song has
     */
    public static int verseCount(List<SongLine> lines) {
        return verses(lines).size();
    }

    /**
     * Up to three mobs, chosen at random but sung in the order he got them. The newest verse is
     * always among them.
     */
    static List<String> pickMobs(List<String> all, int seed, @Nullable String newest) {
        if (all.size() <= MOB_LINES) {
            return List.copyOf(all);
        }
        Rng rng = new Rng(seed);
        List<String> chosen = new ArrayList<>(MOB_LINES);
        if (newest != null && all.contains(newest)) {
            chosen.add(newest);
        }
        List<String> pool = new ArrayList<>();
        for (String id : all) {
            if (!chosen.contains(id)) {
                pool.add(id);
            }
        }
        while (chosen.size() < MOB_LINES) {
            chosen.add(pool.remove((int) Math.floor(rng.next() * pool.size())));
        }
        List<String> ordered = new ArrayList<>(MOB_LINES);
        for (String id : all) {
            if (chosen.contains(id)) {
                ordered.add(id);
            }
        }
        return ordered;
    }

    /**
     * @return how the mob is named in the song; a mob from another mod sings its id, unstressed
     */
    static String genitive(String id) {
        String known = GENITIVE.get(id);
        if (known != null) {
            return known;
        }
        int colon = id.indexOf(':');
        return id.substring(colon + 1).replace('_', ' ');
    }

    /**
     * xorshift32 with a scrambled seed, exactly as the prototype: neighbouring seeds give different
     * mobs, and the same seed gives the same song on the server and on every client.
     */
    static final class Rng {
        private int s;

        Rng(int seed) {
            s = (seed ^ 0x9E3779B9) * 0x85EBCA6B;
            if (s == 0) {
                s = 1;
            }
            for (int i = 0; i < 4; i++) {
                next();
            }
        }

        double next() {
            s ^= s << 13;
            s ^= s >>> 17;
            s ^= s << 5;
            return (s & 0xFFFFFFFFL) / 4294967296.0;
        }
    }
}
