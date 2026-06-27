package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PatientPortalAppointmentResolverService {
    private static final Pattern DIGIT_INDEX_PATTERN = Pattern.compile("\\b(?:option\\s+)?([1-9]|1[0-9])(?:st|nd|rd|th)?\\b");
    private static final Pattern TIME_PATTERN = Pattern.compile("\\b(\\d{1,2}):(\\d{2})\\b");
    private static final Pattern HINDI_SAADHE_TIME_PATTERN = Pattern.compile("साढ़े\\s+(\\S+)");
    private static final Pattern HINDI_WORD_HALF_PATTERN = Pattern.compile("(एक|दो|तीन|चार|पांच|पाँच|छह|छः|सात|आठ|नौ|दस|ग्यारह|बारह)\\s+तीस");
    private static final Pattern HINDI_TIME_PATTERN = Pattern.compile("(\\d{1,2})\\s*(?:बजे|बजकर)?(?:\\s*(\\d{1,2}))?");
    private static final DateTimeFormatter DATE_TEXT = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);

    public AppointmentResolution resolve(
            List<PatientPortalCareAiAppointmentOption> appointments,
            String message,
            String language,
            PatientPortalCareAiExtractedEntities extractedEntities
    ) {
        List<PatientPortalCareAiAppointmentOption> safeAppointments = appointments == null ? List.of() : appointments;
        if (safeAppointments.isEmpty()) {
            return AppointmentResolution.none();
        }

        Integer explicitIndex = parseSelectionIndex(message, extractedEntities);
        if (explicitIndex != null && explicitIndex >= 1 && explicitIndex <= safeAppointments.size()) {
            return AppointmentResolution.resolved(safeAppointments.get(explicitIndex - 1));
        }

        SelectionSignals signals = extractSignals(message, language, extractedEntities);
        List<PatientPortalCareAiAppointmentOption> matches = safeAppointments.stream()
                .filter(option -> matches(option, signals))
                .sorted(Comparator
                        .comparing(PatientPortalCareAiAppointmentOption::appointmentDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PatientPortalCareAiAppointmentOption::appointmentTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        if (matches.size() == 1) {
            return AppointmentResolution.resolved(matches.getFirst());
        }
        if (matches.size() > 1) {
            return AppointmentResolution.multiple(matches, multipleMatchPrompt(language));
        }
        PatientPortalCareAiAppointmentOption scored = bestScoredMatch(safeAppointments, signals);
        if (scored != null) {
            return AppointmentResolution.resolved(scored);
        }
        return AppointmentResolution.none(noMatchPrompt(language));
    }

    private SelectionSignals extractSignals(String message, String language, PatientPortalCareAiExtractedEntities extractedEntities) {
        String raw = message == null ? "" : message.trim();
        String normalized = normalize(raw);
        String doctor = normalize(firstNonBlank(
                extractedEntities == null ? null : extractedEntities.doctor(),
                extractedDoctorFromMessage(raw)
        ));
        String date = firstNonBlank(extractedEntities == null ? null : extractedEntities.date(), extractedDateFromMessage(raw));
        String time = firstNonBlank(
                extractedEntities == null ? null : extractedEntities.time(),
                extractedTimeFromMessage(raw, language)
        );
        String timeWindow = canonicalTimeWindow(firstNonBlank(extractedEntities == null ? null : extractedEntities.timeWindow(), extractedTimeWindowFromMessage(raw, language)));
        return new SelectionSignals(normalized, doctor, date, time, timeWindow);
    }

    private boolean matches(PatientPortalCareAiAppointmentOption option, SelectionSignals signals) {
        if (option == null) {
            return false;
        }
        String normalizedDoctor = normalize(option.doctorName());
        boolean doctorMatch = !StringUtils.hasText(signals.doctor())
                || normalizedContains(normalizedDoctor, signals.doctor())
                || normalizedContains(signals.doctor(), normalizedDoctor);
        boolean dateMatch = !StringUtils.hasText(signals.date())
                || Objects.equals(option.appointmentDate() == null ? null : option.appointmentDate().toString(), signals.date());
        boolean timeMatch = !StringUtils.hasText(signals.time())
                || Objects.equals(option.appointmentTime() == null ? null : option.appointmentTime().format(DateTimeFormatter.ofPattern("HH:mm")), signals.time());
        boolean timeWindowMatch = !StringUtils.hasText(signals.timeWindow())
                || Objects.equals(timeWindowFor(option.appointmentTime()), signals.timeWindow());
        boolean labelMatch = !StringUtils.hasText(signals.normalizedMessage())
                || normalizedContains(normalize(label(option)), signals.normalizedMessage())
                || normalizedContains(signals.normalizedMessage(), normalize(option.doctorName()));
        return doctorMatch && dateMatch && timeMatch && timeWindowMatch && (StringUtils.hasText(signals.doctor())
                || StringUtils.hasText(signals.date())
                || StringUtils.hasText(signals.time())
                || StringUtils.hasText(signals.timeWindow())
                || labelMatch);
    }

    private String label(PatientPortalCareAiAppointmentOption option) {
        StringBuilder builder = new StringBuilder();
        builder.append(safe(option.doctorName()));
        builder.append(" · ");
        builder.append(option.appointmentDate() == null ? "" : DATE_TEXT.format(option.appointmentDate()));
        builder.append(" · ");
        builder.append(option.appointmentTime() == null ? "" : option.appointmentTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        return builder.toString();
    }

    private Integer parseSelectionIndex(String message, PatientPortalCareAiExtractedEntities extractedEntities) {
        if (extractedEntities != null && StringUtils.hasText(extractedEntities.timeSlot())) {
            Integer parsed = parseNumber(extractedEntities.timeSlot());
            if (parsed != null) {
                return parsed;
            }
        }
        String normalized = normalize(message);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        Matcher matcher = DIGIT_INDEX_PATTERN.matcher(normalized);
        if (matcher.find()) {
            return parseNumber(matcher.group(1));
        }
        Integer explicit = parseHindiOrdinalIndex(message);
        if (explicit != null) {
            return explicit;
        }
        return null;
    }

    private Integer parseHindiOrdinalIndex(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String raw = message.trim();
        if (raw.contains("पहला") || raw.contains("पहली") || raw.equals("एक")) {
            return 1;
        }
        if (raw.contains("दूसरा") || raw.contains("दूसरी") || raw.equals("दो")) {
            return 2;
        }
        if (raw.contains("तीसरा") || raw.contains("तीसरी") || raw.equals("तीन")) {
            return 3;
        }
        if (raw.contains("चौथा") || raw.contains("चौथी") || raw.equals("चार")) {
            return 4;
        }
        if (raw.contains("पांचवां") || raw.contains("पांचवी") || raw.contains("पाँचवां") || raw.contains("पाँचवी") || raw.equals("पांच")) {
            return 5;
        }
        if (raw.contains("छठा") || raw.contains("छठी") || raw.equals("छह") || raw.equals("छः")) {
            return 6;
        }
        if (raw.contains("सातवां") || raw.contains("सातवीं") || raw.equals("सात")) {
            return 7;
        }
        if (raw.contains("आठवां") || raw.contains("आठवीं") || raw.equals("आठ")) {
            return 8;
        }
        if (raw.contains("नौवां") || raw.contains("नौवीं") || raw.equals("नौ")) {
            return 9;
        }
        if (raw.contains("दसवां") || raw.contains("दसवीं") || raw.equals("दस")) {
            return 10;
        }
        String normalized = normalize(message);
        if (containsAny(normalized, "पहला", "पहली", "एक", "option one", "one", "first", "1st")) {
            return 1;
        }
        if (containsAny(normalized, "दूसरा", "दूसरी", "दो", "option two", "two", "second", "2nd")) {
            return 2;
        }
        if (containsAny(normalized, "तीसरा", "तीसरी", "तीन", "option three", "three", "third", "3rd")) {
            return 3;
        }
        if (containsAny(normalized, "चौथा", "चौथी", "चार", "option four", "four", "fourth", "4th")) {
            return 4;
        }
        if (containsAny(normalized, "पांचवां", "पांचवी", "पांच", "option five", "five", "fifth", "5th")) {
            return 5;
        }
        if (containsAny(normalized, "छठा", "छठी", "छह", "छः", "six", "sixth", "6th")) {
            return 6;
        }
        if (containsAny(normalized, "सातवां", "सातवीं", "सात", "seven", "seventh", "7th")) {
            return 7;
        }
        if (containsAny(normalized, "आठवां", "आठवीं", "आठ", "eight", "eighth", "8th")) {
            return 8;
        }
        if (containsAny(normalized, "नौवां", "नौवीं", "नौ", "nine", "ninth", "9th")) {
            return 9;
        }
        if (containsAny(normalized, "दसवां", "दसवीं", "दस", "ten", "tenth", "10th")) {
            return 10;
        }
        return null;
    }

    private Integer parseNumber(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (!StringUtils.hasText(digits)) {
            return null;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String extractedDoctorFromMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String normalized = normalize(message);
        String[] tokens = normalized.split("\\s+");
        if (tokens.length < 2) {
            return null;
        }
        List<String> filtered = new ArrayList<>();
        for (String token : tokens) {
            if (!List.of("dr", "doctor", "डॉक्टर", "डॉ").contains(token)) {
                filtered.add(token);
            }
        }
        return filtered.isEmpty() ? null : String.join(" ", filtered);
    }

    private String extractedDateFromMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        Matcher iso = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b").matcher(message);
        if (iso.find()) {
            return iso.group(1) + "-" + iso.group(2) + "-" + iso.group(3);
        }
        Matcher month = Pattern.compile("\\b(\\d{1,2})\\s+([A-Za-z]{3,9})\\s+(\\d{4})\\b").matcher(message);
        if (month.find()) {
            return normalizeMonthDate(month.group(1), month.group(2), month.group(3));
        }
        Matcher monthAlt = Pattern.compile("\\b([A-Za-z]{3,9})\\s+(\\d{1,2})\\s+(\\d{4})\\b").matcher(message);
        if (monthAlt.find()) {
            return normalizeMonthDate(monthAlt.group(2), monthAlt.group(1), monthAlt.group(3));
        }
        return null;
    }

    private String normalizeMonthDate(String day, String monthName, String year) {
        try {
            int month = monthNumber(monthName);
            if (month < 1) {
                return null;
            }
            LocalDate date = LocalDate.of(Integer.parseInt(year), month, Integer.parseInt(day));
            return date.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private String extractedTimeFromMessage(String message, String language) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        Matcher explicit = TIME_PATTERN.matcher(message);
        if (explicit.find()) {
            int hour = Integer.parseInt(explicit.group(1));
            int minute = Integer.parseInt(explicit.group(2));
            if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                return LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("HH:mm"));
            }
        }
        String lower = message.toLowerCase(Locale.ROOT);
        Matcher halfPast = Pattern.compile("\\bhalf past (\\d{1,2})\\b").matcher(lower);
        if (halfPast.find()) {
            return LocalTime.of(parseHour(halfPast.group(1)), 30).format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        Matcher hindiHalfPast = HINDI_SAADHE_TIME_PATTERN.matcher(message);
        if (hindiHalfPast.find()) {
            Integer hour = parseHindiHour(hindiHalfPast.group(1));
            if (hour != null) {
                return LocalTime.of(hour, 30).format(DateTimeFormatter.ofPattern("HH:mm"));
            }
        }
        Matcher hindiWordHalf = HINDI_WORD_HALF_PATTERN.matcher(message);
        if (hindiWordHalf.find()) {
            Integer hour = parseHindiHour(hindiWordHalf.group(1));
            if (hour != null) {
                return LocalTime.of(hour, 30).format(DateTimeFormatter.ofPattern("HH:mm"));
            }
        }
        Matcher hindiClock = HINDI_TIME_PATTERN.matcher(message);
        if (hindiClock.find()) {
            Integer hour = parseHindiHour(hindiClock.group(1));
            if (hour != null) {
                int minute = hindiClock.group(2) == null ? 0 : parseMinute(hindiClock.group(2));
                return LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("HH:mm"));
            }
        }
        return null;
    }

    private String extractedTimeWindowFromMessage(String message, String language) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("morning") || message.contains("सुबह")) {
            return isHindi(language) ? "सुबह" : "morning";
        }
        if (lower.contains("afternoon") || message.contains("दोपहर")) {
            return isHindi(language) ? "दोपहर" : "afternoon";
        }
        if (lower.contains("evening") || message.contains("शाम")) {
            return isHindi(language) ? "शाम" : "evening";
        }
        if (lower.contains("night") || message.contains("रात")) {
            return isHindi(language) ? "रात" : "night";
        }
        return null;
    }

    private int parseHour(String value) {
        Integer parsed = parseNumber(value);
        return parsed == null ? 0 : Math.max(0, Math.min(23, parsed));
    }

    private int parseMinute(String value) {
        Integer parsed = parseNumber(value);
        return parsed == null ? 0 : Math.max(0, Math.min(59, parsed));
    }

    private Integer parseHindiHour(String value) {
        String normalized = normalize(value);
        return switch (normalized) {
            case "एक" -> 1;
            case "दो" -> 2;
            case "तीन" -> 3;
            case "चार" -> 4;
            case "पांच", "पाँच" -> 5;
            case "छह", "छः" -> 6;
            case "सात" -> 7;
            case "आठ" -> 8;
            case "नौ" -> 9;
            case "दस" -> 10;
            case "ग्यारह" -> 11;
            case "बारह" -> 12;
            default -> parseNumber(normalized);
        };
    }

    private String timeWindowFor(LocalTime time) {
        if (time == null) {
            return null;
        }
        int hour = time.getHour();
        if (hour >= 5 && hour < 12) {
            return "morning";
        }
        if (hour >= 12 && hour < 17) {
            return "afternoon";
        }
        if (hour >= 17 && hour < 21) {
            return "evening";
        }
        return "night";
    }

    private String multipleMatchPrompt(String language) {
        return isHindi(language)
                ? "मुझे एक से अधिक अपॉइंटमेंट मिल रही हैं। कृपया डॉक्टर का नाम, तारीख, समय या सूची का क्रमांक बताइए।"
                : "I found more than one matching appointment. Please share the doctor name, date, time, or list number.";
    }

    private PatientPortalCareAiAppointmentOption bestScoredMatch(List<PatientPortalCareAiAppointmentOption> appointments, SelectionSignals signals) {
        if (appointments == null || appointments.isEmpty()) {
            return null;
        }
        int bestScore = 0;
        PatientPortalCareAiAppointmentOption best = null;
        boolean tie = false;
        for (PatientPortalCareAiAppointmentOption option : appointments) {
            int score = score(option, signals);
            if (score > bestScore) {
                bestScore = score;
                best = option;
                tie = false;
            } else if (score == bestScore && score > 0) {
                tie = true;
            }
        }
        return tie ? null : best;
    }

    private int score(PatientPortalCareAiAppointmentOption option, SelectionSignals signals) {
        if (option == null || signals == null) {
            return 0;
        }
        int score = 0;
        String normalizedDoctor = normalize(option.doctorName());
        String normalizedLabel = normalize(label(option));
        if (StringUtils.hasText(signals.doctor())
                && (normalizedContains(normalizedDoctor, signals.doctor()) || normalizedContains(signals.doctor(), normalizedDoctor))) {
            score += 4;
        }
        if (StringUtils.hasText(signals.date())
                && Objects.equals(option.appointmentDate() == null ? null : option.appointmentDate().toString(), signals.date())) {
            score += 3;
        }
        if (StringUtils.hasText(signals.time())
                && Objects.equals(option.appointmentTime() == null ? null : option.appointmentTime().format(DateTimeFormatter.ofPattern("HH:mm")), signals.time())) {
            score += 2;
        }
        if (StringUtils.hasText(signals.timeWindow())
                && Objects.equals(timeWindowFor(option.appointmentTime()), signals.timeWindow())) {
            score += 1;
        }
        if (StringUtils.hasText(signals.normalizedMessage())
                && (normalizedContains(normalizedLabel, signals.normalizedMessage()) || normalizedContains(signals.normalizedMessage(), normalizedLabel))) {
            score += 1;
        }
        return score;
    }

    private String noMatchPrompt(String language) {
        return isHindi(language)
                ? "मुझे उस विवरण से कोई अपॉइंटमेंट नहीं मिली। कृपया डॉक्टर का नाम, तारीख, समय या सूची का क्रमांक बताइए।"
                : "I could not find a matching appointment. Please share the doctor name, date, time, or list number.";
    }

    private boolean normalizedContains(String haystack, String needle) {
        return StringUtils.hasText(haystack) && StringUtils.hasText(needle) && normalize(haystack).contains(normalize(needle));
    }

    private String canonicalTimeWindow(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = normalize(value);
        if (containsAny(normalized, "morning", "सुबह")) {
            return "morning";
        }
        if (containsAny(normalized, "afternoon", "दोपहर")) {
            return "afternoon";
        }
        if (containsAny(normalized, "evening", "शाम")) {
            return "evening";
        }
        if (containsAny(normalized, "night", "रात")) {
            return "night";
        }
        return normalized;
    }

    private boolean containsAny(String haystack, String... needles) {
        if (!StringUtils.hasText(haystack) || needles == null || needles.length == 0) {
            return false;
        }
        for (String needle : needles) {
            if (StringUtils.hasText(needle) && (haystack.equals(needle) || haystack.contains(needle))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String cleaned = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace("doctor", " ")
                .replace("dr.", " ")
                .replace("dr", " ")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return cleaned;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private int monthNumber(String monthName) {
        if (!StringUtils.hasText(monthName)) {
            return -1;
        }
        String normalized = normalize(monthName);
        return switch (normalized) {
            case "jan", "january", "जनवरी" -> 1;
            case "feb", "february", "फ़रवरी", "फरवरी" -> 2;
            case "mar", "march", "मार्च" -> 3;
            case "apr", "april", "अप्रैल" -> 4;
            case "may", "मई" -> 5;
            case "jun", "june", "जून" -> 6;
            case "jul", "july", "जुलाई" -> 7;
            case "aug", "august", "अगस्त" -> 8;
            case "sep", "september", "सितंबर", "सितम्बर" -> 9;
            case "oct", "october", "अक्टूबर" -> 10;
            case "nov", "november", "नवंबर", "नवम्बर" -> 11;
            case "dec", "december", "दिसंबर", "दिसम्बर" -> 12;
            default -> -1;
        };
    }

    private boolean isHindi(String language) {
        return StringUtils.hasText(language) && language.toLowerCase(Locale.ROOT).startsWith("hi");
    }

    public enum ResolutionStatus {
        RESOLVED,
        MULTIPLE,
        NONE
    }

    public record AppointmentResolution(
            ResolutionStatus status,
            PatientPortalCareAiAppointmentOption appointment,
            List<PatientPortalCareAiAppointmentOption> matches,
            String prompt
    ) {
        static AppointmentResolution resolved(PatientPortalCareAiAppointmentOption appointment) {
            return new AppointmentResolution(ResolutionStatus.RESOLVED, appointment, List.of(appointment), null);
        }

        static AppointmentResolution multiple(List<PatientPortalCareAiAppointmentOption> matches, String prompt) {
            return new AppointmentResolution(ResolutionStatus.MULTIPLE, null, List.copyOf(matches), prompt);
        }

        static AppointmentResolution none(String prompt) {
            return new AppointmentResolution(ResolutionStatus.NONE, null, List.of(), prompt);
        }

        static AppointmentResolution none() {
            return none(null);
        }

        public boolean resolved() {
            return status == ResolutionStatus.RESOLVED;
        }
    }

    private record SelectionSignals(
            String normalizedMessage,
            String doctor,
            String date,
            String time,
            String timeWindow
    ) {
    }
}
