package com.deepthoughtnet.clinic.api.patientportal.aivav2.language;

import java.util.Map;

/** Small, bounded transliterator used only for Devanagari entity spans. */
final class DevanagariNameTransliterator {
    private static final Map<Character, String> INDEPENDENT_VOWELS = Map.ofEntries(
            Map.entry('अ', "a"), Map.entry('आ', "aa"), Map.entry('इ', "i"), Map.entry('ई', "ee"),
            Map.entry('उ', "u"), Map.entry('ऊ', "oo"), Map.entry('ए', "e"), Map.entry('ऐ', "ai"),
            Map.entry('ओ', "o"), Map.entry('औ', "au"), Map.entry('ऋ', "ri"));
    private static final Map<Character, String> CONSONANTS = Map.ofEntries(
            Map.entry('क', "k"), Map.entry('ख', "kh"), Map.entry('ग', "g"), Map.entry('घ', "gh"),
            Map.entry('च', "ch"), Map.entry('छ', "chh"), Map.entry('ज', "j"), Map.entry('झ', "jh"),
            Map.entry('ट', "t"), Map.entry('ठ', "th"), Map.entry('ड', "d"), Map.entry('ढ', "dh"),
            Map.entry('त', "t"), Map.entry('थ', "th"), Map.entry('द', "d"), Map.entry('ध', "dh"),
            Map.entry('न', "n"), Map.entry('प', "p"), Map.entry('फ', "ph"), Map.entry('ब', "b"),
            Map.entry('भ', "bh"), Map.entry('म', "m"), Map.entry('य', "y"), Map.entry('र', "r"),
            Map.entry('ल', "l"), Map.entry('व', "v"), Map.entry('श', "sh"), Map.entry('ष', "sh"),
            Map.entry('स', "s"), Map.entry('ह', "h"), Map.entry('क़', "q"), Map.entry('ख़', "kh"),
            Map.entry('ग़', "gh"), Map.entry('ज़', "z"), Map.entry('ड़', "r"), Map.entry('ढ़', "rh"),
            Map.entry('फ़', "f"));
    private static final Map<Character, String> MATRAS = Map.ofEntries(
            Map.entry('ा', "aa"), Map.entry('ि', "i"), Map.entry('ी', "ee"), Map.entry('ु', "u"),
            Map.entry('ू', "oo"), Map.entry('ृ', "ri"), Map.entry('े', "e"), Map.entry('ै', "ai"),
            Map.entry('ो', "o"), Map.entry('ौ', "au"));

    private DevanagariNameTransliterator() { }

    static String transliterate(String text) {
        StringBuilder result = new StringBuilder();
        boolean pendingVowel = false;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (CONSONANTS.containsKey(current)) {
                result.append(CONSONANTS.get(current)).append('a');
                pendingVowel = true;
            } else if (INDEPENDENT_VOWELS.containsKey(current)) {
                result.append(INDEPENDENT_VOWELS.get(current));
                pendingVowel = false;
            } else if (MATRAS.containsKey(current)) {
                if (pendingVowel && result.length() > 0 && result.charAt(result.length() - 1) == 'a') {
                    result.deleteCharAt(result.length() - 1);
                }
                result.append(MATRAS.get(current));
                pendingVowel = false;
            } else if (current == '्') {
                if (pendingVowel && result.length() > 0 && result.charAt(result.length() - 1) == 'a') {
                    result.deleteCharAt(result.length() - 1);
                }
                pendingVowel = false;
            } else if (current == 'ं' || current == 'ँ') {
                result.append('n');
                pendingVowel = false;
            } else if (current == '़') {
                // Nukta is already represented by the extended consonant when present.
            } else if (Character.isWhitespace(current)) {
                result.append(' ');
                pendingVowel = false;
            }
        }
        return result.toString().replaceAll("a$", "").replaceAll("\\s+", " ").trim();
    }

    static String titleCaseWords(String value) {
        StringBuilder result = new StringBuilder();
        for (String word : value.split(" ")) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
