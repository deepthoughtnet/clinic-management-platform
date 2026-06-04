package com.deepthoughtnet.clinic.api.patientportal.careai;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentConfirmationResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentBookingRequest;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorSlotResponse;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PatientPortalCareAiService {
    private static final Pattern ENGLISH_DOCTOR_PATTERN = Pattern.compile("(?i)\\b(?:dr\\.?|doctor)\\s+([A-Za-z][A-Za-z .'-]{1,60})");
    private static final Pattern HINDI_DOCTOR_PATTERN = Pattern.compile("(?:डॉक्टर|डॉ\\.?)([^,.!?]+)");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern EXPLICIT_TIME_PATTERN = Pattern.compile("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\b(\\d{1,2})\\b");

    private static final List<String> POSITIVE_CONFIRMATIONS = List.of("yes", "confirm", "book it", "go ahead", "that's fine", "okay", "ok", "yes please");
    private static final List<String> POSITIVE_CONFIRMATIONS_HI = List.of("हाँ", "हां", "ठीक है", "बुक कर दीजिए", "कन्फर्म", "सही है");
    private static final List<String> NEGATIVE_CONFIRMATIONS = List.of("no", "another slot", "different slot", "different time", "change slot", "not this one");
    private static final List<String> NEGATIVE_CONFIRMATIONS_HI = List.of("नहीं", "दूसरा स्लॉट", "दूसरा समय", "दूसरे समय");
    private static final List<String> GREETING_KEYWORDS = List.of("hello", "hi", "hey", "good morning", "good afternoon", "good evening");
    private static final List<String> BOOKING_INTENT_KEYWORDS = List.of("book appointment", "book", "need doctor", "want consultation", "schedule");
    private static final List<String> RESCHEDULE_INTENT_KEYWORDS = List.of("reschedule", "change my appointment", "move my appointment", "change appointment");
    private static final List<String> CANCEL_INTENT_KEYWORDS = List.of("cancel appointment", "cancel my appointment", "remove booking", "cancel booking");
    private static final List<String> STATUS_INTENT_KEYWORDS = List.of("when is my appointment", "when is my next appointment", "show appointments", "appointment status", "next appointment");
    private static final List<String> NEW_PATIENT_KEYWORDS = List.of("new patient", "first time patient", "first-time patient");
    private static final List<String> NEW_PATIENT_KEYWORDS_HI = List.of("नया मरीज", "पहली बार", "पहली दफ़ा");
    private static final List<String> EMERGENCY_KEYWORDS = List.of("chest pain", "difficulty breathing", "severe bleeding", "unconscious", "stroke", "suicidal");
    private static final List<String> EMERGENCY_KEYWORDS_HI = List.of("सीने में दर्द", "सांस लेने में दिक्कत", "ज़्यादा खून", "बेहोश", "स्ट्रोक", "आत्महत्या");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH);

    private final PatientPortalService patientPortalService;
    private final Map<SessionKey, CareAiState> sessions = new ConcurrentHashMap<>();

    public PatientPortalCareAiService(PatientPortalService patientPortalService) {
        this.patientPortalService = patientPortalService;
    }

    public PatientPortalCareAiMessageResponse message(PatientPortalCareAiMessageRequest request) {
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new IllegalArgumentException("Message is required");
        }

        SessionKey sessionKey = currentSessionKey();
        CareAiState state = sessions.computeIfAbsent(sessionKey, key -> new CareAiState());
        String message = request.message().trim();
        state.language = normalizeLanguage(request.language(), message, state.language);

        if (containsEmergency(message, state.language)) {
            state.handoffRequired = true;
            state.handoffReason = "emergency-symptoms";
            state.confirmationPending = false;
            return response(state, emergencyPrompt(state.language));
        }
        if (isGreetingOnly(message, state.language) && state.currentIntent == null && !state.actionCompleted) {
            return response(state, greetingPrompt(state.language));
        }
        if (isNewPatientIntent(message, state.language)) {
            return response(state, newPatientPrompt(state.language));
        }

        boolean progressed = applyIntent(state, message);
        if (state.confirmationPending && isPositiveConfirmation(message)) {
            return executeConfirmedAction(state);
        }
        if (state.confirmationPending && isNegativeConfirmation(message)) {
            progressed = clearPendingAction(state, true);
        }

        String reply = routeConversation(state, message);
        if (reply == null) {
            state.unresolvedTurns += 1;
            if (state.unresolvedTurns >= 3) {
                state.handoffRequired = true;
                state.handoffReason = "repeated-resolution-failure";
                reply = receptionHandoffPrompt(state.language);
            } else {
                reply = askIntentPrompt(state.language);
            }
        } else if (progressed || !reply.equals(askIntentPrompt(state.language))) {
            state.unresolvedTurns = 0;
        }
        return response(state, reply);
    }

    public PatientPortalCareAiResetResponse reset() {
        sessions.remove(currentSessionKey());
        return new PatientPortalCareAiResetResponse(true, "CareAI booking context cleared.");
    }

    private String routeConversation(CareAiState state, String message) {
        if (state.currentIntent == null) {
            return askIntentPrompt(state.language);
        }
        return switch (state.currentIntent) {
            case BOOK_APPOINTMENT -> handleBooking(state, message);
            case RESCHEDULE_APPOINTMENT -> handleReschedule(state, message);
            case CANCEL_APPOINTMENT -> handleCancellation(state, message);
            case APPOINTMENT_STATUS -> handleStatus(state, message);
        };
    }

    private boolean applyIntent(CareAiState state, String message) {
        PatientPortalCareAiIntent detectedIntent = detectIntent(message);
        boolean changed = false;
        if (detectedIntent != null && detectedIntent != state.currentIntent) {
            resetWorkflowState(state, detectedIntent);
            changed = true;
        }
        if (state.currentIntent == PatientPortalCareAiIntent.BOOK_APPOINTMENT) {
            changed = applyBookingFacts(state, message) || changed;
        } else if (state.currentIntent == PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT) {
            changed = applyRescheduleFacts(state, message) || changed;
        } else if (state.currentIntent == PatientPortalCareAiIntent.CANCEL_APPOINTMENT) {
            changed = applyAppointmentSelectionFacts(state, message) || changed;
        }
        return changed;
    }

    private boolean applyBookingFacts(CareAiState state, String message) {
        boolean changed = false;
        boolean selectionOnlyMessage = isSelectionOnlyMessage(message);

        String doctorName = findRequestedDoctorName(message, state.language);
        if (!StringUtils.hasText(doctorName)) {
            doctorName = findDoctorNameFromFreeText(message);
        }
        if (StringUtils.hasText(doctorName) && !doctorName.equalsIgnoreCase(state.requestedDoctorName)) {
            state.requestedDoctorName = doctorName;
            clearDoctorSelection(state);
            changed = true;
        }

        String speciality = findSpeciality(message);
        if (StringUtils.hasText(speciality) && !speciality.equalsIgnoreCase(state.requestedSpeciality)) {
            state.requestedSpeciality = speciality;
            if (!StringUtils.hasText(state.requestedDoctorName)) {
                clearDoctorSelection(state);
            }
            changed = true;
        }

        if (!selectionOnlyMessage) {
            String preferredDate = findPreferredDate(message, state.language);
            if (StringUtils.hasText(preferredDate) && !preferredDate.equals(state.preferredDate)) {
                state.preferredDate = preferredDate;
                clearSlotSelection(state);
                changed = true;
            }

            String preferredTimeWindow = findPreferredTimeWindow(message, state.language);
            if (StringUtils.hasText(preferredTimeWindow) && !preferredTimeWindow.equalsIgnoreCase(state.preferredTimeWindow)) {
                state.preferredTimeWindow = preferredTimeWindow;
                clearSlotSelection(state);
                changed = true;
            }
        }

        String reason = findReason(message, state.language);
        if (StringUtils.hasText(reason) && !reason.equalsIgnoreCase(state.reason)) {
            state.reason = reason;
            changed = true;
        }
        return changed;
    }

    private boolean applyRescheduleFacts(CareAiState state, String message) {
        boolean changed = applyAppointmentSelectionFacts(state, message);
        boolean selectionOnlyMessage = isSelectionOnlyMessage(message);

        if (!selectionOnlyMessage) {
            String preferredDate = findPreferredDate(message, state.language);
            if (StringUtils.hasText(preferredDate) && !preferredDate.equals(state.preferredDate)) {
                state.preferredDate = preferredDate;
                clearSlotSelection(state);
                changed = true;
            }

            String preferredTimeWindow = findPreferredTimeWindow(message, state.language);
            if (StringUtils.hasText(preferredTimeWindow) && !preferredTimeWindow.equalsIgnoreCase(state.preferredTimeWindow)) {
                state.preferredTimeWindow = preferredTimeWindow;
                clearSlotSelection(state);
                changed = true;
            }
        }
        return changed;
    }

    private boolean applyAppointmentSelectionFacts(CareAiState state, String message) {
        if (StringUtils.hasText(message) && !state.appointmentOptions.isEmpty()) {
            AppointmentChoice match = resolveAppointmentChoice(state, message);
            if (match != null) {
                selectAppointment(state, match);
                return true;
            }
        }
        return false;
    }

    private String handleBooking(CareAiState state, String message) {
        if (tryResolveDoctorSelection(state, message)) {
            if (!StringUtils.hasText(state.preferredDate)) {
                return askDatePrompt(state.language);
            }
        }
        if (!StringUtils.hasText(state.selectedDoctorId)) {
            return promptForDoctorSelection(state, message);
        }
        if (!StringUtils.hasText(state.preferredDate)) {
            return askDatePrompt(state.language);
        }

        if (tryResolveSlotSelection(state, message, state.selectedDoctorId)) {
            return bookingConfirmationPrompt(state);
        }
        if (state.slotOptions.isEmpty()) {
            return askTimePrompt(state.language);
        }
        return slotChoicePrompt(state);
    }

    private String handleReschedule(CareAiState state, String message) {
        if (!ensureAppointmentOptions(state)) {
            return noUpcomingAppointmentsPrompt(state.language);
        }
        if (!StringUtils.hasText(state.selectedAppointmentId)) {
            AppointmentChoice match = resolveAppointmentChoice(state, message);
            if (match != null) {
                selectAppointment(state, match);
            } else if (state.appointmentOptions.size() == 1) {
                selectAppointment(state, state.appointmentOptions.getFirst());
            } else {
                return appointmentChoicePrompt(state, "reschedule");
            }
        }
        if (!StringUtils.hasText(state.preferredDate)) {
            return askRescheduleDatePrompt(state);
        }
        if (tryResolveSlotSelection(state, message, state.selectedDoctorId)) {
            return rescheduleConfirmationPrompt(state);
        }
        if (state.slotOptions.isEmpty()) {
            return askTimePrompt(state.language);
        }
        return slotChoicePrompt(state);
    }

    private String handleCancellation(CareAiState state, String message) {
        if (!ensureAppointmentOptions(state)) {
            return noUpcomingAppointmentsPrompt(state.language);
        }
        if (!StringUtils.hasText(state.selectedAppointmentId)) {
            AppointmentChoice match = resolveAppointmentChoice(state, message);
            if (match != null) {
                selectAppointment(state, match);
            } else if (state.appointmentOptions.size() == 1) {
                selectAppointment(state, state.appointmentOptions.getFirst());
            } else {
                return appointmentChoicePrompt(state, "cancel");
            }
        }
        state.pendingAction = PatientPortalCareAiIntent.CANCEL_APPOINTMENT;
        state.confirmationPending = true;
        return cancellationConfirmationPrompt(state);
    }

    private String handleStatus(CareAiState state, String message) {
        if (!ensureAppointmentOptions(state)) {
            return noUpcomingAppointmentsPrompt(state.language);
        }
        AppointmentChoice selected = resolveAppointmentChoice(state, message);
        if (selected != null) {
            selectAppointment(state, selected);
            return appointmentStatusPrompt(selected, state.language);
        }
        if (asksForAllAppointments(message)) {
            return appointmentListPrompt(state, "Here are your upcoming appointments:");
        }
        AppointmentChoice next = state.appointmentOptions.getFirst();
        selectAppointment(state, next);
        return appointmentStatusPrompt(next, state.language);
    }

    private String promptForDoctorSelection(CareAiState state, String message) {
        List<DoctorChoice> matches = resolveDoctorMatches(state, message);
        if (matches.isEmpty()) {
            state.doctorOptions = patientPortalService.doctors().stream()
                    .limit(4)
                    .map(this::doctorLabel)
                    .toList();
            return askDoctorPrompt(state.language, containsBookingIntent(message, state.language));
        }
        if (matches.size() == 1) {
            selectDoctor(state, matches.getFirst());
            return askDatePrompt(state.language);
        }
        state.doctorOptions = matches.stream().map(DoctorChoice::label).toList();
        state.doctorChoices = matches;
        return doctorChoicePrompt(state);
    }

    private boolean tryResolveDoctorSelection(CareAiState state, String message) {
        if (!state.doctorChoices.isEmpty()) {
            DoctorChoice selected = resolveDoctorChoice(state, message);
            if (selected != null) {
                selectDoctor(state, selected);
                return true;
            }
        }
        if (StringUtils.hasText(state.selectedDoctorId)) {
            return true;
        }
        List<DoctorChoice> matches = resolveDoctorMatches(state, message);
        if (matches.size() == 1) {
            selectDoctor(state, matches.getFirst());
            return true;
        }
        if (matches.size() > 1) {
            state.doctorChoices = matches;
            state.doctorOptions = matches.stream().map(DoctorChoice::label).toList();
        }
        return false;
    }

    private boolean tryResolveSlotSelection(CareAiState state, String message, String publicDoctorId) {
        if (!state.slotChoices.isEmpty()) {
            SlotChoice selected = resolveSlotChoice(state, message);
            if (selected != null) {
                state.selectedSlot = selected.slotTime().format(TIME_FORMATTER);
                state.preferredDate = selected.appointmentDate().toString();
                state.confirmationPending = true;
                state.pendingAction = state.currentIntent;
                return true;
            }
        }
        if (state.confirmationPending && StringUtils.hasText(state.selectedSlot)) {
            return true;
        }
        if (!StringUtils.hasText(publicDoctorId) || !StringUtils.hasText(state.preferredDate)) {
            return false;
        }

        LocalDate date = LocalDate.parse(state.preferredDate);
        List<PatientPortalDoctorSlotResponse> selectableSlots = patientPortalService.doctorSlots(publicDoctorId, date).stream()
                .filter(PatientPortalDoctorSlotResponse::selectable)
                .sorted(Comparator.comparing(PatientPortalDoctorSlotResponse::slotTime))
                .toList();
        if (selectableSlots.isEmpty()) {
            clearSlotSelection(state);
            return false;
        }

        List<PatientPortalDoctorSlotResponse> filtered = filterSlots(selectableSlots, state.preferredTimeWindow);
        List<PatientPortalDoctorSlotResponse> candidates = filtered.isEmpty() ? selectableSlots : filtered;
        if (candidates.isEmpty()) {
            clearSlotSelection(state);
            return false;
        }

        if (isExactTime(state.preferredTimeWindow)) {
            PatientPortalDoctorSlotResponse exact = candidates.stream()
                    .filter(slot -> slot.slotTime().format(TIME_FORMATTER).equalsIgnoreCase(state.preferredTimeWindow))
                    .findFirst()
                    .orElse(null);
            if (exact != null) {
                state.slotChoices = List.of(new SlotChoice(exact.appointmentDate(), exact.slotTime()));
                state.slotOptions = List.of(exact.slotTime().format(TIME_FORMATTER));
                state.selectedSlot = exact.slotTime().format(TIME_FORMATTER);
                state.confirmationPending = true;
                state.pendingAction = state.currentIntent;
                return true;
            }
        }

        List<SlotChoice> options = candidates.stream()
                .limit(3)
                .map(slot -> new SlotChoice(slot.appointmentDate(), slot.slotTime()))
                .toList();
        state.slotChoices = options;
        state.slotOptions = options.stream().map(choice -> choice.slotTime().format(TIME_FORMATTER)).toList();
        state.selectedSlot = null;
        state.confirmationPending = false;
        state.pendingAction = null;
        return false;
    }

    private PatientPortalCareAiMessageResponse response(CareAiState state, String message) {
        return new PatientPortalCareAiMessageResponse(
                message,
                new PatientPortalCareAiStateResponse(
                        state.language,
                        state.currentIntent == null ? null : state.currentIntent.name(),
                        state.selectedDoctorName,
                        state.selectedSpeciality,
                        state.selectedAppointmentLabel,
                        state.preferredDate,
                        state.preferredTimeWindow,
                        state.selectedSlot,
                        state.confirmationPending,
                        state.booked,
                        state.actionCompleted,
                        state.lastAction == null ? null : state.lastAction.name(),
                        state.bookedAppointmentDate,
                        state.bookedAppointmentTime,
                        state.bookingStatus,
                        state.handoffRequired,
                        state.handoffReason,
                        state.doctorOptions,
                        state.appointmentOptions.stream().map(AppointmentChoice::label).toList(),
                        state.slotOptions
                )
        );
    }

    private PatientPortalCareAiMessageResponse executeConfirmedAction(CareAiState state) {
        if (state.pendingAction == null) {
            state.confirmationPending = false;
            return response(state, askIntentPrompt(state.language));
        }
        try {
            PatientPortalAppointmentConfirmationResponse confirmation = switch (state.pendingAction) {
                case BOOK_APPOINTMENT -> patientPortalService.bookAppointment(new PatientPortalAppointmentBookingRequest(
                        state.selectedDoctorId,
                        LocalDate.parse(state.preferredDate),
                        LocalTime.parse(state.selectedSlot, TIME_FORMATTER),
                        state.reason
                ));
                case RESCHEDULE_APPOINTMENT -> patientPortalService.rescheduleAppointment(
                        UUID.fromString(state.selectedAppointmentId),
                        LocalDate.parse(state.preferredDate),
                        LocalTime.parse(state.selectedSlot, TIME_FORMATTER),
                        state.selectedAppointmentReason
                );
                case CANCEL_APPOINTMENT -> patientPortalService.cancelAppointment(
                        UUID.fromString(state.selectedAppointmentId),
                        "Cancelled via CareAI"
                );
                case APPOINTMENT_STATUS -> throw new IllegalStateException("Status lookups do not require confirmation");
            };
            state.actionCompleted = true;
            state.booked = state.pendingAction == PatientPortalCareAiIntent.BOOK_APPOINTMENT;
            state.lastAction = state.pendingAction;
            state.bookingStatus = confirmation.status();
            state.bookedAppointmentDate = confirmation.appointmentDate() == null ? null : confirmation.appointmentDate().toString();
            state.bookedAppointmentTime = confirmation.appointmentTime() == null ? null : confirmation.appointmentTime().format(TIME_FORMATTER);
            state.confirmationPending = false;
            state.pendingAction = null;
            state.handoffRequired = false;
            state.handoffReason = null;
            state.unresolvedTurns = 0;
            if (state.lastAction == PatientPortalCareAiIntent.CANCEL_APPOINTMENT) {
                clearAppointmentSelection(state);
            }
            return response(state, confirmation.message());
        } catch (RuntimeException ex) {
            state.confirmationPending = false;
            state.pendingAction = null;
            state.unresolvedTurns += 1;
            if (state.unresolvedTurns >= 3) {
                state.handoffRequired = true;
                state.handoffReason = "booking-failed";
                return response(state, receptionHandoffPrompt(state.language));
            }
            return response(state, bookingFailedPrompt(state.language, ex.getMessage()));
        }
    }

    private boolean clearPendingAction(CareAiState state, boolean clearSlots) {
        state.confirmationPending = false;
        state.pendingAction = null;
        state.actionCompleted = false;
        state.booked = false;
        state.bookingStatus = null;
        state.bookedAppointmentDate = null;
        state.bookedAppointmentTime = null;
        if (clearSlots) {
            state.selectedSlot = null;
            state.slotChoices = List.of();
            state.slotOptions = List.of();
        }
        return true;
    }

    private void resetWorkflowState(CareAiState state, PatientPortalCareAiIntent intent) {
        state.currentIntent = intent;
        state.requestedDoctorName = null;
        state.requestedSpeciality = null;
        state.selectedDoctorId = null;
        state.selectedDoctorName = null;
        state.selectedSpeciality = null;
        state.preferredDate = null;
        state.preferredTimeWindow = null;
        state.reason = null;
        state.doctorChoices = List.of();
        state.doctorOptions = List.of();
        clearAppointmentSelection(state);
        clearSlotSelection(state);
        state.confirmationPending = false;
        state.pendingAction = null;
        state.booked = false;
        state.actionCompleted = false;
        state.lastAction = null;
        state.bookingStatus = null;
        state.bookedAppointmentDate = null;
        state.bookedAppointmentTime = null;
        state.handoffRequired = false;
        state.handoffReason = null;
        state.unresolvedTurns = 0;
    }

    private void clearDoctorSelection(CareAiState state) {
        state.selectedDoctorId = null;
        state.selectedDoctorName = null;
        state.selectedSpeciality = null;
        state.doctorChoices = List.of();
        state.doctorOptions = List.of();
        clearSlotSelection(state);
    }

    private void clearAppointmentSelection(CareAiState state) {
        state.selectedAppointmentId = null;
        state.selectedAppointmentLabel = null;
        state.selectedAppointmentReason = null;
        state.appointmentOptions = List.of();
        if (state.currentIntent != PatientPortalCareAiIntent.BOOK_APPOINTMENT) {
            state.selectedDoctorId = null;
            state.selectedDoctorName = null;
            state.selectedSpeciality = null;
        }
        clearSlotSelection(state);
    }

    private void clearSlotSelection(CareAiState state) {
        state.selectedSlot = null;
        state.slotChoices = List.of();
        state.slotOptions = List.of();
        state.confirmationPending = false;
        state.pendingAction = null;
    }

    private boolean ensureAppointmentOptions(CareAiState state) {
        List<AppointmentChoice> choices = patientPortalService.careAiUpcomingAppointments().stream()
                .sorted(Comparator
                        .comparing(PatientPortalCareAiAppointmentOption::appointmentDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PatientPortalCareAiAppointmentOption::appointmentTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toAppointmentChoice)
                .toList();
        state.appointmentOptions = choices;
        return !choices.isEmpty();
    }

    private AppointmentChoice toAppointmentChoice(PatientPortalCareAiAppointmentOption option) {
        String label = safe(option.doctorName()) + " · "
                + safe(option.appointmentDate() == null ? null : DATE_FORMATTER.format(option.appointmentDate())) + " · "
                + safe(option.appointmentTime() == null ? null : option.appointmentTime().format(TIME_FORMATTER));
        return new AppointmentChoice(
                option.appointmentId(),
                option.doctorUserId(),
                option.doctorName(),
                option.clinicName(),
                option.appointmentDate(),
                option.appointmentTime(),
                option.status(),
                option.reason(),
                label
        );
    }

    private List<DoctorChoice> resolveDoctorMatches(CareAiState state, String message) {
        List<PatientPortalDoctorResponse> doctors = patientPortalService.doctors();
        if (doctors.isEmpty()) {
            state.handoffRequired = true;
            state.handoffReason = "no-tenant-doctors";
            return List.of();
        }

        String doctorHint = state.requestedDoctorName;
        if (!StringUtils.hasText(doctorHint)) {
            doctorHint = findDoctorNameFromFreeText(message);
        }
        final String doctorHintValue = doctorHint;
        final String specialityHint = state.requestedSpeciality;
        String normalizedMessage = normalizeDoctorText(message);

        List<PatientPortalDoctorResponse> matches = doctors.stream()
                .filter(doctor -> {
                    if (StringUtils.hasText(doctorHintValue)) {
                        return matchesDoctorName(doctor.doctorName(), doctorHintValue);
                    }
                    if (StringUtils.hasText(specialityHint)) {
                        return containsIgnoreCase(doctor.specialization(), specialityHint);
                    }
                    return StringUtils.hasText(normalizedMessage)
                            && containsDoctorTokenMatch(doctor.doctorName(), normalizedMessage);
                })
                .sorted(Comparator.comparing(PatientPortalDoctorResponse::doctorName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (!matches.isEmpty()) {
            return matches.stream().map(this::toDoctorChoice).toList();
        }
        if (StringUtils.hasText(state.requestedSpeciality)) {
            return doctors.stream()
                    .filter(doctor -> containsIgnoreCase(doctor.specialization(), state.requestedSpeciality))
                    .sorted(Comparator.comparing(PatientPortalDoctorResponse::doctorName, String.CASE_INSENSITIVE_ORDER))
                    .map(this::toDoctorChoice)
                    .toList();
        }
        return List.of();
    }

    private DoctorChoice toDoctorChoice(PatientPortalDoctorResponse doctor) {
        return new DoctorChoice(doctor.publicDoctorId(), doctor.doctorName(), doctor.specialization(), doctorLabel(doctor));
    }

    private void selectDoctor(CareAiState state, DoctorChoice selected) {
        state.selectedDoctorId = selected.publicDoctorId();
        state.selectedDoctorName = selected.doctorName();
        state.selectedSpeciality = selected.speciality();
        state.doctorChoices = List.of();
        state.doctorOptions = List.of();
        clearSlotSelection(state);
    }

    private void selectAppointment(CareAiState state, AppointmentChoice selected) {
        state.selectedAppointmentId = selected.appointmentId().toString();
        state.selectedAppointmentLabel = selected.label();
        state.selectedAppointmentReason = selected.reason();
        state.selectedDoctorId = selected.doctorUserId() == null ? null : selected.doctorUserId().toString();
        state.selectedDoctorName = selected.doctorName();
        state.selectedSpeciality = null;
        clearSlotSelection(state);
    }

    private DoctorChoice resolveDoctorChoice(CareAiState state, String message) {
        return resolveIndexedOrNamedChoice(
                state.doctorChoices,
                message,
                choice -> choice.doctorName() + " " + nullToBlank(choice.speciality())
        );
    }

    private AppointmentChoice resolveAppointmentChoice(CareAiState state, String message) {
        if (state.appointmentOptions.isEmpty()) {
            return null;
        }
        String normalized = normalizeDoctorText(message);
        if (List.of("next", "next appointment", "first", "1st").contains(normalized)) {
            return state.appointmentOptions.getFirst();
        }
        return resolveIndexedOrNamedChoice(
                state.appointmentOptions,
                message,
                choice -> choice.doctorName() + " " + nullToBlank(choice.appointmentDate() == null ? null : choice.appointmentDate().toString())
        );
    }

    private SlotChoice resolveSlotChoice(CareAiState state, String message) {
        if (state.slotChoices.isEmpty()) {
            return null;
        }
        Integer index = parseSelectionIndex(message);
        if (index != null && index >= 1 && index <= state.slotChoices.size()) {
            return state.slotChoices.get(index - 1);
        }
        String normalized = normalizeDoctorText(message);
        return state.slotChoices.stream()
                .filter(choice -> choice.slotTime().format(TIME_FORMATTER).equalsIgnoreCase(normalized))
                .findFirst()
                .orElseGet(() -> state.slotChoices.stream()
                        .filter(choice -> normalizeDoctorText(choice.slotTime().format(TIME_FORMATTER)).contains(normalized))
                        .findFirst()
                        .orElse(null));
    }

    private <T> T resolveIndexedOrNamedChoice(List<T> choices, String message, java.util.function.Function<T, String> labelExtractor) {
        Integer index = parseSelectionIndex(message);
        if (index != null && index >= 1 && index <= choices.size()) {
            return choices.get(index - 1);
        }
        String normalized = normalizeDoctorText(message);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        List<T> matches = choices.stream()
                .filter(choice -> normalizeDoctorText(labelExtractor.apply(choice)).contains(normalized))
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private Integer parseSelectionIndex(String message) {
        Matcher matcher = DIGIT_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<PatientPortalDoctorSlotResponse> filterSlots(
            List<PatientPortalDoctorSlotResponse> slots,
            String preferredTimeWindow
    ) {
        if (!StringUtils.hasText(preferredTimeWindow)) {
            return slots;
        }
        String normalized = preferredTimeWindow.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("morning") || normalized.startsWith("सुबह")) {
            return slots.stream().filter(slot -> {
                LocalTime time = slot.slotTime();
                return !time.isBefore(LocalTime.of(8, 0)) && time.isBefore(LocalTime.NOON);
            }).toList();
        }
        if (normalized.startsWith("afternoon") || normalized.startsWith("दोपहर")) {
            return slots.stream().filter(slot -> {
                LocalTime time = slot.slotTime();
                return !time.isBefore(LocalTime.NOON) && time.isBefore(LocalTime.of(16, 0));
            }).toList();
        }
        if (normalized.startsWith("evening") || normalized.startsWith("शाम")) {
            return slots.stream().filter(slot -> !slot.slotTime().isBefore(LocalTime.of(16, 0))).toList();
        }
        if (isExactTime(preferredTimeWindow)) {
            return slots.stream()
                    .filter(slot -> slot.slotTime().format(TIME_FORMATTER).equalsIgnoreCase(preferredTimeWindow))
                    .toList();
        }
        return slots;
    }

    private boolean isExactTime(String preferredTimeWindow) {
        if (!StringUtils.hasText(preferredTimeWindow)) {
            return false;
        }
        try {
            LocalTime.parse(preferredTimeWindow, TIME_FORMATTER);
            return true;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private PatientPortalCareAiIntent detectIntent(String transcript) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        if (RESCHEDULE_INTENT_KEYWORDS.stream().anyMatch(lower::contains) || transcript.contains("रीशेड्यूल")) {
            return PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT;
        }
        if (CANCEL_INTENT_KEYWORDS.stream().anyMatch(lower::contains) || transcript.contains("रद्द")) {
            return PatientPortalCareAiIntent.CANCEL_APPOINTMENT;
        }
        if (STATUS_INTENT_KEYWORDS.stream().anyMatch(lower::contains) || transcript.contains("अपॉइंटमेंट कब")) {
            return PatientPortalCareAiIntent.APPOINTMENT_STATUS;
        }
        if (BOOKING_INTENT_KEYWORDS.stream().anyMatch(lower::contains) || transcript.contains("बुक")) {
            return PatientPortalCareAiIntent.BOOK_APPOINTMENT;
        }
        return null;
    }

    private SessionKey currentSessionKey() {
        return new SessionKey(RequestContextHolder.requireTenantId(), RequestContextHolder.require().appUserId());
    }

    private boolean matchesDoctorName(String doctorName, String requestedDoctorName) {
        String doctor = normalizeDoctorText(doctorName);
        String requested = normalizeDoctorText(requestedDoctorName);
        return StringUtils.hasText(doctor) && StringUtils.hasText(requested)
                && (doctor.contains(requested) || requested.contains(doctor));
    }

    private String normalizeDoctorText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace("doctor", "")
                .replace("dr.", "")
                .replace("dr", "")
                .replaceAll("[^\\p{L}\\p{N}: ]", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
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

    private String findDoctorNameFromFreeText(String transcript) {
        String normalized = normalizeDoctorText(transcript);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        List<String> names = patientPortalService.doctors().stream()
                .map(PatientPortalDoctorResponse::doctorName)
                .filter(StringUtils::hasText)
                .filter(name -> normalizeDoctorText(name).contains(normalized) || normalized.contains(normalizeDoctorText(name)))
                .toList();
        return names.size() == 1 ? names.getFirst() : null;
    }

    private String findSpeciality(String transcript) {
        String normalized = transcript.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> specialities = new LinkedHashSet<>();
        patientPortalService.doctors().stream()
                .map(PatientPortalDoctorResponse::specialization)
                .filter(StringUtils::hasText)
                .forEach(specialities::add);
        return specialities.stream()
                .filter(item -> normalized.contains(item.toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElse(null);
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

    private String findPreferredTimeWindow(String transcript, String language) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        if (lower.contains("morning") || transcript.contains("सुबह")) {
            return isHindi(language) ? "सुबह" : "morning";
        }
        if (lower.contains("afternoon") || transcript.contains("दोपहर")) {
            return isHindi(language) ? "दोपहर" : "afternoon";
        }
        if (lower.contains("evening") || transcript.contains("शाम")) {
            return isHindi(language) ? "शाम" : "evening";
        }
        String sanitized = ISO_DATE_PATTERN.matcher(lower).replaceAll(" ");
        Matcher matcher = EXPLICIT_TIME_PATTERN.matcher(sanitized);
        if (matcher.find()) {
            int hour = Integer.parseInt(matcher.group(1));
            int minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            String meridiem = matcher.group(3);
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

    private String findReason(String transcript, String language) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        for (String token : List.of("because ", "for ", "regarding ", "symptoms are ")) {
            int index = lower.indexOf(token);
            if (index >= 0) {
                return trimToLength(transcript.substring(index + token.length()).trim(), 160);
            }
        }
        if (isHindi(language) && transcript.contains("के लिए")) {
            return trimToLength(transcript.substring(transcript.indexOf("के लिए") + "के लिए".length()).trim(), 160);
        }
        return null;
    }

    private boolean containsEmergency(String transcript, String language) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        return EMERGENCY_KEYWORDS.stream().anyMatch(lower::contains)
                || EMERGENCY_KEYWORDS_HI.stream().anyMatch(transcript::contains)
                || ("hi".equalsIgnoreCase(language) && transcript.contains("आपातकाल"));
    }

    private boolean isGreetingOnly(String transcript, String language) {
        String lower = transcript.toLowerCase(Locale.ROOT).trim();
        if (GREETING_KEYWORDS.stream().anyMatch(lower::equals)) {
            return true;
        }
        return isHindi(language) && List.of("नमस्ते", "हेलो", "हाय").contains(transcript.trim());
    }

    private boolean containsBookingIntent(String transcript, String language) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        if (BOOKING_INTENT_KEYWORDS.stream().anyMatch(lower::contains)) {
            return true;
        }
        return isHindi(language) && (transcript.contains("अपॉइंटमेंट") || transcript.contains("बुक") || transcript.contains("मुलाकात"));
    }

    private boolean isNewPatientIntent(String transcript, String language) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        if (NEW_PATIENT_KEYWORDS.stream().anyMatch(lower::contains)) {
            return true;
        }
        return isHindi(language) && NEW_PATIENT_KEYWORDS_HI.stream().anyMatch(transcript::contains);
    }

    private boolean asksForAllAppointments(String transcript) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        return lower.contains("show")
                || lower.contains("all")
                || lower.contains("appointments")
                || transcript.contains("सभी");
    }

    private boolean isPositiveConfirmation(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return POSITIVE_CONFIRMATIONS.stream().anyMatch(lower::contains)
                || POSITIVE_CONFIRMATIONS_HI.stream().anyMatch(message::contains);
    }

    private boolean isNegativeConfirmation(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return NEGATIVE_CONFIRMATIONS.stream().anyMatch(lower::contains)
                || NEGATIVE_CONFIRMATIONS_HI.stream().anyMatch(message::contains);
    }

    private String normalizeLanguage(String requestedLanguage, String transcript, String previousLanguage) {
        if (StringUtils.hasText(requestedLanguage) && !"auto".equalsIgnoreCase(requestedLanguage)) {
            return requestedLanguage.trim().toLowerCase(Locale.ROOT);
        }
        if (transcript.codePoints().anyMatch(codePoint -> codePoint >= 0x0900 && codePoint <= 0x097F)) {
            return "hi";
        }
        return StringUtils.hasText(previousLanguage) ? previousLanguage : "en";
    }

    private boolean isHindi(String language) {
        return "hi".equalsIgnoreCase(language);
    }

    private String doctorLabel(PatientPortalDoctorResponse doctor) {
        if (StringUtils.hasText(doctor.specialization())) {
            return doctor.doctorName() + " · " + doctor.specialization();
        }
        return doctor.doctorName();
    }

    private boolean containsIgnoreCase(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right)
                && left.toLowerCase(Locale.ROOT).contains(right.toLowerCase(Locale.ROOT));
    }

    private boolean containsDoctorTokenMatch(String doctorName, String normalizedMessage) {
        if (!StringUtils.hasText(doctorName) || !StringUtils.hasText(normalizedMessage)) {
            return false;
        }
        List<String> doctorTokens = List.of(normalizeDoctorText(doctorName).split(" "));
        for (String token : doctorTokens) {
            if (token.length() >= 3 && normalizedMessage.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSelectionOnlyMessage(String message) {
        return StringUtils.hasText(message) && message.trim().matches("\\d{1,2}");
    }

    private String cleanName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.replaceAll("[^\\p{L} .'-]", " ").replaceAll("\\s{2,}", " ").trim();
    }

    private String trimToLength(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String askIntentPrompt(String language) {
        return isHindi(language)
                ? "मैं अपॉइंटमेंट बुक, रीशेड्यूल, रद्द, या अगली अपॉइंटमेंट की जानकारी दे सकता हूँ। आप क्या करना चाहते हैं?"
                : "I can help book, reschedule, cancel, or check an appointment. What would you like to do?";
    }

    private String askDoctorPrompt(String language, boolean bookingIntent) {
        if (isHindi(language)) {
            return bookingIntent
                    ? "ज़रूर। आप किस स्पेशियलिटी या डॉक्टर से अपॉइंटमेंट लेना चाहते हैं?"
                    : "कृपया डॉक्टर का नाम या स्पेशियलिटी बताइए।";
        }
        return bookingIntent
                ? "Sure. Which speciality or doctor would you like to see?"
                : "Please tell me the doctor name or speciality you want.";
    }

    private String greetingPrompt(String language) {
        return isHindi(language)
                ? "नमस्ते। मैं आपकी अपॉइंटमेंट बुकिंग, रीशेड्यूल, कैंसिल, और स्टेटस में मदद कर सकता हूँ।"
                : "Hello. I can help with booking, rescheduling, cancelling, and appointment status.";
    }

    private String newPatientPrompt(String language) {
        return isHindi(language)
                ? "अगर आप नए मरीज हैं, तो पहले क्लिनिक कोड और OTP से मोबाइल सत्यापित करें। अगर रिकॉर्ड नहीं मिलता, तो क्विक रजिस्ट्रेशन पूरा करके मैं बुकिंग में मदद कर सकता हूँ। OTP सत्यापन के बिना मैं मरीज प्रोफ़ाइल नहीं बनाता।"
                : "If you are a new patient, first verify your mobile with the clinic code and OTP. If no record is found, complete quick registration and then I can continue booking guidance. I do not create patient profiles without OTP verification.";
    }

    private String doctorChoicePrompt(CareAiState state) {
        return numberedChoicePrompt(
                state.language,
                "I found multiple matching doctors. Please choose one:",
                "मुझे कई डॉक्टर मिले। कृपया एक चुनिए:",
                state.doctorOptions
        );
    }

    private String appointmentChoicePrompt(CareAiState state, String actionWord) {
        String english = "Please choose which appointment you want to " + actionWord + ":";
        String hindi = "कृपया वह अपॉइंटमेंट चुनिए जिसे आप " + actionWord + " चाहते हैं:";
        return numberedChoicePrompt(state.language, english, hindi, state.appointmentOptions.stream().map(AppointmentChoice::label).toList());
    }

    private String slotChoicePrompt(CareAiState state) {
        return numberedChoicePrompt(
                state.language,
                "Please choose a slot by number or time:",
                "कृपया नंबर या समय से स्लॉट चुनिए:",
                state.slotOptions
        );
    }

    private String numberedChoicePrompt(String language, String englishLead, String hindiLead, List<String> options) {
        List<String> lines = new ArrayList<>();
        lines.add(isHindi(language) ? hindiLead : englishLead);
        for (int i = 0; i < options.size(); i += 1) {
            lines.add((i + 1) + ". " + options.get(i));
        }
        return String.join("\n", lines);
    }

    private String askDatePrompt(String language) {
        return isHindi(language)
                ? "कृपया बताइए, आप किस तारीख को आना चाहते हैं?"
                : "What date would you prefer for the appointment?";
    }

    private String askRescheduleDatePrompt(CareAiState state) {
        if (isHindi(state.language)) {
            return "कृपया नई तारीख बताइए। अभी चुनी गई अपॉइंटमेंट: " + safe(state.selectedAppointmentLabel);
        }
        return "What new date would you prefer? Current appointment: " + safe(state.selectedAppointmentLabel);
    }

    private String askTimePrompt(String language) {
        return isHindi(language)
                ? "कृपया समय बताइए, जैसे सुबह, दोपहर, शाम या कोई विशेष समय।"
                : "What time works best, such as morning, afternoon, evening, or a specific time?";
    }

    private String bookingConfirmationPrompt(CareAiState state) {
        if (isHindi(state.language)) {
            return safe(state.selectedDoctorName) + " के लिए " + safe(state.preferredDate) + " को " + safe(state.selectedSlot) + " बुक कर दूँ?";
        }
        return "Should I book " + safe(state.selectedDoctorName) + " on " + safe(state.preferredDate) + " at " + safe(state.selectedSlot) + "?";
    }

    private String rescheduleConfirmationPrompt(CareAiState state) {
        if (isHindi(state.language)) {
            return safe(state.selectedAppointmentLabel) + " को " + safe(state.preferredDate) + " " + safe(state.selectedSlot) + " पर रीशेड्यूल कर दूँ?";
        }
        return "Should I reschedule " + safe(state.selectedAppointmentLabel) + " to " + safe(state.preferredDate) + " at " + safe(state.selectedSlot) + "?";
    }

    private String cancellationConfirmationPrompt(CareAiState state) {
        if (isHindi(state.language)) {
            return "क्या मैं यह अपॉइंटमेंट रद्द कर दूँ? " + safe(state.selectedAppointmentLabel);
        }
        return "Should I cancel this appointment? " + safe(state.selectedAppointmentLabel);
    }

    private String appointmentStatusPrompt(AppointmentChoice appointment, String language) {
        String summary = safe(appointment.doctorName()) + " · "
                + safe(appointment.appointmentDate() == null ? null : DATE_FORMATTER.format(appointment.appointmentDate())) + " · "
                + safe(appointment.appointmentTime() == null ? null : appointment.appointmentTime().format(TIME_FORMATTER)) + " · "
                + safe(appointment.clinicName());
        return isHindi(language)
                ? "आपकी अपॉइंटमेंट: " + summary
                : "Your appointment is with " + summary;
    }

    private String appointmentListPrompt(CareAiState state, String header) {
        List<String> lines = new ArrayList<>();
        lines.add(header);
        for (int i = 0; i < state.appointmentOptions.size(); i += 1) {
            lines.add((i + 1) + ". " + state.appointmentOptions.get(i).label());
        }
        return String.join("\n", lines);
    }

    private String noUpcomingAppointmentsPrompt(String language) {
        return isHindi(language)
                ? "कोई आगामी अपॉइंटमेंट नहीं मिली।"
                : "I could not find any upcoming appointments.";
    }

    private String bookingFailedPrompt(String language, String errorMessage) {
        if (isHindi(language)) {
            return "मैं यह अनुरोध पूरा नहीं कर सका। कृपया दूसरा समय चुनें या क्लिनिक से संपर्क करें।";
        }
        return StringUtils.hasText(errorMessage)
                ? errorMessage
                : "I could not complete that request. Please choose another option or contact the clinic.";
    }

    private String receptionHandoffPrompt(String language) {
        return isHindi(language)
                ? "मैं क्लिनिक टीम से इस अनुरोध में मदद करने के लिए कहूँगा।"
                : "I could not safely finish this request. Please contact the clinic team for help.";
    }

    private String handoffPrompt(String language) {
        return isHindi(language)
                ? "कृपया क्लिनिक या इमरजेंसी सेवाओं से तुरंत संपर्क करें।"
                : "Please contact emergency services or the clinic immediately.";
    }

    private String emergencyPrompt(String language) {
        return isHindi(language)
                ? "ये लक्षण आपातकालीन लग रहे हैं। कृपया इमरजेंसी सेवा या क्लिनिक से तुरंत संपर्क करें।"
                : "Please contact emergency services or the clinic immediately.";
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value : "the clinic";
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static final class CareAiState {
        private String language = "en";
        private PatientPortalCareAiIntent currentIntent;
        private String requestedDoctorName;
        private String requestedSpeciality;
        private String selectedDoctorId;
        private String selectedDoctorName;
        private String selectedSpeciality;
        private String selectedAppointmentId;
        private String selectedAppointmentLabel;
        private String selectedAppointmentReason;
        private String preferredDate;
        private String preferredTimeWindow;
        private String reason;
        private String selectedSlot;
        private List<DoctorChoice> doctorChoices = List.of();
        private List<String> doctorOptions = List.of();
        private List<AppointmentChoice> appointmentOptions = List.of();
        private List<SlotChoice> slotChoices = List.of();
        private List<String> slotOptions = List.of();
        private boolean confirmationPending;
        private PatientPortalCareAiIntent pendingAction;
        private boolean booked;
        private boolean actionCompleted;
        private PatientPortalCareAiIntent lastAction;
        private String bookedAppointmentDate;
        private String bookedAppointmentTime;
        private String bookingStatus;
        private boolean handoffRequired;
        private String handoffReason;
        private int unresolvedTurns;
    }

    private record SessionKey(UUID tenantId, UUID appUserId) {
    }

    private record DoctorChoice(String publicDoctorId, String doctorName, String speciality, String label) {
    }

    private record AppointmentChoice(
            UUID appointmentId,
            UUID doctorUserId,
            String doctorName,
            String clinicName,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            String status,
            String reason,
            String label
    ) {
    }

    private record SlotChoice(LocalDate appointmentDate, LocalTime slotTime) {
    }
}
