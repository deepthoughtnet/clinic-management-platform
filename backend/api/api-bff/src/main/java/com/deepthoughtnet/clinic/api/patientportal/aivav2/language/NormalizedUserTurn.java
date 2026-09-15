package com.deepthoughtnet.clinic.api.patientportal.aivav2.language;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Canonical, immutable language boundary consumed by the AIVA decision gateway. */
public record NormalizedUserTurn(
        String rawText,
        String normalizedText,
        String selectionText,
        String languageCode,
        String explicitDoctorReference,
        boolean explicitLookupQualifierPresent,
        List<DeterministicControl> controls,
        String responseLanguage,
        ResponseStyle responseStyle,
        String canonicalSemanticText,
        EntityReference doctorEntity,
        EntityReference specialtyEntity,
        TemporalFact temporal,
        LocalTime exactTime,
        Daypart daypart,
        Integer ordinal,
        Confirmation confirmation,
        Pagination pagination,
        Intent intent
) {
    public enum Daypart { MORNING, AFTERNOON, EVENING }
    public enum Confirmation { POSITIVE, NEGATIVE, NONE }
    public enum Pagination { SHOW_MORE, NONE }
    public enum Intent { BOOKING, LOOKUP, CANCELLATION, RESCHEDULE, UNKNOWN }
    public enum TemporalStatus { RESOLVED, AMBIGUOUS, INVALID, NONE }
    public enum RelativeKind { TODAY, TOMORROW, DAY_AFTER_TOMORROW, THIS_WEEKDAY, NEXT_WEEKDAY, NONE }

    public record EntityReference(String rawSpan, String canonicalQuery) { }

    public record TemporalFact(String rawSpan, TemporalStatus status, LocalDate localDate,
                               RelativeKind relativeKind, boolean yearInferred) {
        public TemporalFact {
            status = status == null ? TemporalStatus.NONE : status;
            relativeKind = relativeKind == null ? RelativeKind.NONE : relativeKind;
        }

        public static TemporalFact none() {
            return new TemporalFact(null, TemporalStatus.NONE, null, RelativeKind.NONE, false);
        }
    }

    public NormalizedUserTurn(String rawText, String normalizedText, String selectionText,
                              String languageCode, String explicitDoctorReference,
                              boolean explicitLookupQualifierPresent, List<DeterministicControl> controls,
                              String responseLanguage, ResponseStyle responseStyle) {
        this(rawText, normalizedText, selectionText, languageCode, explicitDoctorReference,
                explicitLookupQualifierPresent, controls, responseLanguage, responseStyle,
                normalizedText, entity(explicitDoctorReference, explicitDoctorReference), null, TemporalFact.none(),
                extractTime(selectionText), null, extractOrdinal(selectionText),
                confirmation(controls), pagination(controls), Intent.UNKNOWN);
    }

    public NormalizedUserTurn(String rawText, String normalizedText, String selectionText,
                              String languageCode, String explicitDoctorReference,
                              boolean explicitLookupQualifierPresent) {
        this(rawText, normalizedText, selectionText, languageCode, explicitDoctorReference,
                explicitLookupQualifierPresent, List.of(), languageCode, ResponseStyle.STANDARD);
    }

    public NormalizedUserTurn(String rawText, String normalizedText, String selectionText,
                              String languageCode, String explicitDoctorReference,
                              boolean explicitLookupQualifierPresent, List<DeterministicControl> controls) {
        this(rawText, normalizedText, selectionText, languageCode, explicitDoctorReference,
                explicitLookupQualifierPresent, controls, languageCode, ResponseStyle.STANDARD);
    }

    public NormalizedUserTurn {
        rawText = rawText == null ? "" : rawText;
        normalizedText = normalizedText == null ? "" : normalizedText;
        selectionText = selectionText == null ? "" : selectionText;
        controls = controls == null ? List.of() : List.copyOf(controls);
        responseLanguage = responseLanguage == null ? "en" : responseLanguage;
        responseStyle = responseStyle == null ? ResponseStyle.STANDARD : responseStyle;
        canonicalSemanticText = canonicalSemanticText == null ? normalizedText : canonicalSemanticText;
        temporal = temporal == null ? TemporalFact.none() : temporal;
        confirmation = confirmation == null ? Confirmation.NONE : confirmation;
        pagination = pagination == null ? Pagination.NONE : pagination;
        intent = intent == null ? Intent.UNKNOWN : intent;
    }

    public String detectedLanguage() { return languageCode; }
    public String orderedControlsText() { return String.join(" ", controls.stream().map(Enum::name).toList()); }

    public static EntityReference entity(String rawSpan, String canonicalQuery) {
        return rawSpan == null && canonicalQuery == null ? null : new EntityReference(rawSpan, canonicalQuery);
    }

    private static LocalTime extractTime(String text) {
        if (text == null) return null;
        var match = java.util.regex.Pattern.compile("(?<!\\d)([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)").matcher(text);
        if (!match.find()) return null;
        try { return LocalTime.of(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2))); }
        catch (RuntimeException ignored) { return null; }
    }

    private static Integer extractOrdinal(String text) {
        if (text == null) return null;
        var match = java.util.regex.Pattern.compile("^(?:the\\s+)?(first|second|third|fourth|[1-9])(?:\\s+(?:one|slot))?$",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text.trim());
        if (!match.matches()) return null;
        return switch (match.group(1).toLowerCase(java.util.Locale.ROOT)) {
            case "first" -> 1; case "second" -> 2; case "third" -> 3; case "fourth" -> 4;
            default -> Integer.valueOf(match.group(1));
        };
    }

    private static Confirmation confirmation(List<DeterministicControl> controls) {
        if (controls == null) return Confirmation.NONE;
        if (controls.contains(DeterministicControl.CONFIRMATION_POSITIVE)) return Confirmation.POSITIVE;
        if (controls.contains(DeterministicControl.CONFIRMATION_NEGATIVE)) return Confirmation.NEGATIVE;
        return Confirmation.NONE;
    }

    private static Pagination pagination(List<DeterministicControl> controls) {
        return controls != null && controls.contains(DeterministicControl.SHOW_MORE_SLOTS)
                ? Pagination.SHOW_MORE : Pagination.NONE;
    }
}
