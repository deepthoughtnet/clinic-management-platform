package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

final class PatientPortalCareAiEntityExtractor {
    private static final Pattern ENGLISH_DOCTOR_PATTERN = Pattern.compile("(?i)\\b(?:dr\\.?|doctor)\\s+([A-Za-z][A-Za-z .'-]{1,60})");
    private static final Pattern DOCTOR_CONTEXT_PATTERN = Pattern.compile("(?i)\\b(?:with|for|see|consult|meet)\\s+(?:dr\\.?|doctor)\\s+([A-Za-z][A-Za-z .'-]{1,60})");
    private static final Pattern HINDI_DOCTOR_PATTERN = Pattern.compile("(?:डॉक्टर|डॉ\\.?)([^,.!?]+)");
    private static final Pattern ENGLISH_CLINIC_PATTERN = Pattern.compile("(?i)\\b(?:at|in|clinic|hospital|centre|center|branch)\\s+([A-Za-z][A-Za-z0-9 .'-]{1,80})");
    private static final Pattern HINDI_CLINIC_PATTERN = Pattern.compile("(?:क्लिनिक|हॉस्पिटल|सेंटर|केंद्र)([^,.!?]+)");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern DMY_DATE_PATTERN = Pattern.compile("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+([A-Za-z]{3,9})\\s+(\\d{4})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MDY_DATE_PATTERN = Pattern.compile("\\b([A-Za-z]{3,9})\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,)?\\s+(\\d{4})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DMY_DATE_WITHOUT_YEAR_PATTERN = Pattern.compile("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+([A-Za-z]{3,9})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MDY_DATE_WITHOUT_YEAR_PATTERN = Pattern.compile("\\b([A-Za-z]{3,9})\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SLASH_DATE_PATTERN = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b");
    private static final Pattern EXPLICIT_TIME_PATTERN = Pattern.compile("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SLOT_NUMBER_PATTERN = Pattern.compile("(?i)\\b(?:slot|number|book)\\s*(\\d{1,2})\\b");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\b(\\d{1,2})\\b");
    private static final Pattern SPECIALITY_PATTERN = Pattern.compile("(?i)\\b(?:speciality|specialty|department|dept)\\s+([A-Za-z][A-Za-z .'-]{1,60})");
    private static final DateTimeFormatter STRICT_ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT);
    private static final Map<String, Month> MONTH_NAME_MAP = monthNameMap();

    private final PatientPortalCareAiEntityRegistry registry;

    PatientPortalCareAiEntityExtractor(PatientPortalCareAiEntityRegistry registry) {
        this.registry = registry;
    }

    PatientPortalCareAiExtractedEntities extract(String transcript, String language) {
        if (!StringUtils.hasText(transcript)) {
            return PatientPortalCareAiExtractedEntities.empty();
        }
        Map<PatientPortalCareAiEntityType, List<String>> values = new EnumMap<>(PatientPortalCareAiEntityType.class);
        Map<PatientPortalCareAiEntityType, Double> confidence = new EnumMap<>(PatientPortalCareAiEntityType.class);
        String[] dateIssue = new String[1];
        String lower = transcript.toLowerCase(Locale.ROOT);

        extractDoctor(transcript, language, values, confidence);
        extractClinic(transcript, values, confidence);
        extractAppointment(transcript, values, confidence);
        extractDate(transcript, values, confidence, dateIssue);
        extractTime(transcript, values, confidence);
        extractSlotNumber(transcript, values, confidence);
        extractTimeWindow(transcript, language, values, confidence);
        extractSpeciality(transcript, values, confidence);
        extractLocation(transcript, values, confidence);
        extractConfirmation(lower, transcript, values, confidence);
        extractCancellation(lower, transcript, values, confidence);
        extractReset(lower, transcript, values, confidence);

        return new PatientPortalCareAiExtractedEntities(
                copy(values),
                copyConfidence(confidence),
                dateIssue[0]
        );
    }

