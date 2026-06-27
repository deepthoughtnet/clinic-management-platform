package com.deepthoughtnet.clinic.tts.spi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * Normalizes assistant text for voice output without changing the text shown in the UI.
 */
public final class VoiceTextNormalizer {
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");
    private static final Pattern DMY_DATE_PATTERN = Pattern.compile("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+([A-Za-z]{3,9})\\s+(\\d{4})\\b");
    private static final Pattern MDY_DATE_PATTERN = Pattern.compile("\\b([A-Za-z]{3,9})\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,)?\\s+(\\d{4})\\b");
    private static final Pattern TIME_PATTERN = Pattern.compile("\\b(\\d{1,2}):(\\d{2})\\b");
    private static final Pattern LATIN_WORD_PATTERN = Pattern.compile("\\b[A-Za-z][A-Za-z.'-]*\\b");
    private static final Pattern APPOINTMENT_LINE_PATTERN = Pattern.compile(
            "(?i)^(\\s*)([A-Za-z][A-Za-z.'-]*(?:\\s+[A-Za-z][A-Za-z.'-]*){1,4})(\\s*,\\s*(?:\\d{4}-\\d{2}-\\d{2}|\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{4})\\s*,\\s*\\d{1,2}:\\d{2}\\b.*)$"
    );

    private static final Map<String, String> DIRECT_WORD_REPLACEMENTS = Map.ofEntries(
            Map.entry("dr", "डॉक्टर"),
            Map.entry("doctor", "डॉक्टर"),
            Map.entry("ashish", "आशीष"),
            Map.entry("vikas", "विकास"),
            Map.entry("singh", "सिंह"),
            Map.entry("shri", "श्री"),
            Map.entry("appointment", "अपॉइंटमेंट"),
            Map.entry("appointments", "अपॉइंटमेंट्स"),
            Map.entry("upcoming", "आने वाली"),
            Map.entry("here", "यहाँ"),
            Map.entry("your", "आपकी"),
            Map.entry("are", "हैं"),
            Map.entry("book", "बुक"),
            Map.entry("booking", "बुकिंग"),
            Map.entry("clinic", "क्लिनिक"),
            Map.entry("patient", "मरीज"),
            Map.entry("slot", "स्लॉट")
    );

    private static final List<ReplacementRule> PHRASE_REPLACEMENTS = List.of(
            new ReplacementRule("(?i)^\\s*here are your upcoming appointments:\\s*", "आपकी आने वाली अपॉइंटमेंट ये हैं: "),
            new ReplacementRule("(?i)^\\s*here are your upcoming appointments\\s*", "आपकी आने वाली अपॉइंटमेंट ये हैं: "),
            new ReplacementRule("(?i)\\byour upcoming appointments are\\b", "आपकी आने वाली अपॉइंटमेंट ये हैं"),
            new ReplacementRule("(?i)\\byour upcoming appointments\\b", "आपकी आने वाली अपॉइंटमेंट्स"),
            new ReplacementRule("(?i)\\byou have no upcoming appointments\\b", "आपकी कोई आने वाली अपॉइंटमेंट नहीं है"),
            new ReplacementRule("(?i)\\byour appointment is with\\b", "आपकी अपॉइंटमेंट है"),
            new ReplacementRule("(?i)\\bappointment list\\b", "अपॉइंटमेंट सूची"),
            new ReplacementRule("(?i)\\bupcoming appointment\\b", "आने वाली अपॉइंटमेंट"),
            new ReplacementRule("(?i)\\bupcoming appointments\\b", "आने वाली अपॉइंटमेंट्स")
    );

    private static final Map<Integer, String> NUMBER_WORDS = buildNumberWords();
    private static final Map<Integer, String> MONTH_WORDS = Map.ofEntries(
            Map.entry(1, "जनवरी"),
            Map.entry(2, "फ़रवरी"),
            Map.entry(3, "मार्च"),
            Map.entry(4, "अप्रैल"),
            Map.entry(5, "मई"),
            Map.entry(6, "जून"),
            Map.entry(7, "जुलाई"),
            Map.entry(8, "अगस्त"),
            Map.entry(9, "सितंबर"),
            Map.entry(10, "अक्टूबर"),
            Map.entry(11, "नवंबर"),
            Map.entry(12, "दिसंबर")
    );

