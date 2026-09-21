package com.deepthoughtnet.clinic.api.patientportal.aivav2.language;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Locale-backed date extraction performed before a turn reaches the kernel. */
public final class LocaleTemporalNormalizer {
    private static final Pattern ISO = Pattern.compile("(?<![\\p{L}\\p{N}])\\d{4}-\\d{1,2}-\\d{1,2}(?![\\p{L}\\p{N}])");
    private static final Pattern NUMERIC = Pattern.compile("(?<![\\p{L}\\p{N}])(\\d{1,2})[./,\\-](\\d{1,2})(?:[./,\\-](\\d{2}|\\d{4}))?(?![\\p{L}\\p{N}])");
    private static final Pattern MONTH_FIRST = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(\\d{1,2})(?:st|nd|rd|th)?\\s+([\\p{L}\\p{M}.]+)(?:\\s+(\\d{4}))?(?![\\p{L}\\p{N}])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TEXT_MONTH_FIRST = Pattern.compile(
            "(?<![\\p{L}\\p{N}])([\\p{L}\\p{M}.]+)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:\\s+(\\d{4}))?(?![\\p{L}\\p{N}])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DAY_ONLY = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(\\d{1,2})(?:st|nd|rd|th)(?!\\s+(?:slot|appointment|one))(?![\\p{L}\\p{N}])"
                    + "|(?<![\\p{L}\\p{N}])(\\d{1,2})\\s*(?:ko|तारीख़?(?:\\s*को)?)(?![\\p{L}\\p{N}])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern RELATIVE = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(day after tomorrow|parson|परसों|tomorrow|kal|कल|today|aaj|आज)(?![\\p{L}\\p{N}])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern WEEKDAY = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(?:(next|this|अगले|इस|agale|is|आने\\s+वाला|aane\\s+wala)\\s+)?([\\p{L}\\p{M}]+)(?![\\p{L}\\p{N}])");
    private static final Map<String, Month> MONTHS = months();
    private static final Map<String, DayOfWeek> WEEKDAYS = weekdays();

    public NormalizedUserTurn.TemporalFact normalize(String text, LocalDate referenceDate) {
        String raw = text == null ? "" : text;
        LocalDate reference = referenceDate == null ? LocalDate.now() : referenceDate;
        Matcher iso = ISO.matcher(raw);
        if (iso.find()) return parsed(iso.group(), () -> LocalDate.parse(iso.group()));
        Matcher numeric = NUMERIC.matcher(raw);
        if (numeric.find()) {
            String span = numeric.group();
            return numeric(span, Integer.parseInt(numeric.group(1)), Integer.parseInt(numeric.group(2)),
                    numeric.group(3), reference);
        }
        Matcher monthDate = MONTH_FIRST.matcher(raw);
        while (monthDate.find()) {
            Month month = MONTHS.get(key(monthDate.group(2)));
            if (month == null) continue;
            String span = monthDate.group();
            int day = Integer.parseInt(monthDate.group(1));
            Integer year = monthDate.group(3) == null ? null : Integer.valueOf(monthDate.group(3));
            return resolve(span, () -> LocalDate.of(year == null ? inferredYear(reference, month.getValue(), day) : year,
                    month, day), year == null, NormalizedUserTurn.RelativeKind.NONE);
        }
        Matcher textMonthFirst = TEXT_MONTH_FIRST.matcher(raw);
        while (textMonthFirst.find()) {
            Month month = MONTHS.get(key(textMonthFirst.group(1)));
            if (month == null) continue;
            String span = textMonthFirst.group();
            int day = Integer.parseInt(textMonthFirst.group(2));
            Integer year = textMonthFirst.group(3) == null ? null : Integer.valueOf(textMonthFirst.group(3));
            return resolve(span, () -> LocalDate.of(year == null ? inferredYear(reference, month.getValue(), day) : year,
                    month, day), year == null, NormalizedUserTurn.RelativeKind.NONE);
        }
        Matcher dayOnly = DAY_ONLY.matcher(raw);
        if (dayOnly.find()) {
            String span = dayOnly.group();
            int day = Integer.parseInt(dayOnly.group(1) != null ? dayOnly.group(1) : dayOnly.group(2));
            return resolve(span, () -> {
                LocalDate candidate = LocalDate.of(reference.getYear(), reference.getMonthValue(), day);
                return candidate.isBefore(reference) ? candidate.plusYears(1) : candidate;
            }, true, NormalizedUserTurn.RelativeKind.NONE);
        }
        Matcher relative = RELATIVE.matcher(raw);
        if (relative.find()) {
            String token = key(relative.group(1));
            int days = switch (token) {
                case "today", "aaj", "आज" -> 0;
                case "day after tomorrow", "parson", "परसों" -> 2;
                default -> 1;
            };
            var kind = days == 0 ? NormalizedUserTurn.RelativeKind.TODAY
                    : days == 1 ? NormalizedUserTurn.RelativeKind.TOMORROW
                    : NormalizedUserTurn.RelativeKind.DAY_AFTER_TOMORROW;
            return new NormalizedUserTurn.TemporalFact(relative.group(), NormalizedUserTurn.TemporalStatus.RESOLVED,
                    reference.plusDays(days), kind, false);
        }
        Matcher weekday = WEEKDAY.matcher(raw);
        while (weekday.find()) {
            DayOfWeek day = WEEKDAYS.get(key(weekday.group(2)));
            if (day == null) continue;
            boolean thisWeek = weekday.group(1) != null
                    && List.of("this", "इस", "is").contains(key(weekday.group(1)));
            LocalDate resolved = thisWeek ? reference.with(TemporalAdjusters.nextOrSame(day))
                    : reference.with(TemporalAdjusters.next(day));
            return new NormalizedUserTurn.TemporalFact(weekday.group(), NormalizedUserTurn.TemporalStatus.RESOLVED,
                    resolved, thisWeek ? NormalizedUserTurn.RelativeKind.THIS_WEEKDAY
                            : NormalizedUserTurn.RelativeKind.NEXT_WEEKDAY, false);
        }
        return NormalizedUserTurn.TemporalFact.none();
    }

