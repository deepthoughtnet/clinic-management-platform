package com.deepthoughtnet.clinic.api.platform.communicationtest;

import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiExecutionContext;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiChannel;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiReadOnlyToolGateway;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiReadOnlyToolGateway.ToolInput;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaExecutionContext;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2VoiceConversationAdapter;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2VoiceToolExecutor;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilitySlot;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiReadOnlyToolGateway.VoiceAvailabilityData;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiReadOnlyToolGateway.VoiceScheduleWindow;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiReadOnlyToolGateway.VoiceSlot;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import com.deepthoughtnet.clinic.api.voice.VoicePersona;
import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import java.time.ZoneId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Call-scoped voice adapter for the existing Care AI runtime. No portal login is created here. */
@Service
public class VoiceReadOnlyAivaService {
    private static final Logger log = LoggerFactory.getLogger(VoiceReadOnlyAivaService.class);
    private static final String MUTATION_RESPONSE =
            "मैं अभी उपलब्ध डॉक्टर और समय बता सकती हूँ। अपॉइंटमेंट बुकिंग अगले चरण में उपलब्ध होगी।";
    private static final String FAILURE_RESPONSE =
            "क्षमा कीजिए, अभी जानकारी प्राप्त नहीं हो पा रही है। कृपया दोबारा बताइए।";

    private final HealthcareAiReadOnlyToolGateway toolGateway;
    private final AivaV2VoiceConversationAdapter v2VoiceAdapter;
    private final VoicePersona voicePersona;
    private final ClinicTimeZoneResolver timeZoneResolver;

    @Autowired
    public VoiceReadOnlyAivaService(
            HealthcareAiReadOnlyToolGateway toolGateway,
            AivaV2VoiceConversationAdapter v2VoiceAdapter,
            VoiceTestProperties voiceTestProperties,
            ClinicTimeZoneResolver timeZoneResolver
    ) {
        this.toolGateway = toolGateway;
        this.v2VoiceAdapter = v2VoiceAdapter;
        this.voicePersona = voiceTestProperties == null || voiceTestProperties.getPersona() == null
                ? VoicePersona.FEMALE : voiceTestProperties.getPersona();
        this.timeZoneResolver = timeZoneResolver;
    }

    public VoiceReadOnlyAivaService(HealthcareAiReadOnlyToolGateway toolGateway,
                                    AivaV2VoiceConversationAdapter v2VoiceAdapter) {
        this(toolGateway, v2VoiceAdapter, new VoiceTestProperties(), null);
    }

    public VoiceReadOnlyAivaService(
            HealthcareAiReadOnlyToolGateway toolGateway,
            AivaV2VoiceConversationAdapter v2VoiceAdapter,
            VoiceTestProperties voiceTestProperties
    ) {
        this(toolGateway, v2VoiceAdapter, voiceTestProperties, null);
    }