    public String normalizeForVoice(String text, String language) {
        if (!isHindiLanguage(language) || !StringUtils.hasText(text)) {
            return text;
        }
        return normalizeHindiVoiceText(text);
    }

    public String normalizeHindiVoiceText(String text) {
        String normalized = text.trim();
        normalized = applyPhraseReplacements(normalized);
        normalized = normalized.replace('·', ',');
        normalized = normalized.replaceAll("\\s*,\\s*", ", ");
        normalized = normalizeAppointmentHeaders(normalized);
        normalized = replaceMatches(normalized, ISO_DATE_PATTERN, match -> spokenDate(
                Integer.parseInt(match.group(1)),
                Integer.parseInt(match.group(2)),
                Integer.parseInt(match.group(3))
        ));
        normalized = replaceMatches(normalized, DMY_DATE_PATTERN, match -> spokenDate(
                Integer.parseInt(match.group(3)),
                monthFromName(match.group(2)),
                Integer.parseInt(match.group(1))
        ));
        normalized = replaceMatches(normalized, MDY_DATE_PATTERN, match -> spokenDate(
                Integer.parseInt(match.group(3)),
                monthFromName(match.group(1)),
                Integer.parseInt(match.group(2))
        ));
        normalized = replaceMatches(normalized, TIME_PATTERN, match -> spokenTime(
                Integer.parseInt(match.group(1)),
                Integer.parseInt(match.group(2))
        ));
        normalized = normalized.replaceAll("\\s+को\\s+", ", ");
        normalized = replaceMatches(normalized, LATIN_WORD_PATTERN, match -> replaceLatinWord(match.group()));
        normalized = normalized.replaceAll("\\s{2,}", " ").trim();
        normalized = normalized.replaceAll("\\s+,", ",");
        normalized = normalized.replaceAll(",\\s+", ", ");
        normalized = normalized.replaceAll("\\s+([?.!])", "$1");
        return normalized;
    }

    private String normalizeAppointmentHeaders(String text) {
        Matcher matcher = APPOINTMENT_LINE_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(2);
            if (name == null || isAlreadyDoctorPrefixed(name)) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1) + "Doctor " + name + matcher.group(3)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private boolean isAlreadyDoctorPrefixed(String value) {
        String candidate = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return candidate.startsWith("dr ") || candidate.startsWith("dr.") || candidate.startsWith("doctor ");
    }

    private String applyPhraseReplacements(String text) {
        String result = text;
        for (ReplacementRule rule : PHRASE_REPLACEMENTS) {
            result = result.replaceAll(rule.pattern(), rule.replacement());
        }
        return result;
    }

    private String replaceLatinWord(String word) {
        String cleaned = word == null ? "" : word.trim();
        if (cleaned.isBlank()) {
            return cleaned;
        }
        String direct = DIRECT_WORD_REPLACEMENTS.get(cleaned.toLowerCase(Locale.ROOT));
        if (StringUtils.hasText(direct)) {
            return direct;
        }
        if (cleaned.length() <= 4 && cleaned.equals(cleaned.toUpperCase(Locale.ROOT))) {
            return cleaned;
        }
        return transliterateLatinWord(cleaned);
    }

    private String transliterateLatinWord(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return word;
        }

