package com.deepthoughtnet.clinic.api.voice;

import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilityRecord;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotRecord;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.patient.service.model.PatientSearchCriteria;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
class VoiceAppointmentWorkflowService {
    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\+?\\d[\\d\\s-]{6,15}\\d)");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern NAME_PATTERN = Pattern.compile("(?i)\\b(?:my name is|this is|i am|patient name is)\\s+([A-Za-z][A-Za-z .'-]{1,50})");
    private static final Pattern HINDI_NAME_PATTERN = Pattern.compile("(?:मेरा नाम|मैं)\\s+([^,.!?]+?)(?:\\s+हूँ|\\s+है|[,.!?]|$)");
    private static final List<String> POSITIVE_CONFIRMATIONS = List.of("yes", "confirm", "book it", "go ahead", "that's fine", "okay", "ok");
    private static final List<String> POSITIVE_CONFIRMATIONS_HI = List.of("हाँ", "हां", "ठीक है", "बुक कर दीजिए", "कन्फर्म", "सही है");
    private static final List<String> NEGATIVE_CONFIRMATIONS = List.of("no", "not that time", "another slot", "different time", "different doctor");
    private static final List<String> NEGATIVE_CONFIRMATIONS_HI = List.of("नहीं", "दूसरा समय", "दूसरी डॉक्टर", "दूसरा डॉक्टर", "दूसरा स्लॉट");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final AppointmentService appointmentService;
    private final PatientService patientService;

    VoiceAppointmentWorkflowService(AppointmentService appointmentService, PatientService patientService) {
        this.appointmentService = appointmentService;
        this.patientService = patientService;
    }

    VoiceWorkflowSummary resolve(UUID tenantId,
                                 String transcript,
                                 String language,
                                 VoiceWorkflowSummary previousSummary) {
        if (!StringUtils.hasText(transcript)) {
            return previousSummary == null
                    ? emptySummary(language)
                    : previousSummary;
        }

        String resolvedLanguage = normalizeLanguage(language, previousSummary);
        MutableState state = MutableState.from(tenantId, previousSummary, resolvedLanguage);
        ExtractedTurnDetails extracted = extract(transcript, resolvedLanguage, tenantId);
        boolean madeProgress = apply(state, extracted);

        if (state.confirmationRequested && extracted.negativeConfirmation) {
            state.bookingConfirmed = false;
            state.confirmationRequested = false;
            state.suggestedSlot = null;
            state.intentState = "COLLECTING_DETAILS";
            state.nextPrompt = questionForField("preferredTimeWindow", resolvedLanguage);
            madeProgress = true;
        }

        if (state.confirmationRequested && extracted.positiveConfirmation) {
            state.bookingConfirmed = true;
            state.intentState = "CONFIRMED_PENDING_RECEPTIONIST";
            state.nextPrompt = isHindi(resolvedLanguage)
                    ? "पुष्टि स्वीकार करें और बताएं कि रिसेप्शनिस्ट अपॉइंटमेंट अंतिम रूप देगा। बुक होने का दावा न करें।"
                    : "Acknowledge the confirmation and explain that a receptionist will finalize the appointment. Do not claim it is booked yet.";
            state.handoffRequired = false;
            state.handoffReason = null;
        } else if (!state.bookingConfirmed) {
            evaluateNextStep(tenantId, state, madeProgress);
        }

        if (!madeProgress && !state.bookingConfirmed && state.missingFields().size() >= 2) {
            state.unresolvedTurns += 1;
        } else if (madeProgress) {
            state.unresolvedTurns = 0;
        }

        if (state.unresolvedTurns >= 3) {
            state.handoffRequired = true;
            state.handoffReason = "insufficient-understanding";
            state.intentState = "HANDOFF_REQUIRED";
            state.nextPrompt = isHindi(resolvedLanguage)
                    ? "शिष्टता से बताएं कि रिसेप्शनिस्ट आगे मदद करेगा।"
                    : "Politely explain that a receptionist will take over from here.";
        }

        return state.toSummary();
    }

    private void evaluateNextStep(UUID tenantId, MutableState state, boolean madeProgress) {
        List<String> schedulingMissingFields = state.schedulingMissingFields();
        if (!schedulingMissingFields.isEmpty()) {
            state.intentState = "COLLECTING_DETAILS";
            state.confirmationRequested = false;
            state.suggestedSlot = null;
            state.nextPrompt = questionForField(schedulingMissingFields.getFirst(), state.language);
            return;
        }

        if (!StringUtils.hasText(state.doctorUserId) || !StringUtils.hasText(state.preferredDate)) {
            state.intentState = "COLLECTING_DETAILS";
            state.nextPrompt = questionForField("preferredDate", state.language);
            return;
        }

        Optional<VoiceSuggestedSlot> suggestedSlot = suggestSlot(tenantId, state);
        if (suggestedSlot.isPresent()) {
            state.suggestedSlot = suggestedSlot.get();
            state.confirmationRequested = true;
            state.intentState = "AWAITING_CONFIRMATION";
            state.nextPrompt = buildSlotPrompt(state.suggestedSlot, state.language);
            return;
        }

        state.intentState = "COLLECTING_DETAILS";
        state.confirmationRequested = false;
        state.suggestedSlot = null;
        state.nextPrompt = isHindi(state.language)
                ? "उपयुक्त स्लॉट नहीं मिला। कृपया दूसरा समय या तारीख बताएं।"
                : "I could not find a matching slot. Please share another date or time window.";
    }

    private Optional<VoiceSuggestedSlot> suggestSlot(UUID tenantId, MutableState state) {
        try {
            LocalDate appointmentDate = LocalDate.parse(state.preferredDate);
            List<DoctorAvailabilitySlotRecord> slots = appointmentService.listSlots(
                    tenantId,
                    UUID.fromString(state.doctorUserId),
                    appointmentDate
            );
            return slots.stream()
                    .filter(DoctorAvailabilitySlotRecord::selectable)
                    .filter(slot -> matchesTimeWindow(slot, state.preferredTimeWindow))
                    .min(Comparator.comparing(DoctorAvailabilitySlotRecord::slotTime))
                    .map(slot -> new VoiceSuggestedSlot(
                            slot.doctorUserId() == null ? null : slot.doctorUserId().toString(),
                            slot.doctorName(),
                            slot.appointmentDate() == null ? null : slot.appointmentDate().toString(),
                            slot.slotTime() == null ? null : slot.slotTime().toString(),
                            slot.slotEndTime() == null ? null : slot.slotEndTime().toString()
                    ));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private boolean matchesTimeWindow(DoctorAvailabilitySlotRecord slot, String preferredTimeWindow) {
        if (!StringUtils.hasText(preferredTimeWindow) || slot.slotTime() == null) {
            return true;
        }
        String normalized = preferredTimeWindow.toLowerCase(Locale.ROOT);
        LocalTime slotTime = slot.slotTime();
        if (normalized.startsWith("morning") || normalized.startsWith("सुबह")) {
            return !slotTime.isBefore(LocalTime.of(8, 0)) && slotTime.isBefore(LocalTime.NOON);
        }
        if (normalized.startsWith("afternoon") || normalized.startsWith("दोपहर")) {
            return !slotTime.isBefore(LocalTime.NOON) && slotTime.isBefore(LocalTime.of(16, 0));
        }
        if (normalized.startsWith("evening") || normalized.startsWith("शाम")) {
            return !slotTime.isBefore(LocalTime.of(16, 0));
        }
        try {
            LocalTime requested = LocalTime.parse(normalized, TIME_FORMATTER);
            return requested.equals(slotTime);
        } catch (DateTimeParseException ignored) {
            return true;
        }
    }

    private String buildSlotPrompt(VoiceSuggestedSlot slot, String language) {
        if (slot == null) {
            return questionForField("preferredTimeWindow", language);
        }
        if (isHindi(language)) {
            return "यह स्लॉट ऑफर करें: " + safe(slot.doctorName()) + ", " + safe(slot.appointmentDate()) + " को " + safe(slot.slotTime())
                    + ". केवल पुष्टि पूछें, बुक होने का दावा न करें।";
        }
        return "Offer this slot: " + safe(slot.doctorName()) + " on " + safe(slot.appointmentDate()) + " at " + safe(slot.slotTime())
                + ". Ask only for confirmation and do not claim it is booked.";
    }

    private String questionForField(String field, String language) {
        if (isHindi(language)) {
            return switch (field) {
                case "doctorName" -> "कृपया बताइए, आप किस डॉक्टर के साथ अपॉइंटमेंट चाहते हैं?";
                case "preferredDate" -> "कृपया तारीख बताइए, आप किस दिन आना चाहते हैं?";
                case "preferredTimeWindow" -> "कृपया समय बताइए, जैसे सुबह, दोपहर या शाम?";
                case "patientIdentity" -> "कृपया अपना नाम या फोन नंबर बताइए।";
                default -> "कृपया अगले ज़रूरी विवरण बताइए।";
            };
        }
        return switch (field) {
            case "doctorName" -> "Which doctor would you like to see?";
            case "preferredDate" -> "What date would you prefer for the appointment?";
            case "preferredTimeWindow" -> "What time works best, such as morning, afternoon, or evening?";
            case "patientIdentity" -> "Please share the patient name or phone number.";
            default -> "Please share the next appointment detail.";
        };
    }

    private boolean apply(MutableState state, ExtractedTurnDetails extracted) {
        boolean changed = false;
        if (StringUtils.hasText(extracted.patientPhone) && !extracted.patientPhone.equals(state.patientPhone)) {
            state.patientPhone = extracted.patientPhone;
            changed = true;
        }
        if (StringUtils.hasText(extracted.patientName) && !extracted.patientName.equalsIgnoreCase(state.patientName)) {
            state.patientName = extracted.patientName;
            changed = true;
        }
        if (StringUtils.hasText(extracted.doctorUserId) && !extracted.doctorUserId.equals(state.doctorUserId)) {
            state.doctorUserId = extracted.doctorUserId;
            state.doctorName = extracted.doctorName;
            changed = true;
        }
        if (StringUtils.hasText(extracted.preferredDate) && !extracted.preferredDate.equals(state.preferredDate)) {
            state.preferredDate = extracted.preferredDate;
            changed = true;
        }
        if (StringUtils.hasText(extracted.preferredTimeWindow) && !extracted.preferredTimeWindow.equalsIgnoreCase(state.preferredTimeWindow)) {
            state.preferredTimeWindow = extracted.preferredTimeWindow;
            changed = true;
        }
        if (StringUtils.hasText(extracted.reason) && !extracted.reason.equalsIgnoreCase(state.reason)) {
            state.reason = extracted.reason;
            changed = true;
        }
        if (!StringUtils.hasText(state.patientName) && StringUtils.hasText(state.patientPhone)) {
            List<PatientRecord> patients = patientService.search(state.tenantId, new PatientSearchCriteria(null, state.patientPhone, null, true));
            if (patients.size() == 1 && StringUtils.hasText(patients.getFirst().fullName())) {
                state.patientName = patients.getFirst().fullName();
                changed = true;
            }
        }
        return changed;
    }

    private ExtractedTurnDetails extract(String transcript, String language, UUID tenantId) {
        String normalized = transcript.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        List<DoctorAvailabilityRecord> doctorAvailabilities = appointmentService.listAvailabilities(tenantId);
        DoctorMatch doctorMatch = resolveDoctor(normalized, doctorAvailabilities);
        return new ExtractedTurnDetails(
                findPatientName(normalized, language),
                findPhone(normalized),
                doctorMatch == null ? null : doctorMatch.doctorUserId,
                doctorMatch == null ? null : doctorMatch.doctorName,
                findPreferredDate(normalized, language),
                findPreferredTimeWindow(normalized, lower, language),
                findReason(normalized, lower, language),
                containsAny(lower, POSITIVE_CONFIRMATIONS) || containsAny(normalized, POSITIVE_CONFIRMATIONS_HI),
                containsAny(lower, NEGATIVE_CONFIRMATIONS) || containsAny(normalized, NEGATIVE_CONFIRMATIONS_HI)
        );
    }

    private DoctorMatch resolveDoctor(String transcript, List<DoctorAvailabilityRecord> availabilities) {
        if (availabilities == null || availabilities.isEmpty()) {
            return null;
        }
        String normalizedTranscript = normalizeDoctorText(transcript);
        return availabilities.stream()
                .filter(record -> StringUtils.hasText(record.doctorName()))
                .map(record -> new DoctorMatch(
                        record.doctorUserId() == null ? null : record.doctorUserId().toString(),
                        record.doctorName(),
                        normalizeDoctorText(record.doctorName())
                ))
                .filter(match -> StringUtils.hasText(match.normalizedDoctorName) && normalizedTranscript.contains(match.normalizedDoctorName))
                .max(Comparator.comparingInt(match -> match.normalizedDoctorName.length()))
                .orElse(null);
    }

    private String findPatientName(String transcript, String language) {
        Matcher english = NAME_PATTERN.matcher(transcript);
        if (english.find()) {
            return cleanName(english.group(1));
        }
        if (isHindi(language) || transcript.contains("मेरा नाम") || transcript.contains("मैं ")) {
            Matcher hindi = HINDI_NAME_PATTERN.matcher(transcript);
            if (hindi.find()) {
                return cleanName(hindi.group(1));
            }
        }
        return null;
    }

    private String findPhone(String transcript) {
        Matcher matcher = PHONE_PATTERN.matcher(transcript);
        if (!matcher.find()) {
            return null;
        }
        String digits = matcher.group(1).replaceAll("[^\\d+]", "");
        return digits.length() >= 7 ? digits : null;
    }

    private String findPreferredDate(String transcript, String language) {
        Matcher iso = ISO_DATE_PATTERN.matcher(transcript);
        if (iso.find()) {
            return iso.group(1);
        }
        String lower = transcript.toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now();
        if (lower.contains("day after tomorrow") || transcript.contains("परसों")) {
            return today.plusDays(2).toString();
        }
        if (lower.contains("tomorrow") || transcript.contains("कल")) {
            return today.plusDays(1).toString();
        }
        if (lower.contains("today") || transcript.contains("आज")) {
            return today.toString();
        }
        Map<DayOfWeek, List<String>> weekdays = Map.of(
                DayOfWeek.MONDAY, List.of("monday", "सोमवार"),
                DayOfWeek.TUESDAY, List.of("tuesday", "मंगलवार"),
                DayOfWeek.WEDNESDAY, List.of("wednesday", "बुधवार"),
                DayOfWeek.THURSDAY, List.of("thursday", "गुरुवार"),
                DayOfWeek.FRIDAY, List.of("friday", "शुक्रवार"),
                DayOfWeek.SATURDAY, List.of("saturday", "शनिवार"),
                DayOfWeek.SUNDAY, List.of("sunday", "रविवार")
        );
        for (Map.Entry<DayOfWeek, List<String>> entry : weekdays.entrySet()) {
            if (entry.getValue().stream().anyMatch(token -> lower.contains(token.toLowerCase(Locale.ROOT)) || transcript.contains(token))) {
                return today.with(TemporalAdjusters.nextOrSame(entry.getKey())).toString();
            }
        }
        return null;
    }

    private String findPreferredTimeWindow(String transcript, String lower, String language) {
        if (lower.contains("morning") || transcript.contains("सुबह")) {
            return isHindi(language) ? "सुबह" : "morning";
        }
        if (lower.contains("afternoon") || transcript.contains("दोपहर")) {
            return isHindi(language) ? "दोपहर" : "afternoon";
        }
        if (lower.contains("evening") || transcript.contains("शाम")) {
            return isHindi(language) ? "शाम" : "evening";
        }
        Matcher explicitTime = Pattern.compile("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b", Pattern.CASE_INSENSITIVE).matcher(lower);
        if (explicitTime.find()) {
            int hour = Integer.parseInt(explicitTime.group(1));
            int minute = explicitTime.group(2) == null ? 0 : Integer.parseInt(explicitTime.group(2));
            String meridiem = explicitTime.group(3);
            if ("pm".equalsIgnoreCase(meridiem) && hour < 12) {
                hour += 12;
            } else if ("am".equalsIgnoreCase(meridiem) && hour == 12) {
                hour = 0;
            }
            if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                return LocalTime.of(hour, minute).format(TIME_FORMATTER);
            }
        }
        return null;
    }

    private String findReason(String transcript, String lower, String language) {
        for (String token : List.of("because ", "regarding ", "complaint is ", "symptoms are ")) {
            int index = lower.indexOf(token);
            if (index >= 0) {
                return trimToLength(transcript.substring(index + token.length()), 80);
            }
        }
        if (isHindi(language) && transcript.contains("के लिए")) {
            return trimToLength(transcript.substring(transcript.indexOf("के लिए") + "के लिए".length()), 80);
        }
        return null;
    }

    private boolean containsAny(String haystack, List<String> needles) {
        return needles.stream().anyMatch(haystack::contains);
    }

    private String normalizeLanguage(String language, VoiceWorkflowSummary previousSummary) {
        if (StringUtils.hasText(language) && !"auto".equalsIgnoreCase(language)) {
            return language.trim().toLowerCase(Locale.ROOT);
        }
        if (previousSummary != null && StringUtils.hasText(previousSummary.language())) {
            return previousSummary.language();
        }
        return "auto";
    }

    private boolean isHindi(String language) {
        return "hi".equalsIgnoreCase(language);
    }

    private VoiceWorkflowSummary emptySummary(String language) {
        return new VoiceWorkflowSummary(
                VoiceWorkflowMode.APPOINTMENT_BOOKING.configValue(),
                "COLLECTING_DETAILS",
                normalizeLanguage(language, null),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("patientIdentity", "doctorName", "preferredDate", "preferredTimeWindow"),
                null,
                false,
                false,
                false,
                null,
                questionForField("doctorName", language),
                0
        );
    }

    private String cleanName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.replaceAll("[^\\p{L} .'-]", " ").replaceAll("\\s{2,}", " ").trim();
    }

    private String normalizeDoctorText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace("doctor", "")
                .replace("dr.", "")
                .replace("dr", "")
                .replaceAll("[^\\p{L}\\p{Nd}]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private String trimToLength(String value, int maxChars) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars) + "...";
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value : "the available slot";
    }

    private static final class MutableState {
        private UUID tenantId;
        private String language;
        private String patientName;
        private String patientPhone;
        private String doctorUserId;
        private String doctorName;
        private String preferredDate;
        private String preferredTimeWindow;
        private String reason;
        private VoiceSuggestedSlot suggestedSlot;
        private boolean confirmationRequested;
        private boolean bookingConfirmed;
        private boolean handoffRequired;
        private String handoffReason;
        private String nextPrompt;
        private int unresolvedTurns;
        private String intentState;

        private static MutableState from(UUID tenantId, VoiceWorkflowSummary previousSummary, String language) {
            MutableState state = new MutableState();
            state.tenantId = tenantId;
            state.language = language;
            if (previousSummary != null) {
                state.patientName = previousSummary.patientName();
                state.patientPhone = previousSummary.patientPhone();
                state.doctorUserId = previousSummary.doctorUserId();
                state.doctorName = previousSummary.doctorName();
                state.preferredDate = previousSummary.preferredDate();
                state.preferredTimeWindow = previousSummary.preferredTimeWindow();
                state.reason = previousSummary.reason();
                state.suggestedSlot = previousSummary.suggestedSlot();
                state.confirmationRequested = previousSummary.confirmationRequested();
                state.bookingConfirmed = previousSummary.bookingConfirmed();
                state.handoffRequired = previousSummary.handoffRequired();
                state.handoffReason = previousSummary.handoffReason();
                state.nextPrompt = previousSummary.nextPrompt();
                state.unresolvedTurns = previousSummary.unresolvedTurns();
                state.intentState = previousSummary.intentState();
            } else {
                state.intentState = "COLLECTING_DETAILS";
            }
            return state;
        }

        private List<String> missingFields() {
            LinkedHashSet<String> missing = new LinkedHashSet<>();
            if (!StringUtils.hasText(doctorName) || !StringUtils.hasText(doctorUserId)) {
                missing.add("doctorName");
            }
            if (!StringUtils.hasText(preferredDate)) {
                missing.add("preferredDate");
            }
            if (!StringUtils.hasText(preferredTimeWindow)) {
                missing.add("preferredTimeWindow");
            }
            if (!StringUtils.hasText(patientName) && !StringUtils.hasText(patientPhone)) {
                missing.add("patientIdentity");
            }
            return new ArrayList<>(missing);
        }

        private List<String> schedulingMissingFields() {
            LinkedHashSet<String> missing = new LinkedHashSet<>();
            if (!StringUtils.hasText(doctorName) || !StringUtils.hasText(doctorUserId)) {
                missing.add("doctorName");
            }
            if (!StringUtils.hasText(preferredDate)) {
                missing.add("preferredDate");
            }
            if (!StringUtils.hasText(preferredTimeWindow)) {
                missing.add("preferredTimeWindow");
            }
            return new ArrayList<>(missing);
        }

        private VoiceWorkflowSummary toSummary() {
            return new VoiceWorkflowSummary(
                    VoiceWorkflowMode.APPOINTMENT_BOOKING.configValue(),
                    intentState,
                    language,
                    patientName,
                    patientPhone,
                    doctorUserId,
                    doctorName,
                    preferredDate,
                    preferredTimeWindow,
                    reason,
                    List.copyOf(missingFields()),
                    suggestedSlot,
                    confirmationRequested,
                    bookingConfirmed,
                    handoffRequired,
                    handoffReason,
                    nextPrompt,
                    unresolvedTurns
            );
        }
    }

    private record DoctorMatch(String doctorUserId, String doctorName, String normalizedDoctorName) {
    }

    private record ExtractedTurnDetails(
            String patientName,
            String patientPhone,
            String doctorUserId,
            String doctorName,
            String preferredDate,
            String preferredTimeWindow,
            String reason,
            boolean positiveConfirmation,
            boolean negativeConfirmation
    ) {
    }
}
