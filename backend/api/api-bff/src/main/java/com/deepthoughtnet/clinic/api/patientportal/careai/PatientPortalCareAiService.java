package com.deepthoughtnet.clinic.api.patientportal.careai;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
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
    private static final List<String> POSITIVE_CONFIRMATIONS = List.of("yes", "confirm", "book it", "go ahead", "that's fine", "okay", "ok");
    private static final List<String> POSITIVE_CONFIRMATIONS_HI = List.of("हाँ", "हां", "ठीक है", "बुक कर दीजिए", "कन्फर्म", "सही है");
    private static final List<String> NEGATIVE_CONFIRMATIONS = List.of("no", "another slot", "different slot", "different time", "change slot");
    private static final List<String> NEGATIVE_CONFIRMATIONS_HI = List.of("नहीं", "दूसरा स्लॉट", "दूसरा समय", "दूसरे समय");
    private static final List<String> GREETING_KEYWORDS = List.of("hello", "hi", "hey", "good morning", "good afternoon", "good evening");
    private static final List<String> BOOKING_INTENT_KEYWORDS = List.of("book", "appointment", "visit", "schedule");
    private static final List<String> EMERGENCY_KEYWORDS = List.of("chest pain", "difficulty breathing", "severe bleeding", "unconscious", "stroke", "suicidal");
    private static final List<String> EMERGENCY_KEYWORDS_HI = List.of("सीने में दर्द", "सांस लेने में दिक्कत", "ज़्यादा खून", "बेहोश", "स्ट्रोक", "आत्महत्या");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

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
        state.language = normalizeLanguage(request.language(), request.message(), state.language);

        String message = request.message().trim();
        if (containsEmergency(message, state.language)) {
            state.handoffRequired = true;
            state.handoffReason = "emergency-symptoms";
            state.confirmationPending = false;
            return response(state, emergencyPrompt(state.language));
        }
        if (!state.booked && isGreetingOnly(message, state.language)) {
            return response(state, greetingPrompt(state.language));
        }

        boolean progressed = applyIntent(state, message);
        if (state.confirmationPending && isPositiveConfirmation(message)) {
            progressed = true;
            return confirmBooking(state);
        }
        if (state.confirmationPending && isNegativeConfirmation(message)) {
            progressed = true;
            state.confirmationPending = false;
            state.suggestedSlot = null;
            state.slotOptions = List.of();
            return response(state, alternateSlotPrompt(state.language));
        }

        progressed = resolveDoctor(state, message) || progressed;
        progressed = resolveSlotSuggestion(state) || progressed;

        if (!progressed && !state.confirmationPending && !state.booked) {
            state.unresolvedTurns += 1;
            if (state.unresolvedTurns >= 3) {
                state.handoffRequired = true;
                state.handoffReason = "repeated-resolution-failure";
                return response(state, handoffPrompt(state.language));
            }
        } else if (progressed) {
            state.unresolvedTurns = 0;
        }

        if (state.confirmationPending && state.suggestedSlot != null) {
            return response(state, slotPrompt(state));
        }
        if (state.doctorOptions.size() > 1 && !StringUtils.hasText(state.doctorId)) {
            return response(state, doctorChoicePrompt(state));
        }
        if (!StringUtils.hasText(state.doctorName) && !StringUtils.hasText(state.requestedSpeciality)) {
            return response(state, askDoctorPrompt(state.language, containsBookingIntent(message, state.language)));
        }
        if (!StringUtils.hasText(state.preferredDate)) {
            return response(state, askDatePrompt(state.language));
        }
        if (!StringUtils.hasText(state.preferredTimeWindow)) {
            return response(state, askTimePrompt(state.language));
        }
        return response(state, slotUnavailablePrompt(state.language));
    }

    public PatientPortalCareAiResetResponse reset() {
        sessions.remove(currentSessionKey());
        return new PatientPortalCareAiResetResponse(true, "CareAI booking context cleared.");
    }

    private PatientPortalCareAiMessageResponse confirmBooking(CareAiState state) {
        if (state.suggestedSlot == null || !StringUtils.hasText(state.doctorId)) {
            state.handoffRequired = true;
            state.handoffReason = "booking-context-missing";
            return response(state, handoffPrompt(state.language));
        }
        try {
            var confirmation = patientPortalService.bookAppointment(new PatientPortalAppointmentBookingRequest(
                    state.doctorId,
                    state.suggestedSlot.appointmentDate(),
                    state.suggestedSlot.slotTime(),
                    state.reason
            ));
            state.booked = true;
            state.confirmationPending = false;
            state.bookingStatus = confirmation.status();
            state.bookedAppointmentDate = confirmation.appointmentDate() == null ? null : confirmation.appointmentDate().toString();
            state.bookedAppointmentTime = confirmation.appointmentTime() == null ? null : confirmation.appointmentTime().format(TIME_FORMATTER);
            return response(state, confirmation.message());
        } catch (RuntimeException ex) {
            state.confirmationPending = false;
            state.suggestedSlot = null;
            state.slotOptions = List.of();
            state.unresolvedTurns += 1;
            if (state.unresolvedTurns >= 3) {
                state.handoffRequired = true;
                state.handoffReason = "booking-failed";
                return response(state, handoffPrompt(state.language));
            }
            return response(state, bookingFailedPrompt(state.language, ex.getMessage()));
        }
    }

    private boolean applyIntent(CareAiState state, String message) {
        boolean changed = false;
        String requestedDoctorName = findRequestedDoctorName(message, state.language);
        if (StringUtils.hasText(requestedDoctorName) && !requestedDoctorName.equalsIgnoreCase(state.requestedDoctorName)) {
            state.requestedDoctorName = requestedDoctorName;
            state.requestedSpeciality = null;
            state.doctorId = null;
            state.doctorName = null;
            changed = true;
        }

        String speciality = findSpeciality(message);
        if (StringUtils.hasText(speciality) && !speciality.equalsIgnoreCase(state.requestedSpeciality)) {
            state.requestedSpeciality = speciality;
            if (!StringUtils.hasText(state.requestedDoctorName)) {
                state.doctorId = null;
                state.doctorName = null;
            }
            changed = true;
        }

        String preferredDate = findPreferredDate(message, state.language);
        if (StringUtils.hasText(preferredDate) && !preferredDate.equals(state.preferredDate)) {
            state.preferredDate = preferredDate;
            state.suggestedSlot = null;
            state.slotOptions = List.of();
            changed = true;
        }

        String preferredTimeWindow = findPreferredTimeWindow(message, state.language);
        if (StringUtils.hasText(preferredTimeWindow) && !preferredTimeWindow.equalsIgnoreCase(state.preferredTimeWindow)) {
            state.preferredTimeWindow = preferredTimeWindow;
            state.suggestedSlot = null;
            state.slotOptions = List.of();
            changed = true;
        }

        String reason = findReason(message, state.language);
        if (StringUtils.hasText(reason) && !reason.equalsIgnoreCase(state.reason)) {
            state.reason = reason;
            changed = true;
        }
        return changed;
    }

    private boolean resolveDoctor(CareAiState state, String message) {
        List<PatientPortalDoctorResponse> doctors = patientPortalService.doctors();
        if (doctors.isEmpty()) {
            state.handoffRequired = true;
            state.handoffReason = "no-tenant-doctors";
            return false;
        }

        if (StringUtils.hasText(state.requestedDoctorName)) {
            List<PatientPortalDoctorResponse> matches = doctors.stream()
                    .filter(doctor -> matchesDoctorName(doctor.doctorName(), state.requestedDoctorName))
                    .sorted(Comparator.comparing(PatientPortalDoctorResponse::doctorName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            if (matches.size() == 1) {
                PatientPortalDoctorResponse doctor = matches.getFirst();
                state.doctorId = doctor.publicDoctorId();
                state.doctorName = doctor.doctorName();
                state.speciality = doctor.specialization();
                state.doctorOptions = List.of();
                return true;
            }
            if (matches.size() > 1) {
                state.doctorOptions = matches.stream().map(this::doctorLabel).toList();
                state.doctorId = null;
                state.doctorName = null;
                return true;
            }
            state.doctorOptions = doctors.stream().limit(4).map(this::doctorLabel).toList();
            state.doctorId = null;
            state.doctorName = null;
            return false;
        }

        if (StringUtils.hasText(state.requestedSpeciality)) {
            List<PatientPortalDoctorResponse> matches = doctors.stream()
                    .filter(doctor -> containsIgnoreCase(doctor.specialization(), state.requestedSpeciality))
                    .sorted(Comparator.comparing(PatientPortalDoctorResponse::doctorName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            if (matches.size() == 1) {
                PatientPortalDoctorResponse doctor = matches.getFirst();
                state.doctorId = doctor.publicDoctorId();
                state.doctorName = doctor.doctorName();
                state.speciality = doctor.specialization();
                state.doctorOptions = List.of();
                return true;
            }
            if (matches.size() > 1) {
                state.doctorOptions = matches.stream().limit(4).map(this::doctorLabel).toList();
                return true;
            }
        }

        if (StringUtils.hasText(message) && state.doctorOptions.isEmpty()) {
            state.doctorOptions = doctors.stream().limit(4).map(this::doctorLabel).toList();
        }
        return false;
    }

    private boolean resolveSlotSuggestion(CareAiState state) {
        if (!StringUtils.hasText(state.doctorId) || !StringUtils.hasText(state.preferredDate) || !StringUtils.hasText(state.preferredTimeWindow)) {
            return false;
        }

        List<PatientPortalDoctorSlotResponse> selectableSlots = patientPortalService.doctorSlots(state.doctorId, LocalDate.parse(state.preferredDate)).stream()
                .filter(PatientPortalDoctorSlotResponse::selectable)
                .sorted(Comparator.comparing(PatientPortalDoctorSlotResponse::slotTime))
                .toList();
        if (selectableSlots.isEmpty()) {
            state.suggestedSlot = null;
            state.slotOptions = List.of();
            return false;
        }

        List<PatientPortalDoctorSlotResponse> matching = filterByPreferredTime(selectableSlots, state.preferredTimeWindow);
        List<PatientPortalDoctorSlotResponse> resolved = matching.isEmpty() ? nearestSlots(selectableSlots, state.preferredTimeWindow) : matching;
        if (resolved.isEmpty()) {
            state.suggestedSlot = null;
            state.slotOptions = List.of();
            return false;
        }

        state.suggestedSlot = resolved.getFirst();
        state.slotOptions = resolved.stream()
                .limit(3)
                .map(slot -> slot.slotTime().format(TIME_FORMATTER))
                .toList();
        state.confirmationPending = true;
        return true;
    }

    private List<PatientPortalDoctorSlotResponse> filterByPreferredTime(
            List<PatientPortalDoctorSlotResponse> slots,
            String preferredTimeWindow
    ) {
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
        try {
            LocalTime requested = LocalTime.parse(normalized, TIME_FORMATTER);
            return slots.stream().filter(slot -> requested.equals(slot.slotTime())).toList();
        } catch (DateTimeParseException ignored) {
            return slots;
        }
    }

    private List<PatientPortalDoctorSlotResponse> nearestSlots(List<PatientPortalDoctorSlotResponse> slots, String preferredTimeWindow) {
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

    private PatientPortalCareAiMessageResponse response(CareAiState state, String message) {
        return new PatientPortalCareAiMessageResponse(
                message,
                new PatientPortalCareAiStateResponse(
                        state.language,
                        state.doctorName,
                        state.speciality,
                        state.preferredDate,
                        state.preferredTimeWindow,
                        state.suggestedSlot == null ? null : state.suggestedSlot.slotTime().format(TIME_FORMATTER),
                        state.confirmationPending,
                        state.booked,
                        state.bookedAppointmentDate,
                        state.bookedAppointmentTime,
                        state.bookingStatus,
                        state.handoffRequired,
                        state.handoffReason,
                        state.doctorOptions,
                        state.slotOptions
                )
        );
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
                .replaceAll("[^\\p{L}\\p{N} ]", " ")
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
        Matcher matcher = EXPLICIT_TIME_PATTERN.matcher(lower);
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
                ? "नमस्ते। मैं आपकी अपॉइंटमेंट बुकिंग में मदद कर सकता हूँ। आप किस स्पेशियलिटी या डॉक्टर से मिलना चाहते हैं?"
                : "Hello. I can help you book an appointment. Which speciality or doctor would you like to see?";
    }

    private String doctorChoicePrompt(CareAiState state) {
        String options = String.join(", ", state.doctorOptions);
        return isHindi(state.language)
                ? "मुझे कई डॉक्टर मिले। कृपया एक चुनिए: " + options + "."
                : "I found multiple matching doctors. Please choose one: " + options + ".";
    }

    private String askDatePrompt(String language) {
        return isHindi(language)
                ? "कृपया बताइए, आप किस तारीख को आना चाहते हैं?"
                : "What date would you prefer for the appointment?";
    }

    private String askTimePrompt(String language) {
        return isHindi(language)
                ? "कृपया समय बताइए, जैसे सुबह, दोपहर, शाम या कोई विशेष समय।"
                : "What time works best, such as morning, afternoon, evening, or a specific time?";
    }

    private String slotPrompt(CareAiState state) {
        String primary = state.slotOptions.isEmpty() ? null : state.slotOptions.getFirst();
        String alternatives = state.slotOptions.size() > 1 ? String.join(", ", state.slotOptions.subList(1, state.slotOptions.size())) : "";
        if (isHindi(state.language)) {
            return StringUtils.hasText(alternatives)
                    ? safe(state.doctorName) + " के लिए " + safe(state.preferredDate) + " को " + safe(primary) + " उपलब्ध है। दूसरे समय " + alternatives + " हैं। क्या मैं यह स्लॉट बुक करूँ?"
                    : safe(state.doctorName) + " के लिए " + safe(state.preferredDate) + " को " + safe(primary) + " उपलब्ध है। क्या मैं यह स्लॉट बुक करूँ?";
        }
        return StringUtils.hasText(alternatives)
                ? safe(state.doctorName) + " is available on " + safe(state.preferredDate) + " at " + safe(primary) + ". Other nearby slots are " + alternatives + ". Should I book this slot?"
                : safe(state.doctorName) + " is available on " + safe(state.preferredDate) + " at " + safe(primary) + ". Should I book this slot?";
    }

    private String slotUnavailablePrompt(String language) {
        return isHindi(language)
                ? "मुझे उपयुक्त स्लॉट नहीं मिला। कृपया दूसरी तारीख या समय बताइए।"
                : "I could not find a suitable slot yet. Please share another date or time.";
    }

    private String alternateSlotPrompt(String language) {
        return isHindi(language)
                ? "ठीक है। कृपया दूसरा स्लॉट या समय बताइए।"
                : "Okay. Please tell me another slot or time.";
    }

    private String bookingFailedPrompt(String language, String errorMessage) {
        if (isHindi(language)) {
            return "यह स्लॉट अभी उपलब्ध नहीं है। कृपया दूसरा समय चुनिए।";
        }
        return StringUtils.hasText(errorMessage)
                ? errorMessage
                : "That slot is no longer available. Please choose another time.";
    }

    private String handoffPrompt(String language) {
        return isHindi(language)
                ? "मैं रिसेप्शन टीम से आपकी बुकिंग में मदद करने को कहूँगा।"
                : "I’ll ask the receptionist to help you with this booking.";
    }

    private String emergencyPrompt(String language) {
        return isHindi(language)
                ? "यह लक्षण आपातकालीन लग रहे हैं। कृपया तुरंत क्लिनिक, नज़दीकी आपातकालीन सेवा, या हेल्पलाइन से संपर्क करें।"
                : "These symptoms may be urgent. Please contact the clinic, emergency care, or a local helpline right away.";
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value : "the clinic";
    }

    private static final class CareAiState {
        private String language = "en";
        private String requestedDoctorName;
        private String requestedSpeciality;
        private String doctorId;
        private String doctorName;
        private String speciality;
        private String preferredDate;
        private String preferredTimeWindow;
        private String reason;
        private PatientPortalDoctorSlotResponse suggestedSlot;
        private List<String> doctorOptions = List.of();
        private List<String> slotOptions = List.of();
        private boolean confirmationPending;
        private boolean booked;
        private String bookedAppointmentDate;
        private String bookedAppointmentTime;
        private String bookingStatus;
        private boolean handoffRequired;
        private String handoffReason;
        private int unresolvedTurns;
    }

    private record SessionKey(UUID tenantId, UUID appUserId) {
    }
}