    private void extractDoctor(String transcript,
                               String language,
                               Map<PatientPortalCareAiEntityType, List<String>> values,
                               Map<PatientPortalCareAiEntityType, Double> confidence) {
        Matcher english = ENGLISH_DOCTOR_PATTERN.matcher(transcript);
        if (english.find()) {
            put(values, confidence, PatientPortalCareAiEntityType.DOCTOR, cleanCandidate(trimDoctorQueryTail(english.group(1))), 0.97);
            return;
        }
        Matcher context = DOCTOR_CONTEXT_PATTERN.matcher(transcript);
        if (context.find()) {
            put(values, confidence, PatientPortalCareAiEntityType.DOCTOR, cleanCandidate(trimDoctorQueryTail(context.group(1))), 0.9);
            return;
        }
        if (isHindi(language) || transcript.contains("डॉक्टर") || transcript.contains("डॉ")) {
            Matcher hindi = HINDI_DOCTOR_PATTERN.matcher(transcript);
            if (hindi.find()) {
                put(values, confidence, PatientPortalCareAiEntityType.DOCTOR, cleanCandidate(hindi.group(1)), 0.95);
            }
        }
    }

    private void extractClinic(String transcript,
                               Map<PatientPortalCareAiEntityType, List<String>> values,
                               Map<PatientPortalCareAiEntityType, Double> confidence) {
        Matcher english = ENGLISH_CLINIC_PATTERN.matcher(transcript);
        if (english.find()) {
            put(values, confidence, PatientPortalCareAiEntityType.CLINIC, cleanCandidate(trimTrailingNoise(english.group(1))), 0.9);
            return;
        }
        Matcher hindi = HINDI_CLINIC_PATTERN.matcher(transcript);
        if (hindi.find()) {
            put(values, confidence, PatientPortalCareAiEntityType.CLINIC, cleanCandidate(trimTrailingNoise(hindi.group(1))), 0.88);
        }
    }