    private static int inferredYear(LocalDate reference, int month, int day) {
        LocalDate candidate = LocalDate.of(reference.getYear(), month, day);
        return candidate.isBefore(reference) ? reference.getYear() + 1 : reference.getYear();
    }

    private static NormalizedUserTurn.TemporalFact parsed(String span, DateFactory factory) {
        return resolve(span, factory, false, NormalizedUserTurn.RelativeKind.NONE);
    }

    private static NormalizedUserTurn.TemporalFact numeric(String span, int first, int second,
                                                           String yearText, LocalDate reference) {
        Integer year = yearText == null ? null : normalizedYear(yearText);
        LocalDate dayFirst = safeDate(year == null ? inferredYear(reference, second, first) : year, second, first);
        LocalDate monthFirst = safeDate(year == null ? inferredYear(reference, first, second) : year, first, second);
        if (dayFirst != null && monthFirst != null && !dayFirst.equals(monthFirst)) {
            return new NormalizedUserTurn.TemporalFact(span, NormalizedUserTurn.TemporalStatus.AMBIGUOUS,
                    null, NormalizedUserTurn.RelativeKind.NONE, year == null, List.of(dayFirst, monthFirst));
        }
        LocalDate resolved = dayFirst != null ? dayFirst : monthFirst;
        if (resolved == null) {
            return new NormalizedUserTurn.TemporalFact(span, NormalizedUserTurn.TemporalStatus.INVALID,
                    null, NormalizedUserTurn.RelativeKind.NONE, year == null);
        }
        return new NormalizedUserTurn.TemporalFact(span, NormalizedUserTurn.TemporalStatus.RESOLVED,
                resolved, NormalizedUserTurn.RelativeKind.NONE, year == null);
    }

    private static Integer normalizedYear(String value) {
        int year = Integer.parseInt(value);
        return value.length() == 2 ? 2000 + year : year;
    }

    private static LocalDate safeDate(int year, int month, int day) {
        try { return LocalDate.of(year, month, day); }
        catch (DateTimeException | NumberFormatException ex) { return null; }
    }

    private static NormalizedUserTurn.TemporalFact resolve(String span, DateFactory factory, boolean inferred,
                                                            NormalizedUserTurn.RelativeKind relativeKind) {
        try {
            return new NormalizedUserTurn.TemporalFact(span, NormalizedUserTurn.TemporalStatus.RESOLVED,
                    factory.create(), relativeKind, inferred);
        } catch (DateTimeException | NumberFormatException ex) {
            return new NormalizedUserTurn.TemporalFact(span, NormalizedUserTurn.TemporalStatus.INVALID,
                    null, relativeKind, inferred);
        }
    }

    private static Map<String, Month> months() {
        Map<String, Month> values = new HashMap<>();
        for (Month month : Month.values()) {
            values.put(key(month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)), month);
            values.put(key(month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)), month);
            Locale hindi = Locale.forLanguageTag("hi-IN");
            values.put(key(month.getDisplayName(TextStyle.FULL, hindi)), month);
            values.put(key(month.getDisplayName(TextStyle.SHORT, hindi)), month);
        }
        // Common Hindi month spelling variants; resolved to java.time.Month here, never in the kernel.
        values.put(key("फ़रवरी"), Month.FEBRUARY);
        values.put(key("फरवरी"), Month.FEBRUARY);
        values.put(key("सितम्बर"), Month.SEPTEMBER);
        values.put(key("सितंबर"), Month.SEPTEMBER);
        values.put(key("नवम्बर"), Month.NOVEMBER);
        values.put(key("नवंबर"), Month.NOVEMBER);
        values.put(key("दिसम्बर"), Month.DECEMBER);
        values.put(key("दिसंबर"), Month.DECEMBER);
        return Map.copyOf(values);
    }

    private static Map<String, DayOfWeek> weekdays() {
        Map<String, DayOfWeek> values = new HashMap<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            values.put(key(day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)), day);
            values.put(key(day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)), day);
            Locale hindi = Locale.forLanguageTag("hi-IN");
            values.put(key(day.getDisplayName(TextStyle.FULL, hindi)), day);
            values.put(key(day.getDisplayName(TextStyle.SHORT, hindi)), day);
        }
        String[][] transliterations = {{"somvaar", "सोमवार"}, {"mangalvaar", "मंगलवार"},
                {"budhvaar", "बुधवार"}, {"guruvaar", "गुरुवार"}, {"shukravaar", "शुक्रवार"},
                {"shanivaar", "शनिवार"}, {"ravivaar", "रविवार"}};
        for (int index = 0; index < transliterations.length; index++) {
            DayOfWeek day = DayOfWeek.of(index + 1);
            values.put(key(transliterations[index][0]), day);
            values.put(key(transliterations[index][1]), day);
        }
        return Map.copyOf(values);
    }

    private static String key(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replace("़", "").replaceAll("[.]", "").trim();
    }

    @FunctionalInterface private interface DateFactory { LocalDate create(); }
}
