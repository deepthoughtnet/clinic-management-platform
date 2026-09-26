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
    private static final Pattern EXPLICIT_CLOCK_TIME = Pattern.compile(
            "(?<![\\p{L}\\p{N}])([01]?\\d|2[0-3]):([0-5]\\d)(?![\\p{L}\\p{N}])");
    private static final Pattern TIME_BOUND_OR_RANGE = Pattern.compile(
            "(?i)\\b(?:after|before|between|from|later than|earlier than)\\b");
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
        // Normalize common spoken/ASR punctuation variants before parsing the
        // canonical clock.  This is language-boundary normalization, not a
        // slot or provider-specific rule.
        normalized = normalized
                .replaceAll("(?i)(?<![\\p{L}\\p{N}])([ap])\\s*\\.\\s*m\\.?(?![\\p{L}\\p{N}])", "$1m")
                .replaceAll("(?i)(?<![\\p{L}\\p{N}])(\\d{1,2})\\s+(\\d{2})(?=\\s*(?:am|pm)\\b)", "$1:$2");
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
        if (normalized.matches("(?iu)^(?:hi|hello|hey|namaste|नमस्ते)(?:\\s+(?:aiva|iva|एआईवीए))?[.!?]*$")) {
            controls.add(DeterministicControl.GREETING);
        }
        addConversationControls(original, normalized, controls);
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
        if (exactTime == null) exactTime = parseEmbeddedClockTime(original);
        if (exactTime != null) controls.add(DeterministicControl.EXACT_TIME);
        Integer ordinal = parseOrdinal(selection);
        if (ordinal == null) ordinal = appointmentOrdinal(original);
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

    /** Bounded conversation controls shared by English, Hindi, and Hinglish adapters. */
    protected void addConversationControls(String original, String normalized, List<DeterministicControl> controls) {
        String text = ((normalized == null ? "" : normalized) + " " + (original == null ? "" : original))
                .toLowerCase(Locale.ROOT);
        if (text.matches(".*(?:talk\\s+to\\s+(?:a\\s+)?human|speak\\s+to\\s+(?:someone|a\\s+human)|human\\s+agent|receptionist|clinic\\s+se\\s+kisi|रिसेप्शनिस्ट|इंसान|मानव).*") ) {
            controls.add(DeterministicControl.HUMAN_ASSISTANCE);
        }
        if (text.matches(".*(?:are\\s+you\\s+there|can\\s+you\\s+hear\\s+me|aiva\\s*\\?|क्या\\s+आप\\s+हैं|सुन\\s+रही|हो\\s+क्या).*") ) {
            controls.add(DeterministicControl.PRESENCE_CHECK);
        }
        if (text.matches(".*(?:repeat(?:\\s+that)?|say\\s+(?:that|it)\\s+again|फिर\\s+से\\s+बताओ|दोबारा\\s+बताओ).*") ) {
            controls.add(DeterministicControl.REPEAT_LAST_RESPONSE);
        }
        if (text.matches(".*(?:wait|hold\\s+on|one\\s+minute|ek\\s+minute|रुको|एक\\s+मिनट).*") ) {
            controls.add(DeterministicControl.HOLD);
        }
        if (text.matches(".*(?:continue|go\\s+on|जारी\\s+रखो|आगे\\s+बढ़ो).*") ) {
            controls.add(DeterministicControl.CONTINUE);
        }
        if (text.matches(".*(?:stop|cancel\\s+this|never\\s+mind|rehne\\s+do|रहने\\s+दो|बंद\\s+करो).*") ) {
            controls.add(DeterministicControl.ABANDON_WORKFLOW);
        }
        if (text.matches(".*(?:^|\\s)(?:help|what\\s+can\\s+(?:you|i)\\s+do|मदद|क्या\\s+कर\\s+सकती).*(?:$|\\s).*") ) {
            controls.add(DeterministicControl.HELP);
        }
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
        if (meridiem != null && (hour < 1 || hour > 12)) return null;
        if ("pm".equalsIgnoreCase(meridiem) && hour < 12) hour += 12;
        if ("am".equalsIgnoreCase(meridiem) && hour == 12) hour = 0;
        try { return LocalTime.of(hour, minute); } catch (RuntimeException ignored) { return null; }
    }

    private LocalTime parseEmbeddedClockTime(String value) {
        if (TIME_BOUND_OR_RANGE.matcher(value == null ? "" : value).find()) return null;
        Matcher matcher = EXPLICIT_CLOCK_TIME.matcher(value == null ? "" : value);
        if (!matcher.find()) return null;
        String hour = matcher.group(1);
        String minute = matcher.group(2);
        if (matcher.find()) return null;
        try {
            return LocalTime.of(Integer.parseInt(hour), Integer.parseInt(minute));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Integer parseOrdinal(String value) {
        Matcher matcher = Pattern.compile("(?i)^(?:the\\s+)?(first|second|third|fourth|one|two|three|last|[1-9](?:st|nd|rd|th)?)(?:\\s+(?:one|slot))?(?:\\s+works?)?$")
                .matcher(value == null ? "" : value.trim());
        if (!matcher.matches()) return null;
        return switch (matcher.group(1).toLowerCase(Locale.ROOT)) {
            case "first", "one" -> 1; case "second", "two" -> 2; case "third", "three" -> 3; case "fourth" -> 4; case "last" -> -1;
            default -> Integer.valueOf(matcher.group(1).replaceAll("[^0-9]", ""));
        };
    }

    private Integer appointmentOrdinal(String value) {
        String text = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (!text.matches(".*(?:\\bappointments?\\b|अपॉइंटमेंट्स?|अपॉइंटमेन्ट्स?).*")) return null;
        if (text.contains("second") || text.contains("dusri") || text.contains("doosri")
                || text.contains("दूसरी") || text.contains("सेकंड")) return 2;
        if (text.contains("third") || text.contains("teesri") || text.contains("तीसरी")) return 3;
        if (text.contains("first") || text.contains("pehli") || text.contains("pahli")
                || text.contains("पहली")) return 1;
        Matcher matcher = Pattern.compile(
                "(?i)(?:^|[^\\p{L}\\p{N}])(first|second|third|fourth|pehli|pahli|dusri|doosri|teesri|पहली|दूसरी|तीसरी|सेकंड|[1-9](?:st|nd|rd|th)?)(?=[^\\p{L}\\p{N}]|$)",
                Pattern.UNICODE_CASE).matcher(text);
        if (!matcher.find()) return null;
        return switch (matcher.group(1).toLowerCase(Locale.ROOT)) {
            case "first", "pehli", "pahli", "पहली" -> 1;
            case "second", "dusri", "doosri", "दूसरी", "सेकंड" -> 2;
            case "third", "teesri", "तीसरी" -> 3;
            case "fourth" -> 4;
            default -> Integer.valueOf(matcher.group(1).replaceAll("[^0-9]", ""));
        };
    }
}
