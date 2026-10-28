package ru.bagomot.kolobok.entity.song;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Text to sung syllables, the way villagers would sing Russian.
 *
 * <p>A line-for-line port of {@code phonetic()} from the songbook prototype, whose own answers are
 * the expected values in PhoneticsTest: every vowel is a syllable; of the consonants before it only the last
 * one survives, the rest drop out, and so do final consonants. «я ё ю е» at the start of a word or
 * after a vowel give «й». A stress mark is an apostrophe after the stressed vowel; «ё» is always
 * stressed. The consonant run is not reset by spaces: «от за'йца» sings «о-СА».
 *
 * <p>Latin letters are mapped to the same groups, for mumbling lines in non-Russian languages and for
 * mobs from other mods named in Latin; an unmarked word gets no stress, so the song marks such a mob
 * itself.
 */
public final class Phonetics {

    private Phonetics() {
    }

    public static List<Syllable> syllables(String text) {
        List<Syllable> out = new ArrayList<>();
        char cons = 0;
        String lower = text.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if (ch == '\'') {
                if (!out.isEmpty()) {
                    Syllable last = out.removeLast();
                    out.add(new Syllable(last.onset(), last.vowel(), true));
                }
                continue;
            }
            // Latin «y» is a glide before a vowel and a vowel otherwise: «yes», «happy».
            if (ch == 'y') {
                boolean beforeVowel = i + 1 < lower.length() && vowel(lower.charAt(i + 1)) != 0;
                if (beforeVowel) {
                    cons = 'й';
                    continue;
                }
            }
            char vowel = vowel(ch);
            if (vowel != 0) {
                char onset;
                if (cons != 0) {
                    onset = cons;
                } else {
                    onset = "яёюе".indexOf(ch) >= 0 ? 'й' : Syllable.NO_ONSET;
                }
                out.add(new Syllable(onset, vowel, ch == 'ё'));
                cons = 0;
            } else {
                char group = consonant(ch);
                if (group != 0) {
                    cons = group;
                }
            }
        }
        return out;
    }

    /**
     * @return where in the text the vowel of its syllable number {@code n} is, counting from 0, or -1
     *         if it has no more syllables than that
     */
    static int vowelAt(String text, int n) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = Character.toLowerCase(text.charAt(i));
            // As in syllables(): «y» before a vowel is a glide.
            if (ch == 'y' && i + 1 < text.length() && vowel(Character.toLowerCase(text.charAt(i + 1))) != 0) {
                continue;
            }
            if (vowel(ch) != 0 && count++ == n) {
                return i;
            }
        }
        return -1;
    }

    /**
     * @return the songbook spelling, {@code йа-го-ло-БО-го-ло-БО}
     */
    public static String spell(List<Syllable> syllables) {
        StringBuilder sb = new StringBuilder();
        for (Syllable s : syllables) {
            if (!sb.isEmpty()) {
                sb.append('-');
            }
            sb.append(s);
        }
        return sb.toString();
    }

    private static char vowel(char ch) {
        return switch (ch) {
            case 'а', 'я', 'a' -> 'а';
            case 'о', 'ё', 'o' -> 'о';
            case 'у', 'ю', 'u' -> 'у';
            case 'э', 'е', 'e' -> 'э';
            case 'и', 'ы', 'i', 'y' -> 'и';
            default -> 0;
        };
    }

    private static char consonant(char ch) {
        return switch (ch) {
            case 'п', 'б', 'в', 'ф', 'p', 'b', 'v', 'f', 'w' -> 'б';
            case 'м', 'н', 'm', 'n' -> 'м';
            case 'л', 'l' -> 'л';
            case 'р', 'r' -> 'р';
            case 'т', 'д', 't', 'd' -> 'д';
            case 'к', 'г', 'х', 'k', 'g', 'c', 'q', 'h', 'x' -> 'г';
            case 'с', 'з', 'ц', 's', 'z' -> 'с';
            case 'ш', 'ж', 'щ', 'ч', 'j' -> 'ш';
            case 'й' -> 'й';
            default -> 0;
        };
    }
}
