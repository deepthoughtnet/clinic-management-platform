package com.deepthoughtnet.clinic.api.patientportal.aivav2.language;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class EnglishLanguageAdapter implements AivaLanguageAdapter {
    private static final Pattern SPOKEN_TIME = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}])(\\d{1,2})(?::([0-5]\\d))?\\s*(am|pm)?(?:\\s*(?:works?(?:\\s+for\\s+me)?|is\\s+(?:fine|okay)|will\\s+do))?(?![\\p{L}\\p{N}])");
    private static final Pattern EXPLICIT_DOCTOR_REFERENCE = Pattern.compile(
            "(?i)(?:with|from|by)\\s+((?:doctor|dr\\.?|doc)\\s+[\\p{L}][\\p{L}\\p{N}'-]*(?:\\s+(?!on\\b|at\\b|for\\b|in\\b|and\\b|appointment\\b|today\\b|tomorrow\\b|tonight\\b)[\\p{L}][\\p{L}\\p{N}'-]*){0,3})"
                    + "(?=\\s+(?:on|at|for|in|and|appointment)\\b|[?.!,]|$)");
    private static final Pattern SPECIALTY_REFERENCE = Pattern.compile(
            "(?i)(?:specialty|speciality|विशेषज्ञता|विशेषज्ञ)\\s+(?:of|में|की)?\\s*([\\p{L}][\\p{L}\\p{M} -]{1,50})");
    private static final Map<String, String> SPOKEN_CLOCK_NUMBERS = Map.ofEntries(
            Map.entry("one", "1"), Map.entry("two", "2"), Map.entry("three", "3"),
            Map.entry("four", "4"), Map.entry("five", "5"), Map.entry("six", "6"),
            Map.entry("seven", "7"), Map.entry("eight", "8"), Map.entry("nine", "9"),
            Map.entry("ten", "10"), Map.entry("eleven", "11"), Map.entry("twelve", "12"));

    private final LocaleTemporalNormalizer temporalNormalizer = new LocaleTemporalNormalizer();

    @Override public String languageCode() { return "en"; }

    @Override
    public boolean supports(String requestedLanguage, String rawText) {
        return requestedLanguage != null && requestedLanguage.toLowerCase(Locale.ROOT).startsWith("en");
    }

    @Override
    public NormalizedUserTurn normalize(String rawText) {
        return normalize(rawText, LocalDate.now());
    }

    @Override
    public NormalizedUserTurn normalize(String rawText, LocalDate referenceDate) {
        String original = rawText == null ? "" : rawText.trim();
        String normalized = original.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        String selection = normalizeSelection(normalized);
        Matcher doctorMatcher = EXPLICIT_DOCTOR_REFERENCE.matcher(original);
        NormalizedUserTurn.EntityReference doctor = doctorMatcher.find()
                ? new NormalizedUserTurn.EntityReference(doctorMatcher.group(1).trim(), doctorMatcher.group(1).trim()) : null;
        Matcher specialtyMatcher = SPECIALTY_REFERENCE.matcher(original);
        NormalizedUserTurn.EntityReference specialty = specialtyMatcher.find()
                ? new NormalizedUserTurn.EntityReference(specialtyMatcher.group(1).trim(), specialtyMatcher.group(1).trim()) : null;
        boolean qualifier = Pattern.compile("(?i)\\b(?:with|from|by)\\s+([^?.!,]+)").matcher(original).find();
        return canonicalTurn(original, normalized, selection, languageCode(), "en", ResponseStyle.STANDARD,
                doctor, specialty, qualifier, referenceDate);
    }

    protected String normalizeSelection(String text) {
        String normalized = text.replaceFirst("^(?:take|choose|select|use)\\s+", "");
        for (Map.Entry<String, String> entry : SPOKEN_CLOCK_NUMBERS.entrySet()) {
            normalized = normalized.replaceAll("\\b" + entry.getKey()
                    + "(?=\\s*(?:am|pm|o['’]?clock)\\b)", entry.getValue());
        }
        return normalized.replaceAll("\\bo['’]?clock\\b", "").replaceAll("\\s+", " ").trim();
    }

    protected NormalizedUserTurn canonicalTurn(String original, String normalized, String selection,
                                                String detectedLanguage, String responseLanguage,
                                                ResponseStyle style, NormalizedUserTurn.EntityReference doctor,
                                                NormalizedUserTurn.EntityReference specialty, boolean qualifier,
                                                LocalDate referenceDate) {
        List<DeterministicControl> controls = new ArrayList<>();
        boolean negative = Pattern.compile("(?i)(?<![\\p{L}])(?:no|don't|never\\s+mind)(?![\\p{L}])").matcher(normalized).find();
        boolean positive = Pattern.compile("(?i)(?<![\\p{L}])(?:yes|confirm|go\\s+ahead)(?![\\p{L}])").matcher(normalized).find();
        if (negative) controls.add(DeterministicControl.CONFIRMATION_NEGATIVE);
        if (positive) controls.add(DeterministicControl.CONFIRMATION_POSITIVE);
        boolean more = Pattern.compile("(?i)(?<![\\p{L}])(?:more|next|other)(?![\\p{L}])").matcher(normalized).find();
        boolean slots = Pattern.compile("(?i)(?<![\\p{L}])(?:slot|slots|show|provide)(?![\\p{L}])").matcher(normalized).find();
        NormalizedUserTurn.Pagination pagination = more && slots
                ? NormalizedUserTurn.Pagination.SHOW_MORE : NormalizedUserTurn.Pagination.NONE;
        if (pagination == NormalizedUserTurn.Pagination.SHOW_MORE) controls.add(DeterministicControl.SHOW_MORE_SLOTS);

        NormalizedUserTurn.Daypart daypart = normalized.matches(".*(?<![\\p{L}])morning(?![\\p{L}]).*")
                ? NormalizedUserTurn.Daypart.MORNING : normalized.matches(".*(?<![\\p{L}])afternoon(?![\\p{L}]).*")
                ? NormalizedUserTurn.Daypart.AFTERNOON : normalized.matches(".*(?<![\\p{L}])evening(?![\\p{L}]).*")
                ? NormalizedUserTurn.Daypart.EVENING : null;
        if (daypart != null) controls.add(DeterministicControl.valueOf(daypart.name()));

        LocalTime exactTime = parseTime(selection);
        if (exactTime != null) controls.add(DeterministicControl.EXACT_TIME);
        Integer ordinal = parseOrdinal(selection);
        if (ordinal != null) controls.add(DeterministicControl.ORDINAL);

        NormalizedUserTurn.Intent intent = intent(normalized);
        String canonicalText = canonicalSemanticText(normalized, intent);
        NormalizedUserTurn.TemporalFact temporal = temporalNormalizer.normalize(original, referenceDate);
        return new NormalizedUserTurn(original, normalized, selection, detectedLanguage,
                doctor == null ? null : doctor.canonicalQuery(), qualifier, controls, responseLanguage, style,
                canonicalText, doctor, specialty, temporal, exactTime, daypart, ordinal,
                negative ? NormalizedUserTurn.Confirmation.NEGATIVE
                        : positive ? NormalizedUserTurn.Confirmation.POSITIVE : NormalizedUserTurn.Confirmation.NONE,
                pagination, intent);
    }

    protected NormalizedUserTurn.Intent intent(String normalized) {
        if (normalized.matches(".*\\b(?:reschedule|move)\\b.*")) return NormalizedUserTurn.Intent.RESCHEDULE;
        if (normalized.matches(".*\\b(?:cancel|cancellation)\\b.*")) return NormalizedUserTurn.Intent.CANCELLATION;
        if ((normalized.matches(".*\\b(?:my|show|list|what|which|upcoming)\\b.*")
                || normalized.matches(".*\\bdo\\s+i\\s+have\\b.*"))
                && normalized.matches(".*\\bappointments?\\b.*")) return NormalizedUserTurn.Intent.LOOKUP;
        if (normalized.matches(".*\\b(?:book|booking|schedule)\\b.*")) return NormalizedUserTurn.Intent.BOOKING;
        return NormalizedUserTurn.Intent.UNKNOWN;
    }

    protected String canonicalSemanticText(String normalized, NormalizedUserTurn.Intent intent) {
        if (intent == NormalizedUserTurn.Intent.BOOKING) return "book appointment";
        if (intent == NormalizedUserTurn.Intent.LOOKUP) return "lookup appointments";
        if (intent == NormalizedUserTurn.Intent.CANCELLATION) return "cancel appointment";
        if (intent == NormalizedUserTurn.Intent.RESCHEDULE) return "reschedule appointment";
        return normalized;
    }

    private LocalTime parseTime(String value) {
        Matcher matcher = SPOKEN_TIME.matcher(value == null ? "" : value.trim());
        if (!matcher.matches() || (!value.contains(":") && matcher.group(3) == null)) return null;
        int hour = Integer.parseInt(matcher.group(1));
        int minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        String meridiem = matcher.group(3);
        if ("pm".equalsIgnoreCase(meridiem) && hour < 12) hour += 12;
        if ("am".equalsIgnoreCase(meridiem) && hour == 12) hour = 0;
        try { return LocalTime.of(hour, minute); } catch (RuntimeException ignored) { return null; }
    }

    private Integer parseOrdinal(String value) {
        Matcher matcher = Pattern.compile("(?i)^(?:the\\s+)?(first|second|third|fourth|[1-9])(?:\\s+(?:one|slot))?(?:\\s+works?)?$")
                .matcher(value == null ? "" : value.trim());
        if (!matcher.matches()) return null;
        return switch (matcher.group(1).toLowerCase(Locale.ROOT)) {
            case "first" -> 1; case "second" -> 2; case "third" -> 3; case "fourth" -> 4;
            default -> Integer.valueOf(matcher.group(1));
        };
    }
}
