package ru.bagomot.kolobok.entity.song;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The expected strings are what {@code songTimeline()} and {@code songLines()} in the songbook
 * prototype produce: the Java port must lay out the same notes at the same moments.
 */
class SongTest {

    private static final List<String> MOBS = List.of("minecraft:rabbit", "minecraft:wolf", "minecraft:polar_bear",
            "minecraft:creeper", "minecraft:enderman");

    @Test
    void theSongWithoutMobsMatchesTheSongbook() {
        assertEquals("йа:67@0/1 го:72@1/0.5 ло:74@1.5/0.5 БО:76@2/2 го:76@4/1 ло:74@5/1 БО:72@6/2"
                        + " | бо:76@8/1 а:77@9/1 БА:79@10/2 ру:76@12/1 мэ:72@13/1 ДО:74@14/2"
                        + " | бо:74@16/1 су:76@17/1 СЭ:77@18/2 га:74@20/1 рэ:71@21/1 БО:72@22/2"
                        + " | ма:76@24/1 мэ:77@25/1 ДА:79@26/2 мэ:77@28/1 мэ:76@29/1 ШО:74@30/6"
                        + " | БЭ:72@36/2 гу:71@38/1 са:69@39/1 ШО:67@40/2"
                        + " | ма:67@42/1 о:69@43/1 ГО:71@44/2 гэ:72@46/1 ду:74@47/1 ШО:76@48/2"
                        + " | йа:67@50/1 о:67@51/1 ДЭ:72@52/1 ду:72@53/1 ги:74@54/1 у:76@55/1 ШО:74@56/2"
                        + " | йа:67@58/1 о:67@59/1 БА:76@60/1 бу:76@61/1 ги:74@62/1 у:71@63/1 ШО:72@64/4"
                        + " | и:76@68/1 о:76@69/0.5 дэ:76@69.5/0.5 БА:79@70/2 у:74@72/2 ДУ:72@74/6",
                dump(Melody.timeline(SongText.lines(1, List.of(), null, false), false)));
    }

    @Test
    void theCrownsOrnamentMatchesTheSongbook() {
        String song = dump(Melody.timeline(SongText.lines(1, List.of(), null, false), true));
        assertTrue(song.startsWith("йа:67@0/1 го:72@1/0.5 ло:74@1.5/0.5 БО:76@2/2 го:76@4/1 ло:74@5/1 БО:74@6/1 ~о:72@7/1"
                + " | бо:76@8/1"), song);
        assertTrue(song.contains("ШО:76@30/1 ~о:74@31/5 | БЭ:72@36/2"), song);
        assertTrue(song.endsWith("БА:79@70/2 у:74@72/2 ДУ:74@74/1 ~у:72@75/5"), song);
    }

    @Test
    void mobLinesAndHerobrineMatchTheSongbook() {
        assertEquals("Я от зайца ушёл, / Я от крипера ушёл, / От эндермена ушёл,", mobLines(1, "minecraft:creeper", false));
        assertEquals("Я и от Херобрина ушёл, / Я от крипера ушёл, / От эндермена ушёл,", mobLines(1, "minecraft:creeper", true));
        assertEquals("Я от медведя ушёл, / Я от крипера ушёл, / От эндермена ушёл,", mobLines(2, "minecraft:creeper", false));
        assertEquals("Я от медведя ушёл, / Я и от Херобрина ушёл, / От эндермена ушёл,", mobLines(2, "minecraft:creeper", true));
        assertEquals("Я от зайца ушёл, / Я от волка ушёл, / От крипера ушёл,", mobLines(987654321, "minecraft:creeper", false));
        assertEquals("Я от зайца ушёл, / Я и от Херобрина ушёл, / От крипера ушёл,", mobLines(987654321, "minecraft:creeper", true));
        assertEquals("Я от медведя ушёл, / Я от крипера ушёл, / От эндермена ушёл,", mobLines(12345, null, false));
    }

    @Test
    void herobrinesLineAndTheEndMatchTheSongbook() {
        List<Melody.Line> lines = Melody.timeline(SongText.lines(7, MOBS, null, true), false);
        assertEquals("йа:67@68/1 о:67@69/1 СА:72@70/2 са:74@72/1 у:76@73/1 ШО:74@74/2"
                        + " | йа:67@76/1 о:67@77/0.5 мэ:67@77.5/0.5 БЭ:76@78/2 да:74@80/1 у:71@81/1 ШО:72@82/1.5"
                        + " | йа:67@83.5/0.5 и:67@84/0.5 о:67@84.5/0.5 гэ:67@85/0.5 ро:69@85.5/0.5 РИ:74@86/2 ма:76@88/1 у:77@89/1 ШО:79@90/2"
                        + " | и:76@92/1 о:76@93/0.5 дэ:76@93.5/0.5 БА:79@94/2 у:74@96/2 ДУ:72@98/6",
                dump(lines.subList(8, lines.size())));
    }