    public TurnResult continueTurn(Context context, String transcript) {
        if (context == null || !StringUtils.hasText(transcript)) {
            return new TurnResult(FAILURE_RESPONSE, "UNKNOWN", null, false, false, false);
        }
        try {
            ZoneId tenantZone = timeZoneResolver == null ? ZoneId.of("Asia/Kolkata")
                    : timeZoneResolver.resolve(context.tenantId());
            HealthcareAiExecutionContext executionContext = new HealthcareAiExecutionContext(
                    context.tenantId(), context.identifiedPatientId(), context.patientIdentificationStatus(),
                    HealthcareAiChannel.VOICE, context.interactionId(), context.clinicId(), context.locationId(),
                    context.language());
            AivaExecutionContext aivaContext = new AivaExecutionContext(
                    context.tenantId(), context.identifiedPatientId(), "VOICE", context.language(),
                    tenantZone, context.providerCallId(), context.interactionId(),
                    context.clinicId(), context.locationId(),
                    HealthcareAiReadOnlyToolGateway.VOICE_READ_ONLY_ALLOWED_TOOLS);
            AtomicReference<HealthcareAiReadOnlyToolGateway.ToolResult> executed = new AtomicReference<>();
            AivaV2VoiceConversationAdapter.TurnResult turn = v2VoiceAdapter.continueTurn(
                    aivaContext, transcript, (tool, ignored, input) -> {
                        if ("FIND_DOCTOR".equals(tool)) {
                            log.info("VOICE_FIND_DOCTOR_INPUT providerCallId={} tenantId={} doctorQuery={} "
                                            + "canonicalSpecialty={} clinicId={} locationId={}",
                                    context.providerCallId(), context.tenantId(), input.query(), input.speciality(),
                                    input.clinic(), input.location());
                        }
                        HealthcareAiReadOnlyToolGateway.ToolResult result = toolGateway.execute(tool,
                                executionContext, new ToolInput(input.query(), input.speciality(), input.clinic(),
                                        input.location(), input.doctorId(), input.date(), input.queryType(),
                                        input.daypart(), input.timezone(), input.pageOffset()));
                        executed.set(result);
                        return new AivaV2VoiceToolExecutor.ToolResult(result.allowed(), result.success(),
                                result.value(), result.candidateCount(), result.sourceCandidateCount(),
                                result.diagnostics(), providerOptions(result), availabilityContext(result, input));
                    });
            HealthcareAiReadOnlyToolGateway.ToolResult result = executed.get();
            String response = turn.response();
            if (turn.toolExecuted() && result != null) response = render(turn.tool(), result,
                    turn.toolInputSpeciality(), turn.normalizedTurn(), tenantZone);
            if (turn.mutationBlocked()) {
                log.info("VOICE_AIVA_MUTATION_BLOCKED callId={} tool={}", context.providerCallId(), turn.intent());
            }
            return new TurnResult(response, turn.intent(), turn.tool(), turn.aivaUsed(), turn.mutationBlocked(),
                    turn.toolExecuted(), turn.toolInputSpeciality(), turn.toolResultCount(),
                    result == null ? turn.toolResultSummary() : summarizeResult(turn.tool(), result), turn.closing());
        } catch (RuntimeException ex) {
            log.warn("VOICE_AIVA_TURN_FAILED callId={} exception={} reason={}",
                    context.providerCallId(), ex.getClass().getSimpleName(), safeReason(ex));
            return new TurnResult(FAILURE_RESPONSE, "UNKNOWN", null, false, false, false);
        }
    }

    public void endSession(UUID tenantId, String providerCallId) {
        v2VoiceAdapter.endSession(tenantId, providerCallId);
    }

    private java.util.List<AivaV2VoiceToolExecutor.ProviderOption> providerOptions(
            HealthcareAiReadOnlyToolGateway.ToolResult result) {
        if (result.value() instanceof VoiceAvailabilityData availability && !availability.doctorCandidates().isEmpty()) {
            return availability.doctorCandidates().stream().map(doctor -> new AivaV2VoiceToolExecutor.ProviderOption(
                    doctor.doctorId(), doctor.displayName(), doctor.specialty(), null, null, null, null)).toList();
        }
        if (result.value() instanceof VoiceAvailabilityData availability && availability.doctorId() != null) {
            return List.of(new AivaV2VoiceToolExecutor.ProviderOption(availability.doctorId(),
                    availability.doctorName(), null, null, null, null, null));
        }
        if (!(result.value() instanceof java.util.List<?> values)) return java.util.List.of();
        return values.stream().filter(PublicDoctorSummaryResponse.class::isInstance)
                .map(PublicDoctorSummaryResponse.class::cast)
                .map(doctor -> new AivaV2VoiceToolExecutor.ProviderOption(
                        doctor.publicDoctorId(), doctor.doctorDisplayName(), doctor.speciality(), null, null,
                        doctor.clinicSlug(), doctor.clinicDisplayName()))
                .toList();
    }

