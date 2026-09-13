package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.PatchMode;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ValuePatch;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves V2 date meaning before the transactional kernel sees a turn. */
final class AivaV2TemporalNormalizer {
    private static final Pattern ISO = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");
    private static final Pattern NUMERIC = Pattern.compile("\\b(\\d{1,2})[./,\\-\\s]+(\\d{1,2})[./,\\-\\s]+(\\d{4})\\b");
    private static final Pattern MONTH_DATE = Pattern.compile(
            "\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+([a-z]+)\\s+(\\d{4})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MONTH_DATE_WITHOUT_YEAR = Pattern.compile(
            "\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+([a-z]+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Map<String, DayOfWeek> DAYS = Map.of(
            "monday", DayOfWeek.MONDAY, "tuesday", DayOfWeek.TUESDAY, "wednesday", DayOfWeek.WEDNESDAY,
            "thursday", DayOfWeek.THURSDAY, "friday", DayOfWeek.FRIDAY, "saturday", DayOfWeek.SATURDAY,
            "sunday", DayOfWeek.SUNDAY);
    private static final Map<String, Month> MONTHS = Map.ofEntries(
            Map.entry("january", Month.JANUARY), Map.entry("february", Month.FEBRUARY),
            Map.entry("march", Month.MARCH), Map.entry("april", Month.APRIL), Map.entry("may", Month.MAY),
            Map.entry("june", Month.JUNE), Map.entry("july", Month.JULY), Map.entry("august", Month.AUGUST),
            Map.entry("september", Month.SEPTEMBER), Map.entry("october", Month.OCTOBER),
            Map.entry("november", Month.NOVEMBER), Map.entry("december", Month.DECEMBER));

    private final Clock clock;
    private final ZoneId zone;

    AivaV2TemporalNormalizer(Clock clock, ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    AivaV2TemporalResolution normalize(String transcript, ValuePatch modelPatch, LocalDate previous) {
        String modelCandidate = modelPatch != null && modelPatch.mode() == PatchMode.SET
                ? modelPatch.value() : null;
        Optional<Candidate> current = findCurrentTurnDate(transcript);
        if (current.isPresent()) {
            Candidate candidate = current.get();
            if (candidate.date() == null) {
                return new AivaV2TemporalResolution(candidate.invalid()
                                ? AivaV2TemporalResolution.Status.INVALID : AivaV2TemporalResolution.Status.AMBIGUOUS, null,
                        AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT, candidate.text(), modelCandidate, false);
            }
            LocalDate modelDate = resolveValue(modelCandidate).orElse(null);
            return new AivaV2TemporalResolution(AivaV2TemporalResolution.Status.RESOLVED, candidate.date(),
                    AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT, candidate.text(), modelCandidate,
                    modelDate != null && !candidate.date().equals(modelDate));
        }
        if (modelPatch == null || modelPatch.mode() == PatchMode.UNCHANGED) {
            return AivaV2TemporalResolution.unchanged(previous, modelCandidate);
        }
        if (modelPatch.mode() == PatchMode.CLEAR) {
            return new AivaV2TemporalResolution(AivaV2TemporalResolution.Status.RESOLVED, null,
                    AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT, null, modelCandidate, false);
        }
        if (modelCandidate == null || modelCandidate.isBlank()) {
            return new AivaV2TemporalResolution(AivaV2TemporalResolution.Status.INVALID, null,
                    AivaV2TemporalResolution.Source.MODEL_CANONICAL, null, modelCandidate, false);
        }
        return resolveValue(modelCandidate)
                .map(date -> new AivaV2TemporalResolution(AivaV2TemporalResolution.Status.RESOLVED, date,
                        AivaV2TemporalResolution.Source.MODEL_CANONICAL, null, modelCandidate, false))
                .orElseGet(() -> new AivaV2TemporalResolution(isNumericDate(modelCandidate)
                                ? AivaV2TemporalResolution.Status.INVALID : AivaV2TemporalResolution.Status.AMBIGUOUS, null,
                        AivaV2TemporalResolution.Source.MODEL_CANONICAL, null, modelCandidate, false));
    }

    private Optional<Candidate> findCurrentTurnDate(String transcript) {
        if (transcript == null || transcript.isBlank()) return Optional.empty();
        String value = transcript.toLowerCase(Locale.ROOT).replace('\u2013', '-').replace('\u2014', '-');
        Matcher iso = ISO.matcher(value);
        if (iso.find()) return parse(iso.group(), iso.group());
        Matcher numeric = NUMERIC.matcher(value);
        if (numeric.find()) {
            String text = numeric.group();
            int day = Integer.parseInt(numeric.group(1));
            int month = Integer.parseInt(numeric.group(2));
            if (day < 1 || day > 31 || month < 1 || month > 12) {
                return Optional.of(new Candidate(text, null, true));
            }
            // Jeevanam's India/UAT patient-facing date convention is day-month-year.
            return parse(text, text);
        }
        Matcher monthDate = MONTH_DATE.matcher(value);
        if (monthDate.find()) return parse(monthDate.group(), monthDate.group());
        Matcher monthDateWithoutYear = MONTH_DATE_WITHOUT_YEAR.matcher(value);
        if (monthDateWithoutYear.find() && MONTHS.containsKey(monthDateWithoutYear.group(2))) {
            return parseMonthDateWithoutYear(monthDateWithoutYear.group());
        }

        String normalized = value.replaceAll("[,.!?]", " ").replaceAll("\\s+", " ").trim();
        String relative = firstPresent(normalized,
                "day after tomorrow", "parson", "परसों", "tomorrow", "kal", "कल", "today", "aaj", "आज");
        if (relative != null) return relative(relative, relative);
        Matcher dayMatcher = Pattern.compile("(?:next|this|अगले|नेक्स्ट)?\\s*(monday|tuesday|wednesday|thursday|friday|saturday|sunday)",
                Pattern.CASE_INSENSITIVE).matcher(normalized);
        if (dayMatcher.find()) return day(dayMatcher.group(), dayMatcher.group(1), normalized.startsWith("this "));
        return Optional.empty();
    }

    private Optional<LocalDate> resolveValue(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[,.!?]", " ").replaceAll("\\s+", " ").trim();
        Matcher iso = ISO.matcher(normalized);
        if (iso.matches()) return parseDate(() -> LocalDate.parse(normalized));
        Matcher numeric = NUMERIC.matcher(normalized);
        if (numeric.matches()) {
            int year = Integer.parseInt(numeric.group(3));
            return parseDate(() -> LocalDate.of(year,
                    Integer.parseInt(numeric.group(2)), Integer.parseInt(numeric.group(1))));
        }
        Matcher monthDate = MONTH_DATE.matcher(normalized);
        if (monthDate.matches() && MONTHS.containsKey(monthDate.group(2))) {
            return parseDate(() -> LocalDate.of(Integer.parseInt(monthDate.group(3)),
                    MONTHS.get(monthDate.group(2)).getValue(), Integer.parseInt(monthDate.group(1))));
        }
        Matcher monthDateWithoutYear = MONTH_DATE_WITHOUT_YEAR.matcher(normalized);
        if (monthDateWithoutYear.matches() && MONTHS.containsKey(monthDateWithoutYear.group(2))) {
            return parseMonthDateWithoutYear(normalized).map(Candidate::date);
        }
        String relative = firstPresent(normalized, "day after tomorrow", "parson", "परसों", "tomorrow", "kal", "कल", "today", "aaj", "आज");
        if (relative != null) return relative(relative, relative).map(Candidate::date);
        Matcher day = Pattern.compile("(?:next|this|अगले|नेक्स्ट)?\\s*(monday|tuesday|wednesday|thursday|friday|saturday|sunday)", Pattern.CASE_INSENSITIVE).matcher(normalized);
        if (day.matches()) return this.day(normalized, day.group(1), normalized.startsWith("this ")).map(Candidate::date);
        return Optional.empty();
    }

    private boolean isNumericDate(String value) {
        if (value == null || value.isBlank()) return false;
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[,.!?]", " ").replaceAll("\\s+", " ").trim();
        return NUMERIC.matcher(normalized).matches();
    }

    private Optional<Candidate> relative(String text, String candidate) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        int days = text.equals("today") || text.equals("aaj") || text.equals("आज") ? 0
                : text.equals("day after tomorrow") || text.equals("parson") || text.equals("परसों") ? 2 : 1;
        return Optional.of(new Candidate(candidate, today.plusDays(days), false));
    }

