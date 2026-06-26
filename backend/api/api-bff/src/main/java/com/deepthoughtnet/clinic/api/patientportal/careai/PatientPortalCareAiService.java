package com.deepthoughtnet.clinic.api.patientportal.careai;

import com.deepthoughtnet.clinic.api.careai.CareAiTaskNotificationService;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentConfirmationResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentBookingRequest;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorSlotResponse;
import com.deepthoughtnet.clinic.api.publicsite.PublicCatalogFacade;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiChannel;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiConversationSessionSnapshot;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiConversationPersistenceService;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiConversationStatus;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiConversationTurnCommand;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiTransport;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiWorkflowSnapshot;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiWorkflowState;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiWorkflowType;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskCreateCommand;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskPriority;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskService;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskType;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PatientPortalCareAiService {
    private static final ObjectMapper CARE_AI_JSON = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> JSON_MAP_TYPE = new TypeReference<>() { };
    private static final Logger log = LoggerFactory.getLogger(PatientPortalCareAiService.class);
    private static final String INSTANCE_ID = buildInstanceId();
    private static final Pattern ENGLISH_DOCTOR_PATTERN = Pattern.compile("(?i)\\b(?:dr\\.?|doctor)\\s+([A-Za-z][A-Za-z .'-]{1,60})");
    private static final Pattern HINDI_DOCTOR_PATTERN = Pattern.compile("(?:डॉक्टर|डॉ\\.?)([^,.!?]+)");
    private static final Pattern LETTER_ONLY_TOKEN_PATTERN = Pattern.compile("[\\p{L}]{2,}");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern DMY_DATE_PATTERN = Pattern.compile("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+([A-Za-z]{3,9})\\s+(\\d{4})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MDY_DATE_PATTERN = Pattern.compile("\\b([A-Za-z]{3,9})\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,)?\\s+(\\d{4})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DMY_DATE_WITHOUT_YEAR_PATTERN = Pattern.compile("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+([A-Za-z]{3,9})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MDY_DATE_WITHOUT_YEAR_PATTERN = Pattern.compile("\\b([A-Za-z]{3,9})\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SLASH_DATE_PATTERN = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b");
    private static final Pattern EXPLICIT_TIME_PATTERN = Pattern.compile("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\b(\\d{1,2})\\b");
    private static final DateTimeFormatter STRICT_ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT);

    private static final List<String> POSITIVE_CONFIRMATIONS = List.of("yes", "confirm", "book it", "go ahead", "that's fine", "okay", "ok", "yes please");
    private static final List<String> POSITIVE_CONFIRMATIONS_HI = List.of("हाँ", "हां", "ठीक है", "बुक कर दीजिए", "कन्फर्म", "सही है");
    private static final List<String> NEGATIVE_CONFIRMATIONS = List.of("no", "another slot", "different slot", "different time", "change slot", "not this one");
    private static final List<String> NEGATIVE_CONFIRMATIONS_HI = List.of("नहीं", "दूसरा स्लॉट", "दूसरा समय", "दूसरे समय");
    private static final List<String> GREETING_KEYWORDS = List.of("hello", "hi", "hey", "good morning", "good afternoon", "good evening");
    private static final List<String> THANK_YOU_KEYWORDS = List.of("thank you", "thanks", "thankyou", "thank u");
    private static final List<String> GOODBYE_KEYWORDS = List.of("bye", "goodbye", "see you", "take care", "have a nice day", "have nice day");
    private static final List<String> BOOKING_INTENT_KEYWORDS = List.of("book appointment", "book", "need doctor", "want consultation", "schedule");
    private static final List<String> RESCHEDULE_INTENT_KEYWORDS = List.of("reschedule", "change my appointment", "move my appointment", "change appointment");
    private static final List<String> CANCEL_INTENT_KEYWORDS = List.of("cancel appointment", "cancel my appointment", "remove booking", "cancel booking");
    private static final List<String> STATUS_INTENT_KEYWORDS = List.of(
            "check my appointment",
            "check my bookings",
            "show my appointments",
            "when is my appointment",
            "when is my next appointment",
            "do i have upcoming appointments",
            "my appointments",
            "upcoming appointments",
            "appointment list",
            "show my bookings",
            "show appointments",
            "appointment status",
            "next appointment"
    );
    private static final List<String> HUMAN_HANDOFF_KEYWORDS = List.of(
            "talk to receptionist",
            "connect me to staff",
            "talk to human",
            "speak to someone",
            "transfer me",
            "i need help",
            "this is not working",
            "call receptionist",
            "please connect to clinic",
            "connect to clinic"
    );
    private static final List<String> CALLBACK_REQUEST_KEYWORDS = List.of(
            "call me back",
            "please call me",
            "call me tomorrow",
            "ask receptionist to call me",
            "doctor unavailable call me later",
            "doctor unavailable, call me later",
            "schedule callback",
            "callback in evening",
            "call me later"
    );
    private static final List<String> TOPIC_SWITCH_KEYWORDS = List.of(
            "switch topic",
            "switch the conversation",
            "change topic",
            "change conversation",
            "change the conversation",
            "cancel this",
            "cancel this flow",
            "start over",
            "forget booking",
            "talk about something else",
            "let's talk about something else",
            "leave this",
            "stop this",
            "stop this booking"
    );
    private static final List<String> TOPIC_SWITCH_KEYWORDS_HI = List.of("विषय बदलें", "बुकिंग भूल जाओ", "शुरू से", "कुछ और बात");
    private static final List<String> CLINIC_TIMING_KEYWORDS = List.of("clinic timing", "clinic timings", "timing", "timings", "hours", "open", "opening time");
    private static final List<String> DOCTOR_AVAILABILITY_KEYWORDS = List.of("availability", "available", "free slot", "slots", "doctor availability");
    private static final List<String> AMBIGUOUS_CANCEL_KEYWORDS = List.of("cancel that", "cancel it", "actually cancel it", "forget it", "don't book that");
    private static final List<String> SLOT_RERENDER_KEYWORDS = List.of(
            "i can't see the options",
            "i cannot see the options",
            "can't see the options",
            "cannot see the options",
            "provide options",
            "show options again",
            "show the options again",
            "repeat options",
            "repeat the options"
    );
    private static final List<String> SLOT_MORE_KEYWORDS = List.of(
            "more slots",
            "show more",
            "show me more slot",
            "show me more slots",
            "any other slots",
            "other slots"
    );
    private static final List<String> NEW_PATIENT_KEYWORDS = List.of("new patient", "first time patient", "first-time patient");
    private static final List<String> NEW_PATIENT_KEYWORDS_HI = List.of("नया मरीज", "पहली बार", "पहली दफ़ा");
    private static final List<String> EMERGENCY_KEYWORDS = List.of("chest pain", "difficulty breathing", "severe bleeding", "unconscious", "stroke", "suicidal");
    private static final List<String> EMERGENCY_KEYWORDS_HI = List.of("सीने में दर्द", "सांस लेने में दिक्कत", "ज़्यादा खून", "बेहोश", "स्ट्रोक", "आत्महत्या");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH);
    private static final int SLOT_PAGE_SIZE = 3;
    private static final Map<String, Month> MONTH_NAME_MAP = monthNameMap();
    private final PatientPortalService patientPortalService;
    private final ClinicTimeZoneResolver clinicTimeZoneResolver;
    private final PatientPortalCareAiPlanner planner;
    private final CareAiConversationPersistenceService conversationPersistenceService;
    private final CareAiReceptionistTaskService receptionistTaskService;
    private final CareAiTaskNotificationService taskNotificationService;
    private final PublicCatalogFacade publicCatalogFacade;
    private final PatientPortalCareAiBusinessLookupService businessLookupService;
    private final PatientPortalCareAiIntentRegistry intentRegistry;
    private final PatientPortalCareAiWorkflowRegistry workflowRegistry;
    private final PatientPortalCareAiWorkflowRouter workflowRouter;
    private final Map<SessionKey, CareAiState> sessions = new ConcurrentHashMap<>();

    @Autowired
    public PatientPortalCareAiService(
            PatientPortalService patientPortalService,
            ClinicTimeZoneResolver clinicTimeZoneResolver,
            PatientPortalCareAiPlanner planner,
            CareAiConversationPersistenceService conversationPersistenceService,
            CareAiReceptionistTaskService receptionistTaskService,
            CareAiTaskNotificationService taskNotificationService,
            PublicCatalogFacade publicCatalogFacade
    ) {
        this.patientPortalService = patientPortalService;
        this.clinicTimeZoneResolver = clinicTimeZoneResolver;
        this.planner = planner;
        this.conversationPersistenceService = conversationPersistenceService;
        this.receptionistTaskService = receptionistTaskService;
        this.taskNotificationService = taskNotificationService;
        this.publicCatalogFacade = publicCatalogFacade;
        this.businessLookupService = new PatientPortalCareAiBusinessLookupService(patientPortalService, publicCatalogFacade);
        this.intentRegistry = new PatientPortalCareAiIntentRegistry();
        this.workflowRegistry = new PatientPortalCareAiWorkflowRegistry();
        this.workflowRouter = new PatientPortalCareAiWorkflowRouter(intentRegistry, workflowRegistry);
    }

    public PatientPortalCareAiService(
            PatientPortalService patientPortalService,
            ClinicTimeZoneResolver clinicTimeZoneResolver,
            PatientPortalCareAiPlanner planner,
            CareAiConversationPersistenceService conversationPersistenceService,
            CareAiReceptionistTaskService receptionistTaskService,
            CareAiTaskNotificationService taskNotificationService
    ) {
        this(
                patientPortalService,
                clinicTimeZoneResolver,
                planner,
                conversationPersistenceService,
                receptionistTaskService,
                taskNotificationService,
                null
        );
    }

    public PatientPortalCareAiMessageResponse message(PatientPortalCareAiMessageRequest request) {
        return messageInternal(
                request,
                CareAiChannel.PATIENT_PORTAL_CHAT,
                currentChatExternalSessionId(),
                patientPortalService.currentPatientId(),
                CareAiTransport.HTTP_CHAT
        );
    }

    public PatientPortalCareAiMessageResponse messageFromVoice(PatientPortalCareAiMessageRequest request) {
        return messageInternal(
                request,
                CareAiChannel.PATIENT_PORTAL_VOICE,
                RequestContextHolder.require().correlationId(),
                patientPortalService.currentPatientId(),
                CareAiTransport.WEBSOCKET_PATIENT_PORTAL
        );
    }

    private PatientPortalCareAiMessageResponse messageInternal(
            PatientPortalCareAiMessageRequest request,
            CareAiChannel channel,
            String externalSessionId,
            UUID patientId,
            CareAiTransport transport
    ) {
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new IllegalArgumentException("Message is required");
        }

        SessionKey sessionKey = currentSessionKey();
        CareAiState state = sessions.computeIfAbsent(sessionKey, key -> new CareAiState());
        clearLookupCaches(state);
        String message = request.message().trim();
        hydrateStateFromPersistence(state, channel, patientId, externalSessionId);
        state.lastChannel = channel;
        state.lastExternalSessionId = externalSessionId;
        state.lastPatientId = patientId;
        state.lastTransport = transport;
        state.lastUserMessage = message;
        state.language = normalizeLanguage(request.language(), message, state.language);
        if (log.isDebugEnabled()) {
            log.debug(
                    "careai.turn.begin source=web-public-patient-careai conversationTenantId={} tenantContextTenantId={} patientPortalSessionId={} patientId={} patientMobile={} channel={} transport={} userText={}",
                    RequestContextHolder.requireTenantId(),
                    RequestContextHolder.get() == null ? null : RequestContextHolder.get().tenantId().value(),
                    externalSessionId,
                    patientId,
                    patientPortalService.currentPatientMobile(),
                    channel,
                    transport,
                    trimToLength(message, 160)
            );
        }
        markAnsweredFacts(state);
        String detectedTimePreference = findPreferredTimeWindow(message, state.language, state);
        careAiTrace("messageInternal", "received", state,
                "channel=" + channel
                        + " transport=" + transport
                        + " userText=" + trimToLength(message, 160)
                        + " detectedTimePreference=" + detectedTimePreference);
        log.info(
                "careai.turn.received conversationTenantId={} patientPortalSessionId={} patientId={} patientMobile={} userText={} activeWorkflow={} lastQuestionKey={} escalationReason={} topicClassification={} detectedTimePreference={} preferredTimeWindow={} answeredTimePreference={} voiceChannel={}",
                RequestContextHolder.requireTenantId(),
                externalSessionId,
                patientId,
                patientPortalService.currentPatientMobile(),
                trimToLength(message, 160),
                state.currentIntent == null ? null : state.currentIntent.name(),
                state.lastQuestionKey,
                state.handoffReason,
                null,
                detectedTimePreference,
                state.preferredTimeWindow,
                state.answeredTimePreference,
                channel
        );

        if (containsEmergency(message, state.language)) {
            prepareEscalationResponse(state, message, "emergency-symptoms", emergencyPriority(message));
            return response(state, emergencyPrompt(state.language));
        }
        if (state.awaitingFreshConfirmation && isPositiveConfirmation(message) && !state.confirmationPending) {
            state.awaitingFreshConfirmation = false;
            return response(state, reconfirmationPrompt(state));
        }
        if (isGreetingOnly(message, state.language) && state.currentIntent == null && !state.actionCompleted) {
            return response(state, greetingPrompt(state.language));
        }
        if (state.actionCompleted && state.currentIntent == null && isPostCompletionCourtesy(message, state.language)) {
            return response(state, postCompletionCourtesyPrompt(state.language));
        }
        if (isNewPatientIntent(message, state.language)) {
            return response(state, newPatientPrompt(state.language));
        }
        if (detectCallbackRequest(message, state.language)) {
            return handleCallbackRequest(state, message);
        }
        if (detectHumanHandoffRequest(message, state.language)) {
            return handleHumanHandoffRequest(state, message);
        }
        PatientPortalCareAiPlannerDecision plannerDecision = shouldUsePlanner(state, message)
                ? planner.plan(buildPlanningContext(state, message))
                : null;
        PatientPortalCareAiIntent classifiedIntent = classifyIntent(state, message, plannerDecision);
        PatientPortalCareAiWorkflowRouteDecision workflowRoute = workflowRouter.route(
                state.currentIntent,
                state,
                classifiedIntent,
                message
        );
        log.info(
                "CAREAI_TRACE_WORKFLOW_ROUTER previousWorkflow={} intent={} targetWorkflow={} shouldSwitch={} reset={} reason={}",
                state.currentIntent == null ? null : state.currentIntent.name(),
                classifiedIntent == null ? null : classifiedIntent.name(),
                workflowRoute.targetWorkflow() == null ? null : workflowRoute.targetWorkflow().name(),
                workflowRoute.shouldSwitch(),
                workflowRoute.shouldResetState(),
                workflowRoute.reason()
        );
        if (classifiedIntent == PatientPortalCareAiIntent.RESET_CONVERSATION) {
            clearCurrentConversation(state);
            return response(state, topicSwitchPrompt(state.language));
        }
        if (workflowRoute.shouldSwitch() && workflowRoute.targetWorkflow() != null) {
            if (state.currentIntent != null && PatientPortalCareAiIntent.normalize(state.currentIntent) != workflowRoute.targetWorkflow()) {
                queueWorkflowEvent(state, "TOPIC_SWITCHED", workflowContextJson(state));
            }
            transitionWorkflow(state, workflowRoute.targetWorkflow(), workflowRoute.reason());
        }
        boolean explicitWorkflowIntent = classifiedIntent != null
                && PatientPortalCareAiIntent.normalize(classifiedIntent) != null
                && PatientPortalCareAiIntent.normalize(classifiedIntent).isWorkflowIntent();
        careAiTrace("classifyTopic", "enter", state,
                "userText=" + trimToLength(message, 160)
                        + " currentWorkflow=" + state.currentIntent
                        + " classifiedIntent=" + classifiedIntent
                        + " lastQuestionKey=" + state.lastQuestionKey);
        CareAiTopicClassification topicClassification = classifyTopic(state, message, classifiedIntent);
        if (plannerDecision != null && StringUtils.hasText(plannerDecision.sideTopic())) {
            topicClassification = CareAiTopicClassification.SIDE_QUESTION;
        }
        careAiTrace("classifyTopic", "exit", state,
                "topicClassification=" + topicClassification
                        + " currentWorkflow=" + state.currentIntent
                        + " classifiedIntent=" + classifiedIntent
                        + " lastQuestionKey=" + state.lastQuestionKey);
        log.info(
                "careai.turn.classified conversationTenantId={} patientPortalSessionId={} patientId={} patientMobile={} userText={} activeWorkflow={} lastQuestionKey={} escalationReason={} topicClassification={} detectedTimePreference={}",
                RequestContextHolder.requireTenantId(),
                externalSessionId,
                patientId,
                patientPortalService.currentPatientMobile(),
                trimToLength(message, 160),
                state.currentIntent == null ? null : state.currentIntent.name(),
                state.lastQuestionKey,
                state.handoffReason,
                topicClassification,
                detectedTimePreference
        );
        if (state.lastTopicClassification == CareAiTopicClassification.SIDE_QUESTION
                && topicClassification == CareAiTopicClassification.ACTIVE_WORKFLOW_CONTINUATION) {
            queueWorkflowEvent(state, "WORKFLOW_RESUMED", workflowContextJson(state));
            state.lastSideTopic = null;
        }
        if (!explicitWorkflowIntent && wantsTopicSwitch(message, state.language)) {
            if (state.currentIntent != null) {
                queueWorkflowEvent(state, "TOPIC_SWITCH_REQUESTED", workflowContextJson(state));
                return response(state, topicSwitchClarificationPrompt(state.language));
            }
            clearCurrentConversation(state);
            return response(state, topicSwitchPrompt(state.language));
        }
        if (!explicitWorkflowIntent && plannerDecision != null && plannerDecision.topicSwitch()) {
            if (state.currentIntent != null) {
                queueWorkflowEvent(state, "TOPIC_SWITCH_REQUESTED", workflowContextJson(state));
                return response(state, topicSwitchClarificationPrompt(state.language));
            }
            clearCurrentConversation(state);
            return response(state, topicSwitchPrompt(state.language));
        }
        if (!explicitWorkflowIntent && topicClassification == CareAiTopicClassification.AMBIGUOUS_CANCEL) {
            queueWorkflowEvent(state, "AMBIGUOUS_CANCEL_DETECTED", workflowContextJson(state));
            return response(state, ambiguousCancelPrompt(state));
        }
        if (!explicitWorkflowIntent && topicClassification == CareAiTopicClassification.CANCEL_CURRENT_WORKFLOW) {
            queueWorkflowEvent(state, "WORKFLOW_SUSPEND_REQUESTED", workflowContextJson(state));
            return response(state, topicSwitchClarificationPrompt(state.language));
        }
        if (topicClassification == CareAiTopicClassification.SIDE_QUESTION) {
            state.suspendedIntent = state.currentIntent == null ? null : state.currentIntent.name();
            state.lastTopicClassification = CareAiTopicClassification.SIDE_QUESTION;
            queueWorkflowEvent(state, "WORKFLOW_SUSPENDED", workflowContextJson(state));
            return response(state, sideTopicResponse(state, message, plannerDecision));
        }
        if (!workflowRoute.shouldSwitch()
                && (topicClassification == CareAiTopicClassification.NEW_WORKFLOW
                || topicClassification == CareAiTopicClassification.CANCEL_EXISTING_APPOINTMENT)) {
            queueWorkflowEvent(state, "TOPIC_SWITCHED", workflowContextJson(state));
        }

        careAiTrace("applyIntent", "enter", state,
                "userText=" + trimToLength(message, 160)
                        + " plannerIntent=" + (plannerDecision == null ? null : plannerDecision.intent())
                        + " plannerDoctor=" + (plannerDecision == null ? null : plannerDecision.doctorName())
                        + " plannerSpeciality=" + (plannerDecision == null ? null : plannerDecision.speciality()));
        boolean progressed = applyIntent(state, message, plannerDecision, classifiedIntent);
        careAiTrace("applyIntent", "exit", state,
                "progressed=" + progressed
                        + " currentWorkflow=" + state.currentIntent
                        + " selectedDoctorId=" + state.selectedDoctorId
                        + " selectedClinicId=" + state.selectedClinicId
                        + " selectedTenantId=" + state.selectedTenantId
                        + " selectedClinicSlug=" + state.selectedClinicSlug
                        + " selectedAppointmentId=" + state.selectedAppointmentId);
        if (state.confirmationPending && isPositiveConfirmation(message)) {
            return executeConfirmedAction(state);
        }
        if (state.confirmationPending && isNegativeConfirmation(message)) {
            progressed = clearPendingAction(state, true);
        }
        if (state.confirmationPending && !isSelectionOnlyMessage(message)) {
            if (plannerDecision != null
                    && plannerDecision.confirmationDecision() == PatientPortalCareAiPlannerConfirmationDecision.CONFIRM) {
                return executeConfirmedAction(state);
            }
            if (plannerDecision != null
                    && plannerDecision.confirmationDecision() == PatientPortalCareAiPlannerConfirmationDecision.REJECT) {
                progressed = clearPendingAction(state, true);
            }
        }

        String reply = routeConversation(state, message);
        String nextPromptKey = inferQuestionKey(state, reply);
        log.info(
                "careai.turn.next-prompt conversationTenantId={} patientPortalSessionId={} patientId={} patientMobile={} userText={} activeWorkflow={} lastQuestionKey={} escalationReason={} topicClassification={} detectedTimePreference={} nextPromptKey={} repeatedQuestionCount={}",
                RequestContextHolder.requireTenantId(),
                externalSessionId,
                patientId,
                patientPortalService.currentPatientMobile(),
                trimToLength(message, 160),
                state.currentIntent == null ? null : state.currentIntent.name(),
                state.lastQuestionKey,
                state.handoffReason,
                topicClassification,
                detectedTimePreference,
                nextPromptKey,
                state.repeatedQuestionCount
        );
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

    private void transitionWorkflow(CareAiState state, PatientPortalCareAiIntent nextIntent, String reason) {
        PatientPortalCareAiIntent previousIntent = state.currentIntent;
        boolean staleStateCleared = previousIntent != nextIntent
                || state.handoffRequired
                || state.confirmationPending
                || StringUtils.hasText(state.selectedSlot)
                || StringUtils.hasText(state.selectedAppointmentId)
                || StringUtils.hasText(state.selectedDoctorId);
        careAiTrace("transitionWorkflow", "enter", state,
                "previousWorkflow=" + (previousIntent == null ? null : previousIntent.name())
                        + " newWorkflow=" + (nextIntent == null ? null : nextIntent.name())
                        + " reason=" + reason
                        + " staleStateCleared=" + staleStateCleared);
        if (log.isDebugEnabled()) {
            log.debug(
                    "careai.workflow.transition conversationTenantId={} patientPortalSessionId={} patientId={} patientMobile={} previousWorkflow={} newWorkflow={} reason={} staleStateCleared={}",
                    RequestContextHolder.requireTenantId(),
                    state.lastExternalSessionId,
                    state.lastPatientId,
                    patientPortalService.currentPatientMobile(),
                    previousIntent == null ? null : previousIntent.name(),
                    nextIntent == null ? null : nextIntent.name(),
                    reason,
                    staleStateCleared
            );
        }
        invalidatePendingConfirmation(state, reason);
        resetWorkflowState(state, nextIntent);
        careAiTrace("transitionWorkflow", "exit", state,
                "previousWorkflow=" + (previousIntent == null ? null : previousIntent.name())
                        + " newWorkflow=" + (state.currentIntent == null ? null : state.currentIntent.name())
                        + " reason=" + reason);
    }

    public PatientPortalCareAiResetResponse reset() {
        sessions.remove(currentSessionKey());
        conversationPersistenceService.safeCloseConversation(
                RequestContextHolder.requireTenantId(),
                CareAiChannel.PATIENT_PORTAL_CHAT,
                patientPortalService.currentPatientId(),
                currentChatExternalSessionId(),
                CareAiConversationStatus.CANCELLED,
                "AIVA booking context cleared."
        );
        return new PatientPortalCareAiResetResponse(true, "AIVA booking context cleared.");
    }

    public List<Map<String, Object>> debugDoctorLookup(String query) {
        CareAiState state = currentState();
        List<DoctorChoice> careAiDoctors = lookupDoctors(state, query, null);
        int publicCatalogCount = publicCatalogFacade == null ? -1 : publicCatalogFacade.listDoctors(query, null, null, null, null, null, 0, 24).items().size();
        careAiTrace("CAREAI_TRACE_DOCTOR_COMPARE", "enter", state,
                "publicCatalogCount=" + publicCatalogCount
                        + " careAiDoctorCount=" + careAiDoctors.size()
                        + " query=" + query);
        return careAiDoctors.stream()
                .map(this::doctorDebugMap)
                .toList();
    }

    public List<Map<String, Object>> debugAppointmentLookup() {
        CareAiState state = currentState();
        List<PatientPortalCareAiAppointmentOption> patientPortalAppointments = patientPortalService.debugAppointments();
        List<PatientPortalCareAiAppointmentOption> careAiAppointments = businessLookupService.upcomingAppointments();
        careAiTrace("CAREAI_TRACE_APPOINTMENT_COMPARE", "enter", state,
                "patientPortalAppointmentsCount=" + patientPortalAppointments.size()
                        + " careAiAppointmentsCount=" + careAiAppointments.size());
        return careAiAppointments.stream()
                .map(this::appointmentDebugMap)
                .toList();
    }

    public Map<String, Object> debugActiveConversation() {
        CareAiState state = currentState();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workflow", state.currentIntent == null ? null : state.currentIntent.name());
        result.put("lastQuestionKey", state.lastQuestionKey);
        result.put("selectedDoctorId", state.selectedDoctorId);
        result.put("selectedClinicId", state.selectedClinicId);
        result.put("selectedTenantId", state.selectedTenantId);
        result.put("selectedAppointmentId", state.selectedAppointmentId);
        return result;
    }

    private String routeConversation(CareAiState state, String message) {
        if (state.currentIntent == null) {
            return askIntentPrompt(state.language);
        }
        return switch (state.currentIntent) {
            case BOOK_APPOINTMENT -> handleBooking(state, message);
            case RESCHEDULE_APPOINTMENT -> handleReschedule(state, message);
            case CANCEL_APPOINTMENT -> handleCancellation(state, message);
            case CHECK_APPOINTMENT, APPOINTMENT_STATUS -> handleStatus(state, message);
            default -> askIntentPrompt(state.language);
        };
    }

    private boolean applyIntent(CareAiState state,
                                String message,
                                PatientPortalCareAiPlannerDecision plannerDecision,
                                PatientPortalCareAiIntent classifiedIntent) {
        careAiTrace("applyIntent", "enter", state,
                "message=" + trimToLength(message, 160)
                        + " currentWorkflow=" + state.currentIntent
                        + " lastQuestionKey=" + state.lastQuestionKey);
        PatientPortalCareAiIntent detectedIntent = PatientPortalCareAiIntent.normalize(classifiedIntent);
        boolean changed = false;
        if (detectedIntent != null
                && detectedIntent.isWorkflowIntent()
                && detectedIntent != PatientPortalCareAiIntent.normalize(state.currentIntent)) {
            transitionWorkflow(state, detectedIntent, "intent-changed");
            changed = true;
        }
        if (state.currentIntent == PatientPortalCareAiIntent.BOOK_APPOINTMENT) {
            changed = applyBookingFacts(state, message) || changed;
            changed = applyPlannerBookingFacts(state, plannerDecision) || changed;
        } else if (state.currentIntent == PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT) {
            changed = applyRescheduleFacts(state, message) || changed;
            changed = applyPlannerRescheduleFacts(state, plannerDecision) || changed;
        } else if (state.currentIntent == PatientPortalCareAiIntent.CANCEL_APPOINTMENT) {
            changed = applyAppointmentSelectionFacts(state, message) || changed;
        }
        careAiTrace("applyIntent", "exit", state,
                "detectedIntent=" + detectedIntent
                        + " changed=" + changed
                        + " currentWorkflow=" + state.currentIntent
                        + " selectedDoctorId=" + state.selectedDoctorId
                        + " selectedAppointmentId=" + state.selectedAppointmentId);
        return changed;
    }

    private boolean applyBookingFacts(CareAiState state, String message) {
        careAiTrace("applyBookingFacts", "enter", state,
                "message=" + trimToLength(message, 160)
                        + " requestedDoctorName=" + state.requestedDoctorName
                        + " requestedSpeciality=" + state.requestedSpeciality
                        + " selectedDoctorId=" + state.selectedDoctorId
                        + " selectedClinicSlug=" + state.selectedClinicSlug
                        + " selectedTenantId=" + state.selectedTenantId);
        boolean changed = false;
        boolean selectionOnlyMessage = isSelectionOnlyMessage(message);
        String detectedDate = null;
        String normalizedDate = null;

        String doctorName = findRequestedDoctorName(message, state.language);
        if (!StringUtils.hasText(doctorName)) {
            doctorName = findDoctorNameFromFreeText(message);
        }
        if (StringUtils.hasText(doctorName) && !doctorName.equalsIgnoreCase(state.requestedDoctorName)) {
            invalidatePendingConfirmation(state, "doctor-changed");
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

        String preferredTimeWindow = findPreferredTimeWindow(message, state.language, state);
        if (StringUtils.hasText(preferredTimeWindow) && !preferredTimeWindow.equalsIgnoreCase(state.preferredTimeWindow)) {
            invalidatePendingConfirmation(state, "time-preference-changed");
            state.preferredTimeWindow = preferredTimeWindow;
            state.timePromptCount = 0;
            clearSlotSelection(state);
            changed = true;
        }

        if (!selectionOnlyMessage) {
            DateResolution preferredDate = findPreferredDate(message, state.language);
            detectedDate = preferredDate.date() != null || preferredDate.issue() != null ? trimToLength(message, 160) : null;
            normalizedDate = preferredDate.date();
            if (preferredDate.issue() != null) {
                state.dateResolutionIssue = preferredDate.issue();
                state.preferredDate = null;
                state.preferredDateExplicit = false;
                clearSlotSelection(state);
                changed = true;
            } else if (StringUtils.hasText(preferredDate.date()) && !preferredDate.date().equals(state.preferredDate)) {
                invalidatePendingConfirmation(state, "date-changed");
                state.dateResolutionIssue = null;
                state.preferredDate = preferredDate.date();
                state.preferredDateExplicit = preferredDate.explicit();
                clearSlotSelection(state);
                changed = true;
            }
        }
        log.info(
                "careai.date-resolution userText={} detectedDate={} normalizedDate={} answeredState.date={} nextQuestion={}",
                trimToLength(message, 160),
                detectedDate,
                normalizedDate,
                StringUtils.hasText(state.preferredDate),
                previewNextQuestionAfterDate(state)
        );

        String reason = findReason(message, state.language);
        if (StringUtils.hasText(reason) && !reason.equalsIgnoreCase(state.reason)) {
            state.reason = reason;
            changed = true;
        }
        careAiTrace("applyBookingFacts", "exit", state,
                "changed=" + changed
                        + " requestedDoctorName=" + state.requestedDoctorName
                        + " requestedSpeciality=" + state.requestedSpeciality
                        + " preferredDate=" + state.preferredDate
                        + " preferredTimeWindow=" + state.preferredTimeWindow
                        + " selectedDoctorId=" + state.selectedDoctorId
                        + " selectedClinicSlug=" + state.selectedClinicSlug
                        + " selectedTenantId=" + state.selectedTenantId);
        return changed;
    }

    private boolean applyRescheduleFacts(CareAiState state, String message) {
        careAiTrace("applyRescheduleFacts", "enter", state,
                "message=" + trimToLength(message, 160)
                        + " selectedAppointmentId=" + state.selectedAppointmentId
                        + " preferredDate=" + state.preferredDate
                        + " preferredTimeWindow=" + state.preferredTimeWindow
                        + " selectedTenantId=" + state.selectedTenantId);
        boolean changed = applyAppointmentSelectionFacts(state, message);
        boolean selectionOnlyMessage = isSelectionOnlyMessage(message);
        String detectedDate = null;
        String normalizedDate = null;

        String preferredTimeWindow = findPreferredTimeWindow(message, state.language, state);
        if (StringUtils.hasText(preferredTimeWindow) && !preferredTimeWindow.equalsIgnoreCase(state.preferredTimeWindow)) {
            invalidatePendingConfirmation(state, "time-preference-changed");
            state.preferredTimeWindow = preferredTimeWindow;
            state.timePromptCount = 0;
            clearSlotSelection(state);
            changed = true;
        }

        if (!selectionOnlyMessage) {
            DateResolution preferredDate = findPreferredDate(message, state.language);
            detectedDate = preferredDate.date() != null || preferredDate.issue() != null ? trimToLength(message, 160) : null;
            normalizedDate = preferredDate.date();
            if (preferredDate.issue() != null) {
                state.dateResolutionIssue = preferredDate.issue();
                state.preferredDate = null;
                clearSlotSelection(state);
                changed = true;
            } else if (StringUtils.hasText(preferredDate.date()) && !preferredDate.date().equals(state.preferredDate)) {
                invalidatePendingConfirmation(state, "date-changed");
                state.dateResolutionIssue = null;
                state.preferredDate = preferredDate.date();
                clearSlotSelection(state);
                changed = true;
            }
        }
        log.info(
                "careai.date-resolution userText={} detectedDate={} normalizedDate={} answeredState.date={} nextQuestion={}",
                trimToLength(message, 160),
                detectedDate,
                normalizedDate,
                StringUtils.hasText(state.preferredDate),
                previewNextQuestionAfterDate(state)
        );
        careAiTrace("applyRescheduleFacts", "exit", state,
                "changed=" + changed
                        + " selectedAppointmentId=" + state.selectedAppointmentId
                        + " preferredDate=" + state.preferredDate
                        + " preferredTimeWindow=" + state.preferredTimeWindow
                        + " selectedSlot=" + state.selectedSlot
                        + " selectedTenantId=" + state.selectedTenantId);
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

    private boolean applyPlannerBookingFacts(CareAiState state, PatientPortalCareAiPlannerDecision plannerDecision) {
        if (plannerDecision == null) {
            return false;
        }
        boolean changed = false;
        if (!StringUtils.hasText(state.requestedDoctorName) && StringUtils.hasText(plannerDecision.doctorName())) {
            state.requestedDoctorName = plannerDecision.doctorName();
            clearDoctorSelection(state);
            changed = true;
        }
        if (!StringUtils.hasText(state.requestedSpeciality) && StringUtils.hasText(plannerDecision.speciality())) {
            state.requestedSpeciality = plannerDecision.speciality();
            if (!StringUtils.hasText(state.requestedDoctorName)) {
                clearDoctorSelection(state);
            }
            changed = true;
        }
        if (!StringUtils.hasText(state.preferredDate) || !state.preferredDateExplicit) {
            DateResolution resolution = resolveAiPreferredDate(plannerDecision.preferredDate());
            if (resolution.issue() != null) {
                state.dateResolutionIssue = resolution.issue();
                state.preferredDate = null;
                state.preferredDateExplicit = false;
                clearSlotSelection(state);
                changed = true;
            } else if (StringUtils.hasText(resolution.date()) && !resolution.date().equals(state.preferredDate)) {
                state.dateResolutionIssue = null;
                state.preferredDate = resolution.date();
                state.preferredDateExplicit = true;
                clearSlotSelection(state);
                changed = true;
            }
        }
        if (!StringUtils.hasText(state.preferredTimeWindow) && StringUtils.hasText(plannerDecision.preferredTimeWindow())) {
            state.preferredTimeWindow = normalizePlannerTimeWindow(plannerDecision.preferredTimeWindow());
            state.timePromptCount = 0;
            clearSlotSelection(state);
            changed = true;
        }
        if (!StringUtils.hasText(state.reason) && StringUtils.hasText(plannerDecision.reason())) {
            state.reason = plannerDecision.reason();
            changed = true;
        }
        if (changed) {
            queueWorkflowEvent(state, "PLANNER_CONTEXT_ENRICHED", workflowContextJson(state));
        }
        return changed;
    }

    private boolean applyPlannerRescheduleFacts(CareAiState state, PatientPortalCareAiPlannerDecision plannerDecision) {
        if (plannerDecision == null) {
            return false;
        }
        boolean changed = false;
        if (!StringUtils.hasText(state.preferredDate) || !state.preferredDateExplicit) {
            DateResolution resolution = resolveAiPreferredDate(plannerDecision.preferredDate());
            if (resolution.issue() != null) {
                state.dateResolutionIssue = resolution.issue();
                state.preferredDate = null;
                state.preferredDateExplicit = false;
                clearSlotSelection(state);
                changed = true;
            } else if (StringUtils.hasText(resolution.date()) && !resolution.date().equals(state.preferredDate)) {
                state.dateResolutionIssue = null;
                state.preferredDate = resolution.date();
                state.preferredDateExplicit = true;
                clearSlotSelection(state);
                changed = true;
            }
        }
        if (!StringUtils.hasText(state.preferredTimeWindow) && StringUtils.hasText(plannerDecision.preferredTimeWindow())) {
            state.preferredTimeWindow = normalizePlannerTimeWindow(plannerDecision.preferredTimeWindow());
            state.timePromptCount = 0;
            clearSlotSelection(state);
            changed = true;
        }
        if (changed) {
            queueWorkflowEvent(state, "PLANNER_CONTEXT_ENRICHED", workflowContextJson(state));
        }
        return changed;
    }

    private String handleBooking(CareAiState state, String message) {
        careAiTrace("handleBooking", "enter", state,
                "message=" + trimToLength(message, 160)
                        + " selectedDoctorId=" + state.selectedDoctorId
                        + " selectedClinicSlug=" + state.selectedClinicSlug
                        + " selectedTenantId=" + state.selectedTenantId
                        + " preferredDate=" + state.preferredDate
                        + " preferredTimeWindow=" + state.preferredTimeWindow
                        + " slotCount=" + state.slotOptions.size());
        if (shouldRerenderSlotOptions(state, message)) {
            return rerenderSlotOptions(state);
        }
        if (shouldAdvanceSlotOptions(state, message)) {
            return advanceSlotOptions(state);
        }
        if (tryResolveClinicSelection(state, message)) {
            String clinicDoctorPrompt = clinicDoctorChoicePrompt(state);
            if (StringUtils.hasText(clinicDoctorPrompt)) {
                return clinicDoctorPrompt;
            }
        }
        if (tryResolveDoctorSelection(state, message)) {
            if (StringUtils.hasText(state.dateResolutionIssue)) {
                return invalidDatePrompt(state.language, state.dateResolutionIssue);
            }
            if (!StringUtils.hasText(state.preferredDate)) {
                return askDatePrompt(state.language);
            }
        }
        if (!StringUtils.hasText(state.selectedDoctorId)) {
            return promptForDoctorSelection(state, message);
        }
        if (StringUtils.hasText(state.dateResolutionIssue)) {
            return invalidDatePrompt(state.language, state.dateResolutionIssue);
        }
        if (!StringUtils.hasText(state.preferredDate)) {
            return askDatePrompt(state.language);
        }

        if (tryResolveSlotSelection(state, message, state.selectedDoctorId)) {
            return bookingConfirmationPrompt(state);
        }
        if (state.slotOptions.isEmpty()) {
            if (hasResolvedTimePreference(state)) {
                return timePreferenceSlotUnavailablePrompt(state);
            }
            if (shouldAskTimePreference(state)) {
                return nextTimePrompt(state);
            }
            return slotChoicePrompt(state);
        }
        careAiTrace("handleBooking", "exit", state,
                "selectedDoctorId=" + state.selectedDoctorId
                        + " selectedSlot=" + state.selectedSlot
                        + " confirmationPending=" + state.confirmationPending
                        + " slotCount=" + state.slotOptions.size());
        return slotChoicePrompt(state);
    }

    private String handleReschedule(CareAiState state, String message) {
        careAiTrace("handleReschedule", "enter", state,
                "message=" + trimToLength(message, 160)
                        + " selectedAppointmentId=" + state.selectedAppointmentId
                        + " selectedDoctorId=" + state.selectedDoctorId
                        + " selectedClinicSlug=" + state.selectedClinicSlug
                        + " selectedTenantId=" + state.selectedTenantId
                        + " preferredDate=" + state.preferredDate
                        + " preferredTimeWindow=" + state.preferredTimeWindow
                        + " slotCount=" + state.slotOptions.size());
        if (shouldRerenderSlotOptions(state, message)) {
            return rerenderSlotOptions(state);
        }
        if (shouldAdvanceSlotOptions(state, message)) {
            return advanceSlotOptions(state);
        }
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
        if (StringUtils.hasText(state.dateResolutionIssue)) {
            return invalidDatePrompt(state.language, state.dateResolutionIssue);
        }
        if (!StringUtils.hasText(state.preferredDate)) {
            return askRescheduleDatePrompt(state);
        }
        if (tryResolveSlotSelection(state, message, state.selectedDoctorId)) {
            return rescheduleConfirmationPrompt(state);
        }
        if (state.slotOptions.isEmpty()) {
            if (hasResolvedTimePreference(state)) {
                return timePreferenceSlotUnavailablePrompt(state);
            }
            if (shouldAskTimePreference(state)) {
                return nextTimePrompt(state);
            }
            return slotChoicePrompt(state);
        }
        careAiTrace("handleReschedule", "exit", state,
                "selectedAppointmentId=" + state.selectedAppointmentId
                        + " selectedSlot=" + state.selectedSlot
                        + " confirmationPending=" + state.confirmationPending
                        + " slotCount=" + state.slotOptions.size());
        return slotChoicePrompt(state);
    }

    private String handleCancellation(CareAiState state, String message) {
        careAiTrace("handleCancellation", "enter", state,
                "message=" + trimToLength(message, 160)
                        + " selectedAppointmentId=" + state.selectedAppointmentId
                        + " selectedDoctorId=" + state.selectedDoctorId
                        + " selectedTenantId=" + state.selectedTenantId
                        + " appointmentCount=" + state.appointmentOptions.size());
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
        careAiTrace("handleCancellation", "exit", state,
                "selectedAppointmentId=" + state.selectedAppointmentId
                        + " confirmationPending=" + state.confirmationPending
                        + " appointmentCount=" + state.appointmentOptions.size());
        return cancellationConfirmationPrompt(state);
    }

    private String handleStatus(CareAiState state, String message) {
        careAiTrace("handleStatus", "enter", state,
                "message=" + trimToLength(message, 160)
                        + " selectedAppointmentId=" + state.selectedAppointmentId
                        + " selectedTenantId=" + state.selectedTenantId
                        + " appointmentCount=" + state.appointmentOptions.size());
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
        careAiTrace("handleStatus", "exit", state,
                "selectedAppointmentId=" + state.selectedAppointmentId
                        + " selectedTenantId=" + state.selectedTenantId
                        + " appointmentCount=" + state.appointmentOptions.size());
        return appointmentStatusPrompt(next, state.language);
    }

    private String promptForDoctorSelection(CareAiState state, String message) {
        List<DoctorChoice> matches = resolveDoctorMatches(state, message);
        if (matches.isEmpty()) {
            if (mayContainClinicReference(message)) {
                List<ClinicChoice> clinicMatches = resolveClinicMatches(state, message);
                if (clinicMatches.size() == 1) {
                    selectClinic(state, clinicMatches.getFirst());
                    return clinicDoctorChoicePrompt(state);
                }
                if (clinicMatches.size() > 1) {
                    state.clinicChoices = clinicMatches;
                    state.clinicOptions = clinicMatches.stream().map(ClinicChoice::label).toList();
                    return clinicChoicePrompt(state);
                }
            }
            List<DoctorChoice> fuzzyMatches = resolveFuzzyDoctorMatches(message);
            if (fuzzyMatches.size() == 1) {
                state.doctorChoices = fuzzyMatches;
                state.doctorOptions = fuzzyMatches.stream().map(DoctorChoice::label).toList();
                return doctorCorrectionPrompt(state.language, fuzzyMatches.getFirst());
            }
            if (!fuzzyMatches.isEmpty()) {
                state.doctorChoices = fuzzyMatches;
                state.doctorOptions = fuzzyMatches.stream().map(DoctorChoice::label).toList();
                return doctorChoicePrompt(state);
            }
            if (log.isDebugEnabled()) {
                log.debug(
                        "patient.portal.careai.doctor.lookup.empty source=web-public-patient-careai conversationTenantId={} tenantContextTenantId={} patientPortalSessionId={} patientId={} patientMobile={} selectedDoctorId={} selectedDoctorSlug={} selectedClinicId={} selectedTenantId={} selectedClinicSlug={} lookupMode={} searchText={} speciality={} reason={}",
                        RequestContextHolder.requireTenantId(),
                        RequestContextHolder.get() == null ? null : RequestContextHolder.get().tenantId().value(),
                        RequestContextHolder.require().correlationId(),
                        patientPortalService.currentPatientId(),
                        patientPortalService.currentPatientMobile(),
                        state.selectedDoctorId,
                        state.selectedDoctorSlug,
                        state.selectedClinicId,
                        state.selectedTenantId,
                        state.selectedClinicSlug,
                        StringUtils.hasText(state.selectedClinicSlug) ? "clinic-specific" : "cross-clinic",
                        state.requestedDoctorName,
                        state.requestedSpeciality,
                        StringUtils.hasText(state.selectedClinicSlug) ? "doctor-not-found" : "no-clinic-context"
                );
            }
            state.doctorOptions = publicBookableDoctorChoices(state).stream()
                    .limit(4)
                    .map(DoctorChoice::label)
                    .toList();
            return askDoctorPrompt(state.language, containsBookingIntent(message, state.language));
        }
        if (matches.size() > 1 && sameDoctorAcrossMultipleClinics(matches)) {
            state.clinicChoices = toClinicChoices(matches);
            state.clinicOptions = state.clinicChoices.stream().map(ClinicChoice::label).toList();
            return clinicChoicePrompt(state);
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
            if (isPositiveConfirmation(message) && state.doctorChoices.size() == 1) {
                selectDoctor(state, state.doctorChoices.getFirst());
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
            return false;
        }
        List<DoctorChoice> fuzzyMatches = resolveFuzzyDoctorMatches(message);
        if (fuzzyMatches.size() == 1) {
            state.doctorChoices = fuzzyMatches;
            state.doctorOptions = fuzzyMatches.stream().map(DoctorChoice::label).toList();
        } else if (!fuzzyMatches.isEmpty()) {
            state.doctorChoices = fuzzyMatches;
            state.doctorOptions = fuzzyMatches.stream().map(DoctorChoice::label).toList();
        }
        return false;
    }

    private boolean tryResolveClinicSelection(CareAiState state, String message) {
        if (state.clinicChoices.isEmpty()) {
            return false;
        }
        ClinicChoice selected = resolveClinicChoice(state, message);
        if (selected == null) {
            return false;
        }
        selectClinic(state, selected);
        return true;
    }

    private boolean tryResolveSlotSelection(CareAiState state, String message, String publicDoctorId) {
        if (!state.slotChoices.isEmpty()) {
            SlotChoice selected = resolveSlotChoice(state, message);
            if (selected != null) {
                if (!selected.slotTime().format(TIME_FORMATTER).equals(state.selectedSlot)) {
                    invalidatePendingConfirmation(state, "slot-changed");
                }
                state.selectedSlot = selected.slotTime().format(TIME_FORMATTER);
                state.preferredDate = selected.appointmentDate().toString();
                state.confirmationPending = true;
                state.awaitingFreshConfirmation = false;
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
        logSlotLookupRequest("tryResolveSlotSelection", state, publicDoctorId, date);
        List<PatientPortalDoctorSlotResponse> selectableSlots = loadDoctorSlots(
                state,
                publicDoctorId,
                state.selectedClinicSlug,
                state.selectedTenantId,
                state.selectedClinicId,
                date
        ).stream()
                .filter(PatientPortalDoctorSlotResponse::selectable)
                .sorted(Comparator.comparing(PatientPortalDoctorSlotResponse::slotTime))
                .toList();
        logSlotLookupResponse("tryResolveSlotSelection", state, publicDoctorId, date, selectableSlots);
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
                state.slotPromptLead = null;
                state.allSlotChoices = List.of(new SlotChoice(exact.appointmentDate(), exact.slotTime()));
                state.shownSlotOffset = 0;
                renderSlotPage(state, 0);
                if (!exact.slotTime().format(TIME_FORMATTER).equals(state.selectedSlot)) {
                    invalidatePendingConfirmation(state, "slot-changed");
                }
                state.selectedSlot = exact.slotTime().format(TIME_FORMATTER);
                state.confirmationPending = true;
                state.awaitingFreshConfirmation = false;
                state.pendingAction = state.currentIntent;
                return true;
            }
            List<PatientPortalDoctorSlotResponse> nearest = nearestSlots(candidates, state.preferredTimeWindow);
            if (!nearest.isEmpty()) {
                candidates = nearest;
                state.slotPromptLead = exactTimeUnavailablePrompt(state, state.preferredTimeWindow, nearest);
            }
        } else if (StringUtils.hasText(state.preferredTimeWindow) && filtered.isEmpty()) {
            candidates = selectableSlots.stream().limit(3).toList();
            state.slotPromptLead = broadTimeUnavailablePrompt(state, state.preferredTimeWindow, candidates);
        }

        List<SlotChoice> options = candidates.stream()
                .map(slot -> new SlotChoice(slot.appointmentDate(), slot.slotTime()))
                .toList();
        state.allSlotChoices = options;
        state.shownSlotOffset = 0;
        renderSlotPage(state, 0);
        state.selectedSlot = null;
        state.confirmationPending = false;
        state.pendingAction = null;
        state.awaitingFreshConfirmation = false;
        return false;
    }

    private PatientPortalCareAiMessageResponse response(CareAiState state, String message) {
        message = guardRepeatedQuestion(state, message);
        String questionKey = inferQuestionKey(state, message);
        if (StringUtils.hasText(questionKey) && questionKey.equals(state.lastQuestionKey)) {
            state.repeatedQuestionCount += 1;
        } else {
            state.repeatedQuestionCount = 0;
        }
        state.lastQuestionKey = questionKey;
        markAskedState(state, questionKey);
        markAnsweredFacts(state);
        state.lastTopicClassification = inferResponseTopicClassification(state, questionKey);

        PatientPortalCareAiMessageResponse response = new PatientPortalCareAiMessageResponse(
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
        persistTurn(state, response);
        return response;
    }

    private PatientPortalCareAiMessageResponse executeConfirmedAction(CareAiState state) {
        if (state.pendingAction == null) {
            state.confirmationPending = false;
            return response(state, askIntentPrompt(state.language));
        }
        careAiTrace("executeConfirmedAction", "enter", state,
                "pendingAction=" + state.pendingAction
                        + " selectedDoctorId=" + state.selectedDoctorId
                        + " selectedAppointmentId=" + state.selectedAppointmentId
                        + " selectedClinicSlug=" + state.selectedClinicSlug
                        + " selectedTenantId=" + state.selectedTenantId
                        + " selectedClinicId=" + state.selectedClinicId
                        + " selectedDate=" + state.preferredDate
                        + " selectedSlot=" + state.selectedSlot);
        logAppointmentAction("executeConfirmedAction", state, List.of());
        try {
            logBookingOrMutationRequest("executeConfirmedAction", state);
            PatientPortalAppointmentConfirmationResponse confirmation = switch (state.pendingAction) {
                case BOOK_APPOINTMENT -> patientPortalService.bookAppointment(new PatientPortalAppointmentBookingRequest(
                        state.selectedDoctorId,
                        state.selectedClinicSlug,
                        state.selectedTenantId,
                        state.selectedClinicId,
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
                        "Cancelled via AIVA"
                );
                case CHECK_APPOINTMENT, APPOINTMENT_STATUS -> throw new IllegalStateException("Status lookups do not require confirmation");
                default -> throw new IllegalStateException("Unsupported confirmation action: " + state.pendingAction);
            };
            careAiTrace("executeConfirmedAction", "success", state,
                    "pendingAction=" + state.pendingAction
                            + " confirmationStatus=" + confirmation.status()
                            + " appointmentDate=" + confirmation.appointmentDate()
                            + " appointmentTime=" + confirmation.appointmentTime()
                            + " message=" + confirmation.message());
            state.actionCompleted = true;
            state.booked = state.pendingAction == PatientPortalCareAiIntent.BOOK_APPOINTMENT;
            state.lastAction = state.pendingAction;
            state.bookingStatus = confirmation.status();
            state.bookedAppointmentDate = confirmation.appointmentDate() == null ? null : confirmation.appointmentDate().toString();
            state.bookedAppointmentTime = confirmation.appointmentTime() == null ? null : confirmation.appointmentTime().format(TIME_FORMATTER);
            state.confirmationPending = false;
            state.pendingAction = null;
            state.activeConfirmationScopeKey = null;
            state.awaitingFreshConfirmation = false;
            state.handoffRequired = false;
            state.handoffReason = null;
            state.unresolvedTurns = 0;
            if (state.lastAction == PatientPortalCareAiIntent.CANCEL_APPOINTMENT) {
                clearAppointmentSelection(state);
            }
            completeWorkflowCleanup(state);
            return response(state, confirmation.message());
        } catch (RuntimeException ex) {
            careAiTrace("executeConfirmedAction", "error", state,
                    "pendingAction=" + state.pendingAction + " error=" + ex.getMessage());
            state.confirmationPending = false;
            state.pendingAction = null;
            state.activeConfirmationScopeKey = null;
            state.unresolvedTurns += 1;
            if (state.unresolvedTurns >= 3) {
                prepareAppointmentHandoffResponse(state, state.lastUserMessage, "booking-failed", CareAiReceptionistTaskPriority.HIGH);
                return response(state, receptionHandoffPrompt(state.language));
            }
            return response(state, bookingFailedPrompt(state.language, ex.getMessage()));
        }
    }

    private List<PatientPortalDoctorSlotResponse> loadDoctorSlots(CareAiState state, String publicDoctorId, String clinicSlug, String tenantId, String clinicId, LocalDate date) {
        careAiTrace("loadDoctorSlots", "enter", state,
                "doctorId=" + publicDoctorId
                        + " clinicSlug=" + clinicSlug
                        + " tenantId=" + tenantId
                        + " clinicId=" + clinicId
                        + " date=" + date
                        + " conversationTenantId=" + RequestContextHolder.requireTenantId()
                        + " tenantContextTenantId=" + (RequestContextHolder.get() == null ? null : RequestContextHolder.get().tenantId().value()));
        if (log.isDebugEnabled()) {
            log.debug(
                    "patient.portal.careai.slot.lookup.invoke source=web-public-patient-careai conversationTenantId={} patientPortalSessionId={} patientId={} patientMobile={} doctorId={} selectedDoctorName={} selectedClinicSlug={} selectedTenantId={} selectedClinicId={} date={}",
                    RequestContextHolder.requireTenantId(),
                    RequestContextHolder.require().correlationId(),
                    patientPortalService.currentPatientId(),
                    patientPortalService.currentPatientMobile(),
                    publicDoctorId,
                    state == null ? null : state.selectedDoctorName,
                    clinicSlug,
                    tenantId,
                    clinicId,
                    date
            );
        }
        if (StringUtils.hasText(clinicId) || StringUtils.hasText(tenantId)) {
            List<PatientPortalDoctorSlotResponse> slots = businessLookupService.findSlots(publicDoctorId, clinicSlug, tenantId, clinicId, date);
            careAiTrace("loadDoctorSlots", "exit", state,
                    "service=businessLookupService.findSlots resultCount=" + slots.size()
                            + " results=" + summarizeSlots(slots));
            return slots;
        }
        if (StringUtils.hasText(clinicSlug)) {
            List<PatientPortalDoctorSlotResponse> slots = businessLookupService.findSlots(publicDoctorId, clinicSlug, null, null, date);
            careAiTrace("loadDoctorSlots", "exit", state,
                    "service=businessLookupService.findSlots resultCount=" + slots.size()
                            + " results=" + summarizeSlots(slots));
            return slots;
        }
        List<PatientPortalDoctorSlotResponse> slots = businessLookupService.findSlots(publicDoctorId, null, null, null, date);
        careAiTrace("loadDoctorSlots", "exit", state,
                "service=businessLookupService.findSlots resultCount=" + slots.size()
                        + " results=" + summarizeSlots(slots));
        return slots;
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

    private PatientPortalCareAiMessageResponse handleHumanHandoffRequest(CareAiState state, String message) {
        String reason = detectHumanHandoffReason(message);
        CareAiReceptionistTaskType taskType = activeBookingHandoff(state)
                ? CareAiReceptionistTaskType.APPOINTMENT_HANDOFF
                : CareAiReceptionistTaskType.HUMAN_HANDOFF;
        try {
            var result = taskType == CareAiReceptionistTaskType.APPOINTMENT_HANDOFF
                    ? receptionistTaskService.upsertAppointmentHandoffTask(
                    receptionistTaskCommand(state, message, null, taskType, reason),
                    handoffPriority(message)
            )
                    : receptionistTaskService.upsertHandoffTask(
                    receptionistTaskCommand(state, message, null, taskType, reason),
                    handoffPriority(message)
            );
            if (result.created()) {
                taskNotificationService.notifyTaskCreated(result.task());
            }
            prepareTaskQueueResponse(state, CareAiWorkflowType.HUMAN_HANDOFF, taskType, result.task().getId(), true, reason, "HUMAN_HANDOFF_REQUESTED");
            return response(state, humanHandoffAcknowledgement(state.language));
        } catch (RuntimeException ex) {
            log.warn("careai.receptionist-task.handoff.failed tenantId={} patientId={} channel={} reason={}",
                    RequestContextHolder.requireTenantId(),
                    state.lastPatientId,
                    state.lastChannel,
                    ex.getMessage(),
                    ex);
            state.handoffRequired = true;
            state.handoffReason = reason;
            queueWorkflowEvent(state, "HUMAN_HANDOFF_REQUESTED", workflowContextJson(state));
            return response(state, humanHandoffAcknowledgement(state.language));
        }
    }

    private void prepareAppointmentHandoffResponse(
            CareAiState state,
            String message,
            String reason,
            CareAiReceptionistTaskPriority priority
    ) {
        try {
            var result = receptionistTaskService.upsertAppointmentHandoffTask(
                    receptionistTaskCommand(state, message, null, CareAiReceptionistTaskType.APPOINTMENT_HANDOFF, reason),
                    priority
            );
            if (result.created()) {
                taskNotificationService.notifyTaskCreated(result.task());
            }
            prepareTaskQueueResponse(
                    state,
                    CareAiWorkflowType.HUMAN_HANDOFF,
                    CareAiReceptionistTaskType.APPOINTMENT_HANDOFF,
                    result.task().getId(),
                    true,
                    reason,
                    "APPOINTMENT_HANDOFF_REQUESTED"
            );
        } catch (RuntimeException ex) {
            log.warn("careai.receptionist-task.appointment-handoff.failed tenantId={} patientId={} channel={} reason={}",
                    RequestContextHolder.requireTenantId(),
                    state.lastPatientId,
                    state.lastChannel,
                    ex.getMessage(),
                    ex);
            state.handoffRequired = true;
            state.handoffReason = reason;
            queueWorkflowEvent(state, "APPOINTMENT_HANDOFF_REQUESTED", workflowContextJson(state));
        }
    }

    private PatientPortalCareAiMessageResponse handleCallbackRequest(CareAiState state, String message) {
        CallbackPreference callbackPreference = extractCallbackTimePreference(message, state.language);
        try {
            var result = receptionistTaskService.upsertCallbackTask(
                    receptionistTaskCommand(state, message, callbackPreference, CareAiReceptionistTaskType.CALLBACK_REQUEST, "callback-request"),
                    callbackPriority(message, callbackPreference)
            );
            if (result.created()) {
                taskNotificationService.notifyTaskCreated(result.task());
            }
            prepareTaskQueueResponse(state, CareAiWorkflowType.CALLBACK_REQUEST, CareAiReceptionistTaskType.CALLBACK_REQUEST, result.task().getId(), false, null, "CALLBACK_REQUESTED");
            return response(state, callbackAcknowledgement(state.language, callbackPreference.label()));
        } catch (RuntimeException ex) {
            log.warn("careai.receptionist-task.callback.failed tenantId={} patientId={} channel={} reason={}",
                    RequestContextHolder.requireTenantId(),
                    state.lastPatientId,
                    state.lastChannel,
                    ex.getMessage(),
                    ex);
            queueWorkflowEvent(state, "CALLBACK_REQUESTED", workflowContextJson(state));
            prepareTaskQueueResponse(state, CareAiWorkflowType.CALLBACK_REQUEST, CareAiReceptionistTaskType.CALLBACK_REQUEST, null, false, null, "CALLBACK_REQUESTED");
            return response(state, callbackAcknowledgement(state.language, callbackPreference.label()));
        }
    }

    private CareAiReceptionistTaskCreateCommand receptionistTaskCommand(
            CareAiState state,
            String message,
            CallbackPreference callbackPreference,
            CareAiReceptionistTaskType taskType,
            String reason
    ) {
        return new CareAiReceptionistTaskCreateCommand(
                RequestContextHolder.requireTenantId(),
                state.currentConversationId,
                state.currentWorkflowId,
                state.lastPatientId,
                null,
                parseUuid(state.selectedAppointmentId),
                state.lastChannel == null ? null : state.lastChannel.name(),
                reason,
                trimToLength(message, 500),
                callbackPreference == null ? null : callbackPreference.label(),
                callbackPreference == null ? null : callbackPreference.dueAt(),
                taskMetadataJson(state, taskType)
        );
    }

    private void prepareTaskQueueResponse(
            CareAiState state,
            CareAiWorkflowType workflowType,
            CareAiReceptionistTaskType taskType,
            UUID taskId,
            boolean escalated,
            String handoffReason,
            String workflowEventType
    ) {
        state.slotPromptLead = null;
        state.slotChoices = List.of();
        state.slotOptions = List.of();
        state.selectedSlot = null;
        state.confirmationPending = false;
        state.pendingAction = null;
        state.awaitingFreshConfirmation = false;
        state.doctorChoices = List.of();
        state.doctorOptions = List.of();
        state.appointmentOptions = List.of();
        state.currentIntent = null;
        state.transientWorkflowType = workflowType;
        state.activeTaskType = taskType;
        state.activeTaskId = taskId;
        state.handoffRequired = escalated;
        state.handoffReason = handoffReason;
        state.lastQuestionKey = null;
        state.repeatedQuestionCount = 0;
        queueWorkflowEvent(state, workflowEventType, taskEventPayloadJson(state, taskId, taskType, handoffReason));
    }

    private void prepareEscalationResponse(
            CareAiState state,
            String message,
            String reason,
            CareAiReceptionistTaskPriority priority
    ) {
        try {
            var result = receptionistTaskService.upsertEscalationTask(
                    receptionistTaskCommand(state, message, null, CareAiReceptionistTaskType.ESCALATION, reason),
                    priority
            );
            if (result.created()) {
                taskNotificationService.notifyTaskCreated(result.task());
            }
            prepareTaskQueueResponse(
                    state,
                    CareAiWorkflowType.HUMAN_HANDOFF,
                    CareAiReceptionistTaskType.ESCALATION,
                    result.task().getId(),
                    true,
                    reason,
                    "ESCALATION_CREATED"
            );
        } catch (RuntimeException ex) {
            log.warn("careai.receptionist-task.escalation.failed tenantId={} patientId={} channel={} reason={}",
                    RequestContextHolder.requireTenantId(),
                    state.lastPatientId,
                    state.lastChannel,
                    ex.getMessage(),
                    ex);
            state.handoffRequired = true;
            state.handoffReason = reason;
            queueWorkflowEvent(state, "ESCALATION_CREATED", workflowContextJson(state));
        }
    }

    private boolean activeBookingHandoff(CareAiState state) {
        return state.currentIntent == PatientPortalCareAiIntent.BOOK_APPOINTMENT
                || state.currentIntent == PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT
                || state.currentIntent == PatientPortalCareAiIntent.CANCEL_APPOINTMENT
                || state.selectedDoctorId != null
                || state.preferredDate != null
                || state.selectedSlot != null;
    }

    private void resetWorkflowState(CareAiState state, PatientPortalCareAiIntent intent) {
        state.currentIntent = intent;
        state.requestedDoctorName = null;
        state.requestedSpeciality = null;
        state.requestedClinicName = null;
        state.selectedDoctorId = null;
        state.selectedDoctorSlug = null;
        state.selectedDoctorName = null;
        state.selectedSpeciality = null;
        state.selectedClinicId = null;
        state.selectedTenantId = null;
        state.selectedClinicSlug = null;
        state.selectedClinicName = null;
        state.preferredDate = null;
        state.dateResolutionIssue = null;
        state.preferredTimeWindow = null;
        state.reason = null;
        state.slotPromptLead = null;
        state.timePromptCount = 0;
        state.doctorChoices = List.of();
        state.doctorOptions = List.of();
        state.clinicChoices = List.of();
        state.clinicOptions = List.of();
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
        state.lastSideTopic = null;
        state.awaitingFreshConfirmation = false;
        state.transientWorkflowType = null;
        state.activeTaskId = null;
        state.activeTaskType = null;
    }

    private void clearDoctorSelection(CareAiState state) {
        state.selectedDoctorId = null;
        state.selectedDoctorSlug = null;
        state.selectedDoctorName = null;
        state.selectedSpeciality = null;
        state.selectedClinicId = null;
        state.selectedTenantId = null;
        state.selectedClinicSlug = null;
        state.selectedClinicName = null;
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
            state.selectedDoctorSlug = null;
            state.selectedDoctorName = null;
            state.selectedSpeciality = null;
            state.selectedClinicId = null;
            state.selectedTenantId = null;
            state.selectedClinicSlug = null;
            state.selectedClinicName = null;
        }
        state.clinicChoices = List.of();
        state.clinicOptions = List.of();
        clearSlotSelection(state);
    }

    private void clearSlotSelection(CareAiState state) {
        state.selectedSlot = null;
        state.slotPromptLead = null;
        state.allSlotChoices = List.of();
        state.shownSlotOffset = 0;
        state.slotChoices = List.of();
        state.slotOptions = List.of();
        state.confirmationPending = false;
        state.pendingAction = null;
        state.activeConfirmationScopeKey = null;
    }

    private void completeWorkflowCleanup(CareAiState state) {
        clearSlotSelection(state);
        state.currentIntent = null;
        state.doctorChoices = List.of();
        state.doctorOptions = List.of();
        state.appointmentOptions = List.of();
        state.lastQuestionKey = null;
        state.repeatedQuestionCount = 0;
        state.transientWorkflowType = null;
        state.activeTaskType = null;
        state.activeTaskId = null;
        state.pendingWorkflowEventType = "WORKFLOW_COMPLETED";
        state.pendingWorkflowEventPayloadJson = workflowMetadataJson(state);
    }

    private boolean ensureAppointmentOptions(CareAiState state) {
        careAiTrace("ensureAppointmentOptions", "enter", state,
                "patientId=" + patientPortalService.currentPatientId()
                        + " patientMobile=" + patientPortalService.currentPatientMobile()
                        + " conversationTenantId=" + RequestContextHolder.requireTenantId()
                        + " tenantContextTenantId=" + (RequestContextHolder.get() == null ? null : RequestContextHolder.get().tenantId().value()));
        List<PatientPortalCareAiAppointmentOption> appointments = businessLookupService.upcomingAppointments();
        logAppointmentLookup("ensureAppointmentOptions", state, appointments);
        List<AppointmentChoice> choices = appointments.stream()
                .sorted(Comparator
                        .comparing(PatientPortalCareAiAppointmentOption::appointmentDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PatientPortalCareAiAppointmentOption::appointmentTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toAppointmentChoice)
                .toList();
        state.appointmentOptions = choices;
        careAiTrace("ensureAppointmentOptions", "exit", state,
                "service=businessLookupService.upcomingAppointments resultCount=" + choices.size()
                        + " results=" + summarizeAppointments(appointments)
                        + (choices.isEmpty() ? " reason=no-appointments-found" : ""));
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
                option.tenantId(),
                option.clinicName(),
                option.appointmentDate(),
                option.appointmentTime(),
                option.status(),
                option.reason(),
                label
        );
    }

    private List<DoctorChoice> resolveDoctorMatches(CareAiState state, String message) {
        String doctorHint = state.requestedDoctorName;
        if (!StringUtils.hasText(doctorHint)) {
            doctorHint = findDoctorNameFromFreeText(message);
        }
        if (!StringUtils.hasText(doctorHint)) {
            doctorHint = findCorrectedDoctorNameCandidate(message);
        }
        final String doctorHintValue = doctorHint;
        final String specialityHint = state.requestedSpeciality;
        String normalizedMessage = normalizeDoctorText(message);
        List<DoctorChoice> doctors = searchPublicBookableDoctors(state, doctorHintValue, specialityHint);
        List<DoctorChoice> matches = doctors.stream()
                .filter(doctor -> {
                    if (StringUtils.hasText(doctorHintValue)) {
                        return matchesDoctorNameAny(doctor.doctorName(), doctorHintValue);
                    }
                    if (StringUtils.hasText(specialityHint)) {
                        return containsIgnoreCase(doctor.speciality(), specialityHint);
                    }
                    return StringUtils.hasText(normalizedMessage)
                            && containsDoctorTokenMatch(doctor.doctorName(), normalizedMessage);
                })
                .sorted(Comparator.comparing(DoctorChoice::doctorName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (!matches.isEmpty()) {
            logDoctorLookup("resolveDoctorMatches", state, doctorHintValue, specialityHint, doctors, matches, null);
            return matches;
        }
        if (StringUtils.hasText(state.requestedSpeciality)) {
            List<DoctorChoice> specialityMatches = doctors.stream()
                    .filter(doctor -> containsIgnoreCase(doctor.speciality(), state.requestedSpeciality))
                    .sorted(Comparator.comparing(DoctorChoice::doctorName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            logDoctorLookup("resolveDoctorMatches", state, doctorHintValue, specialityHint, doctors, specialityMatches, "speciality-only");
            return specialityMatches;
        }
        logDoctorLookup("resolveDoctorMatches", state, doctorHintValue, specialityHint, doctors, List.of(), "no-match");
        return List.of();
    }

    private List<ClinicChoice> resolveClinicMatches(CareAiState state, String message) {
        if (!StringUtils.hasText(message)) {
            return List.of();
        }
        List<ClinicChoice> clinics = lookupClinics(state, message);
        if (clinics.isEmpty()) {
            return List.of();
        }
        String normalizedMessage = normalizeDoctorText(message);
        List<ClinicChoice> matches = clinics.stream()
                .filter(choice -> {
                    String normalizedLabel = normalizeDoctorText(choice.label());
                    String normalizedSlug = normalizeDoctorText(choice.clinicSlug());
                    return normalizedLabel.contains(normalizedMessage)
                            || normalizedSlug.contains(normalizedMessage)
                            || normalizedMessage.contains(normalizedLabel);
                })
                .sorted(Comparator.comparing(ClinicChoice::clinicName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        careAiTrace("resolveClinicMatches", "exit", state,
                "resultCount=" + matches.size()
                        + " results=" + matches.stream().limit(5).map(ClinicChoice::label).toList());
        return matches;
    }

    private DoctorChoice toDoctorChoice(PatientPortalDoctorResponse doctor) {
        return new DoctorChoice(
                doctor.publicDoctorId(),
                null,
                doctor.doctorName(),
                doctor.specialization(),
                null,
                null,
                null,
                null,
                doctorLabel(doctor)
        );
    }

    private boolean sameDoctorAcrossMultipleClinics(List<DoctorChoice> matches) {
        if (matches == null || matches.size() < 2) {
            return false;
        }
        long doctorNames = matches.stream()
                .map(DoctorChoice::doctorName)
                .filter(StringUtils::hasText)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .distinct()
                .count();
        long clinics = matches.stream()
                .map(DoctorChoice::clinicSlug)
                .filter(StringUtils::hasText)
                .map(clinic -> clinic.toLowerCase(Locale.ROOT))
                .distinct()
                .count();
        return doctorNames == 1 && clinics > 1;
    }

    private DoctorChoice toDoctorChoice(PublicDoctorSummaryResponse doctor) {
        return new DoctorChoice(
                doctor.publicDoctorId(),
                doctor.doctorSlug(),
                doctor.doctorDisplayName(),
                doctor.speciality(),
                null,
                null,
                doctor.clinicSlug(),
                doctor.clinicDisplayName(),
                doctorLabel(doctor.doctorDisplayName(), doctor.speciality(), doctor.clinicDisplayName())
        );
    }

    private ClinicChoice toClinicChoice(PublicClinicSummaryResponse clinic) {
        return new ClinicChoice(
                clinic.clinicSlug(),
                clinic.clinicDisplayName(),
                clinic.area(),
                clinic.city(),
                null,
                null,
                clinicLabel(clinic.clinicDisplayName(), clinic.area(), clinic.city())
        );
    }

    private List<ClinicChoice> toClinicChoices(List<DoctorChoice> doctors) {
        Map<String, ClinicChoice> clinics = new LinkedHashMap<>();
        for (DoctorChoice doctor : doctors) {
            String slug = StringUtils.hasText(doctor.clinicSlug()) ? doctor.clinicSlug() : doctor.clinicName();
            if (!StringUtils.hasText(slug)) {
                continue;
            }
            String key = slug.toLowerCase(Locale.ROOT);
            clinics.putIfAbsent(key, new ClinicChoice(
                    doctor.clinicSlug(),
                    doctor.clinicName(),
                    null,
                    null,
                    doctor.tenantId(),
                    doctor.clinicId(),
                    clinicLabel(doctor.clinicName(), null, null)
            ));
        }
        return List.copyOf(clinics.values());
    }

    private void selectDoctor(CareAiState state, DoctorChoice selected) {
        careAiTrace("selectDoctor", "enter", state,
                "selectedDoctorId=" + selected.publicDoctorId()
                        + " selectedDoctorName=" + selected.doctorName()
                        + " selectedClinicId=" + selected.clinicId()
                        + " selectedTenantId=" + selected.tenantId()
                        + " selectedClinicSlug=" + selected.clinicSlug());
        state.selectedDoctorId = selected.publicDoctorId();
        state.selectedDoctorSlug = selected.doctorSlug();
        state.selectedDoctorName = selected.doctorName();
        state.selectedSpeciality = selected.speciality();
        state.selectedClinicId = selected.clinicId();
        state.selectedTenantId = selected.tenantId();
        state.selectedClinicSlug = selected.clinicSlug();
        state.selectedClinicName = selected.clinicName();
        state.timePromptCount = 0;
        state.doctorChoices = List.of();
        state.doctorOptions = List.of();
        state.lastSideTopic = null;
        clearSlotSelection(state);
        careAiTrace("selectDoctor", "exit", state,
                "selectedDoctorId=" + state.selectedDoctorId
                        + " selectedDoctorSlug=" + state.selectedDoctorSlug
                        + " selectedClinicId=" + state.selectedClinicId
                        + " selectedTenantId=" + state.selectedTenantId
                        + " selectedClinicSlug=" + state.selectedClinicSlug);
    }

    private void selectClinic(CareAiState state, ClinicChoice selected) {
        careAiTrace("selectClinic", "enter", state,
                "selectedClinicSlug=" + selected.clinicSlug()
                        + " selectedClinicName=" + selected.clinicName()
                        + " selectedClinicId=" + selected.clinicId()
                        + " selectedTenantId=" + selected.tenantId());
        state.selectedClinicSlug = selected.clinicSlug();
        state.selectedClinicName = selected.clinicName();
        state.selectedClinicId = selected.clinicId();
        state.selectedTenantId = selected.tenantId();
        state.clinicChoices = List.of();
        state.clinicOptions = List.of();
        clearSlotSelection(state);
        careAiTrace("selectClinic", "exit", state,
                "selectedClinicSlug=" + state.selectedClinicSlug
                        + " selectedClinicName=" + state.selectedClinicName
                        + " selectedClinicId=" + state.selectedClinicId
                        + " selectedTenantId=" + state.selectedTenantId);
    }

    private void selectAppointment(CareAiState state, AppointmentChoice selected) {
        careAiTrace("selectAppointment", "enter", state,
                "selectedAppointmentId=" + selected.appointmentId()
                        + " selectedDoctorId=" + selected.doctorUserId()
                        + " selectedTenantId=" + selected.tenantId()
                        + " selectedClinicName=" + selected.clinicName());
        state.selectedAppointmentId = selected.appointmentId().toString();
        state.selectedAppointmentLabel = selected.label();
        state.selectedAppointmentReason = selected.reason();
        if (state.currentIntent == PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT) {
            state.selectedDoctorId = selected.doctorUserId() == null ? null : selected.doctorUserId().toString();
            state.selectedDoctorName = selected.doctorName();
            state.selectedTenantId = selected.tenantId() == null ? null : selected.tenantId().toString();
        } else {
            state.selectedDoctorId = null;
            state.selectedDoctorName = null;
            state.selectedTenantId = null;
        }
        state.selectedSpeciality = null;
        state.dateResolutionIssue = null;
        state.timePromptCount = 0;
        state.lastSideTopic = null;
        clearSlotSelection(state);
        careAiTrace("selectAppointment", "exit", state,
                "selectedAppointmentId=" + state.selectedAppointmentId
                        + " selectedDoctorId=" + state.selectedDoctorId
                        + " selectedTenantId=" + state.selectedTenantId
                        + " selectedAppointmentLabel=" + state.selectedAppointmentLabel);
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

    private ClinicChoice resolveClinicChoice(CareAiState state, String message) {
        if (state.clinicChoices.isEmpty()) {
            return null;
        }
        Integer index = parseSelectionIndex(message);
        if (index != null && index >= 1 && index <= state.clinicChoices.size()) {
            return state.clinicChoices.get(index - 1);
        }
        String normalized = normalizeDoctorText(message);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        List<ClinicChoice> matches = state.clinicChoices.stream()
                .filter(choice -> normalizeDoctorText(choice.label()).contains(normalized))
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
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
        String normalized = normalizeDoctorText(message);
        if (List.of("one", "option one", "first", "1st").contains(normalized)) {
            return 1;
        }
        if (List.of("two", "option two", "second", "2nd").contains(normalized)) {
            return 2;
        }
        if (List.of("three", "option three", "third", "3rd").contains(normalized)) {
            return 3;
        }
        if (List.of("four", "option four", "fourth", "4th").contains(normalized)) {
            return 4;
        }
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
        if (normalized.startsWith("night") || normalized.startsWith("रात")) {
            return slots.stream().filter(slot -> !slot.slotTime().isBefore(LocalTime.of(19, 0))).toList();
        }
        if (isExactTime(preferredTimeWindow)) {
            return slots.stream()
                    .filter(slot -> slot.slotTime().format(TIME_FORMATTER).equalsIgnoreCase(preferredTimeWindow))
                    .toList();
        }
        return slots;
    }

    private List<PatientPortalDoctorSlotResponse> nearestSlots(List<PatientPortalDoctorSlotResponse> slots, String preferredTimeWindow) {
        if (!isExactTime(preferredTimeWindow)) {
            return slots;
        }
        LocalTime requested = LocalTime.parse(preferredTimeWindow, TIME_FORMATTER);
        return slots.stream()
                .sorted(Comparator.comparingLong(slot -> Math.abs(java.time.Duration.between(requested, slot.slotTime()).toMinutes())))
                .limit(3)
                .toList();
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

    private PatientPortalCareAiIntent detectWorkflowIntent(String transcript) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        if (RESCHEDULE_INTENT_KEYWORDS.stream().anyMatch(lower::contains) || transcript.contains("रीशेड्यूल")) {
            return PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT;
        }
        if (CANCEL_INTENT_KEYWORDS.stream().anyMatch(lower::contains) || transcript.contains("रद्द")) {
            return PatientPortalCareAiIntent.CANCEL_APPOINTMENT;
        }
        if (STATUS_INTENT_KEYWORDS.stream().anyMatch(lower::contains) || transcript.contains("अपॉइंटमेंट कब")) {
            return PatientPortalCareAiIntent.CHECK_APPOINTMENT;
        }
        if (BOOKING_INTENT_KEYWORDS.stream().anyMatch(lower::contains) || transcript.contains("बुक")) {
            return PatientPortalCareAiIntent.BOOK_APPOINTMENT;
        }
        return null;
    }

    private PatientPortalCareAiIntent classifyIntent(CareAiState state,
                                                     String transcript,
                                                     PatientPortalCareAiPlannerDecision plannerDecision) {
        if (detectResetConversation(transcript, state.language)) {
            return PatientPortalCareAiIntent.RESET_CONVERSATION;
        }
        PatientPortalCareAiIntent workflowIntent = detectWorkflowIntent(transcript);
        if (workflowIntent != null) {
            return workflowIntent;
        }
        if (detectDoctorSearchIntent(transcript)) {
            return PatientPortalCareAiIntent.FIND_DOCTOR;
        }
        if (detectClinicSearchIntent(transcript)) {
            return PatientPortalCareAiIntent.FIND_CLINIC;
        }
        if (isGreetingOnly(transcript, state.language)) {
            return PatientPortalCareAiIntent.GREETING;
        }
        if (isSmallTalkOnly(transcript, state.language)) {
            return PatientPortalCareAiIntent.SMALL_TALK;
        }
        if (plannerDecision != null && plannerDecision.intent() != null) {
            return PatientPortalCareAiIntent.normalize(plannerDecision.intent());
        }
        return PatientPortalCareAiIntent.UNKNOWN;
    }

    private boolean shouldUsePlanner(CareAiState state, String message) {
        if (planner == null || !StringUtils.hasText(message)) {
            return false;
        }
        if (isSelectionOnlyMessage(message) && !state.confirmationPending) {
            return false;
        }
        return state.currentIntent == null
                || state.confirmationPending
                || state.unresolvedTurns > 0
                || StringUtils.hasText(state.preferredDate) && !StringUtils.hasText(state.preferredTimeWindow)
                || StringUtils.hasText(state.selectedDoctorId) && !StringUtils.hasText(state.preferredDate)
                || containsMonthNameWithoutYear(message)
                || (!StringUtils.hasText(state.preferredDate) && !looksLikeDateAbsent(message))
                || (!StringUtils.hasText(state.preferredTimeWindow) && mayContainTimePreference(message))
                || (!StringUtils.hasText(state.requestedDoctorName) && !StringUtils.hasText(state.selectedDoctorId));
    }

    private PatientPortalCareAiPlanningContext buildPlanningContext(CareAiState state, String message) {
        return new PatientPortalCareAiPlanningContext(
                state.language,
                message,
                state.currentIntent == null ? null : state.currentIntent.name(),
                state.persistedWorkflowContextJson,
                state.activeConfirmationScopeKey,
                state.confirmationPending,
                state.pendingAction == null ? null : state.pendingAction.name(),
                state.requestedDoctorName,
                state.selectedDoctorName,
                state.requestedSpeciality,
                state.selectedAppointmentLabel,
                state.preferredDate,
                state.preferredTimeWindow,
                state.selectedSlot,
                missingPlannerFields(state),
                availablePlannerActions(state),
                state.doctorOptions,
                state.appointmentOptions.stream().map(AppointmentChoice::label).limit(5).toList(),
                state.slotOptions,
                publicBookableDoctorChoices(state).stream()
                        .limit(8)
                        .map(DoctorChoice::doctorName)
                        .filter(StringUtils::hasText)
                        .toList(),
                state.recentMessages,
                state.lastQuestionKey,
                state.repeatedQuestionCount
        );
    }

    private List<String> missingPlannerFields(CareAiState state) {
        List<String> missing = new ArrayList<>();
        if (state.currentIntent == PatientPortalCareAiIntent.BOOK_APPOINTMENT) {
            if (!StringUtils.hasText(state.selectedDoctorId)) {
                missing.add("doctor");
            }
            if (!StringUtils.hasText(state.preferredDate)) {
                missing.add("date");
            }
            if (StringUtils.hasText(state.preferredDate) && !StringUtils.hasText(state.preferredTimeWindow) && state.slotOptions.isEmpty()) {
                missing.add("time");
            }
            if (!state.confirmationPending && StringUtils.hasText(state.preferredDate) && !StringUtils.hasText(state.selectedSlot)) {
                missing.add("slot");
            }
        } else if (state.currentIntent == PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT) {
            if (!StringUtils.hasText(state.selectedAppointmentId)) {
                missing.add("appointment");
            }
            if (!StringUtils.hasText(state.preferredDate)) {
                missing.add("date");
            }
            if (StringUtils.hasText(state.preferredDate) && !StringUtils.hasText(state.preferredTimeWindow) && state.slotOptions.isEmpty()) {
                missing.add("time");
            }
            if (!state.confirmationPending && StringUtils.hasText(state.preferredDate) && !StringUtils.hasText(state.selectedSlot)) {
                missing.add("slot");
            }
        } else if (state.currentIntent == PatientPortalCareAiIntent.CANCEL_APPOINTMENT) {
            if (!StringUtils.hasText(state.selectedAppointmentId)) {
                missing.add("appointment");
            }
        }
        if (state.confirmationPending) {
            missing.add("confirmation");
        }
        return missing;
    }

    private List<String> availablePlannerActions(CareAiState state) {
        List<String> actions = new ArrayList<>(List.of("SWITCH_TOPIC", "ASK_CLARIFYING_QUESTION"));
        if (state.currentIntent == null) {
            actions.addAll(List.of("SET_INTENT", "EXTRACT_DOCTOR", "EXTRACT_SPECIALITY", "EXTRACT_DATE", "EXTRACT_TIME"));
            return actions;
        }
        switch (state.currentIntent) {
            case BOOK_APPOINTMENT -> actions.addAll(List.of(
                    "SET_INTENT",
                    "EXTRACT_DOCTOR",
                    "EXTRACT_SPECIALITY",
                    "EXTRACT_DATE",
                    "EXTRACT_TIME",
                    "CHOOSE_SLOT",
                    "CONFIRM_OR_REJECT"
            ));
            case RESCHEDULE_APPOINTMENT -> actions.addAll(List.of(
                    "SET_INTENT",
                    "CHOOSE_APPOINTMENT",
                    "EXTRACT_DATE",
                    "EXTRACT_TIME",
                    "CHOOSE_SLOT",
                    "CONFIRM_OR_REJECT"
            ));
            case CANCEL_APPOINTMENT -> actions.addAll(List.of(
                    "SET_INTENT",
                    "CHOOSE_APPOINTMENT",
                    "CONFIRM_OR_REJECT"
            ));
            case CHECK_APPOINTMENT, APPOINTMENT_STATUS -> actions.addAll(List.of("SET_INTENT", "CHOOSE_APPOINTMENT"));
            default -> {
            }
        }
        return actions;
    }

    private DateResolution resolveAiPreferredDate(String value) {
        if (!StringUtils.hasText(value)) {
            return DateResolution.none();
        }
        DateResolution parsed = findPreferredDate(value, "en");
        if (parsed.date() != null || parsed.issue() != null) {
            return parsed;
        }
        return resolveAbsoluteDate(parseIsoDate(value), true);
    }

    private String normalizePlannerTimeWindow(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = findPreferredTimeWindow(value, "en");
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "before_lunch", "before-lunch" -> "morning";
            case "after_lunch", "after-lunch" -> "afternoon";
            default -> trimToLength(value.trim(), 16);
        };
    }

    private boolean mayContainTimePreference(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("morning")
                || lower.contains("afternoon")
                || lower.contains("evening")
                || lower.contains("night")
                || lower.contains("option one")
                || lower.contains("option two")
                || lower.contains("option three")
                || lower.contains("option four")
                || lower.contains("first option")
                || lower.contains("second option")
                || lower.contains("third option")
                || lower.contains("fourth option")
                || lower.matches(".*\\b(one|two|three|four|1|2|3|4)\\b.*")
                || lower.contains("lunch")
                || EXPLICIT_TIME_PATTERN.matcher(message).find();
    }

    private boolean looksLikeDateAbsent(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return !(ISO_DATE_PATTERN.matcher(message).find()
                || DMY_DATE_PATTERN.matcher(message).find()
                || MDY_DATE_PATTERN.matcher(message).find()
                || SLASH_DATE_PATTERN.matcher(message).find()
                || lower.contains("today")
                || lower.contains("tomorrow")
                || lower.contains("next ")
                || lower.contains("this ")
                || lower.contains("weekend"));
    }

    private boolean containsMonthNameWithoutYear(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return MONTH_NAME_MAP.keySet().stream().anyMatch(lower::contains) && !DMY_DATE_PATTERN.matcher(message).find() && !MDY_DATE_PATTERN.matcher(message).find();
    }

    private SessionKey currentSessionKey() {
        return new SessionKey(RequestContextHolder.requireTenantId(), RequestContextHolder.require().appUserId());
    }

    private String currentChatExternalSessionId() {
        return String.valueOf(RequestContextHolder.require().appUserId());
    }

    private boolean matchesDoctorName(String doctorName, String requestedDoctorName) {
        String doctor = normalizeDoctorText(doctorName);
        String requested = normalizeDoctorText(requestedDoctorName);
        return StringUtils.hasText(doctor) && StringUtils.hasText(requested)
                && (doctor.contains(requested) || requested.contains(doctor));
    }

    private boolean matchesDoctorNameAny(String doctorName, String requestedDoctorName) {
        if (!StringUtils.hasText(requestedDoctorName)) {
            return false;
        }
        for (String candidate : doctorQueryVariants(requestedDoctorName)) {
            if (matchesDoctorName(doctorName, candidate)) {
                return true;
            }
        }
        return false;
    }

    private List<String> doctorQueryVariants(String requestedDoctorName) {
        String normalized = normalizeDoctorText(requestedDoctorName);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        String[] tokens = normalized.split("\\s+");
        List<String> variants = new ArrayList<>();
        for (int end = tokens.length; end >= 1; end -= 1) {
            String variant = String.join(" ", java.util.Arrays.copyOf(tokens, end)).trim();
            if (StringUtils.hasText(variant) && !variants.contains(variant)) {
                variants.add(variant);
            }
        }
        return variants;
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
        careAiTrace("findRequestedDoctorName", "enter", currentState(),
                "transcript=" + trimToLength(transcript, 160) + " language=" + language);
        Matcher english = ENGLISH_DOCTOR_PATTERN.matcher(transcript);
        if (english.find()) {
            String extracted = cleanName(english.group(1));
            careAiTrace("findRequestedDoctorName", "exit", currentState(),
                    "source=english extractedDoctorName=" + extracted);
            return extracted;
        }
        if (isHindi(language) || transcript.contains("डॉक्टर") || transcript.contains("डॉ")) {
            Matcher hindi = HINDI_DOCTOR_PATTERN.matcher(transcript);
            if (hindi.find()) {
                String extracted = cleanName(hindi.group(1));
                careAiTrace("findRequestedDoctorName", "exit", currentState(),
                        "source=hindi extractedDoctorName=" + extracted);
                return extracted;
            }
        }
        careAiTrace("findRequestedDoctorName", "exit", currentState(),
                "source=none extractedDoctorName=null");
        return null;
    }

    private String findDoctorNameFromFreeText(String transcript) {
        careAiTrace("findDoctorNameFromFreeText", "enter", currentState(),
                "transcript=" + trimToLength(transcript, 160));
        String correctedName = findCorrectedDoctorNameCandidate(transcript);
        if (StringUtils.hasText(correctedName)) {
            careAiTrace("findDoctorNameFromFreeText", "exit", currentState(),
                    "source=corrected extractedDoctorName=" + correctedName);
            return correctedName;
        }
        String normalized = normalizeDoctorText(transcript);
        if (!StringUtils.hasText(normalized)) {
            careAiTrace("findDoctorNameFromFreeText", "exit", currentState(),
                    "source=normalized-empty extractedDoctorName=null");
            return null;
        }
        List<String> names = publicBookableDoctorChoices(currentState())
                .stream()
                .map(DoctorChoice::doctorName)
                .filter(StringUtils::hasText)
                .filter(name -> doctorQueryVariants(normalized).stream().anyMatch(candidate -> {
                    String normalizedName = normalizeDoctorText(name);
                        return normalizedName.contains(candidate) || candidate.contains(normalizedName);
                }))
                .toList();
        String extracted = names.size() == 1 ? names.getFirst() : null;
        careAiTrace("findDoctorNameFromFreeText", "exit", currentState(),
                "source=publicBookableDoctorChoices extractedDoctorName=" + extracted
                        + " candidateCount=" + names.size());
        return extracted;
    }

    private List<DoctorChoice> resolveFuzzyDoctorMatches(String transcript) {
        String correctedName = fuzzyDoctorCandidate(transcript);
        if (!StringUtils.hasText(correctedName)) {
            return List.of();
        }
        CareAiState state = currentState();
        List<DoctorChoice> prefixMatches = publicBookableDoctorChoices(state).stream()
                .filter(doctor -> matchesDoctorName(doctor.doctorName(), correctedName))
                .sorted(Comparator.comparing(DoctorChoice::doctorName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (!prefixMatches.isEmpty()) {
            return prefixMatches;
        }
        String normalizedCandidate = normalizeDoctorText(correctedName);
        return publicBookableDoctorChoices(state).stream()
                .map(doctor -> Map.entry(doctor, doctorNameDistance(normalizeDoctorText(doctor.doctorName()), normalizedCandidate)))
                .filter(entry -> entry.getValue() <= 3)
                .sorted(Comparator
                        .comparingInt((Map.Entry<DoctorChoice, Integer> entry) -> entry.getValue())
                        .thenComparing(entry -> entry.getKey().doctorName(), String.CASE_INSENSITIVE_ORDER))
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();
    }

    private String fuzzyDoctorCandidate(String transcript) {
        String correctedName = findCorrectedDoctorNameCandidate(transcript);
        if (StringUtils.hasText(correctedName)) {
            return correctedName;
        }
        return findRequestedDoctorName(transcript, "en");
    }

    private String findCorrectedDoctorNameCandidate(String transcript) {
        String normalized = normalizeDoctorText(transcript);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        int nameIndex = normalized.lastIndexOf("name is ");
        if (nameIndex >= 0) {
            return cleanDoctorCandidate(normalized.substring(nameIndex + "name is ".length()));
        }
        List<String> corrections = List.of("i mean ", "no ", "doctor name is ");
        for (String marker : corrections) {
            int index = normalized.lastIndexOf(marker);
            if (index >= 0) {
                return cleanDoctorCandidate(normalized.substring(index + marker.length()));
            }
        }
        return null;
    }

    private String cleanDoctorCandidate(String rawCandidate) {
        if (!StringUtils.hasText(rawCandidate)) {
            return null;
        }
        String cleaned = rawCandidate
                .replaceAll("^(they want to see|want to see|they want|doctor|dr)\\s+", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return StringUtils.hasText(cleaned) ? trimDoctorQueryTail(cleaned) : null;
    }

    private int doctorNameDistance(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return Integer.MAX_VALUE;
        }
        List<String> leftTokens = extractDoctorTokens(left);
        List<String> rightTokens = extractDoctorTokens(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return levenshteinDistance(left, right);
        }
        int total = 0;
        for (String rightToken : rightTokens) {
            int best = leftTokens.stream()
                    .mapToInt(leftToken -> levenshteinDistance(leftToken, rightToken))
                    .min()
                    .orElse(Integer.MAX_VALUE);
            total += best;
        }
        return total;
    }

    private List<String> extractDoctorTokens(String value) {
        Matcher matcher = LETTER_ONLY_TOKEN_PATTERN.matcher(value);
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return tokens;
    }

    private int levenshteinDistance(String left, String right) {
        int[][] matrix = new int[left.length() + 1][right.length() + 1];
        for (int i = 0; i <= left.length(); i += 1) {
            matrix[i][0] = i;
        }
        for (int j = 0; j <= right.length(); j += 1) {
            matrix[0][j] = j;
        }
        for (int i = 1; i <= left.length(); i += 1) {
            for (int j = 1; j <= right.length(); j += 1) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                matrix[i][j] = Math.min(
                        Math.min(matrix[i - 1][j] + 1, matrix[i][j - 1] + 1),
                        matrix[i - 1][j - 1] + cost
                );
            }
        }
        return matrix[left.length()][right.length()];
    }

    private String findSpeciality(String transcript) {
        String normalized = transcript.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> specialities = new LinkedHashSet<>();
        publicBookableDoctorChoices(currentState()).stream()
                .map(DoctorChoice::speciality)
                .filter(StringUtils::hasText)
                .forEach(specialities::add);
        return specialities.stream()
                .filter(item -> normalized.contains(item.toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElse(null);
    }

    private DateResolution findPreferredDate(String transcript, String language) {
        Matcher iso = ISO_DATE_PATTERN.matcher(transcript);
        if (iso.find()) {
            return resolveAbsoluteDate(parseIsoDate(iso.group(1)), true);
        }
        String lower = transcript.toLowerCase(Locale.ROOT);
        LocalDate today = currentClinicDate();
        if (lower.contains("day after tomorrow") || transcript.contains("परसों")) {
            return DateResolution.valid(today.plusDays(2).toString());
        }
        if (lower.contains("tomorrow") || transcript.contains("कल")) {
            return DateResolution.valid(today.plusDays(1).toString());
        }
        if (lower.contains("today") || transcript.contains("आज")) {
            return DateResolution.valid(today.toString());
        }
        if (lower.contains("next week")) {
            return DateResolution.valid(today.plusWeeks(1).toString());
        }
        if (lower.contains("this weekend")) {
            return DateResolution.valid(resolveThisWeekday(today, DayOfWeek.SATURDAY).toString());
        }
        if (lower.contains("this saturday")) {
            return DateResolution.valid(resolveThisWeekday(today, DayOfWeek.SATURDAY).toString());
        }
        if (lower.contains("this sunday")) {
            return DateResolution.valid(resolveThisWeekday(today, DayOfWeek.SUNDAY).toString());
        }
        if (lower.contains("next monday")) {
            return DateResolution.valid(today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).toString());
        }
        if (lower.contains("next tuesday")) {
            return DateResolution.valid(today.with(TemporalAdjusters.next(DayOfWeek.TUESDAY)).toString());
        }
        if (lower.contains("next wednesday")) {
            return DateResolution.valid(today.with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY)).toString());
        }
        if (lower.contains("next thursday")) {
            return DateResolution.valid(today.with(TemporalAdjusters.next(DayOfWeek.THURSDAY)).toString());
        }
        if (lower.contains("next friday")) {
            return DateResolution.valid(today.with(TemporalAdjusters.next(DayOfWeek.FRIDAY)).toString());
        }
        if (lower.contains("next saturday")) {
            return DateResolution.valid(today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).toString());
        }
        if (lower.contains("next sunday")) {
            return DateResolution.valid(today.with(TemporalAdjusters.next(DayOfWeek.SUNDAY)).toString());
        }
        Matcher dmy = DMY_DATE_PATTERN.matcher(transcript);
        if (dmy.find()) {
            return resolveAbsoluteDate(parseMonthNameDate(dmy.group(1), dmy.group(2), dmy.group(3)), true);
        }
        Matcher mdy = MDY_DATE_PATTERN.matcher(transcript);
        if (mdy.find()) {
            return resolveAbsoluteDate(parseMonthNameDate(mdy.group(2), mdy.group(1), mdy.group(3)), true);
        }
        Matcher dmyWithoutYear = DMY_DATE_WITHOUT_YEAR_PATTERN.matcher(transcript);
        if (dmyWithoutYear.find()) {
            LocalDate parsed = parseMonthNameDateWithoutYear(dmyWithoutYear.group(1), dmyWithoutYear.group(2));
            return parsed == null ? DateResolution.none() : resolveAbsoluteDate(parsed, false);
        }
        Matcher mdyWithoutYear = MDY_DATE_WITHOUT_YEAR_PATTERN.matcher(transcript);
        if (mdyWithoutYear.find()) {
            LocalDate parsed = parseMonthNameDateWithoutYear(mdyWithoutYear.group(1), mdyWithoutYear.group(2));
            return parsed == null ? DateResolution.none() : resolveAbsoluteDate(parsed, false);
        }
        Matcher slash = SLASH_DATE_PATTERN.matcher(transcript);
        if (slash.find()) {
            int first = Integer.parseInt(slash.group(1));
            int second = Integer.parseInt(slash.group(2));
            if (first <= 12 && second <= 12
                    && slash.group(1).length() == 2
                    && slash.group(2).length() == 2) {
                return DateResolution.invalid("ambiguous");
            }
            return resolveAbsoluteDate(parseSlashDate(slash.group(1), slash.group(2), slash.group(3)), true);
        }
        for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
            if (matchesWeekday(transcript, lower, dayOfWeek)) {
                return DateResolution.valid(today.with(TemporalAdjusters.nextOrSame(dayOfWeek)).toString(), true);
            }
        }
        return DateResolution.none();
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
        return findPreferredTimeWindow(transcript, language, null);
    }

    private String findPreferredTimeWindow(String transcript, String language, CareAiState state) {
        String normalizedTranscript = normalizeTimeTranscript(transcript);
        String lower = normalizedTranscript.toLowerCase(Locale.ROOT);
        if (isGreetingOnly(transcript, language)) {
            return null;
        }
        if (lower.contains("before lunch")) {
            return isHindi(language) ? "सुबह" : "morning";
        }
        if (lower.contains("after lunch")) {
            return isHindi(language) ? "दोपहर" : "afternoon";
        }
        if (lower.contains("morning") || transcript.contains("सुबह")) {
            return isHindi(language) ? "सुबह" : "morning";
        }
        if (lower.contains("afternoon") || transcript.contains("दोपहर")) {
            return isHindi(language) ? "दोपहर" : "afternoon";
        }
        if (lower.contains("evening") || transcript.contains("शाम")) {
            return isHindi(language) ? "शाम" : "evening";
        }
        if (lower.contains("night") || transcript.contains("रात")) {
            return isHindi(language) ? "रात" : "night";
        }
        if (lower.contains("right now") || lower.contains("now")) {
            return currentClinicTime().format(TIME_FORMATTER);
        }
        String sanitized = ISO_DATE_PATTERN.matcher(lower).replaceAll(" ");
        Matcher matcher = EXPLICIT_TIME_PATTERN.matcher(sanitized);
        while (matcher.find()) {
            if (matcher.group(2) == null && matcher.group(3) == null) {
                continue;
            }
            int hour = Integer.parseInt(matcher.group(1));
            int minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            String meridiem = matcher.group(3);
            if (meridiem != null) {
                meridiem = meridiem.replace(".", "").trim();
            }
            if ("pm".equalsIgnoreCase(meridiem) && hour < 12) {
                hour += 12;
            } else if ("am".equalsIgnoreCase(meridiem) && hour == 12) {
                hour = 0;
            }
            if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                return LocalTime.of(hour, minute).format(TIME_FORMATTER);
            }
        }
        String enumeratedTimePreference = findEnumeratedTimePreference(lower, language, state);
        if (StringUtils.hasText(enumeratedTimePreference)) {
            return enumeratedTimePreference;
        }
        return null;
    }

    private String findEnumeratedTimePreference(String lowerTranscript, String language, CareAiState state) {
        Integer choiceIndex = parseEnumeratedTimePreferenceIndex(lowerTranscript);
        if (containsAny(lowerTranscript, "morning", "before lunch")) {
            return isHindi(language) ? "सुबह" : "morning";
        }
        if (containsAny(lowerTranscript, "afternoon", "after lunch")) {
            return isHindi(language) ? "दोपहर" : "afternoon";
        }
        if (containsAny(lowerTranscript, "evening")) {
            return isHindi(language) ? "शाम" : "evening";
        }
        if (containsAny(lowerTranscript, "night")) {
            return isHindi(language) ? "रात" : "night";
        }
        if (choiceIndex == null) {
            return null;
        }
        if (isLikelySlotChoice(state, choiceIndex)) {
            return null;
        }
        return switch (choiceIndex) {
            case 1 -> isHindi(language) ? "सुबह" : "morning";
            case 2 -> isHindi(language) ? "दोपहर" : "afternoon";
            case 3 -> isHindi(language) ? "शाम" : "evening";
            case 4 -> isHindi(language) ? "रात" : "night";
            default -> null;
        };
    }

    private boolean expectsTimePreference(CareAiState state, String lowerTranscript) {
        if (state == null) {
            return false;
        }
        if (state.currentIntent != PatientPortalCareAiIntent.BOOK_APPOINTMENT
                && state.currentIntent != PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT) {
            return false;
        }
        if (StringUtils.hasText(state.selectedSlot)) {
            return false;
        }
        return "ask-time".equals(state.lastQuestionKey)
                || state.askedTimePreference
                || state.answeredTimePreference
                || state.timePromptCount > 0;
    }

    private boolean isLikelySlotChoice(CareAiState state, Integer choiceIndex) {
        if (state == null) {
            return false;
        }
        if (choiceIndex == null || choiceIndex < 1) {
            return false;
        }
        int availableChoices = Math.max(state.slotChoices.size(), state.slotOptions.size());
        if (availableChoices <= 0) {
            return false;
        }
        return choiceIndex <= availableChoices;
    }

    private Integer parseEnumeratedTimePreferenceIndex(String lowerTranscript) {
        if (containsAny(lowerTranscript, "morning", "option one", "first option", "one", "1")) {
            return 1;
        }
        if (containsAny(lowerTranscript, "afternoon", "option two", "second option", "two", "2")) {
            return 2;
        }
        if (containsAny(lowerTranscript, "evening", "option three", "third option", "three", "3")) {
            return 3;
        }
        if (containsAny(lowerTranscript, "night", "option four", "fourth option", "four", "4")) {
            return 4;
        }
        return null;
    }

    private String normalizeTimeTranscript(String transcript) {
        return transcript
                .replaceAll("(?i)\\ba\\.?\\s*m\\.?\\b", "am")
                .replaceAll("(?i)\\bp\\.?\\s*m\\.?\\b", "pm");
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

    private boolean isPostCompletionCourtesy(String transcript, String language) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        if (THANK_YOU_KEYWORDS.stream().anyMatch(lower::contains) || GOODBYE_KEYWORDS.stream().anyMatch(lower::contains)) {
            return true;
        }
        return isHindi(language) && (transcript.contains("धन्यवाद") || transcript.contains("शुक्रिया") || transcript.contains("अलविदा"));
    }

    private boolean isSmallTalkOnly(String transcript, String language) {
        return isPostCompletionCourtesy(transcript, language)
                || "how are you".equalsIgnoreCase(transcript.trim())
                || (isHindi(language) && transcript.contains("कैसे हैं"));
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

    private boolean mayContainClinicReference(String transcript) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        return lower.contains("clinic")
                || lower.contains("hospital")
                || lower.contains("centre")
                || lower.contains("center")
                || lower.contains("branch")
                || lower.contains("location");
    }

    private boolean wantsTopicSwitch(String transcript, String language) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        if (TOPIC_SWITCH_KEYWORDS.stream().anyMatch(lower::contains)) {
            return true;
        }
        return isHindi(language) && TOPIC_SWITCH_KEYWORDS_HI.stream().anyMatch(transcript::contains);
    }

    private boolean detectResetConversation(String transcript, String language) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        return lower.contains("reset conversation")
                || lower.contains("reset chat")
                || lower.contains("clear conversation")
                || lower.contains("clear chat")
                || lower.contains("start over")
                || lower.contains("restart conversation")
                || (isHindi(language) && (transcript.contains("शुरू से") || transcript.contains("रीसेट")));
    }

    private boolean detectDoctorSearchIntent(String transcript) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        return lower.contains("find doctor")
                || lower.contains("show doctors")
                || lower.contains("which doctor")
                || lower.contains("available doctor");
    }

    private boolean detectClinicSearchIntent(String transcript) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        return lower.contains("find clinic")
                || lower.contains("show clinics")
                || lower.contains("which clinic")
                || lower.contains("available clinic");
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (candidate.contains(" ")) {
                if (value.contains(candidate)) {
                    return true;
                }
                continue;
            }
            if (Pattern.compile("\\b" + Pattern.quote(candidate) + "\\b").matcher(value).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean asksForAllAppointments(String transcript) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        return lower.contains("show my appointments")
                || lower.contains("show appointments")
                || lower.contains("my appointments")
                || lower.contains("upcoming appointments")
                || lower.contains("appointment list")
                || lower.contains("show my bookings")
                || lower.contains("check my bookings")
                || lower.contains("all appointments")
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

    private String doctorLabel(String doctorName, String speciality, String clinicName) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(doctorName)) {
            parts.add(doctorName);
        }
        if (StringUtils.hasText(speciality)) {
            parts.add(speciality);
        }
        if (StringUtils.hasText(clinicName)) {
            parts.add(clinicName);
        }
        return parts.isEmpty() ? "Doctor" : String.join(" · ", parts);
    }

    private String clinicLabel(String clinicName, String area, String city) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(clinicName)) {
            parts.add(clinicName);
        }
        if (StringUtils.hasText(area)) {
            parts.add(area);
        }
        if (StringUtils.hasText(city)) {
            parts.add(city);
        }
        return parts.isEmpty() ? "Clinic" : String.join(" · ", parts);
    }

    private List<DoctorChoice> publicBookableDoctorChoices(CareAiState state) {
        return lookupDoctors(state, null, null);
    }

    private List<DoctorChoice> searchPublicBookableDoctors(CareAiState state, String doctorQuery, String specialityQuery) {
        return lookupDoctors(state, doctorQuery, specialityQuery);
    }

    private List<DoctorChoice> lookupDoctors(CareAiState state, String doctorQuery, String specialityQuery) {
        careAiTrace("lookupDoctors", "enter", state,
                "lookupMode=" + (StringUtils.hasText(state.selectedClinicSlug) ? "clinic-specific" : "global-public-bookable")
                        + " selectedClinicSlug=" + state.selectedClinicSlug
                        + " selectedTenantId=" + state.selectedTenantId
                        + " searchText=" + doctorQuery
                        + " speciality=" + specialityQuery
                        + " defaultTenantIgnored=true");
        List<DoctorChoice> choices = businessLookupService.findDoctors(doctorQuery, specialityQuery, state.selectedClinicSlug).stream()
                .map(this::toDoctorChoice)
                .toList();
        careAiTrace("lookupDoctors", "exit", state,
                "resultCount=" + choices.size()
                        + " results=" + summarizeDoctors(choices));
        return choices;
    }

    private List<ClinicChoice> lookupClinics(CareAiState state, String clinicQuery) {
        String cacheKey = "clinic|" + nullToBlank(clinicQuery);
        return state.clinicLookupCache.computeIfAbsent(cacheKey, key -> {
            careAiTrace("lookupClinics", "enter", state,
                    "lookupMode=global-public-bookable selectedClinicSlug=" + state.selectedClinicSlug
                            + " searchText=" + clinicQuery
                            + " defaultTenantIgnored=true");
            List<ClinicChoice> choices = businessLookupService.findClinics(clinicQuery).stream()
                    .map(this::toClinicChoice)
                    .toList();
            careAiTrace("lookupClinics", "exit", state,
                    "resultCount=" + choices.size()
                            + " results=" + choices.stream().limit(5).map(ClinicChoice::label).toList());
            return choices;
        });
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
        return trimDoctorQueryTail(raw.replaceAll("[^\\p{L} .'-]", " ").replaceAll("\\s{2,}", " ").trim());
    }

    private String trimDoctorQueryTail(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String[] tokens = raw.trim().split("\\s+");
        List<String> kept = new ArrayList<>();
        for (String token : tokens) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (isDoctorQueryTailToken(lower)) {
                break;
            }
            kept.add(token);
        }
        String joined = String.join(" ", kept).replaceAll("\\s{2,}", " ").trim();
        return StringUtils.hasText(joined) ? joined : raw.trim();
    }

    private boolean isDoctorQueryTailToken(String lowerToken) {
        return "today".equals(lowerToken)
                || "tomorrow".equals(lowerToken)
                || "today's".equals(lowerToken)
                || "tomorrow's".equals(lowerToken)
                || "yesterday".equals(lowerToken)
                || "this".equals(lowerToken)
                || "next".equals(lowerToken)
                || "morning".equals(lowerToken)
                || "afternoon".equals(lowerToken)
                || "evening".equals(lowerToken)
                || "night".equals(lowerToken)
                || "noon".equals(lowerToken)
                || "lunch".equals(lowerToken)
                || "appointment".equals(lowerToken)
                || "appointments".equals(lowerToken)
                || "booking".equals(lowerToken)
                || "book".equals(lowerToken)
                || "schedule".equals(lowerToken)
                || "visit".equals(lowerToken)
                || "slot".equals(lowerToken)
                || "slots".equals(lowerToken)
                || "available".equals(lowerToken)
                || "availability".equals(lowerToken)
                || "for".equals(lowerToken)
                || "with".equals(lowerToken)
                || "on".equals(lowerToken)
                || "at".equals(lowerToken)
                || "please".equals(lowerToken)
                || "kindly".equals(lowerToken)
                || "monday".equals(lowerToken)
                || "tuesday".equals(lowerToken)
                || "wednesday".equals(lowerToken)
                || "thursday".equals(lowerToken)
                || "friday".equals(lowerToken)
                || "saturday".equals(lowerToken)
                || "sunday".equals(lowerToken)
                || MONTH_NAME_MAP.containsKey(lowerToken);
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

    private String doctorCorrectionPrompt(String language, DoctorChoice doctorChoice) {
        return isHindi(language)
                ? "क्या आपका मतलब " + doctorChoice.doctorName() + " था?"
                : "Did you mean " + doctorChoice.doctorName() + "?";
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

    private String clinicChoicePrompt(CareAiState state) {
        return numberedChoicePrompt(
                state.language,
                "I found multiple matching clinics. Please choose one:",
                "मुझे कई क्लिनिक मिले। कृपया एक चुनिए:",
                state.clinicOptions
        );
    }

    private String clinicDoctorChoicePrompt(CareAiState state) {
        if (state.doctorChoices.isEmpty()) {
            state.doctorChoices = publicBookableDoctorChoices(state);
            state.doctorOptions = state.doctorChoices.stream().map(DoctorChoice::label).toList();
        }
        if (state.doctorChoices.size() == 1) {
            selectDoctor(state, state.doctorChoices.getFirst());
            return askDatePrompt(state.language);
        }
        if (state.doctorOptions.isEmpty()) {
            return askDoctorPrompt(state.language, true);
        }
        return numberedChoicePrompt(
                state.language,
                "Please choose a doctor at this clinic:",
                "कृपया इस क्लिनिक में डॉक्टर चुनिए:",
                state.doctorOptions
        );
    }

    private String appointmentChoicePrompt(CareAiState state, String actionWord) {
        String english = "Please choose which appointment you want to " + actionWord + ":";
        String hindi = "कृपया वह अपॉइंटमेंट चुनिए जिसे आप " + actionWord + " चाहते हैं:";
        return numberedChoicePrompt(state.language, english, hindi, state.appointmentOptions.stream().map(AppointmentChoice::label).toList());
    }

    private String slotChoicePrompt(CareAiState state) {
        if (state.slotOptions.isEmpty()) {
            return unavailablePreferredWindowPrompt(state, state.preferredTimeWindow, List.of());
        }
        String englishLead = StringUtils.hasText(state.slotPromptLead)
                ? state.slotPromptLead
                : "Please choose a slot by number or time:";
        String hindiLead = StringUtils.hasText(state.slotPromptLead)
                ? state.slotPromptLead
                : "कृपया नंबर या समय से स्लॉट चुनिए:";
        return numberedChoicePrompt(state.language, englishLead, hindiLead, state.slotOptions);
    }

    private boolean shouldAdvanceSlotOptions(CareAiState state, String message) {
        if (state == null || state.slotOptions.isEmpty() || !StringUtils.hasText(message)) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return SLOT_MORE_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private String advanceSlotOptions(CareAiState state) {
        if (StringUtils.hasText(state.selectedDoctorId) && StringUtils.hasText(state.preferredDate)) {
            refreshSlotChoicesForPagination(state);
        }
        int nextOffset = state.shownSlotOffset + SLOT_PAGE_SIZE;
        if (nextOffset >= state.allSlotChoices.size()) {
            return isHindi(state.language)
                    ? "उस तारीख के लिए मुझे यही उपलब्ध स्लॉट मिले।"
                    : "These are the available slots I found for that date.";
        }
        renderSlotPage(state, nextOffset);
        return slotChoicePrompt(state);
    }

    private String numberedChoicePrompt(String language, String englishLead, String hindiLead, List<String> options) {
        List<String> lines = new ArrayList<>();
        lines.add(isHindi(language) ? hindiLead : englishLead);
        for (int i = 0; i < options.size(); i += 1) {
            lines.add((i + 1) + ". " + options.get(i));
        }
        return String.join("\n", lines);
    }

    private boolean shouldRerenderSlotOptions(CareAiState state, String message) {
        if (state == null || state.slotOptions.isEmpty() || !StringUtils.hasText(message)) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return SLOT_RERENDER_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private String rerenderSlotOptions(CareAiState state) {
        if (state.slotOptions.isEmpty()
                && StringUtils.hasText(state.selectedDoctorId)
                && StringUtils.hasText(state.preferredDate)) {
            tryResolveSlotSelection(state, "show options again", state.selectedDoctorId);
        }
        if (!state.allSlotChoices.isEmpty() && state.slotOptions.isEmpty()) {
            renderSlotPage(state, state.shownSlotOffset);
        }
        if (state.slotOptions.isEmpty()) {
            return unavailablePreferredWindowPrompt(state, state.preferredTimeWindow, List.of());
        }
        return slotChoicePrompt(state);
    }

    private void refreshSlotChoicesForPagination(CareAiState state) {
        if (state == null
                || !StringUtils.hasText(state.selectedDoctorId)
                || !StringUtils.hasText(state.preferredDate)) {
            return;
        }
        LocalDate date = LocalDate.parse(state.preferredDate);
        List<PatientPortalDoctorSlotResponse> selectableSlots = loadDoctorSlots(
                state,
                state.selectedDoctorId,
                state.selectedClinicSlug,
                state.selectedTenantId,
                state.selectedClinicId,
                date
        ).stream()
                .filter(PatientPortalDoctorSlotResponse::selectable)
                .sorted(Comparator.comparing(PatientPortalDoctorSlotResponse::slotTime))
                .toList();
        if (selectableSlots.isEmpty()) {
            return;
        }
        List<PatientPortalDoctorSlotResponse> filtered = filterSlots(selectableSlots, state.preferredTimeWindow);
        List<PatientPortalDoctorSlotResponse> candidates = filtered.isEmpty() ? selectableSlots : filtered;
        if (isExactTime(state.preferredTimeWindow)) {
            PatientPortalDoctorSlotResponse exact = candidates.stream()
                    .filter(slot -> slot.slotTime().format(TIME_FORMATTER).equalsIgnoreCase(state.preferredTimeWindow))
                    .findFirst()
                    .orElse(null);
            if (exact != null) {
                candidates = List.of(exact);
            } else {
                List<PatientPortalDoctorSlotResponse> nearest = nearestSlots(candidates, state.preferredTimeWindow);
                if (!nearest.isEmpty()) {
                    candidates = nearest;
                }
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        state.allSlotChoices = candidates.stream()
                .map(slot -> new SlotChoice(slot.appointmentDate(), slot.slotTime()))
                .toList();
        if (state.slotOptions.isEmpty()) {
            renderSlotPage(state, 0);
        }
    }

    private void renderSlotPage(CareAiState state, int offset) {
        if (state == null || state.allSlotChoices.isEmpty()) {
            state.slotChoices = List.of();
            state.slotOptions = List.of();
            state.shownSlotOffset = 0;
            return;
        }
        int safeOffset = Math.max(0, Math.min(offset, Math.max(0, state.allSlotChoices.size() - 1)));
        int endExclusive = Math.min(safeOffset + SLOT_PAGE_SIZE, state.allSlotChoices.size());
        List<SlotChoice> page = state.allSlotChoices.subList(safeOffset, endExclusive);
        state.shownSlotOffset = safeOffset;
        state.slotChoices = page;
        state.slotOptions = page.stream().map(choice -> choice.slotTime().format(TIME_FORMATTER)).toList();
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
                ? "कृपया समय बताइए, जैसे सुबह, दोपहर, शाम, रात, लंच से पहले, लंच के बाद, या कोई विशेष समय।"
                : "What time works best, such as morning, afternoon, evening, night, before lunch, after lunch, or a specific time?";
    }

    private String nextTimePrompt(CareAiState state) {
        state.timePromptCount += 1;
        if (hasResolvedTimePreference(state)) {
            return timePreferenceSlotUnavailablePrompt(state);
        }
        if (state.repeatedQuestionCount >= 1) {
            if (StringUtils.hasText(state.preferredTimeWindow)) {
                return isHindi(state.language)
                        ? "मैंने " + safe(state.preferredTimeWindow) + " नोट कर लिया है। मैं उस समय के स्लॉट देख रहा हूँ।"
                        : "I have noted " + safe(state.preferredTimeWindow) + ". Let me check slots for that time.";
            }
            return isHindi(state.language)
                    ? "मैं अभी उपलब्ध स्लॉट देख रहा हूँ।"
                    : "I’m checking the next available slots.";
        }
        if (state.timePromptCount >= 3) {
            return isHindi(state.language)
                    ? "मैं अभी उपलब्ध स्लॉट देख रहा हूँ।"
                    : "I’m checking the next available slots.";
        }
        return askTimePrompt(state.language);
    }

    private boolean hasResolvedTimePreference(CareAiState state) {
        return state != null && (StringUtils.hasText(state.preferredTimeWindow) || state.answeredTimePreference);
    }

    private boolean shouldAskTimePreference(CareAiState state) {
        if (state == null) {
            return false;
        }
        if (state.currentIntent != PatientPortalCareAiIntent.BOOK_APPOINTMENT
                && state.currentIntent != PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT) {
            return false;
        }
        return StringUtils.hasText(state.selectedDoctorId)
                && StringUtils.hasText(state.preferredDate)
                && !hasResolvedTimePreference(state)
                && !state.confirmationPending;
    }

    private String timePreferenceSlotUnavailablePrompt(CareAiState state) {
        if (StringUtils.hasText(state.preferredTimeWindow)) {
            return StringUtils.hasText(state.slotPromptLead)
                    ? state.slotPromptLead
                    : broadTimeUnavailablePrompt(state, state.preferredTimeWindow, List.of());
        }
        return nextTimePrompt(state);
    }

    private String previewNextQuestionAfterDate(CareAiState state) {
        if (StringUtils.hasText(state.dateResolutionIssue)) {
            return "ask-date";
        }
        if (!StringUtils.hasText(state.preferredDate)) {
            return "ask-date";
        }
        if (StringUtils.hasText(state.selectedSlot) || state.confirmationPending) {
            return "confirmation";
        }
        if (!state.slotOptions.isEmpty()) {
            return "choose-slot";
        }
        if (!StringUtils.hasText(state.preferredTimeWindow)) {
            return "ask-time";
        }
        return "slot-lookup";
    }

    private String topicSwitchPrompt(String language) {
        return isHindi(language)
                ? "ठीक है, मैंने मौजूदा बुकिंग बातचीत साफ़ कर दी है। मैं और किस चीज़ में मदद कर सकता हूँ?"
                : "Okay, I cleared the current booking flow. How else can I help?";
    }

    private String topicSwitchClarificationPrompt(String language) {
        return isHindi(language)
                ? "ठीक है। क्या आप इस बुकिंग फ्लो को रोकना चाहते हैं, या कोई दूसरा सवाल पूछना चाहते हैं?"
                : "Sure. Do you want to cancel this booking flow, or ask something else?";
    }

    private String postCompletionCourtesyPrompt(String language) {
        return isHindi(language)
                ? "धन्यवाद। अपना ध्यान रखिए।"
                : "You're welcome. Have a nice day!";
    }

    private String exactTimeUnavailablePrompt(CareAiState state, String preferredTimeWindow, List<PatientPortalDoctorSlotResponse> nearestSlots) {
        if (nearestSlots == null || nearestSlots.isEmpty()) {
            return unavailablePreferredWindowPrompt(state, preferredTimeWindow, List.of());
        }
        return isHindi(state.language)
                ? preferredTimeWindow + " पर सटीक स्लॉट उपलब्ध नहीं है। कृपया नज़दीकी विकल्पों में से चुनिए:"
                : "No exact slot is available at " + preferredTimeWindow + ". Please choose from the nearest available options:";
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
                ? "आपकी कोई आगामी अपॉइंटमेंट नहीं है।"
                : "You do not have any upcoming appointments.";
    }

    private String bookingFailedPrompt(String language, String errorMessage) {
        if (isHindi(language)) {
            return "मैं यह अनुरोध पूरा नहीं कर सका। कृपया दूसरा समय चुनें या क्लिनिक से संपर्क करें।";
        }
        return StringUtils.hasText(errorMessage)
                ? errorMessage
                : "I could not complete that request. Please choose another option or contact the clinic.";
    }

    private String invalidDatePrompt(String language, String issue) {
        if ("past".equals(issue)) {
            return isHindi(language)
                    ? "कृपया आज या आने वाली तारीख चुनें। पिछली तारीख पर अपॉइंटमेंट नहीं किया जा सकता।"
                    : "Please choose today or a future date. I cannot use a past date for this request.";
        }
        if ("ambiguous".equals(issue)) {
            return isHindi(language)
                    ? "कृपया तारीख थोड़ा स्पष्ट बताइए। क्या आपका मतलब 6 July 2026 है या 7 June 2026?"
                    : "Please clarify the date. Do you mean 6 July 2026 or 7 June 2026?";
        }
        return isHindi(language)
                ? "मैं वह तारीख समझ नहीं सका। कृपया 4 जून 2026, अगले शुक्रवार, या 04/06/2026 जैसे प्रारूप में बताइए।"
                : "I could not understand that date. Please try a format like 4 June 2026, next Friday, or 04/06/2026.";
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

    private void persistTurn(CareAiState state, PatientPortalCareAiMessageResponse response) {
        try {
            conversationPersistenceService.safeRecordTurn(new CareAiConversationTurnCommand(
                    RequestContextHolder.requireTenantId(),
                    state.lastChannel == null ? CareAiChannel.PATIENT_PORTAL_CHAT : state.lastChannel,
                    state.lastPatientId,
                    null,
                    state.lastExternalSessionId,
                    state.lastTransport == null ? CareAiTransport.HTTP_CHAT : state.lastTransport,
                    INSTANCE_ID,
                    state.lastUserMessage,
                    response.assistantMessage(),
                    state.currentIntent == null ? null : state.currentIntent.name(),
                    "{}",
                    "{}",
                    workflowMetadataJson(state),
                    state.actionCompleted ? response.assistantMessage() : null,
                    workflowConversationStatus(state),
                    workflowSnapshot(state, response.assistantMessage())
            ));
        } catch (RuntimeException ex) {
            log.warn("careai.persistence.patient-portal-hook.failed tenantId={} channel={} externalSessionId={} reason={}",
                    RequestContextHolder.get() == null ? null : RequestContextHolder.get().tenantId().value(),
                    state.lastChannel,
                    state.lastExternalSessionId,
                    ex.getMessage(), ex);
        } finally {
            state.pendingWorkflowEventType = null;
            state.pendingWorkflowEventPayloadJson = null;
        }
    }

    private CareAiConversationStatus workflowConversationStatus(CareAiState state) {
        if (state.handoffRequired) {
            return CareAiConversationStatus.ESCALATED;
        }
        if (state.actionCompleted) {
            return CareAiConversationStatus.COMPLETED;
        }
        return CareAiConversationStatus.ACTIVE;
    }

    private CareAiWorkflowSnapshot workflowSnapshot(CareAiState state, String assistantMessage) {
        CareAiWorkflowType workflowType = workflowType(state);
        if (workflowType == null) {
            return null;
        }
        CareAiWorkflowState workflowState;
        if (state.handoffRequired) {
            workflowState = CareAiWorkflowState.ESCALATED;
        } else if (state.actionCompleted) {
            workflowState = CareAiWorkflowState.COMPLETED;
        } else if (state.confirmationPending) {
            workflowState = CareAiWorkflowState.WAITING_CONFIRMATION;
        } else {
            workflowState = CareAiWorkflowState.COLLECTING_INFO;
        }
        return new CareAiWorkflowSnapshot(
                workflowType,
                workflowState,
                workflowContextJson(state),
                state.lastQuestionKey,
                state.repeatedQuestionCount,
                workflowEventType(state),
                StringUtils.hasText(state.pendingWorkflowEventPayloadJson) ? state.pendingWorkflowEventPayloadJson : workflowMetadataJson(state),
                null,
                state.confirmationPending ? workflowType.name() : null,
                state.confirmationPending ? confirmationScopeKey(state) : null,
                state.confirmationPending ? assistantMessage : null,
                state.confirmationPending ? workflowContextJson(state) : null,
                state.confirmationPending ? OffsetDateTime.now().plusMinutes(15) : null
        );
    }

    private CareAiWorkflowType workflowType(CareAiState state) {
        if (state.transientWorkflowType != null) {
            return state.transientWorkflowType;
        }
        PatientPortalCareAiIntent intent = state.currentIntent != null ? state.currentIntent : state.lastAction;
        if (intent == null && state.handoffRequired) {
            return CareAiWorkflowType.HUMAN_HANDOFF;
        }
        if (intent == null) {
            return null;
        }
        return switch (intent) {
            case BOOK_APPOINTMENT -> CareAiWorkflowType.BOOK_APPOINTMENT;
            case RESCHEDULE_APPOINTMENT -> CareAiWorkflowType.RESCHEDULE_APPOINTMENT;
            case CANCEL_APPOINTMENT -> CareAiWorkflowType.CANCEL_APPOINTMENT;
            case CHECK_APPOINTMENT, APPOINTMENT_STATUS -> CareAiWorkflowType.CHECK_APPOINTMENT;
            default -> null;
        };
    }

    private String workflowEventType(CareAiState state) {
        if (StringUtils.hasText(state.pendingWorkflowEventType)) {
            return state.pendingWorkflowEventType;
        }
        if (state.handoffRequired) {
            return "HANDOFF_REQUIRED";
        }
        if (state.actionCompleted && state.lastAction == PatientPortalCareAiIntent.BOOK_APPOINTMENT) {
            return "APPOINTMENT_BOOKED";
        }
        if (state.actionCompleted) {
            return "WORKFLOW_COMPLETED";
        }
        if (state.confirmationPending) {
            return "CONFIRMATION_PENDING";
        }
        return "WORKFLOW_PROGRESS";
    }

    private String inferQuestionKey(CareAiState state, String assistantMessage) {
        if (state.actionCompleted && state.currentIntent == null) {
            return null;
        }
        if (state.transientWorkflowType != null && state.currentIntent == null) {
            return null;
        }
        if (state.handoffRequired) {
            return "handoff";
        }
        if (state.confirmationPending) {
            return "confirmation";
        }
        if (!state.doctorOptions.isEmpty()) {
            return "choose-doctor";
        }
        if (!state.appointmentOptions.isEmpty()) {
            return "choose-appointment";
        }
        if (!state.slotOptions.isEmpty()) {
            return "choose-slot";
        }
        if (containsIgnoreCase(assistantMessage, "date")) {
            return "ask-date";
        }
        if (containsIgnoreCase(assistantMessage, "time")) {
            return "ask-time";
        }
        return state.currentIntent == null ? "ask-intent" : "workflow-progress";
    }

    private String confirmationScopeKey(CareAiState state) {
        return String.join("|",
                nullToBlank(state.selectedDoctorId),
                nullToBlank(state.selectedAppointmentId),
                nullToBlank(state.preferredDate),
                nullToBlank(state.selectedSlot),
                nullToBlank(state.currentIntent == null ? null : state.currentIntent.name()));
    }

    private String workflowContextJson(CareAiState state) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("intent", state.currentIntent == null ? null : state.currentIntent.name());
        context.put("requestedDoctorName", state.requestedDoctorName);
        context.put("requestedSpeciality", state.requestedSpeciality);
        context.put("doctorId", state.selectedDoctorId);
        context.put("doctorSlug", state.selectedDoctorSlug);
        context.put("doctorName", state.selectedDoctorName);
        context.put("speciality", state.selectedSpeciality);
        context.put("clinicId", state.selectedClinicId);
        context.put("tenantId", state.selectedTenantId);
        context.put("clinicSlug", state.selectedClinicSlug);
        context.put("clinicName", state.selectedClinicName);
        context.put("appointmentId", state.selectedAppointmentId);
        context.put("selectedAppointmentLabel", state.selectedAppointmentLabel);
        context.put("preferredDate", state.preferredDate);
        context.put("preferredDateExplicit", state.preferredDateExplicit);
        context.put("preferredTimeWindow", state.preferredTimeWindow);
        context.put("selectedSlot", state.selectedSlot);
        context.put("slotPromptLead", state.slotPromptLead);
        context.put("allSlotChoices", slotChoicesContext(state.allSlotChoices));
        context.put("shownSlotOffset", state.shownSlotOffset);
        context.put("slotChoices", slotChoicesContext(state));
        context.put("slotOptions", state.slotOptions);
        context.put("reason", state.reason);
        context.put("dateResolutionIssue", state.dateResolutionIssue);
        context.put("handoffReason", state.handoffReason);
        context.put("suspendedIntent", state.suspendedIntent);
        context.put("lastTopicClassification", state.lastTopicClassification == null ? null : state.lastTopicClassification.name());
        context.put("sideTopic", state.lastSideTopic);
        context.put("activeConfirmationScopeKey", state.activeConfirmationScopeKey);
        context.put("confirmationVersion", state.confirmationVersion);
        context.put("awaitingFreshConfirmation", state.awaitingFreshConfirmation);
        context.put("booked", state.booked);
        context.put("actionCompleted", state.actionCompleted);
        context.put("lastAction", state.lastAction == null ? null : state.lastAction.name());
        context.put("bookingStatus", state.bookingStatus);
        context.put("bookedAppointmentDate", state.bookedAppointmentDate);
        context.put("bookedAppointmentTime", state.bookedAppointmentTime);
        context.put("activeTaskId", state.activeTaskId);
        context.put("activeTaskType", state.activeTaskType == null ? null : state.activeTaskType.name());
        context.put("askedState", askedStateMap(state));
        context.put("answeredState", answeredStateMap(state));
        return toJson(context);
    }

    private String workflowMetadataJson(CareAiState state) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("language", state.language);
        metadata.put("bookingStatus", state.bookingStatus);
        metadata.put("bookedAppointmentDate", state.bookedAppointmentDate);
        metadata.put("bookedAppointmentTime", state.bookedAppointmentTime);
        metadata.put("sideTopic", state.lastSideTopic);
        metadata.put("activeTaskId", state.activeTaskId);
        metadata.put("activeTaskType", state.activeTaskType == null ? null : state.activeTaskType.name());
        return toJson(metadata);
    }

    private String taskMetadataJson(CareAiState state, CareAiReceptionistTaskType taskType) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("language", state.language);
        metadata.put("workflowType", workflowType(state) == null ? null : workflowType(state).name());
        metadata.put("taskType", taskType.name());
        metadata.put("requestedDoctorName", state.requestedDoctorName);
        metadata.put("selectedDoctorName", state.selectedDoctorName);
        metadata.put("preferredDate", state.preferredDate);
        metadata.put("preferredTimeWindow", state.preferredTimeWindow);
        metadata.put("selectedAppointmentId", state.selectedAppointmentId);
        metadata.put("selectedAppointmentLabel", state.selectedAppointmentLabel);
        return toJson(metadata);
    }

    private List<Map<String, Object>> slotChoicesContext(CareAiState state) {
        return slotChoicesContext(state.slotChoices);
    }

    private List<Map<String, Object>> slotChoicesContext(List<SlotChoice> slotChoices) {
        return slotChoices.stream()
                .map(choice -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("appointmentDate", choice.appointmentDate() == null ? null : choice.appointmentDate().toString());
                    row.put("slotTime", choice.slotTime() == null ? null : choice.slotTime().format(TIME_FORMATTER));
                    return row;
                })
                .toList();
    }

    private Map<String, Object> askedStateMap(CareAiState state) {
        Map<String, Object> asked = new LinkedHashMap<>();
        asked.put("doctor", state.askedDoctor);
        asked.put("date", state.askedDate);
        asked.put("timePreference", state.askedTimePreference);
        asked.put("slot", state.askedSlot);
        asked.put("confirmation", state.askedConfirmation);
        return asked;
    }

    private Map<String, Object> answeredStateMap(CareAiState state) {
        Map<String, Object> answered = new LinkedHashMap<>();
        answered.put("doctor", state.answeredDoctor);
        answered.put("date", state.answeredDate);
        answered.put("timePreference", state.answeredTimePreference);
        answered.put("slot", state.answeredSlot);
        answered.put("confirmation", state.answeredConfirmation);
        return answered;
    }

    private String toJson(Object value) {
        try {
            return CARE_AI_JSON.writeValueAsString(value);
        } catch (Exception ex) {
            log.debug("careai.json.serialize.failed reason={}", ex.toString());
            return "{}";
        }
    }

    private Map<String, Object> parseJsonMap(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return CARE_AI_JSON.readValue(value, JSON_MAP_TYPE);
        } catch (Exception ex) {
            log.debug("careai.json.parse.failed reason={}", ex.toString());
            return Map.of();
        }
    }

    private void hydrateStateFromPersistence(CareAiState state, CareAiChannel channel, UUID patientId, String externalSessionId) {
        if (state.persistenceHydrated) {
            return;
        }
        CareAiConversationSessionSnapshot snapshot = conversationPersistenceService.findLatestSessionSnapshot(
                RequestContextHolder.requireTenantId(),
                channel,
                patientId,
                externalSessionId,
                8
        );
        if (snapshot == null || snapshot.workflow() == null) {
            state.persistenceHydrated = true;
            return;
        }
        Map<String, Object> context = parseJsonMap(snapshot.workflow().getContextJson());
        state.persistedWorkflowContextJson = snapshot.workflow().getContextJson();
        state.currentConversationId = snapshot.conversation().getId();
        state.currentWorkflowId = snapshot.workflow().getId();
        state.recentMessages = snapshot.recentMessages().stream()
                .map(message -> message.getSpeaker() + ": " + message.getContent())
                .toList();
        mergePersistedValue(context, "intent", value -> {
            if (state.currentIntent == null && StringUtils.hasText(value)) {
                try {
                    state.currentIntent = PatientPortalCareAiIntent.parse(value);
                } catch (IllegalArgumentException ignored) {
                    // Ignore unknown historical values.
                }
            }
        });
        state.requestedDoctorName = coalesce(state.requestedDoctorName, stringValue(context, "requestedDoctorName"));
        state.requestedSpeciality = coalesce(state.requestedSpeciality, stringValue(context, "requestedSpeciality"));
        state.selectedDoctorId = coalesce(state.selectedDoctorId, stringValue(context, "doctorId"));
        state.selectedDoctorSlug = coalesce(state.selectedDoctorSlug, stringValue(context, "doctorSlug"));
        state.selectedDoctorName = coalesce(state.selectedDoctorName, stringValue(context, "doctorName"));
        state.selectedSpeciality = coalesce(state.selectedSpeciality, stringValue(context, "speciality"));
        state.selectedClinicId = coalesce(state.selectedClinicId, stringValue(context, "clinicId"));
        state.selectedTenantId = coalesce(state.selectedTenantId, stringValue(context, "tenantId"));
        state.selectedClinicSlug = coalesce(state.selectedClinicSlug, stringValue(context, "clinicSlug"));
        state.selectedClinicName = coalesce(state.selectedClinicName, stringValue(context, "clinicName"));
        state.selectedAppointmentId = coalesce(state.selectedAppointmentId, stringValue(context, "appointmentId"));
        state.selectedAppointmentLabel = coalesce(state.selectedAppointmentLabel, stringValue(context, "selectedAppointmentLabel"));
        state.preferredDate = coalesce(state.preferredDate, stringValue(context, "preferredDate"));
        state.preferredDateExplicit = state.preferredDateExplicit || booleanValue(context.get("preferredDateExplicit"));
        state.preferredTimeWindow = coalesce(state.preferredTimeWindow, stringValue(context, "preferredTimeWindow"));
        state.selectedSlot = coalesce(state.selectedSlot, stringValue(context, "selectedSlot"));
        state.slotPromptLead = coalesce(state.slotPromptLead, stringValue(context, "slotPromptLead"));
        hydrateAllSlotChoices(state, context.get("allSlotChoices"));
        state.shownSlotOffset = intValue(context.get("shownSlotOffset"));
        hydrateSlotChoices(state, context.get("slotChoices"));
        if (state.slotOptions.isEmpty()) {
            state.slotOptions = stringList(context.get("slotOptions"));
        }
        if (state.slotOptions.isEmpty() && !state.allSlotChoices.isEmpty()) {
            renderSlotPage(state, state.shownSlotOffset);
        }
        state.reason = coalesce(state.reason, stringValue(context, "reason"));
        state.dateResolutionIssue = coalesce(state.dateResolutionIssue, stringValue(context, "dateResolutionIssue"));
        state.handoffReason = coalesce(state.handoffReason, stringValue(context, "handoffReason"));
        state.suspendedIntent = coalesce(state.suspendedIntent, stringValue(context, "suspendedIntent"));
        state.lastSideTopic = coalesce(state.lastSideTopic, stringValue(context, "sideTopic"));
        String persistedTopicClassification = stringValue(context, "lastTopicClassification");
        if (state.lastTopicClassification == null && StringUtils.hasText(persistedTopicClassification)) {
            try {
                state.lastTopicClassification = CareAiTopicClassification.valueOf(persistedTopicClassification);
            } catch (IllegalArgumentException ignored) {
                // Ignore unknown historical values.
            }
        }
        state.activeConfirmationScopeKey = coalesce(state.activeConfirmationScopeKey, stringValue(context, "activeConfirmationScopeKey"));
        state.activeTaskId = parseUuid(stringValue(context, "activeTaskId"));
        String persistedTaskType = stringValue(context, "activeTaskType");
        if (state.activeTaskType == null && StringUtils.hasText(persistedTaskType)) {
            try {
                state.activeTaskType = CareAiReceptionistTaskType.valueOf(persistedTaskType);
            } catch (IllegalArgumentException ignored) {
                // Ignore unknown historical values.
            }
        }
        state.confirmationVersion = intValue(context.get("confirmationVersion"));
        state.awaitingFreshConfirmation = state.awaitingFreshConfirmation || booleanValue(context.get("awaitingFreshConfirmation"));
        state.booked = state.booked || booleanValue(context.get("booked"));
        state.actionCompleted = state.actionCompleted || booleanValue(context.get("actionCompleted"));
        if (state.lastAction == null && StringUtils.hasText(stringValue(context, "lastAction"))) {
            try {
                state.lastAction = PatientPortalCareAiIntent.parse(stringValue(context, "lastAction"));
            } catch (IllegalArgumentException ignored) {
                // Ignore unknown historical values.
            }
        }
        state.bookingStatus = coalesce(state.bookingStatus, stringValue(context, "bookingStatus"));
        state.bookedAppointmentDate = coalesce(state.bookedAppointmentDate, stringValue(context, "bookedAppointmentDate"));
        state.bookedAppointmentTime = coalesce(state.bookedAppointmentTime, stringValue(context, "bookedAppointmentTime"));
        hydrateAskedAnsweredState(state, context);
        if (!StringUtils.hasText(state.lastQuestionKey)) {
            state.lastQuestionKey = snapshot.workflow().getLastQuestionKey();
            state.repeatedQuestionCount = snapshot.workflow().getRepeatedQuestionCount();
        }
        if (snapshot.pendingConfirmation() != null && !state.awaitingFreshConfirmation) {
            state.confirmationPending = state.confirmationPending || snapshot.workflow().getState().equals(CareAiWorkflowState.WAITING_CONFIRMATION.name());
            state.activeConfirmationScopeKey = snapshot.pendingConfirmation().getScopeKey();
            state.confirmationVersion = Math.max(state.confirmationVersion, snapshot.pendingConfirmation().getVersion());
            if (state.pendingAction == null && state.currentIntent != null) {
                state.pendingAction = state.currentIntent;
            }
        }
        state.persistenceHydrated = true;
    }

    private void hydrateSlotChoices(CareAiState state, Object value) {
        if (!(value instanceof List<?> rows) || !state.slotChoices.isEmpty()) {
            return;
        }
        List<SlotChoice> hydrated = new ArrayList<>();
        for (Object row : rows) {
            Map<String, Object> map = nestedMap(row);
            LocalDate appointmentDate = parseIsoDate(stringValue(map, "appointmentDate"));
            String slotTimeValue = stringValue(map, "slotTime");
            if (appointmentDate == null || !StringUtils.hasText(slotTimeValue)) {
                continue;
            }
            try {
                hydrated.add(new SlotChoice(appointmentDate, LocalTime.parse(slotTimeValue, TIME_FORMATTER)));
            } catch (RuntimeException ignored) {
                // Ignore malformed persisted slot rows.
            }
        }
        if (!hydrated.isEmpty()) {
            state.slotChoices = hydrated;
            state.slotOptions = hydrated.stream().map(choice -> choice.slotTime().format(TIME_FORMATTER)).toList();
        }
    }

    private void hydrateAllSlotChoices(CareAiState state, Object value) {
        if (!(value instanceof List<?> rows) || !state.allSlotChoices.isEmpty()) {
            return;
        }
        List<SlotChoice> hydrated = new ArrayList<>();
        for (Object row : rows) {
            Map<String, Object> map = nestedMap(row);
            LocalDate appointmentDate = parseIsoDate(stringValue(map, "appointmentDate"));
            String slotTimeValue = stringValue(map, "slotTime");
            if (appointmentDate == null || !StringUtils.hasText(slotTimeValue)) {
                continue;
            }
            try {
                hydrated.add(new SlotChoice(appointmentDate, LocalTime.parse(slotTimeValue, TIME_FORMATTER)));
            } catch (RuntimeException ignored) {
                // Ignore malformed persisted slot rows.
            }
        }
        if (!hydrated.isEmpty()) {
            state.allSlotChoices = hydrated;
        }
    }

    private void hydrateAskedAnsweredState(CareAiState state, Map<String, Object> context) {
        Map<String, Object> asked = nestedMap(context.get("askedState"));
        Map<String, Object> answered = nestedMap(context.get("answeredState"));
        state.askedDoctor = state.askedDoctor || booleanValue(asked.get("doctor"));
        state.askedDate = state.askedDate || booleanValue(asked.get("date"));
        state.askedTimePreference = state.askedTimePreference || booleanValue(asked.get("timePreference"));
        state.askedSlot = state.askedSlot || booleanValue(asked.get("slot"));
        state.askedConfirmation = state.askedConfirmation || booleanValue(asked.get("confirmation"));
        state.answeredDoctor = state.answeredDoctor || booleanValue(answered.get("doctor"));
        state.answeredDate = state.answeredDate || booleanValue(answered.get("date"));
        state.answeredTimePreference = state.answeredTimePreference || booleanValue(answered.get("timePreference"));
        state.answeredSlot = state.answeredSlot || booleanValue(answered.get("slot"));
        state.answeredConfirmation = state.answeredConfirmation || booleanValue(answered.get("confirmation"));
    }

    private void mergePersistedValue(Map<String, Object> context, String key, java.util.function.Consumer<String> consumer) {
        consumer.accept(stringValue(context, key));
    }

    private String stringValue(Map<String, Object> context, String key) {
        Object value = context.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private Map<String, Object> nestedMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, entryValue) -> copy.put(String.valueOf(key), entryValue));
            return copy;
        }
        return Map.of();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<String> rows = new ArrayList<>();
        for (Object entry : values) {
            if (entry == null) {
                continue;
            }
            String text = String.valueOf(entry).trim();
            if (StringUtils.hasText(text)) {
                rows.add(text);
            }
        }
        return List.copyOf(rows);
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private String coalesce(String current, String persisted) {
        return StringUtils.hasText(current) ? current : persisted;
    }

    private void invalidatePendingConfirmation(CareAiState state, String reason) {
        if (!state.confirmationPending && !StringUtils.hasText(state.activeConfirmationScopeKey)) {
            return;
        }
        state.confirmationPending = false;
        state.pendingAction = null;
        state.awaitingFreshConfirmation = true;
        state.answeredConfirmation = false;
        queueWorkflowEvent(state, "CONFIRMATION_RESET", "{\"reason\":\"" + reason + "\"}");
    }

    private void queueWorkflowEvent(CareAiState state, String eventType, String payloadJson) {
        state.pendingWorkflowEventType = eventType;
        state.pendingWorkflowEventPayloadJson = payloadJson;
    }

    private CareAiTopicClassification classifyTopic(CareAiState state,
                                                    String message,
                                                    PatientPortalCareAiIntent classifiedIntent) {
        PatientPortalCareAiIntent normalizedIntent = PatientPortalCareAiIntent.normalize(classifiedIntent);
        if (state.currentIntent != null
                && !state.slotOptions.isEmpty()
                && (shouldAdvanceSlotOptions(state, message) || shouldRerenderSlotOptions(state, message))) {
            return CareAiTopicClassification.ACTIVE_WORKFLOW_CONTINUATION;
        }
        if (state.currentIntent != null
                && normalizedIntent != PatientPortalCareAiIntent.CANCEL_APPOINTMENT
                && isAmbiguousCancel(message)) {
            return CareAiTopicClassification.AMBIGUOUS_CANCEL;
        }
        if (state.currentIntent != null && asksClinicTiming(message, state.language)) {
            return CareAiTopicClassification.SIDE_QUESTION;
        }
        if (state.currentIntent != null && asksDoctorAvailability(message, state.language)) {
            return CareAiTopicClassification.SIDE_QUESTION;
        }
        if (state.currentIntent != null
                && normalizedIntent != null
                && normalizedIntent.isWorkflowIntent()
                && normalizedIntent != PatientPortalCareAiIntent.normalize(state.currentIntent)) {
            return normalizedIntent == PatientPortalCareAiIntent.CANCEL_APPOINTMENT
                    ? CareAiTopicClassification.CANCEL_EXISTING_APPOINTMENT
                    : CareAiTopicClassification.NEW_WORKFLOW;
        }
        if (state.currentIntent != null
                && (normalizedIntent == null || normalizedIntent == PatientPortalCareAiIntent.UNKNOWN)
                && wantsTopicSwitch(message, state.language)) {
            return CareAiTopicClassification.CANCEL_CURRENT_WORKFLOW;
        }
        return CareAiTopicClassification.ACTIVE_WORKFLOW_CONTINUATION;
    }

    private boolean asksClinicTiming(String message, String language) {
        String lower = message.toLowerCase(Locale.ROOT);
        return CLINIC_TIMING_KEYWORDS.stream().anyMatch(lower::contains)
                || (isHindi(language) && (message.contains("क्लिनिक") || message.contains("समय")));
    }

    private boolean asksDoctorAvailability(String message, String language) {
        String lower = message.toLowerCase(Locale.ROOT);
        return DOCTOR_AVAILABILITY_KEYWORDS.stream().anyMatch(lower::contains)
                || (isHindi(language) && (message.contains("उपलब्ध") || message.contains("स्लॉट")));
    }

    private boolean isAmbiguousCancel(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return AMBIGUOUS_CANCEL_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private String sideTopicResponse(CareAiState state, String message, PatientPortalCareAiPlannerDecision plannerDecision) {
        if (asksClinicTiming(message, state.language)
                || (plannerDecision != null && "CLINIC_TIMINGS".equalsIgnoreCase(plannerDecision.sideTopic()))) {
            state.lastSideTopic = "CLINIC_TIMINGS";
            return resumeWorkflowPrompt(state,
                    isHindi(state.language)
                            ? "क्लिनिक समय दिन के हिसाब से बदल सकते हैं। आज के सटीक समय के लिए कृपया क्लिनिक टीम से संपर्क करें।"
                            : "Clinic timings can vary by day. Please contact the clinic team for today's exact timings.");
        }
        if (asksDoctorAvailability(message, state.language)
                || (plannerDecision != null && "DOCTOR_AVAILABILITY".equalsIgnoreCase(plannerDecision.sideTopic()))) {
            state.lastSideTopic = "DOCTOR_AVAILABILITY";
            return resumeWorkflowPrompt(state, doctorAvailabilitySideAnswer(state));
        }
        if (plannerDecision != null && "APPOINTMENT_STATUS".equalsIgnoreCase(plannerDecision.sideTopic())) {
            state.lastSideTopic = "APPOINTMENT_STATUS";
            return resumeWorkflowPrompt(state, appointmentStatusSideAnswer(state));
        }
        return resumeWorkflowPrompt(state,
                isHindi(state.language)
                        ? "मैं उस सवाल का जवाब दे सकता हूँ और फिर बुकिंग जारी रख सकता हूँ।"
                        : "I can answer that and then continue the current booking flow.");
    }

    private String doctorAvailabilitySideAnswer(CareAiState state) {
        if (StringUtils.hasText(state.selectedDoctorId) && StringUtils.hasText(state.preferredDate)) {
            LocalDate date = LocalDate.parse(state.preferredDate);
            List<String> slots = loadDoctorSlots(state, state.selectedDoctorId, state.selectedClinicSlug, state.selectedTenantId, state.selectedClinicId, date).stream()
                    .filter(PatientPortalDoctorSlotResponse::selectable)
                    .map(slot -> slot.slotTime().format(TIME_FORMATTER))
                    .limit(3)
                    .toList();
            if (!slots.isEmpty()) {
                return "Available slots for " + safe(state.selectedDoctorName) + " on " + state.preferredDate + " include "
                        + String.join(", ", slots) + ".";
            }
        }
        if (StringUtils.hasText(state.selectedDoctorName)) {
            return "I can check exact availability once the date is confirmed for " + safe(state.selectedDoctorName) + ".";
        }
        return "I can check doctor availability once you tell me which doctor and date you want.";
    }

    private String appointmentStatusSideAnswer(CareAiState state) {
        careAiTrace("appointmentStatusSideAnswer", "enter", state,
                "patientId=" + patientPortalService.currentPatientId()
                        + " patientMobile=" + patientPortalService.currentPatientMobile());
        List<PatientPortalCareAiAppointmentOption> appointments = businessLookupService.upcomingAppointments();
        logAppointmentLookup("appointmentStatusSideAnswer", state, appointments);
        if (appointments.isEmpty()) {
            careAiTrace("appointmentStatusSideAnswer", "exit", state,
                    "service=businessLookupService.upcomingAppointments resultCount=0 reason=no-appointments-found");
            return noUpcomingAppointmentsPrompt(state.language);
        }
        PatientPortalCareAiAppointmentOption next = appointments.getFirst();
        careAiTrace("appointmentStatusSideAnswer", "exit", state,
                "service=businessLookupService.upcomingAppointments resultCount=" + appointments.size()
                        + " firstAppointmentId=" + next.appointmentId()
                        + " firstAppointmentDoctor=" + next.doctorName()
                        + " firstAppointmentTenantId=" + next.tenantId());
        return appointmentStatusPrompt(new AppointmentChoice(
                next.appointmentId(),
                next.doctorUserId(),
                next.doctorName(),
                next.tenantId(),
                next.clinicName(),
                next.appointmentDate(),
                next.appointmentTime(),
                next.status(),
                next.reason(),
                next.doctorName()
        ), state.language);
    }

    private void logAppointmentAction(String action, CareAiState state, List<PatientPortalCareAiAppointmentOption> appointments) {
        if (!log.isDebugEnabled()) {
            return;
        }
        List<PatientPortalCareAiAppointmentOption> safeAppointments = appointments == null ? List.of() : appointments;
        log.debug(
                "patient.portal.careai.appointment.action source=web-public-patient-careai action={} conversationTenantId={} tenantContextTenantId={} patientPortalSessionId={} patientId={} patientMobile={} currentIntent={} pendingAction={} confirmationPending={} selectedAppointmentId={} selectedDoctorId={} selectedDoctorSlug={} selectedClinicSlug={} selectedTenantId={} appointmentCount={} appointmentIds={} appointmentTenantIds={} appointmentStatuses={} appointmentDates={}",
                action,
                RequestContextHolder.requireTenantId(),
                RequestContextHolder.get() == null ? null : RequestContextHolder.get().tenantId().value(),
                RequestContextHolder.require().correlationId(),
                patientPortalService.currentPatientId(),
                patientPortalService.currentPatientMobile(),
                state.currentIntent,
                state.pendingAction,
                state.confirmationPending,
                state.selectedAppointmentId,
                state.selectedDoctorId,
                state.selectedDoctorSlug,
                state.selectedClinicSlug,
                state.selectedTenantId,
                safeAppointments.size(),
                safeAppointments.stream().map(PatientPortalCareAiAppointmentOption::appointmentId).toList(),
                safeAppointments.stream().map(PatientPortalCareAiAppointmentOption::tenantId).toList(),
                safeAppointments.stream().map(PatientPortalCareAiAppointmentOption::status).toList(),
                safeAppointments.stream().map(PatientPortalCareAiAppointmentOption::appointmentDate).toList()
        );
    }

    private void logDoctorLookup(
            String method,
            CareAiState state,
            String doctorQuery,
            String specialityQuery,
            List<DoctorChoice> doctors,
            List<DoctorChoice> matches,
            String reason
    ) {
        if (!log.isDebugEnabled()) {
            return;
        }
        List<DoctorChoice> safeDoctors = doctors == null ? List.of() : doctors;
        List<DoctorChoice> safeMatches = matches == null ? List.of() : matches;
        log.debug(
                "patient.portal.careai.doctor.lookup source=web-public-patient-careai method={} conversationTenantId={} tenantContextTenantId={} patientPortalSessionId={} patientId={} patientMobile={} requestedDoctorName={} requestedSpeciality={} selectedDoctorId={} selectedDoctorSlug={} selectedClinicId={} selectedTenantId={} selectedClinicSlug={} lookupMode={} doctorCount={} matchedCount={} doctorIds={} doctorNames={} clinicSlugs={} reason={}",
                method,
                RequestContextHolder.requireTenantId(),
                RequestContextHolder.get() == null ? null : RequestContextHolder.get().tenantId().value(),
                RequestContextHolder.require().correlationId(),
                patientPortalService.currentPatientId(),
                patientPortalService.currentPatientMobile(),
                doctorQuery,
                specialityQuery,
                state.selectedDoctorId,
                state.selectedDoctorSlug,
                state.selectedClinicId,
                state.selectedTenantId,
                state.selectedClinicSlug,
                StringUtils.hasText(state.selectedClinicSlug) ? "clinic-specific" : "cross-clinic",
                safeDoctors.size(),
                safeMatches.size(),
                safeDoctors.stream().map(DoctorChoice::publicDoctorId).toList(),
                safeDoctors.stream().map(DoctorChoice::doctorName).toList(),
                safeDoctors.stream().map(DoctorChoice::clinicSlug).toList(),
                reason
        );
    }

    private void logAppointmentLookup(String method, CareAiState state, List<PatientPortalCareAiAppointmentOption> appointments) {
        if (!log.isDebugEnabled()) {
            return;
        }
        List<PatientPortalCareAiAppointmentOption> safeAppointments = appointments == null ? List.of() : appointments;
        log.debug(
                "patient.portal.careai.appointment.lookup source=web-public-patient-careai method={} conversationTenantId={} tenantContextTenantId={} patientPortalSessionId={} patientId={} patientMobile={} clinicTenantsSearched={} appointmentCount={} appointmentIds={} appointmentTenantIds={} appointmentStatuses={} appointmentDates={} reason={}",
                method,
                RequestContextHolder.requireTenantId(),
                RequestContextHolder.get() == null ? null : RequestContextHolder.get().tenantId().value(),
                RequestContextHolder.require().correlationId(),
                patientPortalService.currentPatientId(),
                patientPortalService.currentPatientMobile(),
                safeAppointments.stream().map(PatientPortalCareAiAppointmentOption::tenantId).distinct().toList(),
                safeAppointments.size(),
                safeAppointments.stream().map(PatientPortalCareAiAppointmentOption::appointmentId).toList(),
                safeAppointments.stream().map(PatientPortalCareAiAppointmentOption::tenantId).toList(),
                safeAppointments.stream().map(PatientPortalCareAiAppointmentOption::status).toList(),
                safeAppointments.stream().map(PatientPortalCareAiAppointmentOption::appointmentDate).toList(),
                safeAppointments.isEmpty() ? "no-appointments-found" : null
        );
    }

    private void logSlotLookupResponse(
            String method,
            CareAiState state,
            String publicDoctorId,
            LocalDate date,
            List<PatientPortalDoctorSlotResponse> slots
    ) {
        if (!log.isDebugEnabled()) {
            return;
        }
        List<PatientPortalDoctorSlotResponse> safeSlots = slots == null ? List.of() : slots;
        log.debug(
                "patient.portal.careai.slot.lookup.response source=web-public-patient-careai method={} conversationTenantId={} tenantContextTenantId={} patientPortalSessionId={} patientId={} patientMobile={} doctorId={} doctorName={} selectedClinicSlug={} selectedTenantId={} selectedClinicId={} date={} slotCount={} slotTimes={}",
                method,
                RequestContextHolder.requireTenantId(),
                RequestContextHolder.get() == null ? null : RequestContextHolder.get().tenantId().value(),
                RequestContextHolder.require().correlationId(),
                patientPortalService.currentPatientId(),
                patientPortalService.currentPatientMobile(),
                publicDoctorId,
                state == null ? null : state.selectedDoctorName,
                state == null ? null : state.selectedClinicSlug,
                state == null ? null : state.selectedTenantId,
                state == null ? null : state.selectedClinicId,
                date,
                safeSlots.size(),
                safeSlots.stream().map(slot -> slot.slotTime().format(TIME_FORMATTER)).toList()
        );
    }

    private void logSlotLookupRequest(String method, CareAiState state, String publicDoctorId, LocalDate date) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "patient.portal.careai.slot.lookup.request source=web-public-patient-careai method={} conversationTenantId={} tenantContextTenantId={} patientPortalSessionId={} patientId={} patientMobile={} doctorId={} doctorName={} selectedClinicSlug={} selectedTenantId={} selectedClinicId={} lookupMode={} date={}",
                method,
                RequestContextHolder.requireTenantId(),
                RequestContextHolder.get() == null ? null : RequestContextHolder.get().tenantId().value(),
                RequestContextHolder.require().correlationId(),
                patientPortalService.currentPatientId(),
                patientPortalService.currentPatientMobile(),
                publicDoctorId,
                state == null ? null : state.selectedDoctorName,
                state == null ? null : state.selectedClinicSlug,
                state == null ? null : state.selectedTenantId,
                state == null ? null : state.selectedClinicId,
                state == null || !StringUtils.hasText(state.selectedClinicSlug) ? "cross-clinic" : "clinic-specific",
                date
        );
    }

    private void logBookingOrMutationRequest(String method, CareAiState state) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "patient.portal.careai.booking.action source=web-public-patient-careai method={} conversationTenantId={} tenantContextTenantId={} patientPortalSessionId={} patientId={} patientMobile={} selectedDoctorId={} selectedDoctorSlug={} selectedDoctorName={} selectedClinicSlug={} selectedTenantId={} selectedClinicId={} selectedDate={} selectedSlot={} selectedAppointmentId={}",
                method,
                RequestContextHolder.requireTenantId(),
                RequestContextHolder.get() == null ? null : RequestContextHolder.get().tenantId().value(),
                RequestContextHolder.require().correlationId(),
                patientPortalService.currentPatientId(),
                patientPortalService.currentPatientMobile(),
                state.selectedDoctorId,
                state.selectedDoctorSlug,
                state.selectedDoctorName,
                state.selectedClinicSlug,
                state.selectedTenantId,
                state.selectedClinicId,
                state.preferredDate,
                state.selectedSlot,
                state.selectedAppointmentId
        );
    }

    private void clearLookupCaches(CareAiState state) {
        if (state == null) {
            return;
        }
        state.doctorLookupCache.clear();
        state.clinicLookupCache.clear();
        state.appointmentLookupCache.clear();
    }

    private Map<String, Object> doctorDebugMap(DoctorChoice doctor) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("doctorId", doctor.publicDoctorId());
        result.put("doctorName", doctor.doctorName());
        result.put("clinicId", doctor.clinicId());
        result.put("clinicName", doctor.clinicName());
        result.put("tenantId", doctor.tenantId());
        result.put("speciality", doctor.speciality());
        return result;
    }

    private Map<String, Object> appointmentDebugMap(PatientPortalCareAiAppointmentOption appointment) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appointmentId", appointment.appointmentId());
        result.put("doctorName", appointment.doctorName());
        result.put("clinicName", appointment.clinicName());
        result.put("tenantId", appointment.tenantId());
        result.put("status", appointment.status());
        result.put("dateTime", appointment.appointmentDate() == null ? null : appointment.appointmentDate().toString() + "T" + appointment.appointmentTime());
        return result;
    }

    private void careAiTrace(String method, String phase, CareAiState state, String details) {
        if (!log.isInfoEnabled()) {
            return;
        }
        log.info(
                "CAREAI_TRACE method={} phase={} conversationTenantId={} tenantContextTenantId={} patientPortalSessionId={} patientId={} patientMobile={} activeWorkflow={} lastQuestionKey={} details={}",
                method,
                phase,
                RequestContextHolder.requireTenantId(),
                RequestContextHolder.get() == null ? null : RequestContextHolder.get().tenantId().value(),
                state == null ? null : state.lastExternalSessionId,
                state == null ? null : state.lastPatientId,
                patientPortalService.currentPatientMobile(),
                state == null || state.currentIntent == null ? null : state.currentIntent.name(),
                state == null ? null : state.lastQuestionKey,
                details
        );
    }

    private String summarizeDoctors(List<DoctorChoice> doctors) {
        if (doctors == null || doctors.isEmpty()) {
            return "[]";
        }
        return doctors.stream()
                .limit(5)
                .map(choice -> choice.publicDoctorId() + ":" + choice.doctorName())
                .toList()
                .toString();
    }

    private String summarizeAppointments(List<PatientPortalCareAiAppointmentOption> appointments) {
        if (appointments == null || appointments.isEmpty()) {
            return "[]";
        }
        return appointments.stream()
                .limit(5)
                .map(option -> option.appointmentId() + ":" + option.doctorName() + ":" + option.tenantId() + ":" + option.status())
                .toList()
                .toString();
    }

    private String summarizeSlots(List<PatientPortalDoctorSlotResponse> slots) {
        if (slots == null || slots.isEmpty()) {
            return "[]";
        }
        return slots.stream()
                .limit(5)
                .map(slot -> slot.appointmentDate() + ":" + slot.slotTime() + ":" + slot.status() + ":" + slot.selectable())
                .toList()
                .toString();
    }

    private String resumeWorkflowPrompt(CareAiState state, String sideAnswer) {
        if (state.currentIntent == null) {
            return sideAnswer;
        }
        return sideAnswer + " "
                + (isHindi(state.language)
                ? "क्या आप मौजूदा बुकिंग जारी रखना चाहते हैं?"
                : "Would you like to continue the current booking?");
    }

    private String ambiguousCancelPrompt(CareAiState state) {
        return isHindi(state.language)
                ? "क्या आप मौजूदा बुकिंग बातचीत रोकना चाहते हैं, या किसी मौजूदा अपॉइंटमेंट को रद्द करना चाहते हैं?"
                : "Do you want to stop the current booking flow, or cancel an existing appointment?";
    }

    private String broadTimeUnavailablePrompt(CareAiState state, String preferredTimeWindow, List<PatientPortalDoctorSlotResponse> nearestSlots) {
        if (nearestSlots == null || nearestSlots.isEmpty()) {
            return unavailablePreferredWindowPrompt(state, preferredTimeWindow, List.of());
        }
        return isHindi(state.language)
                ? preferredTimeWindow + " में स्लॉट उपलब्ध नहीं मिले। नज़दीकी विकल्प ये हैं:"
                : "I couldn't find an " + preferredTimeWindow + " slot. Here are the nearest available options:";
    }

    private String unavailablePreferredWindowPrompt(CareAiState state, String preferredTimeWindow, List<?> nearestSlots) {
        String doctorName = StringUtils.hasText(state.selectedDoctorName) ? state.selectedDoctorName : "the doctor";
        String preferredDateLabel = humanReadablePreferredDate(state.preferredDate);
        if (isHindi(state.language)) {
            return preferredTimeWindow + " में " + doctorName + " के लिए " + preferredDateLabel
                    + " पर स्लॉट नहीं मिला। क्या आप सुबह, दोपहर, रात, या कोई दूसरी तारीख चाहेंगे?";
        }
        return "I couldn't find an " + preferredTimeWindow + " slot for " + doctorName + " on "
                + preferredDateLabel + ". Would you like morning, afternoon, night, or another date?";
    }

    private String humanReadablePreferredDate(String preferredDate) {
        LocalDate parsed = parseIsoDate(preferredDate);
        if (parsed == null) {
            return safe(preferredDate);
        }
        LocalDate today = currentClinicDate();
        if (parsed.equals(today)) {
            return "today";
        }
        if (parsed.equals(today.plusDays(1))) {
            return "tomorrow";
        }
        return parsed.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);
    }

    private String reconfirmationPrompt(CareAiState state) {
        if (StringUtils.hasText(state.selectedSlot) && state.currentIntent == PatientPortalCareAiIntent.BOOK_APPOINTMENT) {
            state.confirmationPending = true;
            state.pendingAction = state.currentIntent;
            state.awaitingFreshConfirmation = false;
            state.activeConfirmationScopeKey = confirmationScopeKey(state);
            return bookingConfirmationPrompt(state);
        }
        if (StringUtils.hasText(state.selectedSlot) && state.currentIntent == PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT) {
            state.confirmationPending = true;
            state.pendingAction = state.currentIntent;
            state.awaitingFreshConfirmation = false;
            state.activeConfirmationScopeKey = confirmationScopeKey(state);
            return rescheduleConfirmationPrompt(state);
        }
        return StringUtils.hasText(state.preferredDate) ? slotChoicePrompt(state) : askIntentPrompt(state.language);
    }

    private String guardRepeatedQuestion(CareAiState state, String assistantMessage) {
        String questionKey = inferQuestionKey(state, assistantMessage);
        if (!StringUtils.hasText(questionKey) || !questionKey.equals(state.lastQuestionKey)) {
            return assistantMessage;
        }
        if ("ask-time".equals(questionKey)) {
            return state.repeatedQuestionCount == 0
                    ? clarifiedTimePrompt(state.language)
                    : selectableTimePrompt(state.language);
        }
        if ("ask-date".equals(questionKey)) {
            return state.repeatedQuestionCount == 0
                    ? clarifiedDatePrompt(state.language)
                    : askDatePrompt(state.language);
        }
        return assistantMessage;
    }

    private String clarifiedTimePrompt(String language) {
        return isHindi(language)
                ? "कृपया समय थोड़ा स्पष्ट बताइए, जैसे सुबह, दोपहर, शाम, रात, 10 बजे, या 7 PM."
                : "Please tell me the time preference more clearly, such as morning, afternoon, evening, night, 10 AM, or 7 PM.";
    }

    private String selectableTimePrompt(String language) {
        return numberedChoicePrompt(
                language,
                "Please choose a time preference:",
                "कृपया समय वरीयता चुनिए:",
                List.of("Morning", "Afternoon", "Evening", "Night")
        );
    }

    private String clarifiedDatePrompt(String language) {
        return isHindi(language)
                ? "कृपया तारीख 7 June 2026, अगले शुक्रवार, या 07/06/2026 जैसे रूप में बताइए।"
                : "Please tell me the date in a format like 7 June 2026, next Friday, or 07/06/2026.";
    }

    private void markAskedState(CareAiState state, String questionKey) {
        if ("choose-doctor".equals(questionKey) || "ask-doctor".equals(questionKey)) {
            state.askedDoctor = true;
        } else if ("ask-date".equals(questionKey)) {
            state.askedDate = true;
        } else if ("ask-time".equals(questionKey)) {
            state.askedTimePreference = true;
        } else if ("choose-slot".equals(questionKey)) {
            state.askedSlot = true;
        } else if ("confirmation".equals(questionKey)) {
            state.askedConfirmation = true;
        }
    }

    private void markAnsweredFacts(CareAiState state) {
        state.answeredDoctor = StringUtils.hasText(state.selectedDoctorId) || StringUtils.hasText(state.requestedDoctorName);
        state.answeredDate = StringUtils.hasText(state.preferredDate);
        state.answeredTimePreference = StringUtils.hasText(state.preferredTimeWindow);
        state.answeredSlot = StringUtils.hasText(state.selectedSlot);
        state.answeredConfirmation = state.actionCompleted;
        if (state.confirmationPending) {
            state.activeConfirmationScopeKey = confirmationScopeKey(state);
        }
    }

    private CareAiTopicClassification inferResponseTopicClassification(CareAiState state, String questionKey) {
        if (StringUtils.hasText(state.lastSideTopic)) {
            return CareAiTopicClassification.SIDE_QUESTION;
        }
        if ("confirmation".equals(questionKey) || "choose-slot".equals(questionKey) || "ask-time".equals(questionKey)) {
            return CareAiTopicClassification.ACTIVE_WORKFLOW_CONTINUATION;
        }
        return state.lastTopicClassification;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value : "the clinic";
    }

    private void clearCurrentConversation(CareAiState state) {
        PatientPortalCareAiIntent previousIntent = state.currentIntent;
        resetWorkflowState(state, previousIntent == null ? PatientPortalCareAiIntent.BOOK_APPOINTMENT : previousIntent);
        state.currentIntent = null;
        state.requestedDoctorName = null;
        state.requestedSpeciality = null;
        state.requestedClinicName = null;
        state.selectedDoctorId = null;
        state.selectedDoctorSlug = null;
        state.selectedDoctorName = null;
        state.selectedSpeciality = null;
        state.selectedClinicId = null;
        state.selectedTenantId = null;
        state.selectedClinicSlug = null;
        state.selectedClinicName = null;
        state.preferredDate = null;
        state.dateResolutionIssue = null;
        state.preferredTimeWindow = null;
        state.reason = null;
        state.slotPromptLead = null;
        state.timePromptCount = 0;
        state.clinicChoices = List.of();
        state.clinicOptions = List.of();
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
        state.lastSideTopic = null;
        state.suspendedIntent = null;
        state.awaitingFreshConfirmation = false;
    }

    private boolean detectHumanHandoffRequest(String transcript, String language) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        if (HUMAN_HANDOFF_KEYWORDS.stream().anyMatch(lower::contains)) {
            return true;
        }
        return isHindi(language) && (transcript.contains("रिसेप्शन") || transcript.contains("स्टाफ") || transcript.contains("इंसान") || transcript.contains("क्लिनिक से जोड़"));
    }

    private boolean detectCallbackRequest(String transcript, String language) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        if (CALLBACK_REQUEST_KEYWORDS.stream().anyMatch(lower::contains)) {
            return true;
        }
        return isHindi(language) && (transcript.contains("कॉल बैक") || transcript.contains("फोन करें") || transcript.contains("बाद में कॉल"));
    }

    private String detectHumanHandoffReason(String transcript) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        if (lower.contains("not working")) {
            return "this-is-not-working";
        }
        if (lower.contains("receptionist") || lower.contains("staff") || lower.contains("clinic")) {
            return "requested-receptionist";
        }
        return "requested-human-help";
    }

    private CareAiReceptionistTaskPriority handoffPriority(String transcript) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        if (lower.contains("urgent") || lower.contains("asap") || lower.contains("not working")) {
            return CareAiReceptionistTaskPriority.HIGH;
        }
        return CareAiReceptionistTaskPriority.MEDIUM;
    }

    private CareAiReceptionistTaskPriority callbackPriority(String transcript, CallbackPreference callbackPreference) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        if (lower.contains("urgent") || lower.contains("asap")) {
            return CareAiReceptionistTaskPriority.HIGH;
        }
        if (callbackPreference != null && callbackPreference.dueAt() != null && callbackPreference.dueAt().isBefore(OffsetDateTime.now().plusHours(4))) {
            return CareAiReceptionistTaskPriority.HIGH;
        }
        return CareAiReceptionistTaskPriority.MEDIUM;
    }

    private CareAiReceptionistTaskPriority emergencyPriority(String transcript) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        if (lower.contains("unconscious") || lower.contains("difficulty breathing") || lower.contains("severe bleeding")) {
            return CareAiReceptionistTaskPriority.URGENT;
        }
        return CareAiReceptionistTaskPriority.HIGH;
    }

    private CallbackPreference extractCallbackTimePreference(String transcript, String language) {
        DateResolution date = findPreferredDate(transcript, language);
        String timePreference = findPreferredTimeWindow(transcript, language, null);
        if (!StringUtils.hasText(date.date()) && !StringUtils.hasText(timePreference)) {
            return new CallbackPreference(null, null);
        }
        String dateLabel = StringUtils.hasText(date.date()) ? humanReadablePreferredDate(date.date()) : null;
        String label;
        if (StringUtils.hasText(dateLabel) && StringUtils.hasText(timePreference)) {
            label = dateLabel + " " + timePreference;
        } else {
            label = StringUtils.hasText(dateLabel) ? dateLabel : timePreference;
        }
        OffsetDateTime dueAt = null;
        if (StringUtils.hasText(date.date())) {
            LocalDate callbackDate = LocalDate.parse(date.date());
            if (isExactTime(timePreference)) {
                dueAt = callbackDate.atTime(LocalTime.parse(timePreference, TIME_FORMATTER)).atZone(currentClinicZone()).toOffsetDateTime();
            }
        }
        return new CallbackPreference(label, dueAt);
    }

    private String humanHandoffAcknowledgement(String language) {
        return isHindi(language)
                ? "मैंने हमारी रिसेप्शन टीम के लिए एक अनुरोध बना दिया है। क्लिनिक टीम का कोई सदस्य जल्द आपकी मदद करेगा।"
                : "I’ve created a request for our receptionist. Someone from the clinic team will help you shortly.";
    }

    private String callbackAcknowledgement(String language, String callbackTimePreference) {
        if (StringUtils.hasText(callbackTimePreference)) {
            return isHindi(language)
                    ? "मैंने " + callbackTimePreference + " के लिए कॉलबैक अनुरोध बना दिया है। क्लिनिक टीम आपसे संपर्क करेगी।"
                    : "I’ve created a callback request for " + callbackTimePreference + ". The clinic team will contact you.";
        }
        return isHindi(language)
                ? "मैंने कॉलबैक अनुरोध बना दिया है। क्लिनिक टीम आपसे संपर्क करेगी।"
                : "I’ve created a callback request. The clinic team will contact you.";
    }

    private String taskEventPayloadJson(
            CareAiState state,
            UUID taskId,
            CareAiReceptionistTaskType taskType,
            String reason
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        payload.put("taskType", taskType == null ? null : taskType.name());
        payload.put("reason", reason);
        payload.put("channel", state.lastChannel == null ? null : state.lastChannel.name());
        payload.put("latestUserMessage", state.lastUserMessage);
        return toJson(payload);
    }

    private UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private DateResolution resolveAbsoluteDate(LocalDate date, boolean explicit) {
        if (date == null) {
            return DateResolution.invalid("invalid");
        }
        if (date.isBefore(currentClinicDate())) {
            return DateResolution.invalid("past");
        }
        return DateResolution.valid(date.toString(), explicit);
    }

    private LocalDate parseIsoDate(String value) {
        try {
            return LocalDate.parse(value, STRICT_ISO_DATE);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private LocalDate parseMonthNameDate(String dayValue, String monthValue, String yearValue) {
        Month month = MONTH_NAME_MAP.get(monthValue.toLowerCase(Locale.ROOT));
        if (month == null) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(yearValue), month, Integer.parseInt(dayValue));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private LocalDate parseSlashDate(String dayValue, String monthValue, String yearValue) {
        try {
            int first = Integer.parseInt(dayValue);
            int second = Integer.parseInt(monthValue);
            int year = Integer.parseInt(yearValue);
            if (first > 12 && second <= 12) {
                return LocalDate.of(year, second, first);
            }
            if (second > 12 && first <= 12) {
                return LocalDate.of(year, first, second);
            }
            if (first <= 12 && second <= 12) {
                return null;
            }
            return LocalDate.of(year, second, first);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private LocalDate parseMonthNameDateWithoutYear(String firstValue, String secondValue) {
        Month month = MONTH_NAME_MAP.get(secondValue.toLowerCase(Locale.ROOT));
        if (month != null) {
            return resolveMonthDayWithoutYear(month, firstValue);
        }
        month = MONTH_NAME_MAP.get(firstValue.toLowerCase(Locale.ROOT));
        if (month != null) {
            return resolveMonthDayWithoutYear(month, secondValue);
        }
        return null;
    }

    private LocalDate resolveMonthDayWithoutYear(Month month, String dayValue) {
        try {
            int day = Integer.parseInt(dayValue);
            LocalDate today = currentClinicDate();
            for (int yearOffset = 0; yearOffset < 5; yearOffset++) {
                try {
                    LocalDate candidate = LocalDate.of(today.getYear() + yearOffset, month, day);
                    if (!candidate.isBefore(today)) {
                        return candidate;
                    }
                } catch (RuntimeException ignored) {
                    // Try the next year; month/day may only be valid in leap years.
                }
            }
            return null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private LocalDate resolveThisWeekday(LocalDate today, DayOfWeek dayOfWeek) {
        LocalDate candidate = today.with(TemporalAdjusters.nextOrSame(dayOfWeek));
        if (candidate.getDayOfWeek() == dayOfWeek) {
            return candidate;
        }
        return today.with(TemporalAdjusters.next(dayOfWeek));
    }

    private static Map<String, Month> monthNameMap() {
        Map<String, Month> map = new HashMap<>();
        for (Month month : Month.values()) {
            map.put(month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH).toLowerCase(Locale.ROOT), month);
            map.put(month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH).toLowerCase(Locale.ROOT), month);
        }
        map.put("sept", Month.SEPTEMBER);
        return Map.copyOf(map);
    }

    private ZoneId currentClinicZone() {
        if (RequestContextHolder.get() == null) {
            return ZoneId.of("Asia/Kolkata");
        }
        return clinicTimeZoneResolver.resolve(RequestContextHolder.requireTenantId());
    }

    private LocalDate currentClinicDate() {
        return LocalDate.now(currentClinicZone());
    }

    private LocalTime currentClinicTime() {
        return LocalTime.now(currentClinicZone()).withSecond(0).withNano(0);
    }

    private CareAiState currentState() {
        return sessions.computeIfAbsent(currentSessionKey(), key -> new CareAiState());
    }

    private static String buildInstanceId() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":" + ManagementFactory.getRuntimeMXBean().getName();
        } catch (Exception ex) {
            return "api-bff:" + ManagementFactory.getRuntimeMXBean().getName();
        }
    }

    private static final class CareAiState {
        private String language = "en";
        private PatientPortalCareAiIntent currentIntent;
        private String requestedDoctorName;
        private String requestedSpeciality;
        private String requestedClinicName;
        private String selectedDoctorId;
        private String selectedDoctorSlug;
        private String selectedDoctorName;
        private String selectedSpeciality;
        private String selectedClinicId;
        private String selectedTenantId;
        private String selectedClinicSlug;
        private String selectedClinicName;
        private String selectedAppointmentId;
        private String selectedAppointmentLabel;
        private String selectedAppointmentReason;
        private List<ClinicChoice> clinicChoices = List.of();
        private List<String> clinicOptions = List.of();
        private String preferredDate;
        private boolean preferredDateExplicit;
        private String dateResolutionIssue;
        private String preferredTimeWindow;
        private String reason;
        private String selectedSlot;
        private String slotPromptLead;
        private List<DoctorChoice> doctorChoices = List.of();
        private List<String> doctorOptions = List.of();
        private List<AppointmentChoice> appointmentOptions = List.of();
        private List<SlotChoice> allSlotChoices = List.of();
        private int shownSlotOffset;
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
        private int timePromptCount;
        private boolean askedDoctor;
        private boolean askedDate;
        private boolean askedTimePreference;
        private boolean askedSlot;
        private boolean askedConfirmation;
        private boolean answeredDoctor;
        private boolean answeredDate;
        private boolean answeredTimePreference;
        private boolean answeredSlot;
        private boolean answeredConfirmation;
        private String lastUserMessage;
        private UUID lastPatientId;
        private CareAiChannel lastChannel;
        private CareAiTransport lastTransport;
        private String lastExternalSessionId;
        private String lastQuestionKey;
        private int repeatedQuestionCount;
        private boolean persistenceHydrated;
        private String persistedWorkflowContextJson;
        private List<String> recentMessages = List.of();
        private UUID currentConversationId;
        private UUID currentWorkflowId;
        private String suspendedIntent;
        private CareAiTopicClassification lastTopicClassification;
        private String lastSideTopic;
        private boolean awaitingFreshConfirmation;
        private String activeConfirmationScopeKey;
        private int confirmationVersion;
        private CareAiWorkflowType transientWorkflowType;
        private UUID activeTaskId;
        private CareAiReceptionistTaskType activeTaskType;
        private String pendingWorkflowEventType;
        private String pendingWorkflowEventPayloadJson;
        private final Map<String, List<DoctorChoice>> doctorLookupCache = new HashMap<>();
        private final Map<String, List<ClinicChoice>> clinicLookupCache = new HashMap<>();
        private final Map<String, List<AppointmentChoice>> appointmentLookupCache = new HashMap<>();
    }

    private record CallbackPreference(String label, OffsetDateTime dueAt) {
    }

    private record SessionKey(UUID tenantId, UUID appUserId) {
    }

    private record DoctorChoice(
            String publicDoctorId,
            String doctorSlug,
            String doctorName,
            String speciality,
            String clinicId,
            String tenantId,
            String clinicSlug,
            String clinicName,
            String label
    ) {
    }

    private record ClinicChoice(
            String clinicSlug,
            String clinicName,
            String area,
            String city,
            String tenantId,
            String clinicId,
            String label
    ) {
    }

    private record AppointmentChoice(
            UUID appointmentId,
            UUID doctorUserId,
            String doctorName,
            UUID tenantId,
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

    private record DateResolution(String date, String issue, boolean explicit) {
        private static DateResolution valid(String date) {
            return new DateResolution(date, null, true);
        }

        private static DateResolution valid(String date, boolean explicit) {
            return new DateResolution(date, null, explicit);
        }

        private static DateResolution invalid(String issue) {
            return new DateResolution(null, issue, false);
        }

        private static DateResolution none() {
            return new DateResolution(null, null, false);
        }
    }
}
