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
    private static final Pattern ISO_DATE_SPACED_PATTERN = Pattern.compile("\\b(\\d{4})\\s+(\\d{1,2})\\s+(\\d{1,2})\\b");
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
    private static final Map<String, Month> HINDI_MONTH_NAME_MAP = hindiMonthNameMap();
    private static final Map<String, String> HINDI_DOCTOR_NAME_MAP = hindiDoctorNameMap();
    private static final Map<String, String> HINDI_NUMBER_WORD_MAP = hindiNumberWordMap();

    private final PatientPortalCareAiEntityRegistry registry;

    PatientPortalCareAiEntityExtractor(PatientPortalCareAiEntityRegistry registry) {
        this.registry = registry;
    }

    PatientPortalCareAiExtractedEntities extract(String transcript, String language) {
        if (!StringUtils.hasText(transcript)) {
            return PatientPortalCareAiExtractedEntities.empty();
        }
        String normalizedTranscript = normalizeForExtraction(transcript);
        Map<PatientPortalCareAiEntityType, List<String>> values = new EnumMap<>(PatientPortalCareAiEntityType.class);
        Map<PatientPortalCareAiEntityType, Double> confidence = new EnumMap<>(PatientPortalCareAiEntityType.class);
        String[] dateIssue = new String[1];
        String lower = normalizedTranscript.toLowerCase(Locale.ROOT);

        extractDoctor(transcript, normalizedTranscript, language, values, confidence);
        extractClinic(transcript, normalizedTranscript, values, confidence);
        extractAppointment(transcript, normalizedTranscript, values, confidence);
        extractDate(transcript, normalizedTranscript, values, confidence, dateIssue);
        extractTime(transcript, normalizedTranscript, values, confidence);
        extractSlotNumber(transcript, normalizedTranscript, values, confidence);
        extractTimeWindow(transcript, normalizedTranscript, language, values, confidence);
        extractSpeciality(transcript, normalizedTranscript, values, confidence);
        extractLocation(transcript, normalizedTranscript, values, confidence);
        extractConfirmation(lower, transcript, normalizedTranscript, values, confidence);
        extractCancellation(lower, transcript, normalizedTranscript, values, confidence);
        extractReset(lower, transcript, normalizedTranscript, values, confidence);

        return new PatientPortalCareAiExtractedEntities(
                transcript,
                normalizedTranscript,
                language,
                copy(values),
                copyConfidence(confidence),
                dateIssue[0]
        );
    }

    private void extractDoctor(String transcript,
                               String normalizedTranscript,
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
                put(values, confidence, PatientPortalCareAiEntityType.DOCTOR, cleanCandidate(normalizeDoctorPhrase(hindi.group(1))), 0.95);
                return;
            }
        }
        String normalizedDoctorText = normalizeDoctorPhrase(transcript);
        if (StringUtils.hasText(normalizedDoctorText) && !normalizedDoctorText.equalsIgnoreCase(transcript)) {
            Matcher normalizedMatcher = ENGLISH_DOCTOR_PATTERN.matcher(normalizedDoctorText);
            if (normalizedMatcher.find()) {
                put(values, confidence, PatientPortalCareAiEntityType.DOCTOR, cleanCandidate(trimDoctorQueryTail(normalizedMatcher.group(1))), 0.9);
            }
        }
    }

    private void extractClinic(String transcript,
                               String normalizedTranscript,
                               Map<PatientPortalCareAiEntityType, List<String>> values,
                               Map<PatientPortalCareAiEntityType, Double> confidence) {
        Matcher english = ENGLISH_CLINIC_PATTERN.matcher(normalizedTranscript);
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
                                    String normalizedTranscript,
                                    Map<PatientPortalCareAiEntityType, List<String>> values,
                                    Map<PatientPortalCareAiEntityType, Double> confidence) {
        String lower = normalizedTranscript.toLowerCase(Locale.ROOT);
        if (lower.contains("appointment") || lower.contains("booking") || lower.contains("bookings") || lower.contains("visit")) {
            put(values, confidence, PatientPortalCareAiEntityType.APPOINTMENT, "appointment", 0.75);
        }
    }

    private void extractDate(String transcript,
                             String normalizedTranscript,
                             Map<PatientPortalCareAiEntityType, List<String>> values,
                             Map<PatientPortalCareAiEntityType, Double> confidence,
                             String[] dateIssue) {
        Matcher iso = ISO_DATE_PATTERN.matcher(normalizedTranscript);
        if (iso.find()) {
            LocalDate parsed = parseIsoDate(iso.group(1));
            if (parsed != null) {
                put(values, confidence, PatientPortalCareAiEntityType.DATE, parsed.toString(), 0.99);
            }
            return;
        }
        Matcher isoSpaced = ISO_DATE_SPACED_PATTERN.matcher(normalizedTranscript);
        if (isoSpaced.find()) {
            LocalDate parsed = parseIsoDate(isoSpaced.group(1) + "-" + isoSpaced.group(2) + "-" + isoSpaced.group(3));
            if (parsed != null) {
                put(values, confidence, PatientPortalCareAiEntityType.DATE, parsed.toString(), 0.97);
            } else {
                dateIssue[0] = "invalid";
            }
            return;
        }
        String lower = normalizedTranscript.toLowerCase(Locale.ROOT);
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
        if (lower.contains("this friday")) {
            put(values, confidence, PatientPortalCareAiEntityType.DATE, resolveWeekday(today, DayOfWeek.FRIDAY).toString(), 0.95);
            return;
        }
        if (lower.contains("next friday") || transcript.contains("अगले शुक्रवार")) {
            put(values, confidence, PatientPortalCareAiEntityType.DATE, today.with(TemporalAdjusters.next(DayOfWeek.FRIDAY)).toString(), 0.95);
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
            if (matchesWeekday(normalizedTranscript, lower, dayOfWeek)) {
                put(values, confidence, PatientPortalCareAiEntityType.DATE, resolveWeekday(today, dayOfWeek).toString(), 0.9);
                return;
            }
        }
        Matcher dmy = DMY_DATE_PATTERN.matcher(normalizedTranscript);
        if (dmy.find()) {
            LocalDate parsed = parseMonthNameDate(dmy.group(1), dmy.group(2), dmy.group(3));
            if (parsed != null) {
                put(values, confidence, PatientPortalCareAiEntityType.DATE, parsed.toString(), 0.98);
            } else {
                dateIssue[0] = "invalid";
            }
            return;
        }
        Matcher mdy = MDY_DATE_PATTERN.matcher(normalizedTranscript);
        if (mdy.find()) {
            LocalDate parsed = parseMonthNameDate(mdy.group(2), mdy.group(1), mdy.group(3));
            if (parsed != null) {
                put(values, confidence, PatientPortalCareAiEntityType.DATE, parsed.toString(), 0.98);
            } else {
                dateIssue[0] = "invalid";
            }
            return;
        }
        Matcher dmyWithoutYear = DMY_DATE_WITHOUT_YEAR_PATTERN.matcher(normalizedTranscript);
        if (dmyWithoutYear.find()) {
            LocalDate parsed = parseMonthNameDateWithoutYear(dmyWithoutYear.group(1), dmyWithoutYear.group(2));
            if (parsed != null) {
                put(values, confidence, PatientPortalCareAiEntityType.DATE, parsed.toString(), 0.82);
            } else {
                dateIssue[0] = "invalid";
            }
            return;
        }
        Matcher mdyWithoutYear = MDY_DATE_WITHOUT_YEAR_PATTERN.matcher(normalizedTranscript);
        if (mdyWithoutYear.find()) {
            LocalDate parsed = parseMonthNameDateWithoutYear(mdyWithoutYear.group(1), mdyWithoutYear.group(2));
            if (parsed != null) {
                put(values, confidence, PatientPortalCareAiEntityType.DATE, parsed.toString(), 0.82);
            } else {
                dateIssue[0] = "invalid";
            }
            return;
        }
        Matcher slash = SLASH_DATE_PATTERN.matcher(normalizedTranscript);
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
                             String normalizedTranscript,
                             Map<PatientPortalCareAiEntityType, List<String>> values,
                             Map<PatientPortalCareAiEntityType, Double> confidence) {
        String sanitized = ISO_DATE_PATTERN.matcher(normalizedTranscript.toLowerCase(Locale.ROOT)).replaceAll(" ");
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
                                   String normalizedTranscript,
                                   Map<PatientPortalCareAiEntityType, List<String>> values,
                                   Map<PatientPortalCareAiEntityType, Double> confidence) {
        Matcher matcher = SLOT_NUMBER_PATTERN.matcher(normalizedTranscript);
        if (matcher.find()) {
            put(values, confidence, PatientPortalCareAiEntityType.TIME_SLOT, matcher.group(1), 0.9);
            return;
        }
        String lower = normalizedTranscript.toLowerCase(Locale.ROOT);
        if (containsDateLikeExpression(normalizedTranscript)) {
            return;
        }
        if (lower.contains("slot") || lower.contains("number") || lower.contains("book")) {
            Matcher digit = DIGIT_PATTERN.matcher(normalizedTranscript);
            if (digit.find()) {
                put(values, confidence, PatientPortalCareAiEntityType.TIME_SLOT, digit.group(1), 0.65);
            }
        }
    }

    private void extractTimeWindow(String transcript,
                                   String normalizedTranscript,
                                   String language,
                                   Map<PatientPortalCareAiEntityType, List<String>> values,
                                   Map<PatientPortalCareAiEntityType, Double> confidence) {
        if (isGreetingOnly(transcript)) {
            return;
        }
        String lower = normalizedTranscript.toLowerCase(Locale.ROOT);
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
                                   String normalizedTranscript,
                                   Map<PatientPortalCareAiEntityType, List<String>> values,
                                   Map<PatientPortalCareAiEntityType, Double> confidence) {
        Matcher matcher = SPECIALITY_PATTERN.matcher(normalizedTranscript);
        if (matcher.find()) {
            put(values, confidence, PatientPortalCareAiEntityType.SPECIALITY, cleanCandidate(trimTrailingNoise(matcher.group(1))), 0.82);
        }
    }

    private void extractLocation(String transcript,
                                 String normalizedTranscript,
                                 Map<PatientPortalCareAiEntityType, List<String>> values,
                                 Map<PatientPortalCareAiEntityType, Double> confidence) {
        String lower = normalizedTranscript.toLowerCase(Locale.ROOT);
        if (lower.contains("location") || lower.contains("area") || lower.contains("city") || lower.contains("near")) {
            put(values, confidence, PatientPortalCareAiEntityType.LOCATION, trimTrailingNoise(transcript), 0.55);
        }
    }

    private void extractConfirmation(String lower, String transcript,
                                     String normalizedTranscript,
                                     Map<PatientPortalCareAiEntityType, List<String>> values,
                                     Map<PatientPortalCareAiEntityType, Double> confidence) {
        String normalized = normalizedTranscript.toLowerCase(Locale.ROOT);
        if (matchesAny(lower, transcript, normalized, registry.aliasesFor(PatientPortalCareAiEntityType.CONFIRMATION))) {
            put(values, confidence, PatientPortalCareAiEntityType.CONFIRMATION, "confirm", 0.99);
        }
    }

    private void extractCancellation(String lower, String transcript,
                                     String normalizedTranscript,
                                     Map<PatientPortalCareAiEntityType, List<String>> values,
                                     Map<PatientPortalCareAiEntityType, Double> confidence) {
        String normalized = normalizedTranscript.toLowerCase(Locale.ROOT);
        if (matchesAny(lower, transcript, normalized, registry.aliasesFor(PatientPortalCareAiEntityType.CANCELLATION))) {
            put(values, confidence, PatientPortalCareAiEntityType.CANCELLATION, "cancel", 0.98);
        }
    }

    private void extractReset(String lower, String transcript,
                              String normalizedTranscript,
                              Map<PatientPortalCareAiEntityType, List<String>> values,
                              Map<PatientPortalCareAiEntityType, Double> confidence) {
        String normalized = normalizedTranscript.toLowerCase(Locale.ROOT);
        if (matchesAny(lower, transcript, normalized, registry.aliasesFor(PatientPortalCareAiEntityType.RESET))) {
            put(values, confidence, PatientPortalCareAiEntityType.RESET, "reset", 0.99);
        }
    }

    private boolean matchesAny(String lower, String transcript, String normalized, Set<String> aliases) {
        for (String alias : aliases) {
            String normalizedAlias = alias.toLowerCase(Locale.ROOT);
            if (lower.contains(normalizedAlias) || normalized.contains(normalizedAlias) || transcript.toLowerCase(Locale.ROOT).contains(normalizedAlias)) {
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
        String cleaned = trimTrailingNoise(value)
                .replaceAll("(?i)\\b(?:appointment|appointments|booking|book|visit|slot|slots|schedule|scheduled)\\b.*$", "")
                .replace("की", " ")
                .replace("के", " ")
                .replace("का", " ")
                .replace("को", " ")
                .replace("से", " ")
                .replace("में", " ")
                .replace("पर", " ")
                .replace("और", " ")
                .replaceAll("\\b(?:ki|ke|ka|ko|se|mein|par|aur)\\b", " ")
                .replaceAll("^(?:dr\\.?|doctor)\\s+", "")
                .replaceAll("\\s{2,}", " ")
                .trim()
                .replaceAll("(?i)\\bdr\\.?\\b\\s*", "")
                .replaceAll("(?i)\\bdoctor\\b\\s*", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return titleCaseLatinWords(cleaned);
    }

    private String titleCaseLatinWords(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String[] parts = value.split("\\s+");
        StringBuilder builder = new StringBuilder(value.length());
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            if (part.matches("(?i)[A-Za-z][A-Za-z']*")) {
                builder.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    builder.append(part.substring(1).toLowerCase(Locale.ROOT));
                }
            } else {
                builder.append(part);
            }
        }
        return builder.toString().trim();
    }

    private String normalizeDoctorPhrase(String value) {
        String normalized = normalizeForExtraction(value);
        if (!StringUtils.hasText(normalized)) {
            return normalized;
        }
        normalized = normalized
                .replaceAll("\\bdoctor\\b", "doctor")
                .replaceAll("\\bdr\\b", "dr");
        for (Map.Entry<String, String> entry : HINDI_DOCTOR_NAME_MAP.entrySet()) {
            normalized = normalized.replace(entry.getKey(), entry.getValue());
        }
        return normalized.replaceAll("\\s{2,}", " ").trim();
    }

    private String normalizeForExtraction(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = replaceDevanagariDigits(value)
                .replace("डॉक्टर", "doctor")
                .replace("डॉ.", "dr")
                .replace("डॉ", "dr")
                .replace("अपॉइंटमेंट", "appointment")
                .replace("बुक", "book")
                .replace("मुलाकात", "appointment")
                .replace("से मिलना", "appointment")
                .replace("दिखाना", "appointment")
                .replace("आना चाहता हूँ", "tomorrow")
                .replace("आना चाहती हूँ", "tomorrow")
                .replace("आज", "today")
                .replace("कल", "tomorrow")
                .replace("परसों", "day after tomorrow")
                .replace("अगले शुक्रवार", "next friday")
                .replace("इस शुक्रवार", "this friday")
                .replace("अगले शनिवार", "next saturday")
                .replace("इस शनिवार", "this saturday")
                .replace("अगले रविवार", "next sunday")
                .replace("इस रविवार", "this sunday")
                .replace("डॉक्टर से मिलना", "book appointment")
                .replace("डॉक्टर दिखाना", "book appointment")
                .replace("टॉपिक चेंज", "switch topic")
                .replace("बातचीत बदलो", "switch conversation")
                .replace("नया शुरू करो", "start over")
                .replace("शुरू से", "start over");
        for (Map.Entry<String, Month> entry : HINDI_MONTH_NAME_MAP.entrySet()) {
            normalized = normalized.replace(entry.getKey(), entry.getValue().name().toLowerCase(Locale.ROOT));
        }
        for (Map.Entry<String, String> entry : HINDI_NUMBER_WORD_MAP.entrySet()) {
            normalized = normalized.replace(entry.getKey(), entry.getValue());
        }
        return normalized
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", " ")
                .replaceAll("[\\p{Punct}&&[^:/]]", " ")
                .replaceAll("\\s{2,}", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String replaceDevanagariDigits(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            builder.append(switch (ch) {
                case '०' -> '0';
                case '१' -> '1';
                case '२' -> '2';
                case '३' -> '3';
                case '४' -> '4';
                case '५' -> '5';
                case '६' -> '6';
                case '७' -> '7';
                case '८' -> '8';
                case '९' -> '9';
                default -> ch;
            });
        }
        return builder.toString();
    }

    private String trimTrailingNoise(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = normalizeForExtraction(raw)
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
        return StringUtils.hasText(language) && language.toLowerCase(Locale.ROOT).startsWith("hi");
    }

    private boolean containsDateLikeExpression(String transcript) {
        if (!StringUtils.hasText(transcript)) {
            return false;
        }
        String normalized = normalizeForExtraction(transcript);
        String lower = normalized.toLowerCase(Locale.ROOT);
        return ISO_DATE_PATTERN.matcher(normalized).find()
                || ISO_DATE_SPACED_PATTERN.matcher(normalized).find()
                || DMY_DATE_PATTERN.matcher(normalized).find()
                || MDY_DATE_PATTERN.matcher(normalized).find()
                || DMY_DATE_WITHOUT_YEAR_PATTERN.matcher(normalized).find()
                || MDY_DATE_WITHOUT_YEAR_PATTERN.matcher(normalized).find()
                || SLASH_DATE_PATTERN.matcher(normalized).find()
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

    private static Map<String, Month> hindiMonthNameMap() {
        Map<String, Month> map = new HashMap<>();
        map.put("जनवरी", Month.JANUARY);
        map.put("फरवरी", Month.FEBRUARY);
        map.put("मार्च", Month.MARCH);
        map.put("अप्रैल", Month.APRIL);
        map.put("मई", Month.MAY);
        map.put("जून", Month.JUNE);
        map.put("जुलाई", Month.JULY);
        map.put("अगस्त", Month.AUGUST);
        map.put("सितंबर", Month.SEPTEMBER);
        map.put("सितम्बर", Month.SEPTEMBER);
        map.put("अक्टूबर", Month.OCTOBER);
        map.put("नवंबर", Month.NOVEMBER);
        map.put("नवम्बर", Month.NOVEMBER);
        map.put("दिसंबर", Month.DECEMBER);
        map.put("दिसम्बर", Month.DECEMBER);
        return Map.copyOf(map);
    }

    private static Map<String, String> hindiDoctorNameMap() {
        Map<String, String> map = new HashMap<>();
        map.put("विकास", "Vikas");
        map.put("आशीष", "Ashish");
        map.put("अशिश", "Ashish");
        map.put("अशिष", "Ashish");
        map.put("नेहा", "Neha");
        map.put("आरती", "Arti");
        map.put("आर्ती", "Arti");
        return Map.copyOf(map);
    }

    private static Map<String, String> hindiNumberWordMap() {
        Map<String, String> map = new HashMap<>();
        map.put("इकतीस", "31");
        map.put("इक्कीस", "21");
        map.put("बाईस", "22");
        map.put("तेईस", "23");
        map.put("चौबीस", "24");
        map.put("पच्चीस", "25");
        map.put("छब्बीस", "26");
        map.put("सत्ताईस", "27");
        map.put("अट्ठाईस", "28");
        map.put("उनतीस", "29");
        map.put("तीस", "30");
        map.put("उन्नीस", "19");
        map.put("अठारह", "18");
        map.put("सत्रह", "17");
        map.put("सोलह", "16");
        map.put("पंद्रह", "15");
        map.put("पन्द्रह", "15");
        map.put("चौदह", "14");
        map.put("तेरह", "13");
        map.put("बारह", "12");
        map.put("ग्यारह", "11");
        map.put("दस", "10");
        map.put("नौ", "9");
        map.put("आठ", "8");
        map.put("सात", "7");
        map.put("छह", "6");
        map.put("छः", "6");
        map.put("पांच", "5");
        map.put("पाँच", "5");
        map.put("चार", "4");
        map.put("तीन", "3");
        map.put("दो", "2");
        map.put("एक", "1");
        return Map.copyOf(map);
    }

}
