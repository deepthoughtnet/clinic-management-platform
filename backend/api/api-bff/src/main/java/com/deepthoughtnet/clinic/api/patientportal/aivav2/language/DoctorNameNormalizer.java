package com.deepthoughtnet.clinic.api.patientportal.aivav2.language;

import java.util.Locale;

/** Shared comparison for Latin provider names and Devanagari voice transcripts. */
public final class DoctorNameNormalizer {
    private static final java.util.regex.Pattern NON_PROVIDER_LANGUAGE = java.util.regex.Pattern.compile(
            "(?iu)(?:appointment|appoint|apaint|booking|book|buk|tomorrow|today|tonight|yesterday|"
                    + "available|availability|slot|time|morning|afternoon|evening|night|"
                    + "अपॉइंटमेंट|बुकिंग|बुक|कल|आज|परसों|उपलब्ध|अवेलेबल|स्लॉट|समय|सुबह|दोपहर|शाम|रात|"
                    + "दे\\s+दीजिए|कर\\s+दीजिए|दें|चाहिए|चाहिये|करना|करनी|करिए|कीजिए|बताइए|बताएं|दिखाइए|दिखाएं)");

    private DoctorNameNormalizer() { }

    public static boolean matches(String storedName, String query) {
        String stored = normalizedIdentity(storedName);
        String requested = normalized(providerEvidence(query));
        if (stored.isBlank() || requested.isBlank()) return false;
        if (stored.contains(requested) || requested.contains(stored)) return true;

        return sameLengthVowelVariant(storedName, query);
    }

    public static String normalized(String value) {
        if (value == null || value.isBlank()) return "";
        value = stripLeadingHonorific(value);
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

    /** Removes bounded conversational wrappers and honorifics for matching only. */
    public static String providerEvidence(String value) {
        if (value == null) return "";
        return value.trim()
                .replaceFirst("(?iu)^\\s*(?:please\\s+)?(?:talk\\s+(?:to\\s+)?)?", "")
                .replaceFirst("(?iu)^(?:doctor|doc|dr\\.?)\\s+", "")
                .replaceFirst("(?iu)\\s+(?:chahiye|chahie|please)$", "")
                .replaceFirst("(?iu)\\s+(?:doctor|doc|dr\\.?)\\s*$", "")
                .replaceFirst("(?iu)^mujhe\\s+", "")
                .replaceAll("\\s+", " ").trim();
    }

    public static String transliteratedReference(String devanagariName) {
        if (devanagariName == null || devanagariName.isBlank()) return "";
        return DevanagariNameTransliterator.titleCaseWords(
                DevanagariNameTransliterator.transliterate(devanagariName));
    }

    /** Rejects conversational/action/date text before it can be treated as a person name. */
    public static boolean isPlausibleReference(String value) {
        if (value == null || value.isBlank() || NON_PROVIDER_LANGUAGE.matcher(value).find()) return false;
        String transliterated = containsDevanagari(value)
                ? DevanagariNameTransliterator.transliterate(value) : value;
        return !NON_PROVIDER_LANGUAGE.matcher(transliterated).find();
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

    private static boolean sameLengthVowelVariant(String left, String right) {
        String leftName = transliteratedWords(left);
        String rightName = transliteratedWords(right);
        String[] leftWords = leftName.split(" ");
        String[] rightWords = rightName.split(" ");
        if (leftWords.length != rightWords.length) return false;
        boolean changed = false;
        for (int word = 0; word < leftWords.length; word++) {
            if (leftWords[word].length() != rightWords[word].length()) {
                if (leftWords[word].equals(rightWords[word] + "a")
                        || rightWords[word].equals(leftWords[word] + "a")) {
                    changed = true;
                    continue;
                }
                return false;
            }
            for (int i = 0; i < leftWords[word].length(); i++) {
                char a = leftWords[word].charAt(i);
                char b = rightWords[word].charAt(i);
                if (a == b) continue;
                if (!isVowel(a) || !isVowel(b)) return false;
                changed = true;
            }
        }
        return changed;
    }

    private static String transliteratedWords(String value) {
        String transliterated = containsDevanagari(value)
                ? DevanagariNameTransliterator.transliterate(value) : value;
        transliterated = stripLeadingHonorific(transliterated);
        return transliterated.toLowerCase(Locale.ROOT)
                .replace("aa", "a").replace("ee", "i").replace("ii", "i")
                .replace("oo", "u").replace("uu", "u").replace('w', 'v')
                .replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static String normalizedIdentity(String value) {
        if (value == null || value.isBlank()) return "";
        return normalized(value);
    }

    private static String stripLeadingHonorific(String value) {
        return value == null ? "" : value.trim().replaceFirst("(?iu)^(?:doctor|doc|dr\\.?)\\s+", "");
    }

    private static boolean isVowel(char value) {
        return "aeiou".indexOf(value) >= 0;
    }

    private static boolean containsDevanagari(String value) {
        return value.codePoints().anyMatch(codePoint -> codePoint >= 0x0900 && codePoint <= 0x097F);
    }
}