    private AvailabilityResult availabilityContext(HealthcareAiReadOnlyToolGateway.ToolResult result,
                                                   AivaV2VoiceToolExecutor.ToolInput input) {
        if (!(result.value() instanceof VoiceAvailabilityData data) || data.doctorId() == null) return null;
        List<AvailabilitySlot> slots = data.bookableSlots().stream().map(slot ->
                new AvailabilitySlot(null, slot.startTime(), slot.endTime(), displayTime(slot.startTime()))).toList();
        int offset = data.displayOffset();
        String daypart = input.daypart() == null ? null : input.daypart().name().toLowerCase(java.util.Locale.ROOT);
        String cursor = data.queryType() == AivaV2VoiceToolExecutor.QueryType.RECURRING_SCHEDULE
                || data.queryType() == AivaV2VoiceToolExecutor.QueryType.EFFECTIVE_DATE_SCHEDULE
                ? data.configuredScheduleWindows().isEmpty() || data.unavailableForDate() ? "VOICE_CONTEXT"
                : daypart == null ? "VOICE_CONFIRM_SCHEDULE" : "VOICE_CONFIRM_SCHEDULE_DAYPART:" + input.daypart().name()
                : daypart == null && data.availableDayparts().size() > 1 ? "VOICE_PENDING_DAYPART"
                : daypart == null && data.availableDayparts().size() == 1
                ? "VOICE_CONFIRM_DAYPART:" + data.availableDayparts().getFirst().name()
                : daypart == null ? "VOICE_CONTEXT" : "VOICE_DAYPART:" + daypart.toUpperCase(java.util.Locale.ROOT);
        return new AvailabilityResult(UUID.randomUUID(), null, 0, "voice-read-only", data.doctorId(),
                data.doctorId(), null, data.date(), daypart, null, input.timezone().getId(), slots, cursor,
                offset + 3 < slots.size(), Instant.now(), Instant.now().plusSeconds(5 * 60), offset, 3);
    }

    private String render(String tool, HealthcareAiReadOnlyToolGateway.ToolResult result, String speciality,
                          com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn turn,
                          ZoneId timezone) {
        if (result.value() instanceof VoiceAvailabilityData availability) {
            logScheduleTrace(availability, Math.max(0, Math.min(3,
                    availability.bookableSlots().size() - availability.displayOffset())));
            String language = turn == null ? "hi-IN" : turn.responseLanguage();
            return renderAvailability(availability, language, turn == null ? null : turn.responseStyle(), timezone);
        }
        if (result.candidateCount() == 0) {
            if ("FIND_DOCTOR".equals(tool) && StringUtils.hasText(speciality)) {
                return "क्षमा कीजिए, इस विशेषज्ञता के लिए कोई डॉक्टर नहीं मिला।";
            }
            return "क्षमा कीजिए, मुझे अभी कोई जानकारी नहीं मिली।";
        }
        return switch (tool) {
            case "FIND_DOCTOR" -> doctorResponse(result, speciality, turn);
            case "FIND_CLINIC" -> "मुझे " + result.candidateCount() + " क्लिनिक मिले हैं।";
            case "FIND_SERVICE" -> "यह सेवा उपलब्ध है।";
            case "FIND_SLOTS" -> "" + result.candidateCount() + " उपलब्ध समय मिले हैं।";
            default -> FAILURE_RESPONSE;
        };
    }

    String renderAvailability(VoiceAvailabilityData data, String language,
                             com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle style) {
        return renderAvailability(data, language, style, ZoneId.of("Asia/Kolkata"));
    }