    @ParameterizedTest
    @CsvSource({"1, false", "2, true", "12345, true", "-7, false", "987654321, true"})
    void everyLineKeepsTheMetre(int seed, boolean herobrine) {
        for (boolean ornament : new boolean[]{false, true}) {
            List<Melody.Line> lines = Melody.timeline(SongText.lines(seed, MOBS, "minecraft:wolf", herobrine), ornament);
            assertMetre(lines);
            assertEquals("И от тебя' уйду'!", lines.getLast().text().sung());
        }
    }

    @ParameterizedTest
    @CsvSource({"othermod:mud_crab", "alexsmobs:crimson_mosquito", "mowziesmobs:ferrous_wroughtnaut",
            "othermod:enormous_abominable_snow_creature", "othermod:yeti", "othermod:grrr"})
    void aMobFromAnotherModKeepsTheMetreInEachOfTheThreePlaces(String mob) {
        for (boolean ornament : new boolean[]{false, true}) {
            assertMetre(Melody.timeline(SongText.book(List.of(mob, mob, mob)), ornament));
            assertMetre(Melody.timeline(SongText.lines(5, List.of("minecraft:wolf", mob), null, true), ornament));
        }
    }

    @Test
    void aMobFromAnotherModIsStressedOnItsFirstSyllable() {
        List<SongLine> book = SongText.book(List.of("othermod:mud_crab", "othermod:yeti",
                "othermod:enormous_abominable_snow_creature"));
        assertEquals("йа-о-МУ-ра-бу-ШО", Phonetics.spell(Phonetics.syllables(book.get(8).sung())));
        assertEquals("йа-о-ЙЭ-ди-у-ШО", Phonetics.spell(Phonetics.syllables(book.get(9).sung())));
        // Cut to seven syllables, to fit the bar.
        assertEquals("о-ДЭ-мо-мо-у-са-бо-ми-у-ШО", Phonetics.spell(Phonetics.syllables(book.get(10).sung())));
        // The upbeat is «Я от» or «От», as with the mobs the song knows.
        List<Melody.Line> lines = Melody.timeline(book, false);
        for (Melody.Line line : lines.subList(8, 11)) {
            assertTrue(line.downbeat() - line.start() <= 2, line.text().sung());
        }
    }

    @Test
    void notesMatchSyllables() {
        for (SongLine line : SongText.lines(3, MOBS, null, true)) {
            Melody.Line set = Melody.timeline(List.of(line), false).getFirst();
            assertEquals(Phonetics.syllables(line.sung()), set.notes().stream().map(Melody.Note::syllable).toList());
        }
    }

    @Test
    void mobsAreAtMostThreeInTheOrderHeGotThemAndTheNewestIsAlwaysSung() {
        for (int seed = 0; seed < 500; seed++) {
            List<String> sung = SongText.pickMobs(MOBS, seed, "minecraft:enderman");
            assertEquals(3, sung.size());
            assertTrue(sung.contains("minecraft:enderman"));
            List<String> ordered = new ArrayList<>(MOBS);
            ordered.retainAll(sung);
            assertEquals(ordered, sung);
            assertEquals(sung, SongText.pickMobs(MOBS, seed, "minecraft:enderman"), "one seed, one song");
        }
        assertEquals(List.of("minecraft:wolf"), SongText.pickMobs(List.of("minecraft:wolf"), 5, null));
    }

    @Test
    void withoutMobsTheEndJoinsTheSecondVerse() {
        List<SongLine> lines = SongText.lines(1, List.of(), null, false);
        assertEquals(2, SongText.verseCount(lines));
        assertEquals(4, SongText.part(lines, 1).size());
        assertEquals(5, SongText.part(lines, 2).size());
        assertEquals("И от тебя' уйду'!", SongText.part(lines, 2).getLast().sung());
        assertEquals(SongText.part(lines, 1), SongText.part(lines, 3), "verses wrap around");
        assertEquals(3, SongText.verseCount(SongText.lines(1, MOBS, null, false)));
    }

    @Test
    void aVerseTakesAboutSixSecondsAndTheMiniHurries() {
        Song verse = Song.compose(1, 1, List.of(), null, false, Voice.NORMAL);
        assertTrue(verse.seconds() > 5.5 && verse.seconds() < 8.0, "verse: " + verse.seconds());
        Song mini = Song.compose(1, 1, List.of(), null, false, Voice.MINI);
        assertTrue(mini.seconds() < verse.seconds());
        assertEquals(0, verse.lineAt(0.0));
        assertEquals(3, verse.lineAt(verse.seconds()));
    }

