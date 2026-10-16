package ru.bagomot.kolobok.entity.song;

import java.util.ArrayList;
import java.util.List;

/**
 * One sung syllable: an optional consonant group and one of five vowels.
 *
 * <p>Consonants are reduced to eight groups plus «й»: б (п б в ф), м (м н), л, р, д (т д),
 * г (к г х), с (с з ц), ш (ш ж щ ч). Vowels are а, о, у, э, и. That gives the 49 samples:
 * 8 × 5, «й» before а, о, у, э, and the five bare vowels.
 *
 * @param onset    the consonant group, or {@link #NO_ONSET}
 * @param vowel    one of {@code а о у э и}
 * @param stressed whether the syllable carries the word stress
 */
public record Syllable(char onset, char vowel, boolean stressed) {

    public static final char NO_ONSET = 0;

    static final String ONSETS = "бмлрдгсш";
    static final String VOWELS = "аоуэи";
    static final String YOT_VOWELS = "аоуэ";

    public Syllable {
        if (VOWELS.indexOf(vowel) < 0) {
            throw new IllegalArgumentException("Not a sung vowel: " + vowel);
        }
        // There is no «йи» sample; the glide disappears into the vowel, as it does in speech.
        if (onset == 'й' && vowel == 'и') {
            onset = NO_ONSET;
        }
        if (onset != NO_ONSET && onset != 'й' && ONSETS.indexOf(onset) < 0) {
            throw new IllegalArgumentException("Not a consonant group: " + onset);
        }
    }

    /**
     * @return the sample name, transliterated because game identifiers are ASCII:
     *         {@code ло → lo}, {@code йа → ya}, {@code ша → sha}, {@code а → a}
     */
    public String sampleName() {
        return latinOnset(onset) + latinVowel(vowel);
    }

    /**
     * @return the syllable as the songbook writes it: {@code йа}, stressed in capitals, {@code БО}
     */
    @Override
    public String toString() {
        String text = onset == NO_ONSET ? String.valueOf(vowel) : "" + onset + vowel;
        return stressed ? text.toUpperCase(java.util.Locale.ROOT) : text;
    }

    /**
     * @return all 49 sample names
     */
    public static List<String> allSampleNames() {
        List<String> names = new ArrayList<>(49);
        for (char c : ONSETS.toCharArray()) {
            for (char v : VOWELS.toCharArray()) {
                names.add(latinOnset(c) + latinVowel(v));
            }
        }
        for (char v : YOT_VOWELS.toCharArray()) {
            names.add(latinOnset('й') + latinVowel(v));
        }
        for (char v : VOWELS.toCharArray()) {
            names.add(latinVowel(v));
        }
        return names;
    }

    private static String latinOnset(char onset) {
        return switch (onset) {
            case NO_ONSET -> "";
            case 'б' -> "b";
            case 'м' -> "m";
            case 'л' -> "l";
            case 'р' -> "r";
            case 'д' -> "d";
            case 'г' -> "g";
            case 'с' -> "s";
            case 'ш' -> "sh";
            case 'й' -> "y";
            default -> throw new IllegalArgumentException("Not a consonant group: " + onset);
        };
    }

    private static String latinVowel(char vowel) {
        return switch (vowel) {
            case 'а' -> "a";
            case 'о' -> "o";
            case 'у' -> "u";
            case 'э' -> "e";
            case 'и' -> "i";
            default -> throw new IllegalArgumentException("Not a sung vowel: " + vowel);
        };
    }
}