    private String renderAvailability(VoiceAvailabilityData data, String language,
                                     com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle style,
                                     ZoneId timezone) {
        if (data.ambiguousDoctor()) {
            String names = data.doctorCandidates().stream().limit(2).map(option -> option.displayName())
                    .reduce((left, right) -> left + " या " + right).orElse(null);
            return StringUtils.hasText(names) ? "कृपया बताइए, आप डॉ. " + names + " में से किस डॉक्टर की बात कर रहे हैं?"
                    : "कृपया बताइए, आप किस डॉक्टर के बारे में पूछ रहे हैं?";
        }
        if (data.doctorId() == null) return "कृपया डॉक्टर का नाम बताइए।";
        boolean hindi = language == null || language.toLowerCase(java.util.Locale.ROOT).startsWith("hi");
        boolean hinglish = hindi && style == com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.HINGLISH;
        if (data.queryType() == AivaV2VoiceToolExecutor.QueryType.RECURRING_SCHEDULE) {
            if (data.configuredScheduleWindows().isEmpty()) return hindi
                    ? hinglish ? "Dr. " + data.doctorName() + " ke consultation hours configured nahi hain."
                    : "डॉ. " + data.doctorName() + " के consultation समय उपलब्ध नहीं हैं।"
                    : "I couldn't find configured consultation hours for Dr. " + data.doctorName() + ".";
            return scheduleResponse(data, hindi, hinglish, timezone);
        }
        if (data.queryType() == AivaV2VoiceToolExecutor.QueryType.EFFECTIVE_DATE_SCHEDULE) {
            if (data.unavailableForDate()) return hindi
                    ? hinglish ? datePrefix(data.date(), timezone, true, true) + "Dr. " + data.doctorName()
                    + " usually consult karte hain, lekin us din available nahi hain."
                    : datePrefix(data.date(), timezone, true, false) + "डॉ. " + data.doctorName()
                    + " आम तौर पर उस समय consult करते हैं, लेकिन उपलब्ध नहीं हैं।"
                    : datePrefix(data.date(), timezone, false, false) + "Dr. " + data.doctorName()
                    + " usually consults then, but is unavailable.";
            if (data.configuredScheduleWindows().isEmpty()) return noScheduleResponse(data, hindi, hinglish);
            return scheduleResponse(data, hindi, hinglish, timezone);
        }
        if (data.queryType() == AivaV2VoiceToolExecutor.QueryType.EFFECTIVE_DATE_AVAILABILITY
                && data.configuredScheduleWindows().isEmpty()) {
            if (data.unavailableForDate()) return hindi
                    ? hinglish ? "Dr. " + data.doctorName() + " us din available nahi hain."
                    : "डॉ. " + data.doctorName() + " उस दिन उपलब्ध नहीं हैं।"
                    : "Dr. " + data.doctorName() + " is unavailable that day.";
            return noScheduleResponse(data, hindi, hinglish);
        }
        if (data.requestedDaypart() == null && !data.availableDayparts().isEmpty()) {
            if (data.availableDayparts().size() == 1) {
                String period = daypartName(data.availableDayparts().getFirst(), hindi, hinglish);
                return hindi && !hinglish ? datePrefix(data.date(), timezone, true, false) + "केवल " + period + " में समय उपलब्ध हैं। क्या मैं उस समय के slots बताऊँ?"
                        : hinglish ? datePrefix(data.date(), timezone, true, true) + "sirf " + period + " mein slots available hain. Kya main woh slots bataun?"
                        : datePrefix(data.date(), timezone, false, false) + "Appointments are available only in the " + period + ". Would you like me to list those slots?";
            }
            String periods = joinDayparts(data.availableDayparts(), hindi, hinglish);
            return hindi && !hinglish ? datePrefix(data.date(), timezone, true, false) + periods + " में समय उपलब्ध हैं। आप कौन सा समय देखना चाहेंगे?"
                    : hinglish ? datePrefix(data.date(), timezone, true, true) + periods + " mein slots available hain. Aap kaunsa time dekhna chahenge?"
                    : datePrefix(data.date(), timezone, false, false) + "Appointments are available in the " + periods + ". Which time would you prefer?";
        }
        if (data.bookableSlots().isEmpty()) {
            if (data.unavailableForDate()) return hindi
                    ? hinglish ? "Dr. " + data.doctorName() + " us din available nahi hain."
                    : "डॉ. " + data.doctorName() + " उस दिन उपलब्ध नहीं हैं।"
                    : "Dr. " + data.doctorName() + " is unavailable that day.";
            if (data.configuredScheduleWindows().isEmpty()) return noScheduleResponse(data, hindi, hinglish);
            if (data.onlyPastSlots()) return hindi
                    ? hinglish ? (data.date().equals(java.time.LocalDate.now(timezone)) ? "Aaj ke liye" : datePrefix(data.date(), timezone, true, true))
                    + " koi future slot available nahi hai."
                    : (data.date().equals(java.time.LocalDate.now(timezone)) ? "आज के लिए" : datePrefix(data.date(), timezone, true, false))
                    + " कोई future slot available नहीं है।"
                    : "There are no future appointment slots for that time.";
            if (data.fullSlotsOnly()) return hindi
                    ? hinglish ? "Dr. " + data.doctorName() + " us din consult karte hain, lekin koi appointment slot free nahi hai."
                    : "डॉ. " + data.doctorName() + " उस दिन consult करते हैं, लेकिन कोई appointment slot खाली नहीं है।"
                    : "Dr. " + data.doctorName() + " consults that day, but no appointment slots are free.";
            if (data.queryType() == AivaV2VoiceToolExecutor.QueryType.EFFECTIVE_DATE_AVAILABILITY
                    && !data.configuredScheduleWindows().isEmpty()) {
                return hindi ? hinglish ? "Dr. " + data.doctorName() + " us time consult karte hain, lekin abhi koi appointment slot free nahi hai."
                        : "डॉ. " + data.doctorName() + " उस समय consultation करते हैं, लेकिन फिलहाल कोई appointment slot खाली नहीं है।"
                        : "Dr. " + data.doctorName() + " consults then, but no appointment slots are currently free.";
            }
            return hindi ? hinglish ? "Us time koi appointment slot free nahi hai."
                    : "उस समय कोई appointment slot खाली नहीं है।"
                    : "There are no free appointment slots for that time.";
        }
        List<VoiceSlot> page = data.bookableSlots().stream().skip(data.displayOffset()).limit(3).toList();
        if (page.isEmpty()) return hindi && !hinglish ? "इस समय के लिए और slots उपलब्ध नहीं हैं।"
                : hinglish ? "Is time window mein aur slots available nahi hain."
                : "There are no more slots available in this time window.";
        String times = page.stream().map(slot -> slotVoiceTime(slot.startTime(), hindi))
                .reduce((a, b) -> a + ", " + b).orElse("");
        boolean more = data.displayOffset() + page.size() < data.bookableSlots().size();
        String spokenDateHindi = data.date() == null ? "" : datePrefix(data.date(), timezone, true, hinglish);
        String spokenDateEnglish = data.date() == null ? "" : datePrefix(data.date(), timezone, false, false);
        if (hinglish) return spokenDateHindi + (data.requestedDaypart() == null ? "available times hain: "
                : daypartName(data.requestedDaypart(), true, true) + " mein " + times + " slots available hain")
                + (data.requestedDaypart() == null ? times : "")
                + (more ? ". Aur slots ke liye ‘aur slots batao’ kahiye." : ".");
        return hindi ? spokenDateHindi + (data.requestedDaypart() == null ? "उपलब्ध समय हैं: " : daypartName(data.requestedDaypart(), true, false) + " के समय हैं: ")
                + times + (more ? "। और समय के लिए ‘और समय बताइए’ कहें।" : "।")
                : (data.requestedDaypart() == null ? spokenDateEnglish + "Available times are "
                : spokenDateEnglish + daypartName(data.requestedDaypart(), false, false) + ", ")
                + times + (data.requestedDaypart() == null ? "" : " are available")
                + (more ? ". Say ‘show me more’ for additional times." : ".");
    }