    private Optional<Candidate> parseMonthDateWithoutYear(String text) {
        Matcher matcher = MONTH_DATE_WITHOUT_YEAR.matcher(text.toLowerCase(Locale.ROOT));
        if (!matcher.matches() || !MONTHS.containsKey(matcher.group(2))) return Optional.empty();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        return parseDate(() -> {
            LocalDate candidate = LocalDate.of(today.getYear(), MONTHS.get(matcher.group(2)).getValue(),
                    Integer.parseInt(matcher.group(1)));
            return candidate.isBefore(today) ? candidate.plusYears(1) : candidate;
        }).map(date -> new Candidate(text, date, false));
    }

    private Optional<Candidate> day(String text, String dayName, boolean sameWeekAllowed) {
        DayOfWeek target = DAYS.get(dayName.toLowerCase(Locale.ROOT));
        if (target == null) return Optional.empty();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate date = sameWeekAllowed ? today.with(TemporalAdjusters.nextOrSame(target)) : today.with(TemporalAdjusters.next(target));
        return Optional.of(new Candidate(text, date, false));
    }

    private Optional<Candidate> parse(String text, String candidate) {
        return Optional.of(resolveValue(candidate)
                .map(date -> new Candidate(text, date, false))
                .orElseGet(() -> new Candidate(text, null, true)));
    }

    private Optional<LocalDate> parseDate(DateSupplier supplier) {
        try { return Optional.of(supplier.get()); } catch (DateTimeException | NumberFormatException ex) { return Optional.empty(); }
    }

    private String firstPresent(String value, String... candidates) {
        for (String candidate : candidates) if (value.equals(candidate) || value.contains(" " + candidate) || value.startsWith(candidate + " ")) return candidate;
        return null;
    }

    private record Candidate(String text, LocalDate date, boolean invalid) { }
    @FunctionalInterface private interface DateSupplier { LocalDate get(); }
}
