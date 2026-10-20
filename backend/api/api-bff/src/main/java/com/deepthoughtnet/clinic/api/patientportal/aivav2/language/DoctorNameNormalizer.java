package com.deepthoughtnet.clinic.api.patientportal.aivav2.language;

import java.util.Locale;

/** Shared comparison for Latin provider names and Devanagari voice transcripts. */
public final class DoctorNameNormalizer {
    private DoctorNameNormalizer() { }

    public static boolean matches(String storedName, String query) {
        String stored = normalized(storedName);
        String requested = normalized(query);
        if (stored.isBlank() || requested.isBlank()) return false;
        if (stored.contains(requested) || requested.contains(stored)) return true;

        String storedConsonants = consonantKey(stored);
        String requestedConsonants = consonantKey(requested);
        return !storedConsonants.isBlank()
                && !requestedConsonants.isBlank()
                && (storedConsonants.contains(requestedConsonants)
                || requestedConsonants.contains(storedConsonants));
    }

    public static String normalized(String value) {
        if (value == null || value.isBlank()) return "";
        String transliterated = containsDevanagari(value)
                ? DevanagariNameTransliterator.transliterate(value) : value;
        return transliterated.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "")
                .replace("aa", "a")
                .replace("ee", "i")
                .replace("ii", "i")
                .replace("oo", "u")
                .replace("uu", "u")
                .replace('w', 'v');
    }

    public static String transliteratedReference(String devanagariName) {
        if (devanagariName == null || devanagariName.isBlank()) return "";
        return DevanagariNameTransliterator.titleCaseWords(
                DevanagariNameTransliterator.transliterate(devanagariName));
    }

    /**
     * Produces the compact provider-name reference used by voice tool input.
     * The existing transliterator intentionally preserves bounded transliteration
     * output for other language features; this narrow adjustment covers the
     * common Hindi name spelling मेहता -> Mehta without adding person aliases.
     */
    public static String canonicalReference(String devanagariName) {
        return transliteratedReference(devanagariName)
                .replaceAll("(?i)hata\\b", "hta");
    }

    private static String consonantKey(String value) {
        return value.replaceAll("[aeiou]", "");
    }

    private static boolean containsDevanagari(String value) {
        return value.codePoints().anyMatch(codePoint -> codePoint >= 0x0900 && codePoint <= 0x097F);
    }
}
