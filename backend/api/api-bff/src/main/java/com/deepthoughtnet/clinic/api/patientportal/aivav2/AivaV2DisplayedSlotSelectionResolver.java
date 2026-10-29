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
    private final AivaV2VoiceSpokenTimeNormalizer spokenTimeNormalizer = new AivaV2VoiceSpokenTimeNormalizer();
    private static final String ORDINAL_TOKEN =
            "(first|second|third|fourth|last|one|two|three|[1-9](?:st|nd|rd|th)?)";
    private static final String ACTION_PREFIX =
            "(?:(?:please)\\s+)?(?:(?:book|select|choose|take|pick|use)(?:\\s+(?:for|number))?\\s+)?";
    private static final Pattern ORDINAL_CONTROL = Pattern.compile(
            "^" + ACTION_PREFIX + "(?:the\\s+)?" + ORDINAL_TOKEN
                    + "(?:\\s+(?:one|slot|option|स्लॉट))?(?:\\s+please)?[.!?]*$",
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
    private static final Pattern ACCEPTED_TIME_CONTROL = Pattern.compile(
            "^" + ACTION_PREFIX + "(?:(ten|nine\\s+thirty|eleven\\s+thirty)|([01]?\\d|2[0-3])(?::([0-5]\\d))?\\s*(am|pm)?)"
                    + "\\s+(?:works(?:\\s+for\\s+me)?|is\\s+(?:fine|okay)|will\\s+do)[.!?]*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SPOKEN_TIME_CONTROL = Pattern.compile(
            "^" + ACTION_PREFIX + "(ten|nine\\s+thirty)[.!?]*$", Pattern.CASE_INSENSITIVE);

    Resolution resolve(NormalizedUserTurn turn, AvailabilityResult availability) {
        if (turn == null || availability == null || availability.slots().isEmpty()) return Resolution.none();
        List<AvailabilitySlot> displayed = displayedSlots(availability);
        if (displayed.isEmpty()) return Resolution.none();

        AivaV2VoiceSpokenTimeNormalizer.Resolution spoken = spokenTimeNormalizer.normalize(turn.rawText());
        if (spoken.recognized()) {
            List<AvailabilitySlot> matches = displayed.stream()
                    .filter(slot -> spoken.candidates().contains(slot.startsAt())).toList();
            if (matches.size() == 1) {
                return Resolution.recognized(new Selection(null, null, null, matches.getFirst().startsAt().toString()));
            }
            // A spoken time remains a slot-context action even when it is ambiguous or
            // absent from this page. The caller can clarify without resetting the page.
            return Resolution.recognized(new Selection(null, null, null, null));
        }

        String text = normalizeClockText(turn.rawText());
        Matcher acceptedTime = ACCEPTED_TIME_CONTROL.matcher(text);
        if (acceptedTime.matches()) {
            LocalTime requested = spokenTime(acceptedTime.group(1));
            boolean meridiemPresent = false;
            if (requested == null) {
                requested = clockTime(acceptedTime.group(2), acceptedTime.group(3), acceptedTime.group(4));
                meridiemPresent = acceptedTime.group(4) != null;
            }
            if (requested != null) {
                return exactTimeResolution(displayed, requested, meridiemPresent,
                        turn.daypart() == NormalizedUserTurn.Daypart.EVENING);
            }
        }
        Matcher spokenTime = SPOKEN_TIME_CONTROL.matcher(text);
        if (spokenTime.matches()) {
            LocalTime requested = spokenTime(spokenTime.group(1));
            return exactTimeResolution(displayed, requested, false,
                    turn.daypart() == NormalizedUserTurn.Daypart.EVENING);
        }
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
            return exactTimeResolution(displayed, extracted, meridiemPresent,
                    turn.daypart() == NormalizedUserTurn.Daypart.EVENING);
        }

        Matcher timeMatcher = TIME_CONTROL.matcher(text);
        if (!timeMatcher.matches()) return Resolution.none();
        LocalTime exact = turn.exactTime() != null ? turn.exactTime()
                : clockTime(timeMatcher.group(1), timeMatcher.group(2), timeMatcher.group(3));
        if (exact == null) return Resolution.none();
        return exactTimeResolution(displayed, exact, timeMatcher.group(3) != null,
                turn.daypart() == NormalizedUserTurn.Daypart.EVENING);
    }

    private Resolution exactTimeResolution(List<AvailabilitySlot> displayed, LocalTime requested,
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
        if (matches.size() > 1) return Resolution.recognized(new Selection(null, null, null, null));
        if (matches.isEmpty()) return Resolution.recognized(new Selection(null, null, null, null));
        LocalTime selected = matches.getFirst().startsAt();
        return Resolution.recognized(new Selection(null, null, null, selected.toString()));
    }

    private LocalTime spokenTime(String words) {
        if (words == null) return null;
        return switch (words.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim()) {
            case "ten" -> LocalTime.of(10, 0);
            case "nine thirty" -> LocalTime.of(9, 30);
            case "eleven thirty" -> LocalTime.of(11, 30);
            default -> null;
        };
    }

    private boolean hasExplicitTimeMarker(String text) {
        return text.contains("बजे") || text.matches("(?is).*\\b(?:baje|am|pm|o['’]?clock)\\b.*")
                || text.matches(".*\\b(?:[01]?\\d|2[0-3]):[0-5]\\d\\b.*");
    }

    private String normalizeClockText(String value) {
        String normalized = (value == null ? "" : value.trim())
                .replaceAll("(?i)(?<![\\p{L}\\p{N}])([ap])\\s*\\.\\s*m\\.?(?![\\p{L}\\p{N}])", "$1m")
                .replaceAll("(?i)(?<![\\p{L}\\p{N}])(\\d{1,2})\\s+(\\d{2})(?=\\s*(?:am|pm)\\b)", "$1:$2")
                .replaceAll("(?iu)ग्यारह\\s+तीस", "11:30")
                .replaceAll("(?iu)दस", "10")
                .replaceAll("(?iu)(?<![\\p{L}\\p{N}])(?:ten)(?![\\p{L}\\p{N}])", "10")
                .replaceAll("(?iu)(?<![\\p{L}\\p{N}])(?:eleven\\s+thirty)(?![\\p{L}\\p{N}])", "11:30")
                .replaceAll("(?iu)(?:पहला|पहली|पहले|pehla|pahla)", "first")
                .replaceAll("(?iu)(?:दूसरा|दूसरे|दूसरी|dusra|doosra|dusri|doosri)", "second")
                .replaceAll("(?iu)(?:तीसरा|तीसरे|तीसरी|teesra|teesri)", "third")
                .replaceAll("(?iu)(?:चौथा|चौथी|chautha|chauthi)", "fourth")
                .replaceAll("(?iu)(?:आखिरी|aakhiri)", "last")
                .replaceAll("(?iu)^\\s*(?:(?:book|select|choose|take|pick|use)\\s+(?:for\\s+|number\\s+)?|option\\s+|number\\s+|the\\s+)+", "")
                .replaceAll("(?iu)\\s+(?:book\\s+(?:it|karo|kar\\s+(?:do|dijiye))|बुक\\s+(?:कर\\s+(?:दो|दीजिए)|करो)|कर\\s+(?:दो|दीजिए)|le\\s+lo)\\s*$", "")
                .replaceAll("(?iu)\\s+(?:wala|waala|वाला|वाली|वाले|slot|स्लॉट|option|one)\\s*$", "")
                .replaceAll("(?iu)[.!?।]+$", "").trim();
        return normalized;
    }

    private boolean hasMeridiemMarker(String text) {
        return text.matches("(?is).*\\b(?:am|pm|o['’]?clock)\\b.*");
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
            case "one" -> 1;
            case "two" -> 2;
            case "three" -> 3;
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