    private String slotVoiceTime(java.time.LocalTime time, boolean hindi) {
        if (hindi || time == null) return voiceTime(time, hindi);
        int hour = time.getHour() % 12 == 0 ? 12 : time.getHour() % 12;
        return hour + ":" + String.format("%02d", time.getMinute()) + (time.getHour() < 12 ? " AM" : " PM");
    }

    private String scheduleResponse(VoiceAvailabilityData data, boolean hindi, boolean hinglish, ZoneId timezone) {
        boolean devanagari = hindi && !hinglish;
        var byDay = data.configuredScheduleWindows().stream().collect(java.util.stream.Collectors.groupingBy(
                VoiceScheduleWindow::dayOfWeek, java.util.TreeMap::new, java.util.stream.Collectors.toList()));
        var sameWindows = new java.util.LinkedHashMap<List<String>, List<java.time.DayOfWeek>>();
        byDay.forEach((day, windows) -> {
            List<String> hours = windows.stream().sorted(java.util.Comparator.comparing(VoiceScheduleWindow::startTime))
                    .map(window -> scheduleHours(window, devanagari)).toList();
            sameWindows.computeIfAbsent(hours, ignored -> new java.util.ArrayList<>()).add(day);
        });
        List<String> periods = sameWindows.entrySet().stream().map(entry -> {
            String days = dayRanges(entry.getValue(), devanagari);
            String hours = String.join(devanagari ? " और " : hinglish ? " aur " : " and ", entry.getKey());
            return (StringUtils.hasText(days) ? days + " " : "") + hours;
        }).toList();
        String rendered = String.join(devanagari ? " और " : hinglish ? " aur " : " and ", periods);
        String date = data.date() == null ? "" : datePrefix(data.date(), timezone, hindi, hinglish);
        if (devanagari) return date + "डॉ. " + data.doctorName() + " " + rendered
                + " consultation करते हैं। क्या आप available appointment slots भी देखना चाहेंगे?";
        if (hinglish) return (data.date() == null ? "" : date) + "Dr. " + data.doctorName() + " " + rendered
                + " consult karte hain. Kya aap available appointment slots bhi dekhna chahenge?";
        return (data.date() == null ? "" : date) + "Dr. " + data.doctorName() + " consults " + rendered
                + ". Would you like me to check available appointment slots too?";
    }

