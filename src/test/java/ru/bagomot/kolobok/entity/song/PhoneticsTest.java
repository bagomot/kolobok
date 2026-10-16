package ru.bagomot.kolobok.entity.song;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneticsTest {

    /**
     * Every expected value here is what {@code phonetic()} in the songbook prototype returns for the
     * same text: the Java port must agree with it line for line.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Я Колобо'к, Колобо'к!       | йа-го-ло-БО-го-ло-БО",
            "По амба'ру метё'н,          | бо-а-БА-ру-мэ-ДО",
            "По сусе'кам скребё'н,       | бо-су-СЭ-га-рэ-БО",
            "На смета'не мешё'н,         | ма-мэ-ДА-мэ-мэ-ШО",
            "В пе'чку сажё'н,            | БЭ-гу-са-ШО",
            "На око'шке стужё'н.         | ма-о-ГО-гэ-ду-ШО",
            "Я от де'душки ушё'л,        | йа-о-ДЭ-ду-ги-у-ШО",
            "Я от ба'бушки ушё'л,        | йа-о-БА-бу-ги-у-ШО",
            "Я и от Херобри'на ушё'л,    | йа-и-о-гэ-ро-РИ-ма-у-ШО",
            "И от тебя' уйду'!           | и-о-дэ-БА-у-ДУ",
            "Я от за'йца ушё'л           | йа-о-СА-са-у-ШО",
            "за'йца                      | СА-са",
            "во'лка                      | БО-га",
            "медве'дя                    | мэ-БЭ-да",
            "зо'мби                      | СО-би",
            "скеле'та                    | гэ-ЛЭ-да",
            "кри'пера                    | РИ-бэ-ра",
            "паука'                      | ба-у-ГА",
            "эндерме'на                  | э-дэ-МЭ-ма",
            "Ба'бы-Яги'                  | БА-би-йа-ГИ",
            "уто'пленника                | у-ДО-лэ-ми-га",
            "фанто'ма                    | ба-ДО-ма",
            "разори'теля                 | ра-со-РИ-дэ-ла",
            "ифри'та                     | и-РИ-да",
            "Храни'теля                  | ра-МИ-дэ-ла",
            "Иссуши'теля                 | и-су-ШИ-дэ-ла",
            "драко'на                    | ра-ГО-ма",
    })
    void matchesTheSongbook(String text, String expected) {
        assertEquals(expected, Phonetics.spell(Phonetics.syllables(text)));
    }

    @Test
    void latinLettersSingToo() {
        assertEquals("гэ-ло", Phonetics.spell(Phonetics.syllables("hello")));
        assertEquals("йэ-си", Phonetics.spell(Phonetics.syllables("yes, sir")));
    }

    @Test
    void yotBeforeIHasNoSampleAndLosesTheGlide() {
        Syllable s = new Syllable('й', 'и', false);
        assertEquals("i", s.sampleName());
    }

    @Test
    void everyPossibleSyllableHasASample() {
        Set<String> names = new HashSet<>(Syllable.allSampleNames());
        String text = "па ба ма ла ра да га са ша я ё ю е а о у э и ы ж ч щ ц х ф в й ";
        for (Syllable s : Phonetics.syllables(text.repeat(3))) {
            assertTrue(names.contains(s.sampleName()), s.sampleName());
        }
    }

    @Test
    void fortyNineDistinctAsciiSamples() {
        List<String> names = Syllable.allSampleNames();
        assertEquals(49, names.size());
        assertEquals(49, new HashSet<>(names).size());
        for (String name : names) {
            assertTrue(name.matches("[a-z]+"), name);
        }
    }
}
