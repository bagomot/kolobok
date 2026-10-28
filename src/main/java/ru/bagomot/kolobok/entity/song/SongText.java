package ru.bagomot.kolobok.entity.song;

import org.jspecify.annotations.Nullable;
import ru.bagomot.kolobok.entity.song.Melody.Template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    /**
     * A mob from another mod is sung by at most this many syllables of its id: from the stress to the
     * end of the line the bar holds eight, and «ушёл» needs one of them.
     */
    private static final int MOD_MOB_SYLLABLES = 7;

    /**
     * Genitive, stressed, of the vanilla mobs a verse can be about: every mob that may hurt him. A
     * few are named the folk way rather than as the game names them: the witch is Баба-Яга.
     */
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
            Map.entry("minecraft:ender_dragon", "драко'на"),
            // The undead and the cave dwellers.
            Map.entry("minecraft:husk", "када'вра"),
            Map.entry("minecraft:stray", "зимого'ра"),
            Map.entry("minecraft:bogged", "боло'тника"),
            Map.entry("minecraft:parched", "пусты'нника"),
            Map.entry("minecraft:zombie_villager", "крестья'нина-зо'мби"),
            Map.entry("minecraft:wither_skeleton", "скеле'та-иссуши'теля"),
            Map.entry("minecraft:camel_husk", "верблю'да-када'вра"),
            Map.entry("minecraft:zombie_nautilus", "наути'луса-зо'мби"),
            Map.entry("minecraft:cave_spider", "пеще'рного паука'"),
            Map.entry("minecraft:silverfish", "чешу'йницы"),
            Map.entry("minecraft:slime", "сли'зня"),
            Map.entry("minecraft:magma_cube", "ма'гмового ку'ба"),
            // Illagers.
            Map.entry("minecraft:pillager", "разбо'йника"),
            Map.entry("minecraft:vindicator", "побо'рника"),
            Map.entry("minecraft:evoker", "заклина'теля"),
            Map.entry("minecraft:illusioner", "иллюзо'ра"),
            Map.entry("minecraft:vex", "вреди'ны"),
            // The Nether.
            Map.entry("minecraft:piglin", "пи'глина"),
            Map.entry("minecraft:piglin_brute", "брута'льного пи'глина"),
            Map.entry("minecraft:zombified_piglin", "зо'мби-пи'глина"),
            Map.entry("minecraft:hoglin", "хо'глина"),
            Map.entry("minecraft:zoglin", "зо'глина"),
            Map.entry("minecraft:ghast", "га'ста"),
            // The sea, the End and the rest.
            Map.entry("minecraft:guardian", "стра'жа"),
            Map.entry("minecraft:elder_guardian", "дре'внего стра'жа"),
            Map.entry("minecraft:pufferfish", "иглобрю'ха"),
            Map.entry("minecraft:shulker", "ша'лкера"),
            Map.entry("minecraft:endermite", "эндерми'та"),
            Map.entry("minecraft:breeze", "ви'хря"),
            Map.entry("minecraft:creaking", "скрипуна'"),
            // Neutral ones, when provoked.
            Map.entry("minecraft:bee", "пчелы'"),
            Map.entry("minecraft:iron_golem", "желе'зного го'лема"),
            Map.entry("minecraft:snow_golem", "снегови'ка"),
            Map.entry("minecraft:llama", "ла'мы"),
            Map.entry("minecraft:trader_llama", "ла'мы торго'вца"),
            Map.entry("minecraft:goat", "козы'"),
            Map.entry("minecraft:panda", "па'нды"),
            Map.entry("minecraft:dolphin", "дельфи'на"));

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
                out.add(mobLine(id, i));
            }
        }
        out.add(FINALE);
        return Collections.unmodifiableList(out);
    }

    /**
     * The whole song with every mob he got away from, for the songbook: three verses or more, never
     * Herobrine. With up to three mobs it is the song as he sings it.
     */
    public static List<SongLine> book(List<String> mobs) {
        List<SongLine> out = new ArrayList<>(HEAD);
        for (int i = 0; i < mobs.size(); i++) {
            // «Я от… / Я от… / От… ушёл,»: the short form closes the list, as in the tale.
            int slot = mobs.size() >= MOB_LINES && i == mobs.size() - 1 ? MOB_LINES - 1 : i % 2;
            out.add(mobLine(mobs.get(i), slot));
        }
        out.add(FINALE);
        return Collections.unmodifiableList(out);
    }

    private static SongLine mobLine(String id, int slot) {
        String subtitle = knows(id) ? "kolobok.song.from." + slot : "kolobok.song.from.unknown";
        return new SongLine(MOB_SUNG[slot].replace("#", genitive(id)), MOB_TUNE[slot], false, subtitle, id);
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
     * @return whether the song knows how to name that mob; another mod's mob is sung by its id and
     *         shown by its own name in quotes
     */
    public static boolean knows(String id) {
        return GENITIVE.containsKey(id);
    }

    /**
     * @return every mob the song can name
     */
    public static Set<String> knownMobs() {
        return GENITIVE.keySet();
    }

    /**
     * @return the mob ids of a verse list as {@link #joinVerses} wrote it
     */
    public static List<String> parseVerses(String joined) {
        if (joined.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String id : joined.split(",")) {
            if (!id.isEmpty() && !out.contains(id)) {
                out.add(id);
            }
        }
        return List.copyOf(out);
    }

    /**
     * @return the verse list as one string: synced data has no list type
     */
    public static String joinVerses(List<String> mobs) {
        return String.join(",", mobs);
    }

    /**
     * @return how the mob is named in the song; a mob from another mod sings its id, stressed on the
     *         first syllable
     */
    static String genitive(String id) {
        String known = GENITIVE.get(id);
        if (known != null) {
            return known;
        }
        int colon = id.indexOf(':');
        String name = id.substring(colon + 1).replace('_', ' ');
        // Unstressed, the line's only stress would be «ушё'л», and all the rest of it an upbeat that
        // starts before the line before has ended. On the first syllable the name starts the bar, as
        // «за'йца» does; a long one is cut short to fit it.
        if (Phonetics.vowelAt(name, MOD_MOB_SYLLABLES) >= 0) {
            name = name.substring(0, Phonetics.vowelAt(name, MOD_MOB_SYLLABLES - 1) + 1);
        }
        int first = Phonetics.vowelAt(name, 0);
        return first < 0 ? name : name.substring(0, first + 1) + "'" + name.substring(first + 1);
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
