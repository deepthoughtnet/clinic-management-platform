package com.deepthoughtnet.clinic.api.voice;

import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentUpsertCommand;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotRecord;
import com.deepthoughtnet.clinic.api.appointment.AppointmentTimingRules;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.patient.service.model.PatientSearchCriteria;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
class VoiceAppointmentWorkflowService {
    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\+?\\d[\\d\\s-]{6,15}\\d)");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern PATIENT_NUMBER_PATTERN = Pattern.compile("\\bPAT-[A-Z0-9-]{4,}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGLISH_NAME_PATTERN = Pattern.compile("(?i)\\b(?:my name is|this is|i am|patient name is)\\s+([A-Za-z][A-Za-z .'-]{1,50})");
    private static final Pattern HINDI_NAME_PATTERN = Pattern.compile("(?:मेरा नाम|मैं)\\s+([^,.!?]+?)(?:\\s+हूँ|\\s+है|[,.!?]|$)");
    private static final Pattern ENGLISH_DOCTOR_PATTERN = Pattern.compile("(?i)\\b(?:dr\\.?|doctor)\\s+([A-Za-z][A-Za-z .'-]{1,50})");
    private static final Pattern HINDI_DOCTOR_PATTERN = Pattern.compile("(?:डॉक्टर|डॉ\\.?)([^,.!?]+)");
    private static final List<String> POSITIVE_CONFIRMATIONS = List.of("yes", "confirm", "book it", "go ahead", "that's fine", "okay", "ok");
    private static final List<String> POSITIVE_CONFIRMATIONS_HI = List.of("हाँ", "हां", "ठीक है", "बुक कर दीजिए", "कन्फर्म", "सही है");
    private static final List<String> NEGATIVE_CONFIRMATIONS = List.of("no", "not that time", "another slot", "different time", "different doctor", "change slot");
    private static final List<String> NEGATIVE_CONFIRMATIONS_HI = List.of("नहीं", "दूसरा समय", "दूसरे समय", "दूसरी डॉक्टर", "दूसरा डॉक्टर", "दूसरा स्लॉट");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final AppointmentService appointmentService;
    private final PatientService patientService;
    private final TenantUserManagementService tenantUserManagementService;
    private final ClinicTimeZoneResolver clinicTimeZoneResolver;

    VoiceAppointmentWorkflowService(
            AppointmentService appointmentService,
            PatientService patientService,
            TenantUserManagementService tenantUserManagementService,
            ClinicTimeZoneResolver clinicTimeZoneResolver
    ) {
        this.appointmentService = appointmentService;
        this.patientService = patientService;
        this.tenantUserManagementService = tenantUserManagementService;
        this.clinicTimeZoneResolver = clinicTimeZoneResolver;
    }

    VoiceWorkflowSummary resolve(UUID tenantId,
                                 String transcript,
                                 String language,
                                 VoiceWorkflowSummary previousSummary) {
        if (!StringUtils.hasText(transcript)) {
            return previousSummary == null ? emptySummary(language, null) : previousSummary;
        }

        String resolvedLanguage = normalizeLanguage(language, previousSummary, transcript);
        MutableState state = MutableState.from(tenantId, previousSummary, resolvedLanguage);
        ExtractedTurnDetails extracted = extract(state.tenantId, transcript, resolvedLanguage);
        boolean madeProgress = applyExtractedFields(state, extracted);

        PatientResolution patientResolution = resolvePatient(state, extracted);
        DoctorResolution doctorResolution = resolveDoctor(state, extracted);
        madeProgress = madeProgress || patientResolution.madeProgress || doctorResolution.madeProgress;

        if (state.confirmationRequested && extracted.negativeConfirmation) {
            state.bookingConfirmed = false;
            state.confirmationRequested = false;
            state.suggestedSlot = null;
            state.slotSuggestions = List.of();
            state.booked = false;
            state.bookedAppointmentId = null;
            state.intentState = "COLLECTING_DETAILS";
            state.bookingWorkflowState = "SLOT_RESELECTION_REQUIRED";
            state.nextPrompt = alternateSlotPrompt(state.language);
            madeProgress = true;
        }

        if (state.confirmationRequested && extracted.positiveConfirmation) {
            madeProgress = true;
            tryBookAppointment(state);
        } else if (!state.booked && !state.handoffRequired) {
            evaluateNextStep(state, patientResolution, doctorResolution);
        }

        if (madeProgress) {
            state.unresolvedTurns = 0;
        } else if (!state.booked) {
            state.unresolvedTurns += 1;
        }

        if (!state.booked && state.unresolvedTurns >= 3) {
            applyHandoff(state, "repeated-resolution-failure");
        }

        return state.toSummary();
    }

    private boolean applyExtractedFields(MutableState state, ExtractedTurnDetails extracted) {
        boolean changed = false;
        if (StringUtils.hasText(extracted.patientPhone) && !extracted.patientPhone.equals(state.patientPhone)) {
            state.patientPhone = extracted.patientPhone;
            changed = true;
        }
        if (StringUtils.hasText(extracted.patientName) && !extracted.patientName.equalsIgnoreCase(state.patientName)) {
            state.patientName = extracted.patientName;
            changed = true;
        }
        if (StringUtils.hasText(extracted.patientNumber) && !extracted.patientNumber.equalsIgnoreCase(state.patientNumber)) {
            state.patientNumber = extracted.patientNumber;
            changed = true;
        }
        if (StringUtils.hasText(extracted.requestedDoctorName) && !extracted.requestedDoctorName.equalsIgnoreCase(state.requestedDoctorName)) {
            state.requestedDoctorName = extracted.requestedDoctorName;
            state.doctorMatchStatus = "PENDING";
            state.doctorUserId = null;
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
        return changed;
    }

    private PatientResolution resolvePatient(MutableState state, ExtractedTurnDetails extracted) {
        if (!StringUtils.hasText(state.patientNumber) && !StringUtils.hasText(state.patientPhone) && !StringUtils.hasText(state.patientName)) {
            return PatientResolution.none();
        }

        List<PatientRecord> patients;
        if (StringUtils.hasText(state.patientNumber)) {
            patients = patientService.search(state.tenantId, new PatientSearchCriteria(state.patientNumber, null, null, true));
        } else if (StringUtils.hasText(state.patientPhone)) {
            patients = patientService.search(state.tenantId, new PatientSearchCriteria(null, normalizePhone(state.patientPhone), null, true));
        } else {
            patients = patientService.search(state.tenantId, new PatientSearchCriteria(null, null, state.patientName, true));
        }

        List<PatientRecord> matches = refinePatientMatches(patients, state);
        if (matches.size() == 1) {
            PatientRecord patient = matches.getFirst();
            boolean changed = !patient.id().toString().equals(state.patientId);
            state.patientId = patient.id().toString();
            state.patientName = patient.fullName();
            state.patientPhone = patient.mobile();
            state.patientNumber = patient.patientNumber();
            state.patientMatchStatus = "IDENTIFIED";
            state.patientOptions = List.of();
            return new PatientResolution(changed, "IDENTIFIED");
        }

        state.patientId = null;
        if (matches.size() > 1) {
            state.patientMatchStatus = "AMBIGUOUS";
            state.patientOptions = matches.stream()
                    .limit(3)
                    .map(this::formatPatientOption)
                    .toList();
            return new PatientResolution(true, "AMBIGUOUS");
        }

        state.patientMatchStatus = "NOT_FOUND";
        state.patientOptions = List.of();
        return new PatientResolution(true, "NOT_FOUND");
    }

    private DoctorResolution resolveDoctor(MutableState state, ExtractedTurnDetails extracted) {
        if (!StringUtils.hasText(state.requestedDoctorName)) {
            return DoctorResolution.none();
        }

        List<TenantUserRecord> doctors = tenantUserManagementService.list(state.tenantId).stream()
                .filter(row -> "DOCTOR".equalsIgnoreCase(row.membershipRole()))
                .toList();
        if (doctors.isEmpty()) {
            state.doctorUserId = null;
            state.doctorName = null;
            state.doctorMatchStatus = "NOT_FOUND";
            state.doctorOptions = List.of();
            return new DoctorResolution(true, "NOT_FOUND");
        }

        String requested = normalizeDoctorText(state.requestedDoctorName);
        List<TenantUserRecord> matches = doctors.stream()
                .filter(row -> StringUtils.hasText(row.displayName()))
                .filter(row -> normalizeDoctorText(row.displayName()).contains(requested) || requested.contains(normalizeDoctorText(row.displayName())))
                .sorted(Comparator.comparing(TenantUserRecord::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (matches.size() == 1) {
            TenantUserRecord doctor = matches.getFirst();
            boolean changed = !doctor.appUserId().toString().equals(state.doctorUserId);
            state.doctorUserId = doctor.appUserId().toString();
            state.doctorName = doctor.displayName();
            state.doctorMatchStatus = "IDENTIFIED";
            state.doctorOptions = List.of();
            return new DoctorResolution(changed, "IDENTIFIED");
        }

        state.doctorUserId = null;
        if (matches.size() > 1) {
            state.doctorName = null;
            state.doctorMatchStatus = "AMBIGUOUS";
            state.doctorOptions = matches.stream().limit(4).map(TenantUserRecord::displayName).toList();
            return new DoctorResolution(true, "AMBIGUOUS");
        }

        state.doctorName = null;
        state.doctorMatchStatus = "NOT_FOUND";
        state.doctorOptions = doctors.stream().limit(4).map(TenantUserRecord::displayName).toList();
        return new DoctorResolution(true, "NOT_FOUND");
    }

    private void evaluateNextStep(MutableState state, PatientResolution patientResolution, DoctorResolution doctorResolution) {
        List<String> missingFields = state.missingFields();
        if (!missingFields.isEmpty()) {
            state.intentState = "COLLECTING_DETAILS";
            state.bookingWorkflowState = "COLLECTING_DETAILS";
            state.confirmationRequested = false;
            state.suggestedSlot = null;
            state.slotSuggestions = List.of();
            if ("AMBIGUOUS".equals(state.patientMatchStatus)) {
                state.nextPrompt = ambiguousPatientPrompt(state.patientOptions, state.language);
                return;
            }
            if ("NOT_FOUND".equals(state.patientMatchStatus) && patientResolution.attempted()) {
                state.nextPrompt = patientNotFoundPrompt(state.language);
                return;
            }
            if ("AMBIGUOUS".equals(state.doctorMatchStatus)) {
                state.nextPrompt = ambiguousDoctorPrompt(state.doctorOptions, state.language);
                return;
            }
            if ("NOT_FOUND".equals(state.doctorMatchStatus) && doctorResolution.attempted()) {
                state.nextPrompt = doctorNotFoundPrompt(state.doctorOptions, state.language);
                return;
            }
            state.nextPrompt = questionForField(missingFields.getFirst(), state.language);
            return;
        }

        resolveSlotSuggestions(state);
    }

    private void resolveSlotSuggestions(MutableState state) {
        List<VoiceSuggestedSlot> slotSuggestions = listSuggestedSlots(state);
        state.slotSuggestions = slotSuggestions;
        if (slotSuggestions.isEmpty()) {
            state.intentState = "COLLECTING_DETAILS";
            state.bookingWorkflowState = "NO_SLOT_AVAILABLE";
            state.confirmationRequested = false;
            state.suggestedSlot = null;
            state.nextPrompt = slotUnavailablePrompt(state.language);
            return;
        }

        state.suggestedSlot = slotSuggestions.getFirst();
        state.confirmationRequested = true;
        state.intentState = "AWAITING_CONFIRMATION";
        state.bookingWorkflowState = "SLOT_SUGGESTED";
        state.nextPrompt = buildSlotPrompt(state, slotSuggestions);
    }

    private List<VoiceSuggestedSlot> listSuggestedSlots(MutableState state) {
        if (!StringUtils.hasText(state.doctorUserId) || !StringUtils.hasText(state.preferredDate)) {
            return List.of();
        }
        try {
            UUID doctorUserId = UUID.fromString(state.doctorUserId);
            LocalDate preferredDate = LocalDate.parse(state.preferredDate);
            ZoneId tenantZone = clinicTimeZoneResolver.resolve(state.tenantId);
            ZonedDateTime clinicNow = ZonedDateTime.now(tenantZone);
            List<DoctorAvailabilitySlotRecord> slots = appointmentService.listSlots(state.tenantId, doctorUserId, preferredDate, tenantZone).stream()
                    .filter(slot -> AppointmentTimingRules.isSlotBookableForPatient(slot.appointmentDate(), slot.slotTime(), tenantZone, clinicNow))
                    .filter(DoctorAvailabilitySlotRecord::selectable)
                    .sorted(Comparator.comparing(DoctorAvailabilitySlotRecord::slotTime))
                    .toList();
            if (slots.isEmpty()) {
                return List.of();
            }
            List<DoctorAvailabilitySlotRecord> matching = filterByPreferredTime(slots, state.preferredTimeWindow);
            List<DoctorAvailabilitySlotRecord> resolved = matching.isEmpty() ? nearestSlots(slots, state.preferredTimeWindow) : matching;
            return resolved.stream()
                    .limit(3)
                    .map(this::toSuggestedSlot)
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void tryBookAppointment(MutableState state) {
        if (state.booked || state.suggestedSlot == null || !StringUtils.hasText(state.patientId) || !StringUtils.hasText(state.doctorUserId)) {
            state.confirmationRequested = false;
            state.bookingConfirmed = false;
            applyHandoff(state, "booking-context-missing");
            return;
        }
        try {
            AppointmentRecord created = appointmentService.createScheduled(
                    state.tenantId,
                    new AppointmentUpsertCommand(
                            UUID.fromString(state.patientId),
                            UUID.fromString(state.doctorUserId),
                            LocalDate.parse(state.suggestedSlot.appointmentDate()),
                            LocalTime.parse(state.suggestedSlot.slotTime()),
                            normalizeNullable(state.reason),
                            null,
                            null,
                            AppointmentPriority.NORMAL,
                            false
                    ),
                    null,
                    false,
                    clinicTimeZoneResolver.resolve(state.tenantId)
            );
            state.confirmationRequested = false;
            state.bookingConfirmed = true;
            state.booked = true;
            state.bookedAppointmentId = created.id() == null ? null : created.id().toString();
            state.intentState = "BOOKED";
            state.bookingWorkflowState = "BOOKED";
            state.handoffRequired = false;
            state.handoffReason = null;
            state.nextPrompt = bookingSuccessPrompt(state);
        } catch (RuntimeException ex) {
            state.bookingConfirmed = false;
            state.booked = false;
            state.bookedAppointmentId = null;
            state.confirmationRequested = false;
            state.intentState = "COLLECTING_DETAILS";
            state.bookingWorkflowState = "BOOKING_FAILED";
            state.nextPrompt = bookingFailedPrompt(state.language);
            state.slotSuggestions = listSuggestedSlots(state);
            state.suggestedSlot = state.slotSuggestions.isEmpty() ? null : state.slotSuggestions.getFirst();
        }
    }

    private void applyHandoff(MutableState state, String reason) {
        state.handoffRequired = true;
        state.handoffReason = reason;
        state.confirmationRequested = false;
        state.intentState = "HANDOFF_REQUIRED";
        state.bookingWorkflowState = "HANDOFF_REQUIRED";
        state.nextPrompt = isHindi(state.language)
                ? "मैं रिसेप्शन टीम से आपकी बुकिंग में मदद करने को कहूँगा।"
                : "I’ll ask the receptionist to help you with this booking.";
    }

    private ExtractedTurnDetails extract(UUID tenantId, String transcript, String language) {
        String normalized = transcript.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        return new ExtractedTurnDetails(
                findPatientName(normalized, language),
                findPhone(normalized),
                findPatientNumber(normalized),
                findRequestedDoctorName(normalized, language),
                findPreferredDate(tenantId, normalized, language),
                findPreferredTimeWindow(normalized, lower, language),
                findReason(normalized, lower, language),
                containsAny(lower, POSITIVE_CONFIRMATIONS) || containsAny(normalized, POSITIVE_CONFIRMATIONS_HI),
                containsAny(lower, NEGATIVE_CONFIRMATIONS) || containsAny(normalized, NEGATIVE_CONFIRMATIONS_HI)
        );
    }

    private List<PatientRecord> refinePatientMatches(List<PatientRecord> patients, MutableState state) {
        if (patients == null || patients.isEmpty()) {
            return List.of();
        }
        if (StringUtils.hasText(state.patientNumber)) {
            return patients.stream()
                    .filter(row -> state.patientNumber.equalsIgnoreCase(row.patientNumber()))
                    .toList();
        }
        if (StringUtils.hasText(state.patientPhone)) {
            String normalizedPhone = normalizePhone(state.patientPhone);
            List<PatientRecord> exactPhoneMatches = patients.stream()
                    .filter(row -> normalizedPhone.equals(normalizePhone(row.mobile())))
                    .toList();
            return exactPhoneMatches.isEmpty() ? patients : exactPhoneMatches;
        }
        if (StringUtils.hasText(state.patientName)) {
            String normalizedName = state.patientName.trim().toLowerCase(Locale.ROOT);
            List<PatientRecord> exactNameMatches = patients.stream()
                    .filter(row -> StringUtils.hasText(row.fullName()))
                    .filter(row -> row.fullName().trim().equalsIgnoreCase(normalizedName))
                    .toList();
            return exactNameMatches.isEmpty() ? patients : exactNameMatches;
        }
        return patients;
    }

    private List<DoctorAvailabilitySlotRecord> filterByPreferredTime(List<DoctorAvailabilitySlotRecord> slots, String preferredTimeWindow) {
        if (!StringUtils.hasText(preferredTimeWindow)) {
            return slots;
        }
        String normalized = preferredTimeWindow.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("morning") || normalized.startsWith("सुबह")) {
            return slots.stream()
                    .filter(slot -> !slot.slotTime().isBefore(LocalTime.of(8, 0)) && slot.slotTime().isBefore(LocalTime.NOON))
                    .toList();
        }
        if (normalized.startsWith("afternoon") || normalized.startsWith("दोपहर")) {
            return slots.stream()
                    .filter(slot -> !slot.slotTime().isBefore(LocalTime.NOON) && slot.slotTime().isBefore(LocalTime.of(16, 0)))
                    .toList();
        }
        if (normalized.startsWith("evening") || normalized.startsWith("शाम")) {
            return slots.stream()
                    .filter(slot -> !slot.slotTime().isBefore(LocalTime.of(16, 0)))
                    .toList();
        }
        try {
            LocalTime requested = LocalTime.parse(normalized, TIME_FORMATTER);
            return slots.stream().filter(slot -> requested.equals(slot.slotTime())).toList();
        } catch (DateTimeParseException ignored) {
            return slots;
        }
    }

    private List<DoctorAvailabilitySlotRecord> nearestSlots(List<DoctorAvailabilitySlotRecord> slots, String preferredTimeWindow) {
        if (!StringUtils.hasText(preferredTimeWindow)) {
            return slots.stream().limit(3).toList();
        }
        try {
            LocalTime requested = LocalTime.parse(preferredTimeWindow.toLowerCase(Locale.ROOT), TIME_FORMATTER);
            return slots.stream()
                    .sorted(Comparator.comparingLong(slot -> Math.abs(java.time.Duration.between(requested, slot.slotTime()).toMinutes())))
                    .limit(3)
                    .toList();
        } catch (DateTimeParseException ignored) {
            return slots.stream().limit(3).toList();
        }
    }

    private VoiceSuggestedSlot toSuggestedSlot(DoctorAvailabilitySlotRecord slot) {
        return new VoiceSuggestedSlot(
                slot.doctorUserId() == null ? null : slot.doctorUserId().toString(),
                slot.doctorName(),
                slot.appointmentDate() == null ? null : slot.appointmentDate().toString(),
                slot.slotTime() == null ? null : slot.slotTime().format(TIME_FORMATTER),
                slot.slotEndTime() == null ? null : slot.slotEndTime().format(TIME_FORMATTER)
        );
    }

    private String buildSlotPrompt(MutableState state, List<VoiceSuggestedSlot> slots) {
        VoiceSuggestedSlot primary = slots.getFirst();
        String alternatives = slots.stream()
                .skip(1)
                .map(slot -> safe(slot.slotTime()))
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        if (isHindi(state.language)) {
            if (StringUtils.hasText(alternatives)) {
                return safe(primary.doctorName()) + " के लिए " + safe(primary.appointmentDate()) + " को " + safe(primary.slotTime())
                        + " उपलब्ध है। अन्य समय " + alternatives + " हैं। क्या मैं यह स्लॉट बुक करूँ?";
            }
            return safe(primary.doctorName()) + " के लिए " + safe(primary.appointmentDate()) + " को " + safe(primary.slotTime())
                    + " उपलब्ध है। क्या मैं यह स्लॉट बुक करूँ?";
        }
        if (StringUtils.hasText(alternatives)) {
            return safe(primary.doctorName()) + " is available on " + safe(primary.appointmentDate()) + " at " + safe(primary.slotTime())
                    + ". Other nearby slots are " + alternatives + ". Should I book this slot?";
        }
        return safe(primary.doctorName()) + " is available on " + safe(primary.appointmentDate()) + " at " + safe(primary.slotTime())
                + ". Should I book this slot?";
    }

    private String bookingSuccessPrompt(MutableState state) {
        if (isHindi(state.language)) {
            return "आपकी अपॉइंटमेंट " + safe(state.doctorName) + " के साथ " + safe(state.preferredDate) + " को " + safe(state.suggestedSlot == null ? null : state.suggestedSlot.slotTime()) + " पर बुक हो गई है।";
        }
        return "Your appointment with " + safe(state.doctorName) + " is booked for " + safe(state.preferredDate) + " at " + safe(state.suggestedSlot == null ? null : state.suggestedSlot.slotTime()) + ".";
    }

    private String bookingFailedPrompt(String language) {
        if (isHindi(language)) {
            return "यह स्लॉट अभी उपलब्ध नहीं है। कृपया दूसरा समय चुनिए।";
        }
        return "That slot is no longer available. Please choose another time.";
    }

    private String patientNotFoundPrompt(String language) {
        if (isHindi(language)) {
            return "मरीज रिकॉर्ड नहीं मिला। क्या रिसेप्शन टीम आपकी रजिस्ट्रेशन में मदद करे?";
        }
        return "I could not find that patient record. Would you like the receptionist to help with registration?";
    }

    private String ambiguousPatientPrompt(List<String> patientOptions, String language) {
        String options = patientOptions.isEmpty() ? "" : " " + String.join(", ", patientOptions) + ".";
        if (isHindi(language)) {
            return "एक से अधिक मरीज मिले। कृपया मोबाइल नंबर या मरीज नंबर बताइए।" + options;
        }
        return "I found multiple patients. Please share the mobile number or patient number." + options;
    }

    private String doctorNotFoundPrompt(List<String> doctorOptions, String language) {
        String suggestions = doctorOptions.isEmpty() ? "" : " " + String.join(", ", doctorOptions) + ".";
        if (isHindi(language)) {
            return "वह डॉक्टर नहीं मिला। उपलब्ध डॉक्टर हैं:" + suggestions;
        }
        return "I could not find that doctor. Available doctors include:" + suggestions;
    }

    private String ambiguousDoctorPrompt(List<String> doctorOptions, String language) {
        String suggestions = doctorOptions.isEmpty() ? "" : " " + String.join(", ", doctorOptions) + ".";
        if (isHindi(language)) {
            return "डॉक्टर का नाम स्पष्ट नहीं है। कृपया डॉक्टर का पूरा नाम बताइए।" + suggestions;
        }
        return "That doctor name is ambiguous. Please tell me the full doctor name." + suggestions;
    }

    private String slotUnavailablePrompt(String language) {
        if (isHindi(language)) {
            return "उपयुक्त स्लॉट नहीं मिला। कृपया दूसरा समय या तारीख बताइए।";
        }
        return "I could not find a suitable slot. Please share another date or time.";
    }

    private String alternateSlotPrompt(String language) {
        if (isHindi(language)) {
            return "ठीक है। कृपया दूसरा स्लॉट या समय बताइए।";
        }
        return "Okay. Please tell me another slot or time.";
    }

    private String questionForField(String field, String language) {
        if (isHindi(language)) {
            return switch (field) {
                case "patientIdentity" -> "कृपया अपना नाम, मोबाइल नंबर या मरीज नंबर बताइए।";
                case "doctorName" -> "कृपया बताइए, आप किस डॉक्टर से मिलना चाहते हैं?";
                case "preferredDate" -> "कृपया बताइए, आप किस तारीख को आना चाहते हैं?";
                case "preferredTimeWindow" -> "कृपया समय बताइए, जैसे सुबह, दोपहर या शाम?";
                default -> "कृपया अगला ज़रूरी विवरण बताइए।";
            };
        }
        return switch (field) {
            case "patientIdentity" -> "Please share the patient name, mobile number, or patient number.";
            case "doctorName" -> "Which doctor would you like to see?";
            case "preferredDate" -> "What date would you prefer for the appointment?";
            case "preferredTimeWindow" -> "What time works best, such as morning, afternoon, or evening?";
            default -> "Please share the next appointment detail.";
        };
    }

    private String findPatientName(String transcript, String language) {
        Matcher english = ENGLISH_NAME_PATTERN.matcher(transcript);
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

    private String findRequestedDoctorName(String transcript, String language) {
        Matcher english = ENGLISH_DOCTOR_PATTERN.matcher(transcript);
        if (english.find()) {
            return cleanName(english.group(1));
        }
        if (isHindi(language) || transcript.contains("डॉक्टर") || transcript.contains("डॉ")) {
            Matcher hindi = HINDI_DOCTOR_PATTERN.matcher(transcript);
            if (hindi.find()) {
                return cleanName(hindi.group(1));
            }
        }
        return null;
    }

    private String findPatientNumber(String transcript) {
        Matcher matcher = PATIENT_NUMBER_PATTERN.matcher(transcript);
        return matcher.find() ? matcher.group().toUpperCase(Locale.ROOT) : null;
    }

    private String findPhone(String transcript) {
        Matcher matcher = PHONE_PATTERN.matcher(transcript);
        if (!matcher.find()) {
            return null;
        }
        String normalized = normalizePhone(matcher.group(1));
        return normalized.length() >= 7 ? normalized : null;
    }

    private String findPreferredDate(UUID tenantId, String transcript, String language) {
        Matcher iso = ISO_DATE_PATTERN.matcher(transcript);
        if (iso.find()) {
            return iso.group(1);
        }
        String lower = transcript.toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now(clinicTimeZoneResolver.resolve(tenantId));
        if (lower.contains("day after tomorrow") || transcript.contains("परसों")) {
            return today.plusDays(2).toString();
        }
        if (lower.contains("tomorrow") || transcript.contains("कल")) {
            return today.plusDays(1).toString();
        }
        if (lower.contains("today") || transcript.contains("आज")) {
            return today.toString();
        }
        for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
            if (matchesWeekday(transcript, lower, dayOfWeek)) {
                return today.with(TemporalAdjusters.nextOrSame(dayOfWeek)).toString();
            }
        }
        return null;
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
        for (String token : List.of("because ", "regarding ", "complaint is ", "symptoms are ", "for ")) {
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

    private String normalizeLanguage(String language, VoiceWorkflowSummary previousSummary, String transcript) {
        if (StringUtils.hasText(language) && !"auto".equalsIgnoreCase(language)) {
            return language.trim().toLowerCase(Locale.ROOT);
        }
        if (containsHindiScript(transcript)) {
            return "hi";
        }
        if (previousSummary != null && StringUtils.hasText(previousSummary.language()) && !"auto".equalsIgnoreCase(previousSummary.language())) {
            return previousSummary.language();
        }
        return "en";
    }

    private boolean containsHindiScript(String transcript) {
        return StringUtils.hasText(transcript) && transcript.codePoints().anyMatch(codePoint -> codePoint >= 0x0900 && codePoint <= 0x097F);
    }

    private boolean isHindi(String language) {
        return "hi".equalsIgnoreCase(language);
    }

    private VoiceWorkflowSummary emptySummary(String language, String transcript) {
        String resolvedLanguage = normalizeLanguage(language, null, transcript);
        return new VoiceWorkflowSummary(
                VoiceWorkflowMode.APPOINTMENT_BOOKING.configValue(),
                "COLLECTING_DETAILS",
                "COLLECTING_DETAILS",
                resolvedLanguage,
                "VOICE_TEST",
                null,
                null,
                null,
                null,
                "PENDING",
                null,
                null,
                "PENDING",
                null,
                null,
                null,
                List.of("patientIdentity", "doctorName", "preferredDate", "preferredTimeWindow"),
                null,
                List.of(),
                false,
                false,
                false,
                null,
                false,
                null,
                questionForField("patientIdentity", resolvedLanguage),
                0,
                List.of(),
                List.of()
        );
    }

    private String formatPatientOption(PatientRecord patient) {
        return patient.fullName() + " • " + patient.patientNumber() + " • " + patient.mobile();
    }

    private String normalizePhone(String value) {
        return value == null ? "" : value.replaceAll("[^\\d+]", "");
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

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private record ExtractedTurnDetails(
            String patientName,
            String patientPhone,
            String patientNumber,
            String requestedDoctorName,
            String preferredDate,
            String preferredTimeWindow,
            String reason,
            boolean positiveConfirmation,
            boolean negativeConfirmation
    ) {
    }

    private record PatientResolution(boolean madeProgress, String status) {
        static PatientResolution none() {
            return new PatientResolution(false, "PENDING");
        }

        boolean attempted() {
            return !"PENDING".equals(status);
        }
    }

    private record DoctorResolution(boolean madeProgress, String status) {
        static DoctorResolution none() {
            return new DoctorResolution(false, "PENDING");
        }

        boolean attempted() {
            return !"PENDING".equals(status);
        }
    }

    private static final class MutableState {
        private UUID tenantId;
        private String bookingWorkflowState;
        private String language;
        private String contactChannel;
        private String patientId;
        private String patientName;
        private String patientPhone;
        private String patientNumber;
        private String patientMatchStatus;
        private String requestedDoctorName;
        private String doctorUserId;
        private String doctorName;
        private String doctorMatchStatus;
        private String preferredDate;
        private String preferredTimeWindow;
        private String reason;
        private VoiceSuggestedSlot suggestedSlot;
        private List<VoiceSuggestedSlot> slotSuggestions = List.of();
        private boolean confirmationRequested;
        private boolean bookingConfirmed;
        private boolean booked;
        private String bookedAppointmentId;
        private boolean handoffRequired;
        private String handoffReason;
        private String nextPrompt;
        private int unresolvedTurns;
        private String intentState;
        private List<String> patientOptions = List.of();
        private List<String> doctorOptions = List.of();

        private static MutableState from(UUID tenantId, VoiceWorkflowSummary previousSummary, String language) {
            MutableState state = new MutableState();
            state.tenantId = tenantId;
            state.language = language;
            state.contactChannel = "VOICE_TEST";
            state.patientMatchStatus = "PENDING";
            state.doctorMatchStatus = "PENDING";
            state.bookingWorkflowState = "COLLECTING_DETAILS";
            state.intentState = "COLLECTING_DETAILS";
            if (previousSummary != null) {
                state.bookingWorkflowState = previousSummary.bookingWorkflowState();
                state.contactChannel = StringUtils.hasText(previousSummary.contactChannel()) ? previousSummary.contactChannel() : "VOICE_TEST";
                state.patientId = previousSummary.patientId();
                state.patientName = previousSummary.patientName();
                state.patientPhone = previousSummary.patientPhone();
                state.patientNumber = previousSummary.patientNumber();
                state.patientMatchStatus = StringUtils.hasText(previousSummary.patientMatchStatus()) ? previousSummary.patientMatchStatus() : "PENDING";
                state.requestedDoctorName = previousSummary.doctorName();
                state.doctorUserId = previousSummary.doctorUserId();
                state.doctorName = previousSummary.doctorName();
                state.doctorMatchStatus = StringUtils.hasText(previousSummary.doctorMatchStatus()) ? previousSummary.doctorMatchStatus() : "PENDING";
                state.preferredDate = previousSummary.preferredDate();
                state.preferredTimeWindow = previousSummary.preferredTimeWindow();
                state.reason = previousSummary.reason();
                state.suggestedSlot = previousSummary.suggestedSlot();
                state.slotSuggestions = previousSummary.slotSuggestions() == null ? List.of() : previousSummary.slotSuggestions();
                state.confirmationRequested = previousSummary.confirmationRequested();
                state.bookingConfirmed = previousSummary.bookingConfirmed();
                state.booked = previousSummary.booked();
                state.bookedAppointmentId = previousSummary.bookedAppointmentId();
                state.handoffRequired = previousSummary.handoffRequired();
                state.handoffReason = previousSummary.handoffReason();
                state.nextPrompt = previousSummary.nextPrompt();
                state.unresolvedTurns = previousSummary.unresolvedTurns();
                state.intentState = previousSummary.intentState();
                state.patientOptions = previousSummary.patientOptions() == null ? List.of() : previousSummary.patientOptions();
                state.doctorOptions = previousSummary.doctorOptions() == null ? List.of() : previousSummary.doctorOptions();
            }
            return state;
        }

        private List<String> missingFields() {
            LinkedHashSet<String> missing = new LinkedHashSet<>();
            if (!"IDENTIFIED".equals(patientMatchStatus) || !StringUtils.hasText(patientId)) {
                missing.add("patientIdentity");
            }
            if (!"IDENTIFIED".equals(doctorMatchStatus) || !StringUtils.hasText(doctorUserId)) {
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
                    bookingWorkflowState,
                    language,
                    contactChannel,
                    patientId,
                    patientName,
                    patientPhone,
                    patientNumber,
                    patientMatchStatus,
                    doctorUserId,
                    doctorName,
                    doctorMatchStatus,
                    preferredDate,
                    preferredTimeWindow,
                    reason,
                    missingFields(),
                    suggestedSlot,
                    slotSuggestions,
                    confirmationRequested,
                    bookingConfirmed,
                    booked,
                    bookedAppointmentId,
                    handoffRequired,
                    handoffReason,
                    nextPrompt,
                    unresolvedTurns,
                    patientOptions,
                    doctorOptions
            );
        }
    }
}