        List<String> syllables = new ArrayList<>();
        int index = 0;
        while (index < lower.length()) {
            Syllable syllable = nextSyllable(lower, index);
            syllables.add(syllable.output());
            index += syllable.lengthConsumed();
        }
        return String.join("", syllables);
    }

    private Syllable nextSyllable(String word, int start) {
        int index = start;
        String consonant = null;
        if (index < word.length()) {
            String tri = matchTriGraph(word, index);
            if (tri != null) {
                consonant = tri;
                index += tri.length();
            } else {
                String di = matchDiGraph(word, index);
                if (di != null) {
                    consonant = di;
                    index += di.length();
                } else if (isConsonant(word.charAt(index))) {
                    consonant = String.valueOf(word.charAt(index));
                    index += 1;
                }
            }
        }

        String vowel = null;
        if (index < word.length()) {
            String tri = matchVowelTriGraph(word, index);
            if (tri != null) {
                vowel = tri;
                index += tri.length();
            } else {
                String di = matchVowelDiGraph(word, index);
                if (di != null) {
                    vowel = di;
                    index += di.length();
                } else if (isVowel(word.charAt(index))) {
                    vowel = String.valueOf(word.charAt(index));
                    index += 1;
                }
            }
        }

        if (consonant == null) {
            String vowelOutput = vowel == null ? String.valueOf(word.charAt(start)) : independentVowel(vowel);
            return new Syllable(vowelOutput, Math.max(1, index - start));
        }
        String output = consonantOutput(consonant, vowel);
        return new Syllable(output, Math.max(1, index - start));
    }

    private String consonantOutput(String consonant, String vowel) {
        String consonantText = switch (consonant) {
            case "b" -> "ब";
            case "c" -> "क";
            case "d" -> "द";
            case "f" -> "फ";
            case "g" -> "ग";
            case "h" -> "ह";
            case "j" -> "ज";
            case "k" -> "क";
            case "l" -> "ल";
            case "m" -> "म";
            case "n" -> "न";
            case "p" -> "प";
            case "q" -> "क";
            case "r" -> "र";
            case "s" -> "स";
            case "t" -> "त";
            case "v", "w" -> "व";
            case "x" -> "क्स";
            case "y" -> "य";
            case "z" -> "ज";
            case "ch" -> "च";
            case "dh" -> "ध";
            case "gh" -> "घ";
            case "jh" -> "झ";
            case "kh" -> "ख";
            case "ph" -> "फ";
            case "sh" -> "श";
            case "th" -> "थ";
            case "bh" -> "भ";
            case "wh" -> "व";
            case "ng" -> "ङ";
            default -> consonant;
        };
        if (!StringUtils.hasText(vowel)) {
            return consonantText;
        }
        String vowelSign = switch (vowel) {
            case "a" -> "";
            case "aa" -> "ा";
            case "i" -> "ि";
            case "ii", "ee" -> "ी";
            case "u" -> "ु";
            case "uu", "oo" -> "ू";
            case "e" -> "े";
            case "ai" -> "ै";
            case "o" -> "ो";
            case "au", "ou" -> "ौ";
            case "ri" -> "ृ";
            default -> "";
        };
        if (vowelSign.isEmpty() && !"a".equals(vowel)) {
            return consonantText + independentVowel(vowel);
        }
        return consonantText + vowelSign;
    }

    private String independentVowel(String vowel) {
        return switch (vowel) {
            case "a" -> "अ";
            case "aa" -> "आ";
            case "i" -> "इ";
            case "ii", "ee" -> "ई";
            case "u" -> "उ";
            case "uu", "oo" -> "ऊ";
            case "e" -> "ए";
            case "ai" -> "ऐ";
            case "o" -> "ओ";
            case "au", "ou" -> "औ";
            case "ri" -> "ऋ";
            default -> vowel;
        };
    }

    private boolean isConsonant(char value) {
        return value >= 'a' && value <= 'z' && !isVowel(value);
    }

    private boolean isVowel(char value) {
        return switch (value) {
            case 'a', 'e', 'i', 'o', 'u', 'y' -> true;
            default -> false;
        };
    }

    private String matchTriGraph(String word, int index) {
        if (index + 3 <= word.length()) {
            String candidate = word.substring(index, index + 3);
            if ("shh".equals(candidate)) {
                return "sh";
            }
        }
        return null;
    }

    private String matchDiGraph(String word, int index) {
        if (index + 2 > word.length()) {
            return null;
        }
        String candidate = word.substring(index, index + 2);
        return switch (candidate) {
            case "ch", "dh", "gh", "jh", "kh", "ph", "sh", "th", "bh", "wh", "ng" -> candidate;
            default -> null;
        };
    }

    private String matchVowelTriGraph(String word, int index) {
        if (index + 3 > word.length()) {
            return null;
        }
        String candidate = word.substring(index, index + 3);
        return switch (candidate) {
            case "aai", "aau" -> null;
            default -> null;
        };
    }

    private String matchVowelDiGraph(String word, int index) {
        if (index + 2 > word.length()) {
            return null;
        }
        String candidate = word.substring(index, index + 2);
        return switch (candidate) {
            case "aa", "ii", "ee", "uu", "oo", "ai", "au", "ou", "ri" -> candidate;
            default -> null;
        };
    }

    private String spokenDate(int year, int month, int day) {
        String monthWord = MONTH_WORDS.getOrDefault(month, String.valueOf(month));
        return spokenNumber(day) + " " + monthWord + " " + spokenYear(year);
    }

    private String spokenYear(int year) {
        if (year >= 2000 && year < 2100) {
            int remainder = year % 100;
            return remainder == 0 ? "दो हज़ार" : "दो हज़ार " + spokenNumber(remainder);
        }
        if (year >= 1900 && year < 2000) {
            int remainder = year % 100;
            return remainder == 0 ? "उन्नीस सौ" : "उन्नीस सौ " + spokenNumber(remainder);
        }
        if (year >= 1000 && year < 2000) {
            int thousands = year / 1000;
            int remainder = year % 1000;
            StringBuilder builder = new StringBuilder();
            builder.append(spokenNumber(thousands)).append(" हज़ार");
            if (remainder > 0) {
                builder.append(' ').append(spokenNumber(remainder));
            }
            return builder.toString();
        }
        return spokenNumber(year);
    }

    private String spokenTime(int hour24, int minute) {
        int normalizedHour = hour24 % 24;
        String partOfDay = partOfDay(normalizedHour);
        int hour12 = normalizedHour % 12;
        if (hour12 == 0) {
            hour12 = 12;
        }
        if (minute == 0) {
            return partOfDay + " " + spokenHour(hour12) + " बजे";
        }
        if (minute == 15) {
            return partOfDay + " सवा " + spokenHour(hour12) + " बजे";
        }
        if (minute == 30) {
            return partOfDay + " साढ़े " + spokenHour(hour12) + " बजे";
        }
        if (minute == 45) {
            return partOfDay + " पौने " + spokenHour((hour12 % 12) + 1) + " बजे";
        }
        return partOfDay + " " + spokenHour(hour12) + " बजकर " + spokenNumber(minute) + " मिनट";
    }

    private String partOfDay(int hour24) {
        if (hour24 < 4) {
            return "रात";
        }
        if (hour24 < 12) {
            return "सुबह";
        }
        if (hour24 < 17) {
            return "दोपहर";
        }
        if (hour24 < 21) {
            return "शाम";
        }
        return "रात";
    }

    private String spokenHour(int hour12) {
        return spokenNumber(hour12);
    }

    private String spokenNumber(int value) {
        if (NUMBER_WORDS.containsKey(value)) {
            return NUMBER_WORDS.get(value);
        }
        if (value < 100) {
            int tens = value / 10 * 10;
            int ones = value % 10;
            if (ones == 0) {
                return NUMBER_WORDS.getOrDefault(tens, String.valueOf(value));
            }
            String tensWord = NUMBER_WORDS.getOrDefault(tens, String.valueOf(tens));
            String onesWord = NUMBER_WORDS.getOrDefault(ones, String.valueOf(ones));
            return tensWord + " " + onesWord;
        }
        if (value < 1000) {
            int hundreds = value / 100;
            int remainder = value % 100;
            StringBuilder builder = new StringBuilder();
            builder.append(NUMBER_WORDS.getOrDefault(hundreds, String.valueOf(hundreds))).append(" सौ");
            if (remainder > 0) {
                builder.append(' ').append(spokenNumber(remainder));
            }
            return builder.toString();
        }
        return String.valueOf(value);
    }

    private int monthFromName(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "jan", "january" -> 1;
            case "feb", "february" -> 2;
            case "mar", "march" -> 3;
            case "apr", "april" -> 4;
            case "may" -> 5;
            case "jun", "june" -> 6;
            case "jul", "july" -> 7;
            case "aug", "august" -> 8;
            case "sep", "sept", "september" -> 9;
            case "oct", "october" -> 10;
            case "nov", "november" -> 11;
            case "dec", "december" -> 12;
            default -> 0;
        };
    }

    private boolean isHindiLanguage(String language) {
        if (!StringUtils.hasText(language)) {
            return false;
        }
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("hi");
    }

    private static Map<Integer, String> buildNumberWords() {
        Map<Integer, String> words = new LinkedHashMap<>();
        words.put(0, "शून्य");
        words.put(1, "एक");
        words.put(2, "दो");
        words.put(3, "तीन");
        words.put(4, "चार");
        words.put(5, "पाँच");
        words.put(6, "छह");
        words.put(7, "सात");
        words.put(8, "आठ");
        words.put(9, "नौ");
        words.put(10, "दस");
        words.put(11, "ग्यारह");
        words.put(12, "बारह");
        words.put(13, "तेरह");
        words.put(14, "चौदह");
        words.put(15, "पंद्रह");
        words.put(16, "सोलह");
        words.put(17, "सत्रह");
        words.put(18, "अठारह");
        words.put(19, "उन्नीस");
        words.put(20, "बीस");
        words.put(21, "इक्कीस");
        words.put(22, "बाईस");
        words.put(23, "तेईस");
        words.put(24, "चौबीस");
        words.put(25, "पच्चीस");
        words.put(26, "छब्बीस");
        words.put(27, "सत्ताईस");
        words.put(28, "अट्ठाईस");
        words.put(29, "उनतीस");
        words.put(30, "तीस");
        words.put(31, "इकतीस");
        words.put(32, "बत्तीस");
        words.put(33, "तैंतीस");
        words.put(34, "चौंतीस");
        words.put(35, "पैंतीस");
        words.put(36, "छत्तीस");
        words.put(37, "सैंतीस");
        words.put(38, "अड़तीस");
        words.put(39, "उनतालीस");
        words.put(40, "चालीस");
        words.put(41, "इकतालीस");
        words.put(42, "बयालीस");
        words.put(43, "तैंतालीस");
        words.put(44, "चवालीस");
        words.put(45, "पैंतालीस");
        words.put(46, "छियालीस");
        words.put(47, "सैंतालीस");
        words.put(48, "अड़तालीस");
        words.put(49, "उन्चास");
        words.put(50, "पचास");
        words.put(51, "इक्यावन");
        words.put(52, "बावन");
        words.put(53, "तिरेपन");
        words.put(54, "चौवन");
        words.put(55, "पचपन");
        words.put(56, "छप्पन");
        words.put(57, "सत्तावन");
        words.put(58, "अट्ठावन");
        words.put(59, "उनसठ");
        words.put(60, "साठ");
        words.put(70, "सत्तर");
        words.put(80, "अस्सी");
        words.put(90, "नब्बे");
        words.put(100, "एक सौ");
        words.put(200, "दो सौ");
        words.put(300, "तीन सौ");
        words.put(400, "चार सौ");
        words.put(500, "पाँच सौ");
        words.put(600, "छह सौ");
        words.put(700, "सात सौ");
        words.put(800, "आठ सौ");
        words.put(900, "नौ सौ");
        return words;
    }

    private String replaceMatches(String text, Pattern pattern, java.util.function.Function<Matcher, String> replacement) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String value = replacement.apply(matcher);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private record ReplacementRule(String pattern, String replacement) {
    }

    private record Syllable(String output, int lengthConsumed) {
    }
}
