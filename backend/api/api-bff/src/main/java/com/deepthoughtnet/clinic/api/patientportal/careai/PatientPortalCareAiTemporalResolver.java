package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small fixed-clock temporal normalizer used before shadow state reduction. */
final class PatientPortalCareAiTemporalResolver {
    private static final Pattern NUMERIC = Pattern.compile("^(\\d{1,2})[\\s,/-]+(\\d{1,2})[\\s,/-]+(\\d{2}|\\d{4})$");
    private static final Pattern DMY = Pattern.compile("^(\\d{1,2})(?:st|nd|rd|th)?\\s+([a-z]+)\\s+(\\d{4})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME = Pattern.compile("^(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?$", Pattern.CASE_INSENSITIVE);
    private static final Map<String, DayOfWeek> DAYS = Map.of(
            "monday", DayOfWeek.MONDAY, "tuesday", DayOfWeek.TUESDAY, "wednesday", DayOfWeek.WEDNESDAY,
            "thursday", DayOfWeek.THURSDAY, "friday", DayOfWeek.FRIDAY, "saturday", DayOfWeek.SATURDAY,
            "sunday", DayOfWeek.SUNDAY);
    private static final Map<String, Month> MONTHS = Map.ofEntries(
            Map.entry("january", Month.JANUARY), Map.entry("february", Month.FEBRUARY), Map.entry("march", Month.MARCH),
            Map.entry("april", Month.APRIL), Map.entry("may", Month.MAY), Map.entry("june", Month.JUNE),
            Map.entry("july", Month.JULY), Map.entry("august", Month.AUGUST), Map.entry("september", Month.SEPTEMBER),
            Map.entry("october", Month.OCTOBER), Map.entry("november", Month.NOVEMBER), Map.entry("december", Month.DECEMBER));

    private final Clock clock;
    private final ZoneId zoneId;

    PatientPortalCareAiTemporalResolver(Clock clock, ZoneId zoneId) {
        this.clock = clock;
        this.zoneId = zoneId;
    }

    Optional<LocalDate> resolveDate(String expression) {
        if (expression == null || expression.isBlank()) return Optional.empty();
        String value = expression.trim().toLowerCase(Locale.ROOT).replaceAll("[,.!?]", " ").replaceAll("\\s+", " ").trim();
        value = value.replaceFirst("^(appointment|booking|book|schedule)\\s+(for|on)\\s+", "").trim();
        LocalDate today = LocalDate.now(clock.withZone(zoneId));
        final String normalizedValue = value;
        if (normalizedValue.matches("\\d{4}-\\d{2}-\\d{2}")) return parse(() -> LocalDate.parse(normalizedValue));
        Matcher numeric = NUMERIC.matcher(value);
        if (numeric.matches()) {
            int parsedYear = Integer.parseInt(numeric.group(3));
            final int year = parsedYear < 100 ? 2000 + parsedYear : parsedYear;
            return parse(() -> LocalDate.of(year, Integer.parseInt(numeric.group(2)), Integer.parseInt(numeric.group(1))));
        }
        Matcher dmy = DMY.matcher(value);
        if (dmy.matches() && MONTHS.containsKey(dmy.group(2))) {
            return parse(() -> LocalDate.of(Integer.parseInt(dmy.group(3)), MONTHS.get(dmy.group(2)).getValue(), Integer.parseInt(dmy.group(1))));
        }
        if (value.equals("today") || value.equals("aaj") || value.equals("आज")) return Optional.of(today);
        if (value.equals("tomorrow") || value.equals("kal") || value.equals("कल")) return Optional.of(today.plusDays(1));
        if (value.equals("day after tomorrow") || value.equals("parson") || value.equals("परसों")) return Optional.of(today.plusDays(2));
        String dayText = value.replaceFirst("^(next|this|अगले|नेक्स्ट)\\s+", "");
        DayOfWeek day = DAYS.get(dayText);
        if (day == null) return Optional.empty();
        return Optional.of(value.startsWith("this ") ? today.with(TemporalAdjusters.nextOrSame(day)) : today.with(TemporalAdjusters.next(day)));
    }

    Optional<LocalTime> resolveExactTime(String expression) {
        if (expression == null || expression.isBlank()) return Optional.empty();
        Matcher matcher = TIME.matcher(expression.trim().toLowerCase(Locale.ROOT).replace(" बजे", ""));
        if (!matcher.matches()) return Optional.empty();
        int hour = Integer.parseInt(matcher.group(1));
        int minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        String meridiem = matcher.group(3);
        if (meridiem != null) {
            if (hour < 1 || hour > 12) return Optional.empty();
            if (meridiem.equals("pm") && hour < 12) hour += 12;
            if (meridiem.equals("am") && hour == 12) hour = 0;
        }
        return hour < 24 && minute < 60 ? Optional.of(LocalTime.of(hour, minute)) : Optional.empty();
    }

    private Optional<LocalDate> parse(DateSupplier supplier) {
        try { return Optional.of(supplier.get()); } catch (DateTimeParseException | NumberFormatException ex) { return Optional.empty(); }
    }

    @FunctionalInterface private interface DateSupplier { LocalDate get(); }
}
