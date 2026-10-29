package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parses common spoken clock forms into candidates; callers must bind them to canonical displayed slots. */
final class AivaV2VoiceSpokenTimeNormalizer {
    Resolution normalize(String raw) {
        if (raw == null || raw.isBlank()) return Resolution.none();
        String text = raw.toLowerCase(Locale.ROOT)
                .replaceAll("(?i)(?<![\\p{L}\\p{N}])([ap])\\s*\\.\\s*m\\.?", "$1m")
                .replaceAll("[.!?,।]+$", "").trim();
        if (!hasClockShape(text)) return Resolution.none();
        boolean pm = text.matches("(?s).*\\b(?:pm|p\\s*m)\\b.*|.*(?:पी\\s*एम|शाम|रात|evening|night).*" );
        boolean am = text.matches("(?s).*\\b(?:am|a\\s*m)\\b.*|.*(?:ए\\s*एम|सुबह|morning).*" );
        String normalized = text
                .replaceAll("(?iu)साढ़े|साढ़े|saadhe|saadhey|aadhe", " half ")
                .replaceAll("(?iu)बजकर|बजे\\s+कर|bajkar|bajey?\\s+kar", " ")
                .replaceAll("(?iu)बजे|baje|वाला|वाली|वाले|wala|waala|wali|wale|बुक|करो|कर\\s+दो|बुक\\s+कर\\s+दो|book|please|slot|स्लॉट|का", " ")
                .replaceAll("(?iu)(?<![\\p{L}\\p{N}])(\\d{1,2})[.]([0-5]\\d)(?![\\p{L}\\p{N}])", "$1:$2")
                .replaceAll("(?iu)(?<![\\p{L}\\p{N}])(\\d{1,2})\\s+([0-5]\\d)(?![\\p{L}\\p{N}])", "$1:$2")
                .replaceAll("\\s+", " ").trim();

        Map<String, String> words = Map.ofEntries(
                Map.entry("एक", "1"), Map.entry("दो", "2"), Map.entry("तीन", "3"), Map.entry("चार", "4"),
                Map.entry("पाँच", "5"), Map.entry("पांच", "5"), Map.entry("छह", "6"), Map.entry("छः", "6"),
                Map.entry("सात", "7"), Map.entry("आठ", "8"), Map.entry("नौ", "9"), Map.entry("दस", "10"),
                Map.entry("ग्यारह", "11"), Map.entry("बारह", "12"), Map.entry("तीस", "30"),
                Map.entry("one", "1"), Map.entry("two", "2"), Map.entry("three", "3"), Map.entry("four", "4"),
                Map.entry("five", "5"), Map.entry("six", "6"), Map.entry("seven", "7"), Map.entry("eight", "8"),
                Map.entry("nine", "9"), Map.entry("ten", "10"), Map.entry("eleven", "11"), Map.entry("twelve", "12"),
                Map.entry("thirty", "30"),
                Map.entry("saat", "7"), Map.entry("aath", "8"), Map.entry("nau", "9"), Map.entry("das", "10"),
                Map.entry("gyarah", "11"), Map.entry("tees", "30"));
        for (var entry : words.entrySet()) {
            normalized = normalized.replaceAll("(?iu)(?<![\\p{L}\\p{N}])" + java.util.regex.Pattern.quote(entry.getKey())
                    + "(?![\\p{L}\\p{N}])", entry.getValue());
        }
        normalized = normalized.replaceAll("(?iu)half\\s+(\\d{1,2})", "$1:30")
                .replaceAll("(?iu)(\\d{1,2})\\s+half", "$1:30")
                .replaceAll("(?iu)(\\d{1,2})\\s+(\\d{1,2})", "$1:$2")
                .replaceAll("\\s+", " ").trim();
        var matcher = java.util.regex.Pattern.compile("(?<!\\d)(\\d{1,2})(?::([0-5]\\d))?(?!\\d)").matcher(normalized);
        if (!matcher.find()) return Resolution.none();
        try {
            int hour = Integer.parseInt(matcher.group(1));
            int minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            boolean marker = pm || am;
            if (marker) {
                if (hour < 1 || hour > 12) return Resolution.none();
                if (pm && hour < 12) hour += 12;
                if (am && hour == 12) hour = 0;
                return new Resolution(true, true, List.of(LocalTime.of(hour, minute)));
            }
            if (hour < 1 || hour > 12) return Resolution.none();
            int morningHour = hour == 12 ? 0 : hour;
            int eveningHour = hour == 12 ? 12 : hour + 12;
            List<LocalTime> candidates = new ArrayList<>(List.of(LocalTime.of(morningHour, minute)));
            if (!candidates.getFirst().equals(LocalTime.of(eveningHour, minute))) {
                candidates.add(LocalTime.of(eveningHour, minute));
            }
            return new Resolution(true, false, List.copyOf(candidates));
        } catch (RuntimeException ignored) {
            return Resolution.none();
        }
    }

    private boolean hasClockShape(String text) {
        if (text.matches("(?s).*(?:\\b(?:am|pm)\\b|p\\s*m|a\\s*m|पी\\s*एम|ए\\s*एम|बजे|baje|बजकर|bajkar|साढ़े|साढ़े|saadhe|half|morning|evening|night|सुबह|शाम|रात).*")) {
            return true;
        }
        if (text.matches("(?s).*\\b\\d{1,2}[:.]\\d{2}\\b.*")
                || text.matches("(?s).*\\b\\d{1,2}\\s+\\d{2}\\b.*")) return true;
        String number = "(?:एक|दो|तीन|चार|पाँच|पांच|छह|छः|सात|आठ|नौ|दस|ग्यारह|बारह|तीस|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirty|saat|aath|nau|das|gyarah|tees)";
        return text.matches("(?iu).*" + number + "\\s+" + number + ".*");
    }

    record Resolution(boolean recognized, boolean explicitMeridiem, List<LocalTime> candidates) {
        static Resolution none() { return new Resolution(false, false, List.of()); }
    }
}
