package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilitySlot;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Selection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves bounded selection controls only against the currently displayed availability page. */
final class AivaV2DisplayedSlotSelectionResolver {
    private static final String ORDINAL_TOKEN =
            "(first|second|third|fourth|last|[1-9](?:st|nd|rd|th)?)";
    private static final String ACTION_PREFIX =
            "(?:(?:please)\\s+)?(?:(?:book|select|choose|take|pick|use)\\s+)?";
    private static final Pattern ORDINAL_CONTROL = Pattern.compile(
            "^" + ACTION_PREFIX + "(?:the\\s+)?" + ORDINAL_TOKEN
                    + "(?:\\s+(?:one|slot|option))?(?:\\s+please)?[.!?]*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_CONTROL = Pattern.compile(
            "^" + ACTION_PREFIX + "(?:(?:the\\s+)?(?:slot\\s+)?(?:at|for)\\s+)?"
                    + "([01]?\\d|2[0-3])(?::([0-5]\\d))?\\s*(am|pm)?\\s*(?:o['’]?clock|baje|बजे)?"
                    + "(?:\\s+(?:one|slot))?(?:\\s+please)?[.!?]*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPOUND_CONTROL = Pattern.compile(
            "^" + ACTION_PREFIX + ORDINAL_TOKEN + "\\s*[,/]\\s*"
                    + "([01]?\\d|2[0-3])(?::([0-5]\\d))?\\s*(am|pm)?[.!?]*$",
            Pattern.CASE_INSENSITIVE);

    Resolution resolve(NormalizedUserTurn turn, AvailabilityResult availability) {
        if (turn == null || availability == null || availability.slots().isEmpty()) return Resolution.none();
        List<AvailabilitySlot> displayed = displayedSlots(availability);
        if (displayed.isEmpty()) return Resolution.none();

        String text = normalizeClockText(turn.rawText());
        Matcher compound = COMPOUND_CONTROL.matcher(text);
        if (compound.matches()) {
            int ordinal = ordinalValue(compound.group(1));
            LocalTime time = clockTime(compound.group(2), compound.group(3), compound.group(4));
            if (ordinalMatchesTime(displayed, ordinal, time)) {
                return Resolution.recognized(new Selection(null, ordinal, null, null));
            }
            return Resolution.recognized(new Selection(null, 0, null, null));
        }

        boolean explicitTime = hasExplicitTimeMarker(text);
        Integer ordinal = explicitTime ? null : turn.ordinal();
        if (ordinal == null && !explicitTime) {
            Matcher matcher = ORDINAL_CONTROL.matcher(text);
            if (matcher.matches()) ordinal = ordinalValue(matcher.group(1));
        }
        if (ordinal != null) {
            return Resolution.recognized(new Selection(null, ordinal, null, null));
        }

        // An explicit clock expression may be wrapped in a natural booking
        // request (for example, "रात 9 बजे का अपॉइंटमेंट बुक कर दो।").
        // Once the language boundary has extracted the exact time, resolve it
        // directly against the authoritative displayed page instead of
        // requiring the whole utterance to match the narrow control grammar.
        // This keeps time selection ahead of START_BOOKING while retaining the
        // existing displayed-slot and stale-result safeguards.
        if (explicitTime && turn.exactTime() != null && text.matches("(?is).*\\b(?:baje)\\b.*|.*बजे.*")) {
            LocalTime extracted = turn.exactTime();
            Matcher normalizedControl = TIME_CONTROL.matcher(turn.selectionText() == null ? "" : turn.selectionText());
            boolean meridiemPresent = hasMeridiemMarker(text);
            if (normalizedControl.matches()) {
                LocalTime parsed = clockTime(normalizedControl.group(1), normalizedControl.group(2),
                        normalizedControl.group(3));
                if (parsed != null) extracted = parsed;
                meridiemPresent = normalizedControl.group(3) != null;
            }
            LocalTime authoritative = uniqueDisplayedTime(displayed, extracted,
                    meridiemPresent, turn.daypart() == NormalizedUserTurn.Daypart.EVENING);
            return Resolution.recognized(new Selection(null, null, null, authoritative.toString()));
        }

        Matcher timeMatcher = TIME_CONTROL.matcher(explicitTime ? turn.selectionText() : text);
        if (!timeMatcher.matches()) return Resolution.none();
        LocalTime exact = turn.exactTime() != null ? turn.exactTime()
                : clockTime(timeMatcher.group(1), timeMatcher.group(2), timeMatcher.group(3));
        if (exact == null) return Resolution.none();
        LocalTime authoritative = uniqueDisplayedTime(displayed, exact, timeMatcher.group(3) != null,
                turn.daypart() == NormalizedUserTurn.Daypart.EVENING);
        return Resolution.recognized(new Selection(null, null, null, authoritative.toString()));
    }