    private String scheduleHours(VoiceScheduleWindow window, boolean hindi) {
        String start = voiceTime(window.startTime(), hindi);
        String end = voiceTime(window.endTime(), hindi);
        return hindi ? start + " से " + end : start + " to " + end;
    }

    private String dayRanges(List<java.time.DayOfWeek> days, boolean hindi) {
        if (days.isEmpty()) return "";
        List<String> names = days.stream().map(day -> switch (day) {
            case MONDAY -> hindi ? "सोमवार" : "Monday";
            case TUESDAY -> hindi ? "मंगलवार" : "Tuesday";
            case WEDNESDAY -> hindi ? "बुधवार" : "Wednesday";
            case THURSDAY -> hindi ? "गुरुवार" : "Thursday";
            case FRIDAY -> hindi ? "शुक्रवार" : "Friday";
            case SATURDAY -> hindi ? "शनिवार" : "Saturday";
            case SUNDAY -> hindi ? "रविवार" : "Sunday";
        }).toList();
        if (days.size() == 1) return names.getFirst();
        if (days.size() == 5 && days.getFirst() == java.time.DayOfWeek.MONDAY
                && days.getLast() == java.time.DayOfWeek.FRIDAY) {
            return names.getFirst() + (hindi ? " से " : " to ") + names.getLast();
        }
        return String.join(hindi ? ", " : ", ", names);
    }

    private String daypartName(com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn.Daypart daypart,
                               boolean hindi, boolean hinglish) {
        if (hindi && !hinglish) return switch (daypart) {
            case MORNING -> "सुबह";
            case AFTERNOON -> "दोपहर";
            case EVENING -> "शाम";
        };
        return switch (daypart) {
            case MORNING -> hinglish ? "morning" : "morning";
            case AFTERNOON -> hinglish ? "afternoon" : "afternoon";
            case EVENING -> hinglish ? "evening" : "evening";
        };
    }

    private String joinDayparts(List<com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn.Daypart> dayparts,
                                boolean hindi, boolean hinglish) {
        String separator = hinglish ? " aur " : hindi ? " और " : " and ";
        String joined = dayparts.stream().map(part -> daypartName(part, hindi, hinglish))
                .reduce((left, right) -> left + separator + right).orElse("");
        return hinglish && !joined.isEmpty() ? Character.toUpperCase(joined.charAt(0)) + joined.substring(1) : joined;
    }