    private void extractAppointment(String transcript,
                                    Map<PatientPortalCareAiEntityType, List<String>> values,
                                    Map<PatientPortalCareAiEntityType, Double> confidence) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        if (lower.contains("appointment") || lower.contains("booking") || lower.contains("bookings") || lower.contains("visit")) {
            put(values, confidence, PatientPortalCareAiEntityType.APPOINTMENT, "appointment", 0.75);
        }
    }

    private void extractDate(String transcript,
                             Map<PatientPortalCareAiEntityType, List<String>> values,
                             Map<PatientPortalCareAiEntityType, Double> confidence,
                             String[] dateIssue) {
        Matcher iso = ISO_DATE_PATTERN.matcher(transcript);
        if (iso.find()) {
            LocalDate parsed = parseIsoDate(iso.group(1));
            if (parsed != null) {
                put(values, confidence, PatientPortalCareAiEntityType.DATE, parsed.toString(), 0.99);
            }
            return;
        }
        String lower = transcript.toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        if (lower.contains("day after tomorrow") || transcript.contains("परसों")) {
            put(values, confidence, PatientPortalCareAiEntityType.DATE, today.plusDays(2).toString(), 0.95);
            return;
        }
        if (lower.contains("tomorrow") || transcript.contains("कल")) {
            put(values, confidence, PatientPortalCareAiEntityType.DATE, today.plusDays(1).toString(), 0.95);
            return;
        }
        if (lower.contains("today") || transcript.contains("आज")) {
            put(values, confidence, PatientPortalCareAiEntityType.DATE, today.toString(), 0.95);
            return;
        }
        if (lower.contains("next week")) {
            put(values, confidence, PatientPortalCareAiEntityType.DATE, today.plusWeeks(1).toString(), 0.9);
            return;
        }
        if (lower.contains("next monday")) {
            put(values, confidence, PatientPortalCareAiEntityType.DATE, today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).toString(), 0.9);
            return;
        }
        if (lower.contains("next tuesday")) {
            put(values, confidence, PatientPortalCareAiEntityType.DATE, today.with(TemporalAdjusters.next(DayOfWeek.TUESDAY)).toString(), 0.9);
            return;
        }
        if (lower.contains("next wednesday")) {
            put(values, confidence, PatientPortalCareAiEntityType.DATE, today.with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY)).toString(), 0.9);
            return;
        }
        if (lower.contains("next thursday")) {
            put(values, confidence, PatientPortalCareAiEntityType.DATE, today.with(TemporalAdjusters.next(DayOfWeek.THURSDAY)).toString(), 0.9);
            return;
        }
        if (lower.contains("this weekend")) {
            put(values, confidence, PatientPortalCareAiEntityType.DATE, resolveWeekday(today, DayOfWeek.SATURDAY).toString(), 0.9);
            return;
        }
        if (lower.contains("next friday")) {
            put(values, confidence, PatientPortalCareAiEntityType.DATE, today.with(TemporalAdjusters.next(DayOfWeek.FRIDAY)).toString(), 0.9);
            return;
        }
        if (lower.contains("next saturday")) {
            put(values, confidence, PatientPortalCareAiEntityType.DATE, today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).toString(), 0.9);
            return;
        }
        if (lower.contains("next sunday")) {
            put(values, confidence, PatientPortalCareAiEntityType.DATE, today.with(TemporalAdjusters.next(DayOfWeek.SUNDAY)).toString(), 0.9);
            return;
        }
        if (lower.contains("this saturday")) {
            put(values, confidence, PatientPortalCareAiEntityType.DATE, resolveWeekday(today, DayOfWeek.SATURDAY).toString(), 0.9);
            return;
        }
        if (lower.contains("this sunday")) {
            put(values, confidence, PatientPortalCareAiEntityType.DATE, resolveWeekday(today, DayOfWeek.SUNDAY).toString(), 0.9);
            return;
        }
        for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
            if (matchesWeekday(transcript, lower, dayOfWeek)) {
                put(values, confidence, PatientPortalCareAiEntityType.DATE, resolveWeekday(today, dayOfWeek).toString(), 0.9);
                return;
            }
        }
        Matcher dmy = DMY_DATE_PATTERN.matcher(transcript);
        if (dmy.find()) {
            LocalDate parsed = parseMonthNameDate(dmy.group(1), dmy.group(2), dmy.group(3));
            if (parsed != null) {
                put(values, confidence, PatientPortalCareAiEntityType.DATE, parsed.toString(), 0.98);
            } else {
                dateIssue[0] = "invalid";
            }
            return;
        }
        Matcher mdy = MDY_DATE_PATTERN.matcher(transcript);
        if (mdy.find()) {
            LocalDate parsed = parseMonthNameDate(mdy.group(2), mdy.group(1), mdy.group(3));
            if (parsed != null) {
                put(values, confidence, PatientPortalCareAiEntityType.DATE, parsed.toString(), 0.98);
            } else {
                dateIssue[0] = "invalid";
            }
            return;
        }
        Matcher dmyWithoutYear = DMY_DATE_WITHOUT_YEAR_PATTERN.matcher(transcript);
        if (dmyWithoutYear.find()) {
            LocalDate parsed = parseMonthNameDateWithoutYear(dmyWithoutYear.group(1), dmyWithoutYear.group(2));
            if (parsed != null) {
                put(values, confidence, PatientPortalCareAiEntityType.DATE, parsed.toString(), 0.82);
            } else {
                dateIssue[0] = "invalid";
            }
            return;
        }
        Matcher mdyWithoutYear = MDY_DATE_WITHOUT_YEAR_PATTERN.matcher(transcript);
        if (mdyWithoutYear.find()) {
            LocalDate parsed = parseMonthNameDateWithoutYear(mdyWithoutYear.group(1), mdyWithoutYear.group(2));
            if (parsed != null) {
                put(values, confidence, PatientPortalCareAiEntityType.DATE, parsed.toString(), 0.82);
            } else {
                dateIssue[0] = "invalid";
            }
            return;
        }
        Matcher slash = SLASH_DATE_PATTERN.matcher(transcript);
        if (slash.find()) {
            String issue = slashDateIssue(slash.group(1), slash.group(2));
            if ("ambiguous".equals(issue)) {
                dateIssue[0] = issue;
            } else {
                LocalDate parsed = parseSlashDate(slash.group(1), slash.group(2), slash.group(3));
                if (parsed != null) {
                    put(values, confidence, PatientPortalCareAiEntityType.DATE, parsed.toString(), 0.92);
                } else {
                    dateIssue[0] = "invalid";
                }
            }
        }
    }

    private void extractTime(String transcript,
                             Map<PatientPortalCareAiEntityType, List<String>> values,
                             Map<PatientPortalCareAiEntityType, Double> confidence) {
        String sanitized = ISO_DATE_PATTERN.matcher(transcript.toLowerCase(Locale.ROOT)).replaceAll(" ");
        Matcher matcher = EXPLICIT_TIME_PATTERN.matcher(sanitized);
        while (matcher.find()) {
            if (matcher.group(2) == null && matcher.group(3) == null) {
                continue;
            }
            int hour = Integer.parseInt(matcher.group(1));
            int minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            String meridiem = matcher.group(3);
            if (meridiem != null) {
                meridiem = meridiem.replace(".", "").trim();
            }
            if ("pm".equalsIgnoreCase(meridiem) && hour < 12) {
                hour += 12;
            } else if ("am".equalsIgnoreCase(meridiem) && hour == 12) {
                hour = 0;
            }
            if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                put(values, confidence, PatientPortalCareAiEntityType.TIME, LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("HH:mm")), 0.95);
                return;
            }
        }
    }

    private void extractSlotNumber(String transcript,
                                   Map<PatientPortalCareAiEntityType, List<String>> values,
                                   Map<PatientPortalCareAiEntityType, Double> confidence) {
        Matcher matcher = SLOT_NUMBER_PATTERN.matcher(transcript);
        if (matcher.find()) {
            put(values, confidence, PatientPortalCareAiEntityType.TIME_SLOT, matcher.group(1), 0.9);
            return;
        }
        String lower = transcript.toLowerCase(Locale.ROOT);
        if (containsDateLikeExpression(transcript)) {
            return;
        }
        if (lower.contains("slot") || lower.contains("number") || lower.contains("book")) {
            Matcher digit = DIGIT_PATTERN.matcher(transcript);
            if (digit.find()) {
                put(values, confidence, PatientPortalCareAiEntityType.TIME_SLOT, digit.group(1), 0.65);
            }
        }
    }

    private void extractTimeWindow(String transcript,
                                   String language,
                                   Map<PatientPortalCareAiEntityType, List<String>> values,
                                   Map<PatientPortalCareAiEntityType, Double> confidence) {
        if (isGreetingOnly(transcript)) {
            return;
        }
        String lower = transcript.toLowerCase(Locale.ROOT);
        if (lower.contains("morning") || transcript.contains("सुबह")) {
            put(values, confidence, PatientPortalCareAiEntityType.TIME_WINDOW, isHindi(language) ? "सुबह" : "morning", 0.9);
            return;
        }
        if (lower.contains("afternoon") || transcript.contains("दोपहर")) {
            put(values, confidence, PatientPortalCareAiEntityType.TIME_WINDOW, isHindi(language) ? "दोपहर" : "afternoon", 0.9);
            return;
        }
        if (lower.contains("evening") || transcript.contains("शाम")) {
            put(values, confidence, PatientPortalCareAiEntityType.TIME_WINDOW, isHindi(language) ? "शाम" : "evening", 0.9);
            return;
        }
        if (lower.contains("night") || transcript.contains("रात")) {
            put(values, confidence, PatientPortalCareAiEntityType.TIME_WINDOW, isHindi(language) ? "रात" : "night", 0.9);
        }
    }

    private void extractSpeciality(String transcript,
                                   Map<PatientPortalCareAiEntityType, List<String>> values,
                                   Map<PatientPortalCareAiEntityType, Double> confidence) {
        Matcher matcher = SPECIALITY_PATTERN.matcher(transcript);
        if (matcher.find()) {
            put(values, confidence, PatientPortalCareAiEntityType.SPECIALITY, cleanCandidate(trimTrailingNoise(matcher.group(1))), 0.82);
        }
    }

    private void extractLocation(String transcript,
                                 Map<PatientPortalCareAiEntityType, List<String>> values,
                                 Map<PatientPortalCareAiEntityType, Double> confidence) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        if (lower.contains("location") || lower.contains("area") || lower.contains("city") || lower.contains("near")) {
            put(values, confidence, PatientPortalCareAiEntityType.LOCATION, trimTrailingNoise(transcript), 0.55);
        }
    }

    private void extractConfirmation(String lower, String transcript,
                                     Map<PatientPortalCareAiEntityType, List<String>> values,
                                     Map<PatientPortalCareAiEntityType, Double> confidence) {
        if (matchesAny(lower, transcript, registry.aliasesFor(PatientPortalCareAiEntityType.CONFIRMATION))) {
            put(values, confidence, PatientPortalCareAiEntityType.CONFIRMATION, "confirm", 0.99);
        }
    }

    private void extractCancellation(String lower, String transcript,
                                     Map<PatientPortalCareAiEntityType, List<String>> values,
                                     Map<PatientPortalCareAiEntityType, Double> confidence) {
        if (matchesAny(lower, transcript, registry.aliasesFor(PatientPortalCareAiEntityType.CANCELLATION))) {
            put(values, confidence, PatientPortalCareAiEntityType.CANCELLATION, "cancel", 0.98);
        }
    }

    private void extractReset(String lower, String transcript,
                              Map<PatientPortalCareAiEntityType, List<String>> values,
                              Map<PatientPortalCareAiEntityType, Double> confidence) {
        if (matchesAny(lower, transcript, registry.aliasesFor(PatientPortalCareAiEntityType.RESET))) {
            put(values, confidence, PatientPortalCareAiEntityType.RESET, "reset", 0.99);
        }
    }

    private boolean matchesAny(String lower, String transcript, Set<String> aliases) {
        for (String alias : aliases) {
            String normalizedAlias = alias.toLowerCase(Locale.ROOT);
            if (lower.contains(normalizedAlias) || transcript.toLowerCase(Locale.ROOT).contains(normalizedAlias)) {
                return true;
            }
        }
        return false;
    }

    private void put(Map<PatientPortalCareAiEntityType, List<String>> values,
                     Map<PatientPortalCareAiEntityType, Double> confidence,
                     PatientPortalCareAiEntityType type,
                     String value,
                     double score) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        values.put(type, List.of(value.trim()));
        confidence.put(type, score);
    }

    private Map<PatientPortalCareAiEntityType, List<String>> copy(Map<PatientPortalCareAiEntityType, List<String>> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<PatientPortalCareAiEntityType, List<String>> copy = new EnumMap<>(PatientPortalCareAiEntityType.class);
        for (Map.Entry<PatientPortalCareAiEntityType, List<String>> entry : values.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    private Map<PatientPortalCareAiEntityType, Double> copyConfidence(Map<PatientPortalCareAiEntityType, Double> confidence) {
        if (confidence == null || confidence.isEmpty()) {
            return Map.of();
        }
        Map<PatientPortalCareAiEntityType, Double> copy = new EnumMap<>(PatientPortalCareAiEntityType.class);
        copy.putAll(confidence);
        return Map.copyOf(copy);
    }

    private String cleanCandidate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return trimTrailingNoise(value)
                .replaceAll("^(?:dr\\.?|doctor)\\s+", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private String trimTrailingNoise(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = raw
                .replaceAll("(?i)\\b(?:tomorrow|today|day after tomorrow|next\\s+friday|next\\s+saturday|next\\s+sunday|next\\s+monday|next\\s+tuesday|next\\s+wednesday|next\\s+thursday|morning|afternoon|evening|night|confirm|book it|cancel it|yes|no)\\b.*$", "")
                .replaceAll("[,.!?].*$", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return StringUtils.hasText(cleaned) ? cleaned : raw.trim();
    }

    private String trimDoctorQueryTail(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String[] tokens = raw.trim().split("\\s+");
        List<String> kept = new ArrayList<>();
        for (String token : tokens) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (isDoctorQueryTailToken(lower)) {
                break;
            }
            kept.add(token);
        }
        String joined = String.join(" ", kept).replaceAll("\\s{2,}", " ").trim();
        return StringUtils.hasText(joined) ? joined : raw.trim();
    }

    private boolean isDoctorQueryTailToken(String lowerToken) {
        return "today".equals(lowerToken)
                || "tomorrow".equals(lowerToken)
                || "today's".equals(lowerToken)
                || "tomorrow's".equals(lowerToken)
                || "yesterday".equals(lowerToken)
                || "this".equals(lowerToken)
                || "next".equals(lowerToken)
                || "morning".equals(lowerToken)
                || "afternoon".equals(lowerToken)
                || "evening".equals(lowerToken)
                || "night".equals(lowerToken)
                || "noon".equals(lowerToken)
                || "lunch".equals(lowerToken)
                || "appointment".equals(lowerToken)
                || "appointments".equals(lowerToken)
                || "booking".equals(lowerToken)
                || "book".equals(lowerToken)
                || "schedule".equals(lowerToken)
                || "visit".equals(lowerToken)
                || "slot".equals(lowerToken)
                || "slots".equals(lowerToken)
                || "available".equals(lowerToken)
                || "availability".equals(lowerToken)
                || "for".equals(lowerToken)
                || "with".equals(lowerToken)
                || "on".equals(lowerToken)
                || "at".equals(lowerToken)
                || "please".equals(lowerToken)
                || "kindly".equals(lowerToken)
                || "monday".equals(lowerToken)
                || "tuesday".equals(lowerToken)
                || "wednesday".equals(lowerToken)
                || "thursday".equals(lowerToken)
                || "friday".equals(lowerToken)
                || "saturday".equals(lowerToken)
                || "sunday".equals(lowerToken)
                || MONTH_NAME_MAP.containsKey(lowerToken);
    }

    private LocalDate parseIsoDate(String value) {
        try {
            return LocalDate.parse(value, STRICT_ISO_DATE);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private LocalDate parseMonthNameDate(String dayValue, String monthValue, String yearValue) {
        Month month = MONTH_NAME_MAP.get(monthValue.toLowerCase(Locale.ROOT));
        if (month == null) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(yearValue), month, Integer.parseInt(dayValue));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private LocalDate parseMonthNameDateWithoutYear(String firstValue, String secondValue) {
        Month month = MONTH_NAME_MAP.get(secondValue.toLowerCase(Locale.ROOT));
        if (month != null) {
            return resolveMonthDayWithoutYear(month, firstValue);
        }
        month = MONTH_NAME_MAP.get(firstValue.toLowerCase(Locale.ROOT));
        if (month != null) {
            return resolveMonthDayWithoutYear(month, secondValue);
        }
        return null;
    }

    private LocalDate resolveMonthDayWithoutYear(Month month, String dayValue) {
        try {
            int day = Integer.parseInt(dayValue);
            LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
            for (int yearOffset = 0; yearOffset < 5; yearOffset++) {
                try {
                    LocalDate candidate = LocalDate.of(today.getYear() + yearOffset, month, day);
                    if (!candidate.isBefore(today)) {
                        return candidate;
                    }
                } catch (RuntimeException ignored) {
                    // Try the next year.
                }
            }
            return null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private LocalDate parseSlashDate(String dayValue, String monthValue, String yearValue) {
        try {
            int first = Integer.parseInt(dayValue);
            int second = Integer.parseInt(monthValue);
            int year = Integer.parseInt(yearValue);
            if (first > 12 && second <= 12) {
                return LocalDate.of(year, second, first);
            }
            if (second > 12 && first <= 12) {
                return LocalDate.of(year, first, second);
            }
            if (first <= 12 && second <= 12) {
                if (dayValue.length() == 2 && monthValue.length() == 2) {
                    return null;
                }
                return LocalDate.of(year, second, first);
            }
            return LocalDate.of(year, second, first);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String slashDateIssue(String firstValue, String secondValue) {
        try {
            int first = Integer.parseInt(firstValue);
            int second = Integer.parseInt(secondValue);
            if (first <= 12 && second <= 12 && firstValue.length() == 2 && secondValue.length() == 2) {
                return "ambiguous";
            }
            return null;
        } catch (RuntimeException ex) {
            return "invalid";
        }
    }

    private LocalDate resolveWeekday(LocalDate today, DayOfWeek dayOfWeek) {
        LocalDate candidate = today.with(TemporalAdjusters.nextOrSame(dayOfWeek));
        if (candidate.getDayOfWeek() == dayOfWeek) {
            return candidate;
        }
        return today.with(TemporalAdjusters.next(dayOfWeek));
    }

    private boolean matchesWeekday(String transcript, String lower, DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> lower.contains("monday") || transcript.contains("सोमवार");
            case TUESDAY -> lower.contains("tuesday") || transcript.contains("मंगलवार");
            case WEDNESDAY -> lower.contains("wednesday") || transcript.contains("बुधवार");
            case THURSDAY -> lower.contains("thursday") || transcript.contains("गुरुवार");
            case FRIDAY -> lower.contains("friday") || transcript.contains("शुक्रवार");
            case SATURDAY -> lower.contains("saturday") || transcript.contains("शनिवार");
            case SUNDAY -> lower.contains("sunday") || transcript.contains("रविवार");
        };
    }

    private boolean isHindi(String language) {
        return "hi".equalsIgnoreCase(language);
    }

    private boolean containsDateLikeExpression(String transcript) {
        if (!StringUtils.hasText(transcript)) {
            return false;
        }
        String lower = transcript.toLowerCase(Locale.ROOT);
        return ISO_DATE_PATTERN.matcher(transcript).find()
                || DMY_DATE_PATTERN.matcher(transcript).find()
                || MDY_DATE_PATTERN.matcher(transcript).find()
                || DMY_DATE_WITHOUT_YEAR_PATTERN.matcher(transcript).find()
                || MDY_DATE_WITHOUT_YEAR_PATTERN.matcher(transcript).find()
                || SLASH_DATE_PATTERN.matcher(transcript).find()
                || lower.contains("today")
                || lower.contains("tomorrow")
                || lower.contains("day after tomorrow")
                || lower.contains("next ")
                || lower.contains("this ")
                || lower.contains("yesterday");
    }

    private boolean isGreetingOnly(String transcript) {
        if (!StringUtils.hasText(transcript)) {
            return false;
        }
        String lower = transcript.toLowerCase(Locale.ROOT).trim();
        return List.of("hello", "hi", "hey", "good morning", "good afternoon", "good evening").contains(lower);
    }

    private static Map<String, Month> monthNameMap() {
        Map<String, Month> map = new HashMap<>();
        for (Month month : Month.values()) {
            map.put(month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH).toLowerCase(Locale.ROOT), month);
            map.put(month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH).toLowerCase(Locale.ROOT), month);
        }
        map.put("sept", Month.SEPTEMBER);
        return Map.copyOf(map);
    }
}