    @Test
    void anyVoiceKeepsThePitchInsideTheGamesRange() {
        // KolobokEntity clamps the voice to 0.67–1.33; the melody spans 0.75–1.5 around it.
        for (Voice voice : List.of(Voice.NORMAL, Voice.HELMET, Voice.CROWN, Voice.MINI, Voice.SOGGY, Voice.STALE)) {
            for (Song.Sample s : Song.compose(9, 0, MOBS, null, true, voice).samples()) {
                float low = s.pitch() * 0.67F;
                float high = s.pitch() * 1.33F;
                // A soggy voice floats 3 % either way; the engine clamps that sliver.
                float slack = voice.wobble() + 0.001F;
                assertTrue(low >= 0.5F * (1 - slack) && high <= 2.0F * (1 + slack), voice + ": " + s);
            }
        }
    }

    @Test
    void everyMobTheSongKnowsFitsTheBarInEachOfTheThreePlaces() {
        for (String mob : SongText.knownMobs()) {
            List<SongLine> book = SongText.book(List.of(mob, mob, mob));
            List<Melody.Line> lines = Melody.timeline(book, false);
            for (Melody.Line line : lines.subList(8, 11)) {
                Melody.Note last = line.notes().getLast();
                assertEquals(line.downbeat() + 4, last.start(), 1e-9, "too long for one bar: " + line.text().sung());
            }
        }
    }

    @Test
    void aMobFromAnotherModIsSungByItsIdAndShownInQuotes() {
        List<SongLine> lines = SongText.lines(1, List.of("minecraft:zombie", "othermod:mud_crab"), null, false);
        assertEquals("kolobok.song.from.0", lines.get(8).subtitle());
        assertEquals("kolobok.song.from.unknown", lines.get(9).subtitle());
        assertEquals("Я от mu'd crab ушё'л,", lines.get(9).sung());
        assertEquals("othermod:mud_crab", lines.get(9).mob());
    }

    @Test
    void theSongbookHasEveryMobAndNeverHerobrine() {
        List<SongLine> book = SongText.book(MOBS);
        assertEquals(8 + MOBS.size() + 1, book.size());
        assertEquals(MOBS, book.stream().map(SongLine::mob).filter(m -> m != null).toList());
        assertEquals(List.of("kolobok.song.from.0", "kolobok.song.from.1", "kolobok.song.from.0", "kolobok.song.from.1",
                        "kolobok.song.from.2"),
                book.subList(8, 13).stream().map(SongLine::subtitle).toList());
        assertTrue(book.stream().noneMatch(l -> l.subtitle().equals("kolobok.song.herobrine")));
        assertEquals("И от тебя' уйду'!", book.getLast().sung());
        // Up to three mobs the book is the song as he sings it.
        List<String> three = MOBS.subList(0, 3);
        assertEquals(SongText.lines(4, three, null, false), SongText.book(three));
        assertEquals(SongText.lines(4, List.of(), null, false), SongText.book(List.of()));
    }

    @Test
    void theVerseListSurvivesSyncedData() {
        assertEquals(List.of(), SongText.parseVerses(""));
        assertEquals(MOBS, SongText.parseVerses(SongText.joinVerses(MOBS)));
        assertEquals(List.of("minecraft:wolf", "minecraft:rabbit"), SongText.parseVerses("minecraft:wolf,,minecraft:rabbit,minecraft:wolf"));
    }

    /**
     * Every line ends on the strong beat of its second bar, each note starts where the one before it
     * ended, and the tune stays in its sixteenths and its octave.
     */
    private static void assertMetre(List<Melody.Line> lines) {
        double t = 0;
        for (Melody.Line line : lines) {
            // The last syllable lands on the strong beat of the second bar.
            Melody.Note last = line.notes().stream().filter(n -> !n.tie()).reduce((a, b) -> b).orElseThrow();
            assertEquals(line.downbeat() + 4, last.start(), 1e-9, line.text().sung());
            for (Melody.Note note : line.notes()) {
                assertEquals(t, note.start(), 1e-9, "no gap and no overlap: " + line.text().sung());
                assertEquals(0.0, note.length() * 2 % 1, 1e-9, "lengths are whole sixteenths");
                assertTrue(note.midi() >= 67 && note.midi() <= 79, "G4–G5: " + note);
                t = note.end();
            }
        }
    }

    private static String mobLines(int seed, String newest, boolean herobrine) {
        return SongText.lines(seed, MOBS, newest, herobrine).stream()
                .filter(l -> l.mob() != null || l.subtitle().equals("kolobok.song.herobrine"))
                .map(l -> l.sung().replace("'", ""))
                .collect(Collectors.joining(" / "));
    }

    private static String dump(List<Melody.Line> lines) {
        return lines.stream()
                .map(line -> line.notes().stream()
                        .map(n -> (n.tie() ? "~" : "") + n.syllable() + ":" + n.midi() + "@" + num(n.start()) + "/" + num(n.length()))
                        .collect(Collectors.joining(" ")))
                .collect(Collectors.joining(" | "));
    }

    private static String num(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