    private boolean hasExplicitTimeMarker(String text) {
        return text.contains("बजे") || text.matches("(?is).*\\b(?:baje|am|pm|o['’]?clock)\\b.*")
                || text.matches(".*\\b(?:[01]?\\d|2[0-3]):[0-5]\\d\\b.*");
    }

    private String normalizeClockText(String value) {
        return (value == null ? "" : value.trim())
                .replaceAll("(?i)(?<![\\p{L}\\p{N}])([ap])\\s*\\.\\s*m\\.?(?![\\p{L}\\p{N}])", "$1m")
                .replaceAll("(?i)(?<![\\p{L}\\p{N}])(\\d{1,2})\\s+(\\d{2})(?=\\s*(?:am|pm)\\b)", "$1:$2");
    }

    private boolean hasMeridiemMarker(String text) {
        return text.matches("(?is).*\\b(?:am|pm|o['’]?clock)\\b.*");
    }

    private LocalTime uniqueDisplayedTime(List<AvailabilitySlot> displayed, LocalTime requested,
                                          boolean meridiemPresent, boolean eveningContext) {
        LocalTime contextual = requested;
        if (!meridiemPresent && eveningContext && requested.getHour() < 12) {
            contextual = LocalTime.of(requested.getHour() + 12, requested.getMinute());
        }
        LocalTime resolved = contextual;
        List<AvailabilitySlot> matches = displayed.stream()
                .filter(slot -> slot.startsAt().equals(resolved)
                        || (!meridiemPresent && slot.startsAt().getMinute() == requested.getMinute()
                        && slot.startsAt().getHour() % 12 == requested.getHour() % 12))
                .toList();
        return matches.size() == 1 ? matches.get(0).startsAt() : resolved;
    }

    private List<AvailabilitySlot> displayedSlots(AvailabilityResult availability) {
        int start = Math.min(availability.displayOffset(), availability.slots().size());
        int end = Math.min(availability.slots().size(), start + availability.pageSize());
        return availability.slots().subList(start, end);
    }

    private boolean ordinalMatchesTime(List<AvailabilitySlot> displayed, int ordinal, LocalTime time) {
        int index = ordinal == -1 ? displayed.size() - 1 : ordinal - 1;
        return time != null && index >= 0 && index < displayed.size()
                && time.equals(displayed.get(index).startsAt())
                && displayed.stream().filter(slot -> time.equals(slot.startsAt())).count() == 1;
    }

    private int ordinalValue(String token) {
        return switch (token.toLowerCase(Locale.ROOT)) {
            case "first" -> 1;
            case "second" -> 2;
            case "third" -> 3;
            case "fourth" -> 4;
            case "last" -> -1;
            default -> Integer.parseInt(token.replaceAll("[^0-9]", ""));
        };
    }

    private LocalTime clockTime(String hourText, String minuteText, String meridiem) {
        try {
            int hour = Integer.parseInt(hourText);
            int minute = minuteText == null ? 0 : Integer.parseInt(minuteText);
            if (meridiem != null && (hour < 1 || hour > 12)) return null;
            if ("pm".equalsIgnoreCase(meridiem) && hour < 12) hour += 12;
            if ("am".equalsIgnoreCase(meridiem) && hour == 12) hour = 0;
            return LocalTime.of(hour, minute);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    record Resolution(boolean recognized, Selection selection) {
        static Resolution none() {
            return new Resolution(false, null);
        }

        static Resolution recognized(Selection selection) {
            return new Resolution(true, selection);
        }
    }
}