    private String datePrefix(java.time.LocalDate date, ZoneId timezone, boolean hindi, boolean hinglish) {
        if (date == null) return "";
        java.time.LocalDate today = java.time.LocalDate.now(timezone == null ? ZoneId.of("Asia/Kolkata") : timezone);
        if (date.equals(today.plusDays(1))) return hindi && !hinglish ? "कल " : hinglish ? "Kal " : "Tomorrow ";
        if (date.equals(today)) return hindi && !hinglish ? "आज " : hinglish ? "Aaj " : "Today ";
        return date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)) + " ";
    }

    private String voiceTime(java.time.LocalTime time, boolean hindi) {
        if (time == null) return "";
        int hour = time.getHour() % 12 == 0 ? 12 : time.getHour() % 12;
        String suffix = !hindi ? (time.getHour() < 12 ? "AM" : "PM")
                : time.getHour() < 12 ? "सुबह" : time.getHour() < 17 ? "दोपहर"
                : time.getHour() < 21 ? "शाम" : "रात";
        return hour + (time.getMinute() == 0 ? "" : ":" + String.format("%02d", time.getMinute())) + " " + suffix;
    }

    private String displayTime(java.time.LocalTime time) {
        return time == null ? "" : time.toString();
    }

    private void logScheduleTrace(VoiceAvailabilityData data, int displayedSlotCount) {
        String windows = data.configuredScheduleWindows().stream().map(window -> window.dayOfWeek() + ":"
                + window.startTime() + "-" + window.endTime() + (window.breakStartTime() == null ? ""
                : " break=" + window.breakStartTime() + "-" + window.breakEndTime())).toList().toString();
        int displayOffset = data.displayOffset();
        boolean hasMore = displayOffset + displayedSlotCount < data.bookableSlots().size();
        log.info("VOICE_SCHEDULE_TRACE doctorId={} doctorName={} queryType={} date={} requestedDaypart={} "
                        + "configuredScheduleWindows={} availableDayparts={} slotCount={} displayOffset={} "
                        + "displayedSlotCount={} hasMore={}",
                data.doctorId(), data.doctorName(), data.queryType(), data.date(), data.requestedDaypart(), windows,
                data.availableDayparts(), data.bookableSlots().size(), displayOffset, displayedSlotCount, hasMore);
    }

    private String doctorResponse(HealthcareAiReadOnlyToolGateway.ToolResult result, String speciality,
                                  com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn turn) {
        String responseLanguage = turn == null ? "hi" : turn.responseLanguage();
        boolean english = responseLanguage != null && responseLanguage.toLowerCase(java.util.Locale.ROOT).startsWith("en");
        boolean hinglish = !english && turn != null
                && turn.responseStyle() == com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.HINGLISH;
        if (result.value() instanceof java.util.List<?> doctors && !doctors.isEmpty()
                && doctors.getFirst() instanceof PublicDoctorSummaryResponse doctor) {
            if (result.candidateCount() == 1) {
                if (english) return StringUtils.hasText(speciality)
                        ? "Dr. " + doctor.doctorDisplayName() + " is available as a "
                        + englishSpecialty(speciality) + "."
                        : "Dr. " + doctor.doctorDisplayName() + " is available.";
                if (hinglish) return StringUtils.hasText(speciality)
                        ? "Dr. " + doctor.doctorDisplayName() + " " + englishSpecialty(speciality)
                        + " doctor ke roop mein available hain."
                        : "Dr. " + doctor.doctorDisplayName() + " available hain.";
                String role = "Pediatrics".equalsIgnoreCase(speciality)
                        ? "बच्चों के डॉक्टर" : speciality + " विशेषज्ञ";
                if (!StringUtils.hasText(speciality)) {
                    return "डॉ. " + doctor.doctorDisplayName() + " उपलब्ध हैं।";
                }
                return "डॉ. " + doctor.doctorDisplayName() + " " + role + " के रूप में उपलब्ध हैं।";
            }
            String names = doctors.stream()
                    .limit(3)
                    .filter(PublicDoctorSummaryResponse.class::isInstance)
                    .map(PublicDoctorSummaryResponse.class::cast)
                    .map(PublicDoctorSummaryResponse::doctorDisplayName)
                    .reduce((left, right) -> left + " और " + right)
                    .orElse(null);
            if (english && StringUtils.hasText(names)) {
                String englishNames = doctors.stream().limit(3)
                        .filter(PublicDoctorSummaryResponse.class::isInstance)
                        .map(PublicDoctorSummaryResponse.class::cast)
                        .map(PublicDoctorSummaryResponse::doctorDisplayName)
                        .reduce((left, right) -> left + " and " + right).orElse(names);
                return "Available doctors include " + englishNames + ".";
            }
            if (hinglish && StringUtils.hasText(names)) return "Available doctors hain: "
                    + names.replace(" और ", " aur ") + ".";
            if (!StringUtils.hasText(speciality) && StringUtils.hasText(names)) {
                return "उपलब्ध डॉक्टर हैं: " + names + "।";
            }
        }
        return "मुझे " + Math.min(result.candidateCount(), 3) + " डॉक्टर मिले हैं। कृपया बताइए, क्या मैं उपलब्ध समय भी देखूँ?";
    }

    private String englishSpecialty(String specialty) {
        return "Pediatrics".equalsIgnoreCase(specialty) ? "pediatrician" : specialty.toLowerCase(java.util.Locale.ROOT) + " specialist";
    }

    private String noScheduleResponse(VoiceAvailabilityData data, boolean hindi, boolean hinglish) {
        if (data.requestedDaypart() == null) {
            return hindi ? hinglish ? "Dr. " + data.doctorName() + " us din consultation nahi karte."
                    : "डॉ. " + data.doctorName() + " उस दिन consultation नहीं करते।"
                    : "Dr. " + data.doctorName() + " does not consult that day.";
        }
        String period = daypartName(data.requestedDaypart(), hindi, hinglish);
        return hindi ? hinglish ? "Dr. " + data.doctorName() + " us din " + period + " mein consult nahi karte."
                : "डॉ. " + data.doctorName() + " उस दिन " + period + " में consultation नहीं करते।"
                : "Dr. " + data.doctorName() + " does not consult in the " + period + " that day.";
    }

    private String summarizeResult(String tool, HealthcareAiReadOnlyToolGateway.ToolResult result) {
        if (result.candidateCount() == 0) return "sourceCount=" + result.sourceCandidateCount() + "; filteredCount=0";
        if (!(result.value() instanceof java.util.List<?> values)) return "count=" + result.candidateCount();
        String summary = values.stream().limit(3).map(value -> {
            if (value instanceof PublicDoctorSummaryResponse doctor) {
                return doctor.doctorDisplayName() + " / " + doctor.speciality();
            }
            return String.valueOf(value);
        }).reduce((left, right) -> left + "; " + right).orElse("count=" + result.candidateCount());
        return "sourceCount=" + result.sourceCandidateCount() + "; filteredCount=" + result.candidateCount()
                + "; " + summary;
    }

    private String safeReason(RuntimeException ex) {
        return ex.getClass().getSimpleName();
    }

    public record Context(
            String providerCallId,
            UUID interactionId,
            UUID tenantId,
            UUID communicationChannelId,
            UUID clinicId,
            UUID locationId,
            String language,
            String patientIdentificationStatus,
            String conversationPartyType,
            UUID identifiedPatientId
    ) {
        public Context {
            if (!"SINGLE_MATCH".equals(patientIdentificationStatus)) identifiedPatientId = null;
            if (conversationPartyType == null) conversationPartyType = "GUEST_CALLER";
        }
    }

    public record TurnResult(String response, String intent, String tool, boolean aivaUsed,
                             boolean mutationBlocked, boolean toolExecuted, String toolInputSpeciality,
                             int toolResultCount, String toolResultSummary, boolean closing) {
        public TurnResult(String response, String intent, String tool, boolean aivaUsed,
                          boolean mutationBlocked, boolean toolExecuted) {
            this(response, intent, tool, aivaUsed, mutationBlocked, toolExecuted, null, 0, null, false);
        }

        public TurnResult(String response, String intent, String tool, boolean aivaUsed,
                          boolean mutationBlocked, boolean toolExecuted, String toolInputSpeciality,
                          int toolResultCount, String toolResultSummary) {
            this(response, intent, tool, aivaUsed, mutationBlocked, toolExecuted,
                    toolInputSpeciality, toolResultCount, toolResultSummary, false);
        }
        public String policyDecision() {
            return mutationBlocked ? "BLOCKED_READ_ONLY" : "ALLOWED_READ_ONLY";
        }

        public String toolExecutionStatus() {
            if (mutationBlocked) return "BLOCKED";
            if (tool == null) return "NOT_EXECUTED";
            return toolExecuted ? "EXECUTED" : "FAILED";
        }
    }

}
