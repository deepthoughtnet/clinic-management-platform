package com.deepthoughtnet.clinic.api.patientportal.careai;

import com.deepthoughtnet.clinic.api.careai.CareAiTaskNotificationService;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentConfirmationResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorAvailabilityResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorAvailabilityDayResponse;
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
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceDataService;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.Clock;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.function.Consumer;
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

    private static final List<String> POSITIVE_CONFIRMATIONS = List.of("yes", "confirm", "book it", "go ahead", "that's fine", "yes please", "okay book it", "ok book it");
    private static final List<String> POSITIVE_CONFIRMATIONS_HI = List.of("हाँ", "हां", "ठीक है", "बुक कर दीजिए", "कन्फर्म", "सही है");
    private static final List<String> NEGATIVE_CONFIRMATIONS = List.of("no", "don't confirm", "do not confirm", "not okay", "don't book", "do not book", "cancel that", "another slot", "different slot", "different time", "change slot", "not this one");
    private static final List<String> NEGATIVE_CONFIRMATIONS_HI = List.of("नहीं", "दूसरा स्लॉट", "दूसरा समय", "दूसरे समय");
    private static final List<String> GREETING_KEYWORDS = List.of("hello", "hi", "hey", "good morning", "good afternoon", "good evening");
    private static final List<String> THANK_YOU_KEYWORDS = List.of("thank you", "thanks", "thankyou", "thank u");
    private static final List<String> GOODBYE_KEYWORDS = List.of("bye", "goodbye", "see you", "take care", "have a nice day", "have nice day");
    private static final List<String> ABANDON_CONVERSATION_KEYWORDS = List.of("never mind", "nevermind", "no thanks", "no thank you", "that's all", "thats all", "stop", "cancel this");
    private static final List<String> ANOTHER_DATE_KEYWORDS = List.of("another date", "different date", "some other day", "next available date", "any other date", "another day", "provide me a slot for another date");
    private static final List<String> ANOTHER_TIME_KEYWORDS = List.of("another time", "different time", "some other time", "any other time");
    private static final List<String> BOOKING_INTENT_KEYWORDS = List.of("book appointment", "book", "need doctor", "want consultation", "schedule");
    private static final List<String> BOOKING_INTENT_KEYWORDS_HI = List.of(
            "अपॉइंटमेंट बुक करनी है",
            "appointment book karni hai",
            "appointment book karna hai",
            "डॉक्टर से मिलना है",
            "डॉक्टर दिखाना है",
            "डॉक्टर दिखाने हैं"
    );
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
    private static final List<String> TOPIC_SWITCH_KEYWORDS_HI = List.of(
            "विषय बदलें",
            "बुकिंग भूल जाओ",
            "शुरू से",
            "कुछ और बात",
            "टॉपिक चेंज",
            "बातचीत बदलो",
            "नया शुरू करो",
            "चैट बदलो"
    );
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
    private static final ThreadLocal<CareAiChannel> ACTIVE_CHANNEL = new ThreadLocal<>();
    private final PatientPortalService patientPortalService;
    private final ClinicTimeZoneResolver clinicTimeZoneResolver;
    private final PatientPortalCareAiPlanner planner;
    private final CareAiConversationPersistenceService conversationPersistenceService;
    private final CareAiReceptionistTaskService receptionistTaskService;
    private final CareAiTaskNotificationService taskNotificationService;
    private final PublicCatalogFacade publicCatalogFacade;
    private final DiscoverReferenceDataService discoverReferenceDataService;
    private final PatientPortalCareAiBusinessLookupService businessLookupService;
    private final PatientPortalCareAiIntentRegistry intentRegistry;
    private final PatientPortalCareAiWorkflowRegistry workflowRegistry;
    private final PatientPortalCareAiWorkflowRouter workflowRouter;
    private final PatientPortalCareAiWorkflowSubStateRegistry workflowSubStateRegistry;
    private final PatientPortalCareAiEntityRegistry entityRegistry;
    private final PatientPortalCareAiEntityExtractor entityExtractor;
    private final PatientPortalCareAiTurnInterpreter turnInterpreter;
    private final SpecialtyResolver specialtyResolver;
    private final DoctorResolver doctorResolver;
    private final ClinicResolver clinicResolver;
    private final ServiceResolver serviceResolver;
    private final LocationResolver locationResolver;
    private final SelectionResolver selectionResolver;
    private final CanonicalResolverSupport canonicalResolverSupport;
    private final PatientPortalCareAiToolRegistry toolRegistry;
    private final PatientPortalAppointmentResolverService appointmentResolverService;
    private final PatientPortalCareAiExecutionTracker executionTracker = new PatientPortalCareAiExecutionTracker();
    private final PatientPortalCareAiWaitingPolicy waitingPolicy = new PatientPortalCareAiWaitingPolicy();
    private final PatientPortalCareAiFallbackPolicyRegistry fallbackPolicyRegistry = new PatientPortalCareAiFallbackPolicyRegistry();
    private final PatientPortalCareAiWriteReconciliationPolicy writeReconciliationPolicy = new PatientPortalCareAiWriteReconciliationPolicy();
    private final PatientPortalCareAiConversationStateReducer conversationStateReducer = new PatientPortalCareAiConversationStateReducer();
    private final PatientPortalCareAiActionDecider actionDecider = new PatientPortalCareAiActionDecider();
    private final PatientPortalCareAiResolvedTurnFactsFactory resolvedTurnFactsFactory;
    private final boolean reducerBookingEnabled = environmentFlag("AIVA_CAREAI_REDUCER_BOOKING_ENABLED", false);
    private final boolean reducerBookingShadowEnabled = environmentFlag("AIVA_CAREAI_REDUCER_BOOKING_SHADOW", true);
    private final ScheduledExecutorService progressScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "careai-progress");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<SessionKey, CareAiState> sessions = new ConcurrentHashMap<>();
    private final Map<VoiceSessionKey, CareAiState> voiceSessions = new ConcurrentHashMap<>();
    private static final ThreadLocal<Consumer<PatientPortalCareAiProgressEvent>> ACTIVE_PROGRESS_SINK = new ThreadLocal<>();

    @Autowired
    public PatientPortalCareAiService(
            PatientPortalService patientPortalService,
            ClinicTimeZoneResolver clinicTimeZoneResolver,
            PatientPortalCareAiPlanner planner,
            CareAiConversationPersistenceService conversationPersistenceService,
            CareAiReceptionistTaskService receptionistTaskService,
            CareAiTaskNotificationService taskNotificationService,
            PublicCatalogFacade publicCatalogFacade,
            DiscoverReferenceDataService discoverReferenceDataService,
            PatientPortalAppointmentResolverService appointmentResolverService
    ) {
        this.patientPortalService = patientPortalService;
        this.clinicTimeZoneResolver = clinicTimeZoneResolver;
        this.planner = planner;
        this.conversationPersistenceService = conversationPersistenceService;
        this.receptionistTaskService = receptionistTaskService;
        this.taskNotificationService = taskNotificationService;
        this.publicCatalogFacade = publicCatalogFacade;
        this.discoverReferenceDataService = discoverReferenceDataService;
        this.businessLookupService = new PatientPortalCareAiBusinessLookupService(patientPortalService, publicCatalogFacade, discoverReferenceDataService);
        this.intentRegistry = new PatientPortalCareAiIntentRegistry();
        this.workflowRegistry = new PatientPortalCareAiWorkflowRegistry();
        this.workflowRouter = new PatientPortalCareAiWorkflowRouter(intentRegistry, workflowRegistry);
        this.workflowSubStateRegistry = new PatientPortalCareAiWorkflowSubStateRegistry();
        this.entityRegistry = new PatientPortalCareAiEntityRegistry();
        this.specialtyResolver = new SpecialtyResolver(entityRegistry);
        this.doctorResolver = new DoctorResolver(entityRegistry);
        this.clinicResolver = new ClinicResolver(entityRegistry);
        this.serviceResolver = new ServiceResolver(entityRegistry);
        this.locationResolver = new LocationResolver(entityRegistry);
        this.selectionResolver = new SelectionResolver();
        this.resolvedTurnFactsFactory = new PatientPortalCareAiResolvedTurnFactsFactory(
                doctorResolver, specialtyResolver, clinicResolver, selectionResolver,
                Clock.systemUTC(), this::currentClinicZone);
        this.canonicalResolverSupport = new CanonicalResolverSupport();
        this.entityExtractor = new PatientPortalCareAiEntityExtractor(entityRegistry, specialtyResolver, doctorResolver, clinicResolver, serviceResolver, locationResolver);
        this.turnInterpreter = new PatientPortalCareAiTurnInterpreter(this.entityExtractor);
        this.toolRegistry = new PatientPortalCareAiToolRegistry(businessLookupService, patientPortalService, publicCatalogFacade, discoverReferenceDataService);
        this.appointmentResolverService = appointmentResolverService == null ? new PatientPortalAppointmentResolverService() : appointmentResolverService;
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
                null,
                null,
                new PatientPortalAppointmentResolverService()
        );
    }

    public PatientPortalCareAiService(
            PatientPortalService patientPortalService,
            ClinicTimeZoneResolver clinicTimeZoneResolver,
            PatientPortalCareAiPlanner planner,
            CareAiConversationPersistenceService conversationPersistenceService,
            CareAiReceptionistTaskService receptionistTaskService,
            CareAiTaskNotificationService taskNotificationService,
            PublicCatalogFacade publicCatalogFacade
    ) {
        this(
                patientPortalService,
                clinicTimeZoneResolver,
                planner,
                conversationPersistenceService,
                receptionistTaskService,
                taskNotificationService,
                publicCatalogFacade,
                null,
                new PatientPortalAppointmentResolverService()
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
                currentChatExternalSessionId(),
                patientPortalService.currentPatientId(),
                CareAiTransport.WEBSOCKET_PATIENT_PORTAL
        );
    }

    public PatientPortalCareAiMessageResponse messageFromVoice(
            PatientPortalCareAiMessageRequest request,
            Consumer<PatientPortalCareAiProgressEvent> progressSink
    ) {
        Consumer<PatientPortalCareAiProgressEvent> previousSink = ACTIVE_PROGRESS_SINK.get();
        if (progressSink == null) {
            ACTIVE_PROGRESS_SINK.remove();
        } else {
            ACTIVE_PROGRESS_SINK.set(progressSink);
        }
        try {
            return messageFromVoice(request);
        } finally {
            if (previousSink == null) {
                ACTIVE_PROGRESS_SINK.remove();
            } else {
                ACTIVE_PROGRESS_SINK.set(previousSink);
            }
        }
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
        CareAiChannel previousChannel = ACTIVE_CHANNEL.get();
        ACTIVE_CHANNEL.set(channel);
        try {
            CareAiState state = currentState();
            state.futureAvailabilityFallbackAppliedThisTurn = false;
            clearLookupCaches(state);
            String message = request.message().trim();
            hydrateStateFromPersistence(state, channel, patientId, externalSessionId);
            ensureWorkflowSubState(state);
            beginTurnLifecycle(state, externalSessionId);
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
                        redactedPatientDiagnosticId(),
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
                    redactedPatientDiagnosticId(),
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
            boolean plannerInvoked = shouldUsePlanner(state, message);
            PatientPortalCareAiPlannerDecision plannerDecision = plannerInvoked && planner != null
                ? planner.plan(buildPlanningContext(state, message))
                : null;
            PatientPortalCareAiCanonicalTurn canonicalTurn = turnInterpreter.interpret(
                    message,
                    state.language,
                    buildPlanningContext(state, message),
                    plannerDecision,
                    channel == CareAiChannel.PATIENT_PORTAL_VOICE,
                    plannerInvoked
            );
            if (canonicalTurn.endConversation() || canonicalTurn.abandonWorkflow()) {
                abandonCurrentWorkflow(state);
                return response(state, farewellPrompt(state.language));
            }
            PatientPortalCareAiIntent classifiedIntent = PatientPortalCareAiIntent.normalize(canonicalTurn.intent());
            traceBookingReducerShadow(state, canonicalTurn, classifiedIntent);
            careAiTrace("turnInterpreter", "exit", state,
                    "source=" + canonicalTurn.source()
                            + " dialogAct=" + canonicalTurn.dialogAct()
                            + " intent=" + canonicalTurn.intent()
                            + " confirmation=" + canonicalTurn.confirmation()
                            + " confidence=" + canonicalTurn.confidence());
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
            state.lastCanonicalTurn = canonicalTurn;
            careAiTrace("applyIntent", "enter", state,
                "userText=" + trimToLength(message, 160)
                        + " plannerIntent=" + (plannerDecision == null ? null : plannerDecision.intent())
                        + " plannerDoctor=" + (plannerDecision == null ? null : plannerDecision.doctorName())
                        + " plannerSpeciality=" + (plannerDecision == null ? null : plannerDecision.speciality()));
            boolean progressed = applyIntent(state, canonicalTurn, plannerDecision, classifiedIntent);
            careAiTrace("applyIntent", "exit", state,
                "progressed=" + progressed
                        + " currentWorkflow=" + state.currentIntent
                        + " selectedDoctorId=" + state.selectedDoctorId
                        + " selectedClinicId=" + state.selectedClinicId
                        + " selectedTenantId=" + state.selectedTenantId
                        + " selectedClinicSlug=" + state.selectedClinicSlug
                        + " selectedAppointmentId=" + state.selectedAppointmentId);
            boolean explicitWorkflowIntent = classifiedIntent != null
                && PatientPortalCareAiIntent.normalize(classifiedIntent) != null
                && PatientPortalCareAiIntent.normalize(classifiedIntent).isWorkflowIntent();
            careAiTrace("classifyTopic", "enter", state,
                "userText=" + trimToLength(message, 160)
                        + " currentWorkflow=" + state.currentIntent
                        + " classifiedIntent=" + classifiedIntent
                        + " lastQuestionKey=" + state.lastQuestionKey);
            CareAiTopicClassification topicClassification = classifyTopic(state, message, classifiedIntent, canonicalTurn);
            if (plannerDecision != null
                    && StringUtils.hasText(plannerDecision.sideTopic())
                    && !hasCanonicalSemanticChange(canonicalTurn)) {
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
                redactedPatientDiagnosticId(),
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
            if (!explicitWorkflowIntent && canonicalTurn.abandonWorkflow()) {
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

            if (channel == CareAiChannel.PATIENT_PORTAL_VOICE
                    && state.voiceSlotRefreshRequested
                    && (state.currentIntent == PatientPortalCareAiIntent.BOOK_APPOINTMENT
                    || state.currentIntent == PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT)
                    && !state.confirmationPending
                    && !state.awaitingFreshConfirmation
                    && StringUtils.hasText(state.selectedDoctorId)
                    && StringUtils.hasText(state.preferredDate)) {
                refreshSlotChoicesAfterVoiceCorrection(state);
            }
            state.voiceSlotRefreshRequested = false;
            if (state.confirmationPending && canonicalTurn.confirmation() == PatientPortalCareAiConfirmationPolarity.POSITIVE) {
            return executeConfirmedAction(state);
            }
            if (state.confirmationPending && canonicalTurn.confirmation() == PatientPortalCareAiConfirmationPolarity.NEGATIVE) {
            clearPendingAction(state, true);
            return response(state, negativeConfirmationResponse(state.language));
            }
            String reply = routeConversation(state, message, canonicalTurn, classifiedIntent);
            String nextPromptKey = inferQuestionKey(state, reply);
            log.info(
                "careai.turn.next-prompt conversationTenantId={} patientPortalSessionId={} patientId={} patientMobile={} userText={} activeWorkflow={} lastQuestionKey={} escalationReason={} topicClassification={} detectedTimePreference={} nextPromptKey={} repeatedQuestionCount={}",
                RequestContextHolder.requireTenantId(),
                externalSessionId,
                patientId,
                redactedPatientDiagnosticId(),
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
        } finally {
            if (previousChannel == null) {
                ACTIVE_CHANNEL.remove();
            } else {
                ACTIVE_CHANNEL.set(previousChannel);
            }
        }
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
                    redactedPatientDiagnosticId(),
                    previousIntent == null ? null : previousIntent.name(),
                    nextIntent == null ? null : nextIntent.name(),
                    reason,
                    staleStateCleared
            );
        }
        invalidatePendingConfirmation(state, reason);
        resetWorkflowState(state, nextIntent);
        ensureWorkflowSubState(state);
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

    public void resetVoiceConversation() {
        if (RequestContextHolder.get() == null) {
            return;
        }
        sessions.remove(currentSessionKey());
        conversationPersistenceService.safeCloseConversation(
                RequestContextHolder.requireTenantId(),
                CareAiChannel.PATIENT_PORTAL_CHAT,
                patientPortalService.currentPatientId(),
                currentChatExternalSessionId(),
                CareAiConversationStatus.CANCELLED,
                "AIVA voice conversation context cleared."
        );
    }

    public List<Map<String, Object>> debugDoctorLookup(String query) {
        CareAiState state = currentState();
        List<DoctorChoice> careAiDoctors = lookupDoctors(state, query, null);
        int publicCatalogCount = publicCatalogFacade == null ? -1 : publicCatalogFacade.listDoctors(
                query,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                24
        ).items().size();
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
        PatientPortalCareAiSkillResult<List<PatientPortalCareAiAppointmentOption>> skillResult = toolRegistry.appointmentCheck().execute(
                new PatientPortalCareAiAppointmentCheckSkillInput(
                        patientPortalService.currentPatientId() == null ? null : String.valueOf(patientPortalService.currentPatientId()),
                        patientPortalService.currentPatientMobile()
                )
        );
        List<PatientPortalCareAiAppointmentOption> careAiAppointments = skillResult.value() == null ? List.of() : skillResult.value();
        careAiTrace("CAREAI_TRACE_APPOINTMENT_COMPARE", "enter", state,
                "patientPortalAppointmentsCount=" + patientPortalAppointments.size()
                        + " skill=appointment.check outcome=" + skillResult.outcome()
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

    private String routeConversation(CareAiState state, String message,
                                     PatientPortalCareAiCanonicalTurn canonicalTurn,
                                     PatientPortalCareAiIntent classifiedIntent) {
        if (state.currentIntent == null) {
            if (classifiedIntent == PatientPortalCareAiIntent.FIND_DOCTOR) {
                return handleDoctorDiscovery(state, message, canonicalTurn);
            }
            if (classifiedIntent == PatientPortalCareAiIntent.FIND_CLINIC) {
                return handleClinicDiscovery(state, message, canonicalTurn);
            }
            return askIntentPrompt(state.language);
        }
        if (state.currentIntent == PatientPortalCareAiIntent.BOOK_APPOINTMENT
                && classifiedIntent == PatientPortalCareAiIntent.FIND_DOCTOR
                && isAvailabilityFirstProviderRequest(canonicalTurn)) {
            return handleDoctorDiscovery(state, message, canonicalTurn);
        }
        return switch (state.currentIntent) {
            case BOOK_APPOINTMENT -> handleBooking(state, message, canonicalTurn);
            case RESCHEDULE_APPOINTMENT -> handleReschedule(state, message, canonicalTurn);
            case CANCEL_APPOINTMENT -> handleCancellation(state, message, canonicalTurn);
            case CHECK_APPOINTMENT, APPOINTMENT_STATUS -> handleStatus(state, message);
            default -> askIntentPrompt(state.language);
        };
    }

    private boolean isAvailabilityFirstProviderRequest(PatientPortalCareAiCanonicalTurn turn) {
        if (turn == null || turn.entities() == null) {
            return false;
        }
        PatientPortalCareAiCanonicalEntities entities = turn.entities();
        return !StringUtils.hasText(entities.doctor())
                && StringUtils.hasText(entities.date())
                && (StringUtils.hasText(entities.timeWindow()) || StringUtils.hasText(entities.exactTime()));
    }

    private boolean applyIntent(CareAiState state,
                                PatientPortalCareAiCanonicalTurn canonicalTurn,
                                PatientPortalCareAiPlannerDecision plannerDecision,
                                PatientPortalCareAiIntent classifiedIntent) {
        String message = state.lastUserMessage == null ? "" : state.lastUserMessage;
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
            changed = applyBookingFacts(state, canonicalTurn) || changed;
        } else if (state.currentIntent == PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT) {
            changed = applyRescheduleFacts(state, canonicalTurn) || changed;
        } else if (state.currentIntent == PatientPortalCareAiIntent.CANCEL_APPOINTMENT) {
            changed = applyAppointmentSelectionFacts(state, canonicalTurn, message) || changed;
        } else if (state.currentIntent == PatientPortalCareAiIntent.FIND_DOCTOR
                || detectedIntent == PatientPortalCareAiIntent.FIND_DOCTOR) {
            SpecialtyResolver.SpecialtyResolution resolution = resolveSpecialty(
                    canonicalTurn.entities().speciality(), state);
            if (resolution.resolved() && !resolution.canonicalSpecialty().equalsIgnoreCase(state.requestedSpeciality)) {
                state.requestedSpeciality = resolution.canonicalSpecialty();
                state.selectedSpeciality = resolution.canonicalSpecialty();
                changed = true;
            }
        }
        careAiTrace("applyIntent", "exit", state,
                "detectedIntent=" + detectedIntent
                        + " changed=" + changed
                        + " currentWorkflow=" + state.currentIntent
                        + " selectedDoctorId=" + state.selectedDoctorId
                        + " selectedAppointmentId=" + state.selectedAppointmentId);
        return changed;
    }

    private boolean applyBookingFacts(CareAiState state, PatientPortalCareAiCanonicalTurn canonicalTurn) {
        PatientPortalCareAiCanonicalEntities entities = canonicalTurn.entities();
        String message = state.lastUserMessage == null ? "" : state.lastUserMessage;
        careAiTrace("applyBookingFacts", "enter", state,
                "message=" + trimToLength(message, 160)
                        + " requestedDoctorName=" + state.requestedDoctorName
                        + " requestedSpeciality=" + state.requestedSpeciality
                        + " selectedDoctorId=" + state.selectedDoctorId
                        + " selectedClinicSlug=" + state.selectedClinicSlug
                        + " selectedTenantId=" + state.selectedTenantId);
        boolean changed = false;
        boolean voiceSlotRefreshRequested = false;
        boolean selectionOnlyMessage = canonicalTurn.dialogAct() == PatientPortalCareAiDialogAct.SELECT_OPTION
                && !StringUtils.hasText(entities.date())
                && !StringUtils.hasText(entities.dateIssue());
        boolean timeOnlyAdjustment = canonicalTurn.correction().present()
                && "time".equalsIgnoreCase(canonicalTurn.correction().target());
        String previousPreferredDate = state.preferredDate;
        String previousSelectedDoctorId = state.selectedDoctorId;
        String previousSelectedDoctorSlug = state.selectedDoctorSlug;
        String previousSelectedDoctorName = state.selectedDoctorName;
        String previousSelectedSpeciality = state.selectedSpeciality;
        String previousSelectedClinicId = state.selectedClinicId;
        String previousSelectedTenantId = state.selectedTenantId;
        String previousSelectedClinicSlug = state.selectedClinicSlug;
        String previousSelectedClinicName = state.selectedClinicName;
        String detectedDate = null;
        String normalizedDate = null;

        if (canonicalTurn.alternative().present()
                && "doctor".equalsIgnoreCase(canonicalTurn.alternative().target())
                && (StringUtils.hasText(state.requestedDoctorName)
                || StringUtils.hasText(state.selectedDoctorId)
                || !state.slotOptions.isEmpty())) {
            invalidatePendingConfirmation(state, "alternative-doctor-requested");
            state.requestedDoctorName = null;
            clearDoctorSelection(state);
            changed = true;
        }

        String doctorName = null;
        if (doctorPromotionEligible(state, canonicalTurn, timeOnlyAdjustment)) {
            String requestedDoctor = entities.doctor();
            doctorName = resolveValidatedDoctorName(state, requestedDoctor);
            if (StringUtils.hasText(requestedDoctor) && !StringUtils.hasText(doctorName)
                    && (canonicalTurn.correction().present() || canonicalTurn.dialogAct() == PatientPortalCareAiDialogAct.CHANGE_INFORMATION)) {
                invalidateCriteriaFallback(state, "unresolved-doctor-change");
                state.requestedDoctorName = requestedDoctor;
                clearDoctorSelection(state);
                changed = true;
            }
        }
        if (StringUtils.hasText(doctorName) && !doctorName.equalsIgnoreCase(state.requestedDoctorName)) {
            invalidatePendingConfirmation(state, "doctor-changed");
            state.requestedDoctorName = doctorName;
            clearDoctorSelection(state);
            changed = true;
        }

        String clinicName = providerContextPromotionEligible(state, canonicalTurn)
                ? resolveValidatedClinicName(state, entities.clinic())
                : null;
        if (StringUtils.hasText(clinicName) && !clinicName.equalsIgnoreCase(state.requestedClinicName)) {
            state.requestedClinicName = clinicName;
            changed = true;
        }

        String serviceName = entities.service();
        if (StringUtils.hasText(serviceName) && !serviceName.equalsIgnoreCase(state.requestedServiceName)) {
            state.requestedServiceName = serviceName;
            changed = true;
        }

        String locationName = providerContextPromotionEligible(state, canonicalTurn)
                ? resolveValidatedLocationName(state, entities.location())
                : null;
        if (StringUtils.hasText(locationName) && !locationName.equalsIgnoreCase(state.requestedLocationName)) {
            state.requestedLocationName = locationName;
            changed = true;
        }

        SpecialtyResolver.SpecialtyResolution specialityResolution = resolveSpecialty(entities.speciality(), state);
        if (specialityResolution.resolved() && !specialityResolution.canonicalSpecialty().equalsIgnoreCase(state.requestedSpeciality)) {
            state.requestedSpeciality = specialityResolution.canonicalSpecialty();
            if (!StringUtils.hasText(state.requestedDoctorName)
                    || StringUtils.hasText(state.selectedDoctorId)
                    || !state.slotOptions.isEmpty()) {
                clearDoctorSelection(state);
            }
            changed = true;
        }

        String preferredTimeWindow = StringUtils.hasText(entities.timeWindow())
                ? entities.timeWindow() : entities.exactTime();
            if (StringUtils.hasText(preferredTimeWindow) && !preferredTimeWindow.equalsIgnoreCase(state.preferredTimeWindow)) {
            invalidatePendingConfirmation(state, "time-preference-changed");
            state.preferredTimeWindow = preferredTimeWindow;
            state.timePromptCount = 0;
            clearSlotSelection(state);
            changed = true;
            voiceSlotRefreshRequested = true;
        }

        if (!selectionOnlyMessage && !timeOnlyAdjustment) {
            normalizedDate = entities.date();
            detectedDate = normalizedDate;
            if (StringUtils.hasText(entities.dateIssue())) {
                state.dateResolutionIssue = entities.dateIssue();
                state.preferredDate = null;
                state.preferredDateExplicit = false;
                clearSlotSelection(state);
                changed = true;
            } else if (StringUtils.hasText(normalizedDate)
                    && !isVoiceConversationChannel()
                    && parseIsoDate(normalizedDate) != null
                    && parseIsoDate(normalizedDate).isBefore(currentClinicDate())) {
                state.dateResolutionIssue = "past";
                state.preferredDate = null;
                state.preferredDateExplicit = false;
                clearSlotSelection(state);
                changed = true;
            } else if (StringUtils.hasText(normalizedDate) && !normalizedDate.equals(state.preferredDate)) {
                state.dateResolutionIssue = null;
                state.preferredDate = normalizedDate;
                state.preferredDateExplicit = true;
                clearSlotSelection(state);
                changed = true;
                voiceSlotRefreshRequested = true;
            } else if (!StringUtils.hasText(normalizedDate) && canonicalTurn.dialogAct() == PatientPortalCareAiDialogAct.PROVIDE_INFORMATION
                    && state.workflowSubState == PatientPortalCareAiWorkflowSubState.NEED_DATE) {
                state.dateResolutionIssue = null;
            /* No date candidate: preserve the existing date. */
            }
            /* Date parsing and ambiguity resolution happen before this method. */
        }
        log.info(
                "careai.date-resolution userText={} detectedDate={} normalizedDate={} answeredState.date={} nextQuestion={}",
                trimToLength(message, 160),
                detectedDate,
                normalizedDate,
                StringUtils.hasText(state.preferredDate),
                previewNextQuestionAfterDate(state)
        );
        if (timeOnlyAdjustment) {
            if (!StringUtils.hasText(state.preferredDate) && StringUtils.hasText(previousPreferredDate)) {
                state.preferredDate = previousPreferredDate;
                state.preferredDateExplicit = true;
            }
            if (!StringUtils.hasText(state.selectedDoctorId) && StringUtils.hasText(previousSelectedDoctorId)) {
                state.selectedDoctorId = previousSelectedDoctorId;
                state.selectedDoctorSlug = previousSelectedDoctorSlug;
                state.selectedDoctorName = previousSelectedDoctorName;
                state.selectedSpeciality = previousSelectedSpeciality;
                state.selectedClinicId = previousSelectedClinicId;
                state.selectedTenantId = previousSelectedTenantId;
                state.selectedClinicSlug = previousSelectedClinicSlug;
                state.selectedClinicName = previousSelectedClinicName;
            }
        }

        if (changed && isVoiceConversationChannel()) {
            refreshSlotChoicesAfterVoiceCorrection(state);
        }
        if (changed && isVoiceConversationChannel()) {
            state.voiceSlotRefreshRequested = voiceSlotRefreshRequested;
            resetPromptRepetitionTracking(state);
        }
        if (changed) {
            invalidateCriteriaFallback(state, "canonical-booking-change");
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

    private boolean doctorPromotionEligible(CareAiState state, PatientPortalCareAiCanonicalTurn turn, boolean timeOnlyAdjustment) {
        if (timeOnlyAdjustment || state == null) {
            return false;
        }
        if (state.workflowSubState == PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY
                || state.workflowSubState == PatientPortalCareAiWorkflowSubState.FINDING_PROVIDERS
                || state.workflowSubState == PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION) {
            return true;
        }
        return turn != null && (StringUtils.hasText(turn.entities().doctor())
                || turn.correction().present() && "doctor".equalsIgnoreCase(turn.correction().target())
                || turn.alternative().present() && "doctor".equalsIgnoreCase(turn.alternative().target()));
    }

    private boolean providerContextPromotionEligible(CareAiState state, PatientPortalCareAiCanonicalTurn turn) {
        if (state == null) {
            return false;
        }
        if (state.workflowSubState == PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY
                || state.workflowSubState == PatientPortalCareAiWorkflowSubState.FINDING_PROVIDERS
                || state.workflowSubState == PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION) {
            return true;
        }
        return turn != null && (StringUtils.hasText(turn.entities().clinic())
                || StringUtils.hasText(turn.entities().location())
                || turn.correction().present() && ("clinic".equalsIgnoreCase(turn.correction().target())
                || "location".equalsIgnoreCase(turn.correction().target())));
    }

    private String resolveValidatedDoctorName(CareAiState state, String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        List<DoctorChoice> candidates = state.doctorChoices.isEmpty()
                ? publicBookableDoctorChoices(state)
                : state.doctorChoices;
        CanonicalResolution resolution = doctorResolver.resolve(candidate, doctorCandidates(candidates), state.selectedDoctorId);
        if (!resolution.resolved() && !resolution.ambiguous() && !state.doctorChoices.isEmpty()) {
            candidates = publicBookableDoctorChoices(state);
            resolution = doctorResolver.resolve(candidate, doctorCandidates(candidates), state.selectedDoctorId);
        }
        if (!resolution.resolved() || resolution.candidateIds().isEmpty()) {
            return null;
        }
        DoctorChoice resolved = doctorChoiceById(candidates, resolution.candidateIds().getFirst());
        return resolved == null ? null : resolved.doctorName();
    }

    private void invalidateCriteriaFallback(CareAiState state, String reason) {
        state.lastNoSlotPromptCriteria = null;
        state.lastAvailabilityNoMatchCriteria = null;
        state.lastFallbackAction = PatientPortalCareAiFallbackAction.NONE;
        state.lastSkillOutcome = null;
        state.futureAvailabilityFallbackAppliedThisTurn = false;
        careAiTrace("fallback.invalidate", "semantic-change", state, "reason=" + reason);
    }

    private String resolveValidatedClinicName(CareAiState state, String candidate) {
        if (!StringUtils.hasText(candidate) || state.clinicChoices.isEmpty()) {
            return null;
        }
        CanonicalResolution resolution = clinicResolver.resolve(candidate, clinicCandidates(state.clinicChoices), state.selectedClinicSlug);
        if (!resolution.resolved() || resolution.candidateIds().isEmpty()) {
            return null;
        }
        ClinicChoice resolved = clinicChoiceById(state.clinicChoices, resolution.candidateIds().getFirst());
        return resolved == null ? null : resolved.clinicName();
    }

    private String resolveValidatedLocationName(CareAiState state, String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        String normalized = normalizeDoctorText(candidate);
        if (normalized == null || normalized.length() < 3
                || Set.of("uh", "um", "hmm", "tomorrow", "today", "morning", "afternoon", "evening", "night").contains(normalized)) {
            return null;
        }
        return candidate;
    }

    private boolean applyRescheduleFacts(CareAiState state, PatientPortalCareAiCanonicalTurn turn) {
        String message = state.lastUserMessage == null ? "" : state.lastUserMessage;
        PatientPortalCareAiCanonicalEntities entities = turn.entities();
        careAiTrace("applyRescheduleFacts", "enter", state,
                "message=" + trimToLength(message, 160)
                        + " selectedAppointmentId=" + state.selectedAppointmentId
                        + " preferredDate=" + state.preferredDate
                        + " preferredTimeWindow=" + state.preferredTimeWindow
                        + " selectedTenantId=" + state.selectedTenantId);
        boolean changed = applyAppointmentSelectionFacts(state, turn, message);
        boolean voiceSlotRefreshRequested = false;
        boolean selectionOnlyMessage = turn.dialogAct() == PatientPortalCareAiDialogAct.SELECT_OPTION
                && !StringUtils.hasText(entities.date())
                && !StringUtils.hasText(entities.dateIssue());
        boolean timeOnlyAdjustment = turn.correction().present()
                && "time".equalsIgnoreCase(turn.correction().target());
        String previousPreferredDate = state.preferredDate;
        String previousSelectedDoctorId = state.selectedDoctorId;
        String previousSelectedDoctorSlug = state.selectedDoctorSlug;
        String previousSelectedDoctorName = state.selectedDoctorName;
        String previousSelectedSpeciality = state.selectedSpeciality;
        String previousSelectedClinicId = state.selectedClinicId;
        String previousSelectedTenantId = state.selectedTenantId;
        String previousSelectedClinicSlug = state.selectedClinicSlug;
        String previousSelectedClinicName = state.selectedClinicName;
        String detectedDate = null;
        String normalizedDate = null;

        String clinicName = resolveValidatedClinicName(state, entities.clinic());
        if (StringUtils.hasText(clinicName) && !clinicName.equalsIgnoreCase(state.requestedClinicName)) {
            state.requestedClinicName = clinicName;
            changed = true;
        }

        String serviceName = entities.service();
        if (StringUtils.hasText(serviceName) && !serviceName.equalsIgnoreCase(state.requestedServiceName)) {
            state.requestedServiceName = serviceName;
            changed = true;
        }

        String locationName = resolveValidatedLocationName(state, entities.location());
        if (StringUtils.hasText(locationName) && !locationName.equalsIgnoreCase(state.requestedLocationName)) {
            state.requestedLocationName = locationName;
            changed = true;
        }

        String preferredTimeWindow = StringUtils.hasText(entities.timeWindow())
                ? entities.timeWindow() : entities.exactTime();
        if (StringUtils.hasText(preferredTimeWindow) && !preferredTimeWindow.equalsIgnoreCase(state.preferredTimeWindow)) {
            invalidatePendingConfirmation(state, "time-preference-changed");
            state.preferredTimeWindow = preferredTimeWindow;
            state.timePromptCount = 0;
            clearSlotSelection(state);
            changed = true;
            voiceSlotRefreshRequested = true;
        }

        if (!selectionOnlyMessage && !timeOnlyAdjustment) {
            detectedDate = entities.date() != null || entities.dateIssue() != null ? trimToLength(message, 160) : null;
            normalizedDate = entities.date();
            if (StringUtils.hasText(entities.dateIssue())) {
                state.dateResolutionIssue = entities.dateIssue();
                state.preferredDate = null;
                clearSlotSelection(state);
                changed = true;
            } else if (StringUtils.hasText(normalizedDate)
                    && !isVoiceConversationChannel()
                    && parseIsoDate(normalizedDate) != null
                    && parseIsoDate(normalizedDate).isBefore(currentClinicDate())) {
                state.dateResolutionIssue = "past";
                state.preferredDate = null;
                clearSlotSelection(state);
                changed = true;
            } else if (StringUtils.hasText(normalizedDate) && !normalizedDate.equals(state.preferredDate)) {
                invalidatePendingConfirmation(state, "date-changed");
                state.dateResolutionIssue = null;
                state.preferredDate = normalizedDate;
                clearSlotSelection(state);
                changed = true;
                voiceSlotRefreshRequested = true;
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
        if (timeOnlyAdjustment) {
            if (!StringUtils.hasText(state.preferredDate) && StringUtils.hasText(previousPreferredDate)) {
                state.preferredDate = previousPreferredDate;
                state.preferredDateExplicit = true;
            }
            if (!StringUtils.hasText(state.selectedDoctorId) && StringUtils.hasText(previousSelectedDoctorId)) {
                state.selectedDoctorId = previousSelectedDoctorId;
                state.selectedDoctorSlug = previousSelectedDoctorSlug;
                state.selectedDoctorName = previousSelectedDoctorName;
                state.selectedSpeciality = previousSelectedSpeciality;
                state.selectedClinicId = previousSelectedClinicId;
                state.selectedTenantId = previousSelectedTenantId;
                state.selectedClinicSlug = previousSelectedClinicSlug;
                state.selectedClinicName = previousSelectedClinicName;
            }
        }
        if (changed && isVoiceConversationChannel()) {
            state.voiceSlotRefreshRequested = voiceSlotRefreshRequested;
            resetPromptRepetitionTracking(state);
        }
        careAiTrace("applyRescheduleFacts", "exit", state,
                "changed=" + changed
                        + " selectedAppointmentId=" + state.selectedAppointmentId
                        + " preferredDate=" + state.preferredDate
                        + " preferredTimeWindow=" + state.preferredTimeWindow
                        + " selectedSlot=" + state.selectedSlot
                        + " selectedTenantId=" + state.selectedTenantId);
        return changed;
    }

    private boolean applyAppointmentSelectionFacts(CareAiState state,
                                                   PatientPortalCareAiCanonicalTurn turn,
                                                   String compatibilityMessage) {
        if (state == null
                || state.currentIntent == null
                || state.confirmationPending
                || (state.currentIntent != PatientPortalCareAiIntent.CANCEL_APPOINTMENT
                && state.currentIntent != PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT
                && state.currentIntent != PatientPortalCareAiIntent.CHECK_APPOINTMENT)) {
            return false;
        }
        if (hasCanonicalAppointmentSignal(turn) && !state.appointmentOptions.isEmpty()) {
            PatientPortalAppointmentResolverService.AppointmentResolution resolution = resolveAppointmentSelection(
                    state, state.lastCanonicalTurn);
            if (resolution.resolved()) {
                AppointmentChoice match = findAppointmentChoice(state, resolution.appointment());
                if (match != null) {
                    selectAppointment(state, match);
                    return true;
                }
            }
            if (state.appointmentOptions.size() == 1) {
                selectAppointment(state, state.appointmentOptions.getFirst());
                return true;
            }
        }
        return false;
    }

    private String handleBooking(CareAiState state, String message, PatientPortalCareAiCanonicalTurn turn) {
        careAiTrace("handleBooking", "enter", state,
                "message=" + trimToLength(message, 160)
                        + " selectedDoctorId=" + state.selectedDoctorId
                        + " selectedClinicSlug=" + state.selectedClinicSlug
                        + " selectedTenantId=" + state.selectedTenantId
                        + " preferredDate=" + state.preferredDate
                        + " preferredTimeWindow=" + state.preferredTimeWindow
                        + " slotCount=" + state.slotOptions.size());
        if (isCurrentSlotContextQuestion(state, turn)) {
            return currentSlotContextPrompt(state);
        }
        if (isSlotContextControlTurn(turn) && shouldRerenderSlotOptions(state, message)) {
            return rerenderSlotOptions(state);
        }
        if (isSlotContextControlTurn(turn) && shouldAdvanceSlotOptions(state, message)) {
            return advanceSlotOptions(state);
        }
        if (isAnotherTimeRequest(message)) {
            invalidatePendingConfirmation(state, "another-time-requested");
            state.preferredTimeWindow = null;
            state.slotPromptLead = null;
            clearSlotSelection(state);
            return askTimePrompt(state.language);
        }
        if (isAnotherDateRequest(message) && StringUtils.hasText(state.selectedDoctorId)) {
            invalidatePendingConfirmation(state, "another-date-requested");
            return searchAlternativeAvailability(state);
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
                return askDatePrompt(state);
            }
        }
        if (!StringUtils.hasText(state.selectedDoctorId)) {
            return promptForDoctorSelection(state, message);
        }
        if (StringUtils.hasText(state.dateResolutionIssue)) {
            return invalidDatePrompt(state.language, state.dateResolutionIssue);
        }
        if (!StringUtils.hasText(state.preferredDate)) {
            return askDatePrompt(state);
        }
        if (!isOnlineBookable(state)) {
            return callToBookFallbackPrompt(state);
        }

        if (tryResolveSlotSelection(state, message, state.selectedDoctorId)) {
            return bookingConfirmationPrompt(state);
        }
        if (hasResolvedTimePreference(state)
                && StringUtils.hasText(state.preferredDate)
                && !state.futureAvailabilityFallbackAppliedThisTurn
                && availabilityCriteria(state, parseIsoDate(state.preferredDate)).equals(state.lastNoSlotPromptCriteria)) {
            return unavailablePreferredWindowPrompt(state, state.preferredTimeWindow, List.of());
        }
        if (state.slotOptions.isEmpty()) {
            if (hasResolvedTimePreference(state)) {
                String criteria = availabilityCriteria(state, parseIsoDate(state.preferredDate));
                if (!criteria.equals(state.lastNoSlotPromptCriteria)) {
                    return searchAlternativeAvailability(state);
                }
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

    private String handleReschedule(CareAiState state, String message, PatientPortalCareAiCanonicalTurn turn) {
        careAiTrace("handleReschedule", "enter", state,
                "message=" + trimToLength(message, 160)
                        + " selectedAppointmentId=" + state.selectedAppointmentId
                        + " selectedDoctorId=" + state.selectedDoctorId
                        + " selectedClinicSlug=" + state.selectedClinicSlug
                        + " selectedTenantId=" + state.selectedTenantId
                        + " preferredDate=" + state.preferredDate
                        + " preferredTimeWindow=" + state.preferredTimeWindow
                        + " slotCount=" + state.slotOptions.size());
        if (isCurrentSlotContextQuestion(state, turn)) {
            return currentSlotContextPrompt(state);
        }
        if (isSlotContextControlTurn(turn) && shouldRerenderSlotOptions(state, message)) {
            return rerenderSlotOptions(state);
        }
        if (isSlotContextControlTurn(turn) && shouldAdvanceSlotOptions(state, message)) {
            return advanceSlotOptions(state);
        }
        if (state.confirmationPending && StringUtils.hasText(state.selectedAppointmentId)) {
            return rescheduleConfirmationPrompt(state);
        }
        if (!ensureAppointmentOptions(state)) {
            return noUpcomingAppointmentsPrompt(state.language);
        }
        if (!StringUtils.hasText(state.selectedAppointmentId)) {
            PatientPortalAppointmentResolverService.AppointmentResolution resolution = resolveAppointmentSelection(state, turn);
            if (resolution.resolved()) {
                AppointmentChoice match = findAppointmentChoice(state, resolution.appointment());
                if (match != null) {
                    selectAppointment(state, match);
                } else {
                    return appointmentNoMatchPrompt(state.language);
                }
            } else if (state.appointmentOptions.size() == 1) {
                selectAppointment(state, state.appointmentOptions.getFirst());
            } else if (resolution.status() == PatientPortalAppointmentResolverService.ResolutionStatus.MULTIPLE) {
                return appointmentMultipleMatchPrompt(state, resolution.matches(), "reschedule");
            } else if (resolution.status() == PatientPortalAppointmentResolverService.ResolutionStatus.NONE) {
                return appointmentChoicePrompt(state, "reschedule");
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

    private String handleCancellation(CareAiState state, String message, PatientPortalCareAiCanonicalTurn turn) {
        careAiTrace("handleCancellation", "enter", state,
                "message=" + trimToLength(message, 160)
                        + " selectedAppointmentId=" + state.selectedAppointmentId
                        + " selectedDoctorId=" + state.selectedDoctorId
                        + " selectedTenantId=" + state.selectedTenantId
                        + " appointmentCount=" + state.appointmentOptions.size());
        if (state.confirmationPending && StringUtils.hasText(state.selectedAppointmentId)) {
            return cancellationConfirmationPrompt(state);
        }
        if (!ensureAppointmentOptions(state)) {
            return noUpcomingAppointmentsPrompt(state.language);
        }
        if (!StringUtils.hasText(state.selectedAppointmentId)) {
            PatientPortalAppointmentResolverService.AppointmentResolution resolution = resolveAppointmentSelection(state, turn);
            if (resolution.resolved()) {
                AppointmentChoice match = findAppointmentChoice(state, resolution.appointment());
                if (match != null) {
                    selectAppointment(state, match);
                } else {
                    return appointmentNoMatchPrompt(state.language);
                }
            } else if (state.appointmentOptions.size() == 1) {
                selectAppointment(state, state.appointmentOptions.getFirst());
            } else if (resolution.status() == PatientPortalAppointmentResolverService.ResolutionStatus.MULTIPLE) {
                return appointmentMultipleMatchPrompt(state, resolution.matches(), "cancel");
            } else if (resolution.status() == PatientPortalAppointmentResolverService.ResolutionStatus.NONE) {
                return appointmentChoicePrompt(state, "cancel");
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
        if (asksForAllAppointments(message)) {
            return appointmentListPrompt(state, isHindi(state.language) ? "आपकी आने वाली अपॉइंटमेंट ये हैं:" : "Here are your upcoming appointments:");
        }
        if (isNextAppointmentQuery(message) || state.appointmentOptions.size() == 1) {
            AppointmentChoice next = state.appointmentOptions.getFirst();
            selectAppointment(state, next);
            careAiTrace("handleStatus", "exit", state,
                    "selectedAppointmentId=" + state.selectedAppointmentId
                            + " selectedTenantId=" + state.selectedTenantId
                            + " appointmentCount=" + state.appointmentOptions.size());
            return appointmentStatusPrompt(next, state.language);
        }
        PatientPortalAppointmentResolverService.AppointmentResolution resolution = resolveAppointmentSelection(state, state.lastCanonicalTurn);
        if (resolution.resolved()) {
            AppointmentChoice selected = findAppointmentChoice(state, resolution.appointment());
            if (selected != null) {
                selectAppointment(state, selected);
                return appointmentStatusPrompt(selected, state.language);
            }
        }
        if (resolution.status() == PatientPortalAppointmentResolverService.ResolutionStatus.MULTIPLE) {
            return appointmentMultipleMatchPrompt(state, resolution.matches(), "view");
        }
        if (resolution.status() == PatientPortalAppointmentResolverService.ResolutionStatus.NONE) {
            return appointmentNoMatchPrompt(state.language);
        }
        return appointmentSelectionHelpPrompt(state.language, "view");
    }

    private String promptForDoctorSelection(CareAiState state, String message) {
        PatientPortalCareAiCanonicalTurn turn = state.lastCanonicalTurn;
        String specialityCandidate = turn == null ? null : turn.entities().speciality();
        List<DoctorChoice> matches = resolveDoctorMatches(state, canonicalDoctorSearchText(turn, message));
        if (matches.isEmpty()) {
            SpecialtyResolver.SpecialtyResolution specialityResolution = resolveSpecialty(
                    StringUtils.hasText(specialityCandidate) ? specialityCandidate : message, state);
            if (specialityResolution.status() == SpecialtyResolver.SpecialtyResolutionStatus.AMBIGUOUS) {
                setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY, "doctor-specialty-ambiguous");
                return specialityClarificationPrompt(state, specialityResolution.candidates());
            }
            if ((StringUtils.hasText(specialityCandidate) || specialtyResolver.extractCandidate(message).isPresent())
                    && !specialityResolution.resolved()) {
                setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY, "doctor-specialty-unresolved");
                return specialityClarificationPrompt(state, supportedSpecialties(state));
            }
            if (turn != null && (StringUtils.hasText(turn.entities().clinic())
                    || StringUtils.hasText(turn.entities().location()))) {
                List<ClinicChoice> clinicMatches = resolveClinicMatches(state, canonicalClinicSearchText(turn, null));
                if (clinicMatches.size() == 1) {
                    selectClinic(state, clinicMatches.getFirst());
                    setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION, "clinic-selection-leads-to-doctor");
                    return clinicDoctorChoicePrompt(state);
                }
                if (clinicMatches.size() > 1) {
                    state.clinicChoices = clinicMatches;
                    state.clinicOptions = clinicMatches.stream().map(ClinicChoice::label).toList();
                    setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION, "clinic-choice");
                    return clinicChoicePrompt(state);
                }
            }
            List<DoctorChoice> fuzzyMatches = resolveFuzzyDoctorMatches(message);
            if (fuzzyMatches.size() == 1) {
                state.doctorChoices = fuzzyMatches;
                state.doctorOptions = fuzzyMatches.stream().map(DoctorChoice::label).toList();
                setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION, "doctor-correction");
                return doctorCorrectionPrompt(state.language, fuzzyMatches.getFirst());
            }
            if (!fuzzyMatches.isEmpty()) {
                state.doctorChoices = fuzzyMatches;
                state.doctorOptions = fuzzyMatches.stream().map(DoctorChoice::label).toList();
                setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION, "doctor-choice");
                return doctorChoicePrompt(state);
            }
            if (log.isDebugEnabled()) {
                log.debug(
                        "patient.portal.careai.doctor.lookup.empty source=web-public-patient-careai conversationTenantId={} tenantContextTenantId={} patientPortalSessionId={} patientId={} patientMobile={} selectedDoctorId={} selectedDoctorSlug={} selectedClinicId={} selectedTenantId={} selectedClinicSlug={} lookupMode={} searchText={} speciality={} reason={}",
                        RequestContextHolder.requireTenantId(),
                        RequestContextHolder.get() == null ? null : RequestContextHolder.get().tenantId().value(),
                        RequestContextHolder.require().correlationId(),
                        patientPortalService.currentPatientId(),
                        redactedPatientDiagnosticId(),
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
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY, "doctor-prompt");
            return askDoctorPrompt(state.language, state.currentIntent == PatientPortalCareAiIntent.BOOK_APPOINTMENT);
        }
        if (matches.size() > 1 && sameDoctorAcrossMultipleClinics(matches)) {
            state.clinicChoices = toClinicChoices(matches);
            state.clinicOptions = state.clinicChoices.stream().map(ClinicChoice::label).toList();
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION, "doctor-multi-clinic");
            return clinicChoicePrompt(state);
        }
        if (matches.size() == 1) {
            selectDoctor(state, matches.getFirst());
            return askDatePrompt(state);
        }
        state.doctorOptions = matches.stream().map(DoctorChoice::label).toList();
        state.doctorChoices = matches;
        setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION, "doctor-options");
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
        logSlotLookupTrace("tryResolveSlotSelection.enter", state, publicDoctorId, date, state.preferredTimeWindow, true, null, null);
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
        logSlotLookupTrace("tryResolveSlotSelection.exit", state, publicDoctorId, date, state.preferredTimeWindow, true, selectableSlots.size(), null);
        if (selectableSlots.isEmpty()) {
            if (state.slotOptions.isEmpty()) {
                clearSlotSelection(state);
            }
            logSlotLookupTrace("tryResolveSlotSelection.exit", state, publicDoctorId, date, state.preferredTimeWindow, true, 0, "no-selectable-slots");
            return false;
        }

        List<PatientPortalDoctorSlotResponse> filtered = filterSlots(selectableSlots, state.preferredTimeWindow);
        List<PatientPortalDoctorSlotResponse> candidates = filtered.isEmpty() ? selectableSlots : filtered;
        if (candidates.isEmpty()) {
            clearSlotSelection(state);
            logSlotLookupTrace("tryResolveSlotSelection.exit", state, publicDoctorId, date, state.preferredTimeWindow, true, selectableSlots.size(), "no-candidates-after-filter");
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
                logSlotLookupTrace("tryResolveSlotSelection.exit", state, publicDoctorId, date, state.preferredTimeWindow, true, selectableSlots.size(), null);
                return true;
            }
            List<PatientPortalDoctorSlotResponse> nearest = nearestSlots(candidates, state.preferredTimeWindow);
            if (!nearest.isEmpty()) {
                candidates = nearest;
                state.slotPromptLead = exactTimeUnavailablePrompt(state, state.preferredTimeWindow, nearest);
                logSlotLookupTrace("tryResolveSlotSelection.exit", state, publicDoctorId, date, state.preferredTimeWindow, true, selectableSlots.size(), "exact-time-miss");
            }
        } else if (StringUtils.hasText(state.preferredTimeWindow) && filtered.isEmpty()) {
            candidates = selectableSlots.stream().limit(3).toList();
            state.slotPromptLead = broadTimeUnavailablePrompt(state, state.preferredTimeWindow, candidates);
            logSlotLookupTrace("tryResolveSlotSelection.exit", state, publicDoctorId, date, state.preferredTimeWindow, true, selectableSlots.size(), "preferred-window-miss");
        }

        List<SlotChoice> options = candidates.stream()
                .map(slot -> new SlotChoice(slot.appointmentDate(), slot.slotTime()))
                .toList();
        state.allSlotChoices = options;
        state.shownSlotOffset = 0;
        renderSlotPage(state, 0);

        // The slot list may be loaded lazily on the same turn as a selection
        // (for example, "second slot"). Re-run the canonical selection now
        // that the bounded candidate context is available.
        if (state.lastCanonicalTurn != null
                && state.lastCanonicalTurn.selection().present()) {
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
                logSlotLookupTrace("tryResolveSlotSelection.exit", state, publicDoctorId, date,
                        state.preferredTimeWindow, true, selectableSlots.size(), null);
                return true;
            }
        }
        state.selectedSlot = null;
        state.confirmationPending = false;
        state.pendingAction = null;
        state.awaitingFreshConfirmation = false;
        logSlotLookupTrace("tryResolveSlotSelection.exit", state, publicDoctorId, date, state.preferredTimeWindow, true, selectableSlots.size(), candidates.isEmpty() ? "no-candidates" : null);
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
                        state.workflowSubState == null ? null : state.workflowSubState.name(),
                        state.selectedDoctorName,
                        StringUtils.hasText(state.selectedSpeciality) ? state.selectedSpeciality : state.requestedSpeciality,
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

    private void beginTurnLifecycle(CareAiState state, String externalSessionId) {
        String conversationId = state.currentConversationId == null
                ? (StringUtils.hasText(externalSessionId) ? externalSessionId : "careai-session")
                : state.currentConversationId.toString();
        executionTracker.invalidateReadOnly(conversationId);
        long nextTurn = state.turnSequence + 1;
        state.turnSequence = nextTurn;
        state.executionConversationId = conversationId;
        state.activeTurnId = "turn-" + nextTurn;
        state.pendingSkillId = null;
        state.pendingSkillExecutionId = null;
        state.lastSkillOutcome = null;
        state.lastFallbackAction = PatientPortalCareAiFallbackAction.NONE;
    }

    /**
     * Phase B of the booking migration. This deliberately does not mutate the
     * legacy state or execute a skill; it makes divergence observable before
     * any UAT cutover is permitted.
     */
    private void traceBookingReducerShadow(
            CareAiState state,
            PatientPortalCareAiCanonicalTurn turn,
            PatientPortalCareAiIntent classifiedIntent
    ) {
        if (!reducerBookingShadowEnabled
                || (state.currentIntent != PatientPortalCareAiIntent.BOOK_APPOINTMENT
                && classifiedIntent != PatientPortalCareAiIntent.BOOK_APPOINTMENT)) {
            return;
        }
        PatientPortalCareAiBookingState current = bookingStateSnapshot(state);
        PatientPortalCareAiResolvedTurnFacts facts = resolvedTurnFactsFactory.resolve(
                turn,
                shadowDoctorCandidates(state),
                shadowSupportedSpecialties(state),
                shadowClinicCandidates(state),
                shadowSelectionCandidates(state),
                state.selectedDoctorId,
                state.selectedClinicId
        );
        PatientPortalCareAiStateTransition transition = conversationStateReducer.reduce(
                current,
                turn,
                facts
        );
        PatientPortalCareAiActionDecision newDecision = actionDecider.decide(transition.state(), turn, facts, null);
        PatientPortalCareAiActionDecision oldDecision = legacyBookingDecision(current, turn);
        boolean sameAction = oldDecision.action() == newDecision.action()
                && Objects.equals(oldDecision.skillId(), newDecision.skillId());
        String classification = sameAction ? "MATCH" : shadowClassification(oldDecision, newDecision);
        log.info(
                "CAREAI_TRACE_REDUCER_SHADOW turnId={} stateVersion={} canonicalIntent={} dialogAct={} "
                        + "facts={} legacyWorkflow={} legacySubState={} legacyAction={} legacySkill={} "
                        + "newWorkflow={} newSubState={} newAction={} newSkill={} classification={} differenceReason={} "
                        + "reducerEnabled={} shadowEnabled={}",
                state.activeTurnId,
                current.conversationVersion(),
                classifiedIntent,
                turn.dialogAct(),
                facts.summary(),
                current.workflow(),
                current.subState(),
                oldDecision.action(),
                oldDecision.skillId(),
                transition.state().workflow(),
                transition.state().subState(),
                newDecision.action(),
                newDecision.skillId(),
                classification,
                sameAction ? "same" : oldDecision.reason() + " -> " + newDecision.reason(),
                reducerBookingEnabled,
                reducerBookingShadowEnabled
        );
    }

    private List<CanonicalEntityCandidate> shadowDoctorCandidates(CareAiState state) {
        return state.doctorChoices.stream()
                .map(choice -> new CanonicalEntityCandidate(
                        choice.stableId(), choice.doctorName(), choice.label(), List.of(choice.doctorName())))
                .toList();
    }

    private List<CanonicalEntityCandidate> shadowClinicCandidates(CareAiState state) {
        return state.clinicChoices.stream()
                .map(choice -> new CanonicalEntityCandidate(
                        choice.stableId(), choice.clinicName(), choice.label(),
                        List.of(choice.clinicName(), choice.area(), choice.city).stream()
                                .filter(StringUtils::hasText).toList()))
                .toList();
    }

    private List<CanonicalEntityCandidate> shadowSelectionCandidates(CareAiState state) {
        if (!state.slotChoices.isEmpty()) {
            return state.slotChoices.stream()
                    .map(slot -> new CanonicalEntityCandidate(
                            slot.stableId(), slot.stableId(), slot.slotTime().toString(),
                            List.of(slot.slotTime().toString(), slot.slotTime().toString().replace(":00", ""))))
                    .toList();
        }
        return shadowDoctorCandidates(state);
    }

    private List<String> shadowSupportedSpecialties(CareAiState state) {
        LinkedHashSet<String> specialties = new LinkedHashSet<>();
        if (StringUtils.hasText(state.requestedSpeciality)) specialties.add(state.requestedSpeciality);
        if (StringUtils.hasText(state.selectedSpeciality)) specialties.add(state.selectedSpeciality);
        state.doctorChoices.stream().map(DoctorChoice::speciality).filter(StringUtils::hasText).forEach(specialties::add);
        return List.copyOf(specialties);
    }

    private String shadowClassification(
            PatientPortalCareAiActionDecision oldDecision,
            PatientPortalCareAiActionDecision newDecision
    ) {
        if (oldDecision.action() == PatientPortalCareAiAction.ASK_MISSING_FIELD
                && newDecision.action() != PatientPortalCareAiAction.ASK_MISSING_FIELD) {
            return "EXPECTED_IMPROVEMENT";
        }
        if (newDecision.action() == PatientPortalCareAiAction.ASK_MISSING_FIELD
                && oldDecision.action() != PatientPortalCareAiAction.ASK_MISSING_FIELD) {
            return "NEW_PATH_REGRESSION";
        }
        return "NEEDS_REVIEW";
    }

    private PatientPortalCareAiBookingState bookingStateSnapshot(CareAiState state) {
        PatientPortalCareAiBookingCapability doctor = StringUtils.hasText(state.selectedDoctorId)
                || StringUtils.hasText(state.selectedDoctorName)
                ? new PatientPortalCareAiBookingCapability(
                        state.selectedDoctorId,
                        state.selectedDoctorName,
                        state.selectedClinicId,
                        state.selectedTenantId,
                        state.selectedDoctorBookingMode
                )
                : null;
        LocalDate date = parseIsoDate(state.preferredDate);
        LocalTime exactTime = isExactTime(state.preferredTimeWindow)
                ? LocalTime.parse(state.preferredTimeWindow, TIME_FORMATTER)
                : null;
        PatientPortalCareAiCandidateContext candidates = candidateContextSnapshot(state, date);
        return new PatientPortalCareAiBookingState(
                state.currentIntent,
                state.workflowSubState == null ? PatientPortalCareAiWorkflowSubState.START : state.workflowSubState,
                doctor,
                StringUtils.hasText(state.selectedSpeciality) ? state.selectedSpeciality : state.requestedSpeciality,
                date,
                state.preferredTimeWindow,
                exactTime,
                state.selectedSlot,
                candidates,
                state.pendingAction,
                state.confirmationPending,
                false,
                state.lastSkillOutcome,
                state.turnSequence
        );
    }

    private PatientPortalCareAiCandidateContext candidateContextSnapshot(CareAiState state, LocalDate date) {
        if (state.slotChoices != null && !state.slotChoices.isEmpty()) {
            return new PatientPortalCareAiCandidateContext(
                    "SLOT",
                    state.turnSequence,
                    state.slotChoices.stream().map(SlotChoice::stableId).filter(Objects::nonNull).toList(),
                    state.selectedDoctorId,
                    state.selectedClinicId,
                    date,
                    state.preferredTimeWindow
            );
        }
        if (state.doctorChoices != null && !state.doctorChoices.isEmpty()) {
            return new PatientPortalCareAiCandidateContext(
                    "DOCTOR",
                    state.turnSequence,
                    state.doctorChoices.stream().map(DoctorChoice::stableId).filter(Objects::nonNull).toList(),
                    state.selectedDoctorId,
                    state.selectedClinicId,
                    date,
                    state.preferredTimeWindow
            );
        }
        return null;
    }

    private PatientPortalCareAiActionDecision legacyBookingDecision(
            PatientPortalCareAiBookingState state,
            PatientPortalCareAiCanonicalTurn turn
    ) {
        if (turn.endConversation()) {
            return PatientPortalCareAiActionDecision.of(PatientPortalCareAiAction.END_CONVERSATION, "legacy-control");
        }
        if (turn.abandonWorkflow()) {
            return PatientPortalCareAiActionDecision.of(PatientPortalCareAiAction.ABANDON_WORKFLOW, "legacy-control");
        }
        if (state.confirmationPending() && turn.confirmation() == PatientPortalCareAiConfirmationPolarity.POSITIVE) {
            return new PatientPortalCareAiActionDecision(
                    PatientPortalCareAiAction.EXECUTE_PENDING_ACTION,
                    "appointment.book",
                    "legacy-confirmation-gate",
                    state.pendingAction()
            );
        }
        if (state.confirmationPending()) {
            return PatientPortalCareAiActionDecision.of(PatientPortalCareAiAction.ASK_CONFIRMATION, "legacy-confirmation");
        }
        if (state.candidateContext() != null && "SLOT".equals(state.candidateContext().type())) {
            return PatientPortalCareAiActionDecision.of(PatientPortalCareAiAction.SHOW_SLOT_CHOICES, "legacy-slot-context");
        }
        if (state.resolvedDoctor() == null && state.resolvedSpeciality() == null) {
            return PatientPortalCareAiActionDecision.of(PatientPortalCareAiAction.ASK_MISSING_FIELD, "legacy-provider-missing");
        }
        if (state.resolvedDoctor() == null) {
            return PatientPortalCareAiActionDecision.of(PatientPortalCareAiAction.FIND_PROVIDER, "legacy-provider-search");
        }
        if (state.preferredDate() == null) {
            return PatientPortalCareAiActionDecision.of(PatientPortalCareAiAction.ASK_MISSING_FIELD, "legacy-date-missing");
        }
        return PatientPortalCareAiActionDecision.of(PatientPortalCareAiAction.CHECK_AVAILABILITY, "legacy-availability");
    }

    private static boolean environmentFlag(String name, boolean defaultValue) {
        String value = System.getenv(name);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    private PatientPortalCareAiExecutionIdentity beginSkill(CareAiState state, String skillId, boolean readOnly) {
        String conversationId = StringUtils.hasText(state.executionConversationId)
                ? state.executionConversationId
                : "careai-session";
        PatientPortalCareAiExecutionIdentity identity = executionTracker.begin(
                conversationId,
                state.activeTurnId == null ? "turn-0" : state.activeTurnId,
                skillId,
                readOnly
        );
        state.pendingSkillId = skillId;
        state.pendingSkillExecutionId = identity.skillExecutionId();
        state.pendingSkillStartedAt = System.currentTimeMillis();
        PatientPortalCareAiWorkflowSubState current = state.workflowSubState;
        if (current != PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, "skill-start:" + skillId);
        }
        careAiTrace("skill.lifecycle", "started", state,
                "skillId=" + skillId + " skillExecutionId=" + identity.skillExecutionId()
                        + " readOnly=" + readOnly);
        Consumer<PatientPortalCareAiProgressEvent> progressSink = ACTIVE_PROGRESS_SINK.get();
        if (progressSink != null) {
            progressScheduler.schedule(() -> {
                if (!executionTracker.isCurrent(identity)) {
                    return;
                }
                progressSink.accept(new PatientPortalCareAiProgressEvent(
                        identity.turnId(),
                        identity.skillExecutionId(),
                        identity.skillId(),
                        progressKeyFor(identity.skillId()),
                        PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL.name(),
                        waitingPolicy.acknowledgement(identity.skillId())
                ));
            }, 700, TimeUnit.MILLISECONDS);
        }
        applyOptInProgressTestDelay(skillId);
        return identity;
    }

    /**
     * Dev/UAT-only delay hook. It is disabled unless explicitly configured in the
     * process environment and is limited to read-only search skills.
     */
    private void applyOptInProgressTestDelay(String skillId) {
        if (!"doctor.find".equals(skillId) && !"availability.check".equals(skillId)) {
            return;
        }
        String configured = System.getenv("AIVA_CAREAI_PROGRESS_TEST_DELAY_MS");
        if (!StringUtils.hasText(configured)) {
            return;
        }
        try {
            long delayMs = Math.max(0, Math.min(10_000, Long.parseLong(configured.trim())));
            if (delayMs == 0) {
                return;
            }
            Thread.sleep(delayMs);
        } catch (NumberFormatException ignored) {
            // Invalid UAT configuration leaves production behavior unchanged.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private String progressKeyFor(String skillId) {
        return switch (skillId) {
            case "doctor.find" -> "checking-doctors";
            case "clinic.find" -> "checking-clinics";
            case "service.find" -> "checking-services";
            case "availability.check" -> "checking-availability";
            case "appointment.check" -> "checking-appointment";
            case "appointment.book" -> "confirming-booking";
            case "appointment.cancel" -> "confirming-cancellation";
            case "appointment.reschedule" -> "confirming-reschedule";
            default -> "checking-request";
        };
    }

    private PatientPortalCareAiSkillOutcome finishSkill(CareAiState state,
                                                         PatientPortalCareAiExecutionIdentity identity,
                                                         PatientPortalCareAiSkillOutcome outcome) {
        if (!executionTracker.isCurrent(identity)) {
            state.lastSkillOutcome = PatientPortalCareAiSkillOutcome.STALE;
            state.lastFallbackAction = fallbackPolicyRegistry.actionFor(identity.skillId(), PatientPortalCareAiSkillOutcome.STALE);
            careAiTrace("skill.lifecycle", "stale", state,
                    "skillId=" + identity.skillId() + " skillExecutionId=" + identity.skillExecutionId());
            return PatientPortalCareAiSkillOutcome.STALE;
        }
        state.lastSkillOutcome = outcome;
        state.lastFallbackAction = fallbackPolicyRegistry.actionFor(identity.skillId(), outcome);
        long elapsedMs = state.pendingSkillStartedAt <= 0
                ? 0
                : Math.max(0, System.currentTimeMillis() - state.pendingSkillStartedAt);
        state.pendingSkillId = null;
        state.pendingSkillExecutionId = null;
        executionTracker.invalidate(identity);
        careAiTrace("skill.lifecycle", "completed", state,
                "skillId=" + identity.skillId() + " skillExecutionId=" + identity.skillExecutionId()
                        + " outcome=" + outcome + " fallback=" + state.lastFallbackAction
                        + " elapsedMs=" + elapsedMs
                        + " acknowledgementEligible=" + waitingPolicy.shouldAcknowledge(java.time.Duration.ofMillis(elapsedMs)));
        return outcome;
    }

    private <T> PatientPortalCareAiSkillResult<T> unavailableSkillResult(String message, RuntimeException ex) {
        String detail = ex == null || ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        Throwable cause = ex == null ? null : ex.getCause();
        if (cause instanceof TimeoutException || detail.contains("timeout") || detail.contains("timed out")) {
            return PatientPortalCareAiSkillResult.timeout(message);
        }
        return PatientPortalCareAiSkillResult.temporarilyUnavailable(message);
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
        setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.EXECUTING, "confirmation-execution");
        PatientPortalCareAiExecutionIdentity execution = beginSkill(state, pendingSkillIdFor(state.pendingAction), false);
        try {
            logBookingOrMutationRequest("executeConfirmedAction", state);
            PatientPortalCareAiSkillResult<PatientPortalAppointmentConfirmationResponse> skillResult = switch (state.pendingAction) {
                case BOOK_APPOINTMENT -> toolRegistry.appointmentBook().execute(new PatientPortalCareAiAppointmentBookSkillInput(
                        state.selectedDoctorId,
                        state.selectedClinicSlug,
                        state.selectedTenantId,
                        state.selectedClinicId,
                        state.selectedBookingReference,
                        state.selectedDoctorBookingMode,
                        LocalDate.parse(state.preferredDate),
                        LocalTime.parse(state.selectedSlot, TIME_FORMATTER),
                        state.reason,
                        true,
                        writeReconciliationPolicy.idempotencyKey(
                                state.executionConversationId,
                                state.pendingAction.name(),
                                StringUtils.hasText(state.activeConfirmationScopeKey)
                                        ? state.activeConfirmationScopeKey
                                        : execution.skillExecutionId())
                ));
                case RESCHEDULE_APPOINTMENT -> toolRegistry.appointmentReschedule().execute(new PatientPortalCareAiAppointmentRescheduleSkillInput(
                        UUID.fromString(state.selectedAppointmentId),
                        LocalDate.parse(state.preferredDate),
                        LocalTime.parse(state.selectedSlot, TIME_FORMATTER),
                        state.selectedAppointmentReason,
                        true,
                        writeIdempotencyKey(state, execution)
                ));
                case CANCEL_APPOINTMENT -> toolRegistry.appointmentCancel().execute(new PatientPortalCareAiAppointmentCancelSkillInput(
                        UUID.fromString(state.selectedAppointmentId),
                        true,
                        writeIdempotencyKey(state, execution)
                ));
                case CHECK_APPOINTMENT, APPOINTMENT_STATUS -> throw new IllegalStateException("Status lookups do not require confirmation");
                default -> throw new IllegalStateException("Unsupported confirmation action: " + state.pendingAction);
            };
            PatientPortalCareAiSkillOutcome lifecycleOutcome = finishSkill(state, execution, skillResult.outcome());
            if (lifecycleOutcome == PatientPortalCareAiSkillOutcome.STALE) {
                return response(state, "That request is no longer current. Please tell me what you would like to do next.");
            }
            if (skillResult.outcome() != PatientPortalCareAiSkillOutcome.SUCCESS) {
                careAiTrace("executeConfirmedAction", "skill-non-success", state,
                        "pendingAction=" + state.pendingAction
                                + " outcome=" + skillResult.outcome()
                                + " message=" + skillResult.message());
                setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.FAILED, "confirmation-execution-failed");
                state.actionCompleted = false;
                state.booked = false;
                state.bookingStatus = skillResult.outcome().name();
                state.bookedAppointmentDate = null;
                state.bookedAppointmentTime = null;
                state.confirmationPending = false;
                state.pendingAction = null;
                state.activeConfirmationScopeKey = null;
                state.awaitingFreshConfirmation = false;
                return response(state, skillResult.message());
            }
            careAiTrace("executeConfirmedAction", "success", state,
                    "pendingAction=" + state.pendingAction
                            + " confirmationStatus=" + skillResult.outcome()
                            + " message=" + skillResult.message());
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.COMPLETED, "confirmation-execution-complete");
            state.actionCompleted = true;
            state.booked = state.pendingAction == PatientPortalCareAiIntent.BOOK_APPOINTMENT;
            state.lastAction = state.pendingAction;
            PatientPortalAppointmentConfirmationResponse confirmation = skillResult.value();
            state.bookingStatus = confirmation == null ? null : confirmation.status();
            state.bookedAppointmentDate = confirmation == null || confirmation.appointmentDate() == null ? null : confirmation.appointmentDate().toString();
            state.bookedAppointmentTime = confirmation == null || confirmation.appointmentTime() == null ? null : confirmation.appointmentTime().format(TIME_FORMATTER);
            state.confirmationPending = false;
            state.pendingAction = null;
            state.activeConfirmationScopeKey = null;
            state.awaitingFreshConfirmation = false;
            state.handoffRequired = false;
            state.handoffReason = null;
            state.unresolvedTurns = 0;
            if (state.lastAction == PatientPortalCareAiIntent.CANCEL_APPOINTMENT) {
                ensureAppointmentOptions(state);
                clearAppointmentSelection(state);
            }
            completeWorkflowCleanup(state);
            return response(state, confirmation == null ? skillResult.message() : confirmation.message());
        } catch (RuntimeException ex) {
            finishSkill(state, execution, PatientPortalCareAiSkillOutcome.FAILED);
            careAiTrace("executeConfirmedAction", "error", state,
                    "pendingAction=" + state.pendingAction + " error=" + ex.getMessage());
            if (isTimeoutException(ex)) {
                PatientPortalCareAiWriteReconciliationStatus reconciliation = reconcileWrite(state);
                state.bookingStatus = reconciliation == PatientPortalCareAiWriteReconciliationStatus.SUCCESS
                        ? "RECONCILED_SUCCESS"
                        : "PENDING_RECONCILIATION";
                state.confirmationPending = false;
                state.pendingAction = null;
                state.activeConfirmationScopeKey = null;
                state.awaitingFreshConfirmation = false;
                state.actionCompleted = reconciliation == PatientPortalCareAiWriteReconciliationStatus.SUCCESS;
                setWorkflowSubState(state, reconciliation == PatientPortalCareAiWriteReconciliationStatus.SUCCESS
                        ? PatientPortalCareAiWorkflowSubState.COMPLETED
                        : PatientPortalCareAiWorkflowSubState.FAILED, "write-timeout-reconciliation");
                if (reconciliation == PatientPortalCareAiWriteReconciliationStatus.SUCCESS) {
                    return response(state, "I confirmed that the appointment change was completed.");
                }
                return response(state, reconciliation == PatientPortalCareAiWriteReconciliationStatus.STILL_UNKNOWN
                        ? "I couldn't confirm whether the appointment change was completed. I won't submit it again until I verify the status."
                        : "The appointment change was not completed. You can safely try again.");
            }
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.FAILED, "confirmation-execution-error");
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

    private boolean isTimeoutException(RuntimeException ex) {
        if (ex == null) {
            return false;
        }
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        return ex.getCause() instanceof TimeoutException
                || message.contains("timeout")
                || message.contains("timed out");
    }

    private PatientPortalCareAiWriteReconciliationStatus reconcileWrite(CareAiState state) {
        try {
            List<PatientPortalCareAiAppointmentOption> appointments = patientPortalService.debugAppointments();
            boolean match = appointments.stream().anyMatch(appointment -> writeMatches(state, appointment));
            return writeReconciliationPolicy.reconcile(match, !match, false);
        } catch (RuntimeException ex) {
            return writeReconciliationPolicy.reconcile(false, false, true);
        }
    }

    private boolean writeMatches(CareAiState state, PatientPortalCareAiAppointmentOption appointment) {
        if (state.pendingAction == PatientPortalCareAiIntent.BOOK_APPOINTMENT) {
            return StringUtils.hasText(state.selectedDoctorId)
                    && appointment.doctorUserId() != null
                    && state.selectedDoctorId.equals(appointment.doctorUserId().toString())
                    && Objects.equals(state.preferredDate, appointment.appointmentDate() == null ? null : appointment.appointmentDate().toString())
                    && Objects.equals(state.selectedSlot, appointment.appointmentTime() == null ? null : appointment.appointmentTime().format(TIME_FORMATTER));
        }
        return state.selectedAppointmentId != null
                && appointment.appointmentId() != null
                && state.selectedAppointmentId.equals(appointment.appointmentId().toString())
                && (state.pendingAction == PatientPortalCareAiIntent.CANCEL_APPOINTMENT
                ? "CANCELLED".equalsIgnoreCase(appointment.status())
                : Objects.equals(state.preferredDate, appointment.appointmentDate() == null ? null : appointment.appointmentDate().toString())
                && Objects.equals(state.selectedSlot, appointment.appointmentTime() == null ? null : appointment.appointmentTime().format(TIME_FORMATTER)));
    }

    private String pendingSkillIdFor(PatientPortalCareAiIntent action) {
        return switch (action) {
            case BOOK_APPOINTMENT -> "appointment.book";
            case CANCEL_APPOINTMENT -> "appointment.cancel";
            case RESCHEDULE_APPOINTMENT -> "appointment.reschedule";
            default -> "appointment.write";
        };
    }

    private String writeIdempotencyKey(CareAiState state, PatientPortalCareAiExecutionIdentity execution) {
        return writeReconciliationPolicy.idempotencyKey(
                state.executionConversationId,
                state.pendingAction == null ? "appointment.write" : state.pendingAction.name(),
                StringUtils.hasText(state.activeConfirmationScopeKey)
                        ? state.activeConfirmationScopeKey
                        : execution.skillExecutionId());
    }

    private List<PatientPortalDoctorSlotResponse> loadDoctorSlots(CareAiState state, String publicDoctorId, String clinicSlug, String tenantId, String clinicId, LocalDate date) {
        careAiTrace("loadDoctorSlots", "enter", state,
                "doctorId=" + publicDoctorId
                        + " bookingReference=" + (state == null ? null : state.selectedBookingReference)
                        + " clinicSlug=" + clinicSlug
                        + " tenantId=" + tenantId
                        + " clinicId=" + clinicId
                        + " date=" + date
                        + " conversationTenantId=" + RequestContextHolder.requireTenantId()
                        + " tenantContextTenantId=" + (RequestContextHolder.get() == null ? null : RequestContextHolder.get().tenantId().value()));
        if (log.isDebugEnabled()) {
            log.debug(
                    "patient.portal.careai.slot.lookup.invoke source=web-public-patient-careai conversationTenantId={} patientPortalSessionId={} patientId={} patientMobile={} bookingReference={} doctorId={} selectedDoctorName={} selectedClinicSlug={} selectedTenantId={} selectedClinicId={} date={}",
                    RequestContextHolder.requireTenantId(),
                    RequestContextHolder.require().correlationId(),
                    patientPortalService.currentPatientId(),
                    redactedPatientDiagnosticId(),
                    state == null ? null : state.selectedBookingReference,
                    publicDoctorId,
                    state == null ? null : state.selectedDoctorName,
                    clinicSlug,
                    tenantId,
                    clinicId,
                    date
            );
        }
        setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.CHECKING_AVAILABILITY, "availability-check-start");
        PatientPortalCareAiExecutionIdentity execution = beginSkill(state, "availability.check", true);
        PatientPortalCareAiSkillResult<List<PatientPortalDoctorSlotResponse>> skillResult;
        try {
            skillResult = toolRegistry.availabilityCheck().execute(
                    new PatientPortalCareAiAvailabilityCheckSkillInput(
                            state == null ? null : state.selectedBookingReference,
                            publicDoctorId,
                            clinicSlug,
                            tenantId,
                            clinicId,
                            date
                    )
            );
        } catch (RuntimeException ex) {
            skillResult = unavailableSkillResult("Availability is temporarily unavailable.", ex);
        }
        PatientPortalCareAiSkillOutcome lifecycleOutcome = finishSkill(state, execution, skillResult.outcome());
        if (lifecycleOutcome == PatientPortalCareAiSkillOutcome.STALE) {
            return List.of();
        }
        List<PatientPortalDoctorSlotResponse> slots = skillResult.value() == null ? List.of() : skillResult.value();
        if (skillResult.outcome() == PatientPortalCareAiSkillOutcome.FAILED
                || skillResult.outcome() == PatientPortalCareAiSkillOutcome.NOT_AUTHORIZED
                || skillResult.outcome() == PatientPortalCareAiSkillOutcome.TIMEOUT
                || skillResult.outcome() == PatientPortalCareAiSkillOutcome.TEMPORARILY_UNAVAILABLE) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.FAILED, "availability-check-failed");
        } else if (slots.isEmpty()) {
            String criteria = availabilityCriteria(state, date);
            if (!criteria.equals(state.lastAvailabilityNoMatchCriteria)
                    && isOnlineBookable(state)
                    && StringUtils.hasText(state.preferredTimeWindow)) {
                state.lastAvailabilityNoMatchCriteria = criteria;
                applyFutureAvailabilityFallback(state, date);
            }
            if (state.slotOptions.isEmpty()) {
                setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_DATE, "availability-check-empty");
            }
        } else {
            state.lastAvailabilityNoMatchCriteria = null;
            state.lastNoSlotPromptCriteria = null;
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION, "availability-check-results");
        }
        careAiTrace("loadDoctorSlots", "exit", state,
                "skill=availability.check outcome=" + skillResult.outcome()
                        + " resultCount=" + slots.size()
                        + " results=" + summarizeSlots(slots));
        return slots;
    }

    private String availabilityCriteria(CareAiState state, LocalDate date) {
        return String.join("|",
                nullToBlank(state.selectedDoctorId),
                nullToBlank(state.selectedClinicId),
                nullToBlank(state.selectedTenantId),
                String.valueOf(date),
                nullToBlank(state.preferredTimeWindow));
    }

    private boolean isOnlineBookable(CareAiState state) {
        return state != null
                && (!StringUtils.hasText(state.selectedDoctorBookingMode)
                || !state.selectedDoctorBookingMode.toUpperCase(Locale.ROOT).contains("CALL_TO_BOOK"));
    }

    private String searchAlternativeAvailability(CareAiState state) {
        if (!isOnlineBookable(state)) {
            return callToBookFallbackPrompt(state);
        }
        if (!StringUtils.hasText(state.preferredDate)) {
            return askDatePrompt(state);
        }
        state.lastAvailabilityNoMatchCriteria = null;
        applyFutureAvailabilityFallback(state, LocalDate.parse(state.preferredDate));
        if (state.slotOptions.isEmpty()) {
            return unavailablePreferredWindowPrompt(state, state.preferredTimeWindow, List.of());
        }
        return slotChoicePrompt(state);
    }

    private void applyFutureAvailabilityFallback(CareAiState state, LocalDate requestedDate) {
        state.lastAvailabilityNoMatchCriteria = availabilityCriteria(state, requestedDate);
        PatientPortalDoctorAvailabilityResponse availability;
        try {
            availability = patientPortalService.doctorAvailability(
                    state.selectedBookingReference,
                    state.selectedDoctorId,
                    state.selectedClinicSlug,
                    state.selectedTenantId,
                    state.selectedClinicId,
                    requestedDate
            );
        } catch (RuntimeException ex) {
            careAiTrace("availabilityFallback", "error", state, "date=" + requestedDate + " error=" + ex.getClass().getSimpleName());
            return;
        }
        List<PatientPortalDoctorSlotResponse> futureSlots = availability == null || availability.nextAvailable() == null
                ? List.of()
                : availability.nextAvailable().stream()
                .filter(day -> day != null && day.slots() != null)
                .flatMap(day -> day.slots().stream())
                .filter(PatientPortalDoctorSlotResponse::selectable)
                .sorted(Comparator.comparing(PatientPortalDoctorSlotResponse::appointmentDate)
                        .thenComparing(PatientPortalDoctorSlotResponse::slotTime))
                .toList();
        if (futureSlots.isEmpty()) {
            return;
        }
        state.futureAvailabilityFallbackAppliedThisTurn = true;
        List<PatientPortalDoctorSlotResponse> timeMatches = filterSlots(futureSlots, state.preferredTimeWindow);
        boolean preservedTime = !timeMatches.isEmpty();
        List<PatientPortalDoctorSlotResponse> candidates = (preservedTime ? timeMatches : futureSlots).stream()
                .limit(6)
                .toList();
        state.allSlotChoices = candidates.stream()
                .map(slot -> new SlotChoice(slot.appointmentDate(), slot.slotTime()))
                .toList();
        state.shownSlotOffset = 0;
        state.selectedSlot = null;
        state.confirmationPending = false;
        state.pendingAction = null;
        state.awaitingFreshConfirmation = false;
        state.slotPromptLead = futureAvailabilityPrompt(state, preservedTime);
        state.lastNoSlotPromptCriteria = availabilityCriteria(state, requestedDate);
        renderSlotPage(state, 0);
        setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION, "future-availability-results");
    }

    private String futureAvailabilityPrompt(CareAiState state, boolean preservedTime) {
        String doctor = safe(state.selectedDoctorName);
        String date = humanReadablePreferredDate(state.preferredDate);
        if (isHindi(state.language)) {
            return preservedTime
                    ? date + " पर स्लॉट नहीं मिला। उसी समय के अगले उपलब्ध विकल्प ये हैं:"
                    : date + " पर उस समय स्लॉट नहीं मिला। अगले उपलब्ध विकल्प ये हैं: कृपया एक चुनिए।";
        }
        return preservedTime
                ? "I couldn't find a slot for " + doctor + " on " + date + ". Here are the next available options:"
                : "I couldn't find an " + safe(state.preferredTimeWindow) + " slot for " + doctor + " on " + date
                + ". I couldn't preserve that time window on the next dates, so here are the next available options:";
    }

    private String callToBookFallbackPrompt(CareAiState state) {
        return isHindi(state.language)
                ? safe(state.selectedDoctorName) + " के लिए क्लिनिक से फोन पर बुकिंग करनी होगी।"
                : safe(state.selectedDoctorName) + " requires booking through the clinic. I can show the profile or clinic contact details.";
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
        restoreWorkflowSubStateAfterPendingActionClear(state);
        return true;
    }

    private void restoreWorkflowSubStateAfterPendingActionClear(CareAiState state) {
        if (state == null) {
            return;
        }
        if (state.currentIntent == PatientPortalCareAiIntent.BOOK_APPOINTMENT) {
            if (StringUtils.hasText(state.selectedDoctorId) && StringUtils.hasText(state.preferredDate)) {
                setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.CHECKING_AVAILABILITY, "confirmation-cancelled");
            } else if (StringUtils.hasText(state.selectedDoctorId)) {
                setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_DATE, "confirmation-cancelled");
            } else {
                setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY, "confirmation-cancelled");
            }
            return;
        }
        if (state.currentIntent == PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT) {
            if (StringUtils.hasText(state.selectedAppointmentId) && StringUtils.hasText(state.preferredDate)) {
                setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.CHECKING_AVAILABILITY, "confirmation-cancelled");
            } else if (StringUtils.hasText(state.selectedAppointmentId)) {
                setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_NEW_DATE, "confirmation-cancelled");
            } else {
                setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_APPOINTMENT, "confirmation-cancelled");
            }
            return;
        }
        if (state.currentIntent == PatientPortalCareAiIntent.CANCEL_APPOINTMENT) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_APPOINTMENT, "confirmation-cancelled");
            return;
        }
        if (state.currentIntent == PatientPortalCareAiIntent.FIND_DOCTOR
                || state.currentIntent == PatientPortalCareAiIntent.FIND_CLINIC
                || state.currentIntent == PatientPortalCareAiIntent.CHECK_APPOINTMENT
                || state.currentIntent == PatientPortalCareAiIntent.APPOINTMENT_STATUS) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.RESOLVING, "confirmation-cancelled");
        }
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
        state.selectedBookingReference = null;
        state.selectedDoctorName = null;
        state.selectedDoctorBookingMode = null;
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
        state.lastAvailabilityNoMatchCriteria = null;
        state.lastNoSlotPromptCriteria = null;
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
        clearEntityExtraction(state);
        state.transientWorkflowType = null;
        state.activeTaskId = null;
        state.activeTaskType = null;
        state.workflowSubState = initialWorkflowSubState(intent);
    }

    private void clearDoctorSelection(CareAiState state) {
        state.selectedDoctorId = null;
        state.selectedDoctorSlug = null;
        state.selectedBookingReference = null;
        state.selectedDoctorName = null;
        state.selectedDoctorBookingMode = null;
        state.selectedSpeciality = null;
        state.selectedClinicId = null;
        state.selectedTenantId = null;
        state.selectedClinicSlug = null;
        state.selectedClinicName = null;
        state.doctorChoices = List.of();
        state.doctorOptions = List.of();
        clearSlotSelection(state);
        if (state.currentIntent == PatientPortalCareAiIntent.BOOK_APPOINTMENT) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY, "doctor-selection-cleared");
        } else if (state.currentIntent == PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_APPOINTMENT, "doctor-selection-cleared");
        } else if (state.currentIntent == PatientPortalCareAiIntent.FIND_DOCTOR) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.RESOLVING, "doctor-selection-cleared");
        }
    }

    private void clearAppointmentSelection(CareAiState state) {
        state.selectedAppointmentId = null;
        state.selectedAppointmentLabel = null;
        state.selectedAppointmentReason = null;
        state.appointmentOptions = List.of();
        if (state.currentIntent != PatientPortalCareAiIntent.BOOK_APPOINTMENT) {
            state.selectedDoctorId = null;
            state.selectedDoctorSlug = null;
            state.selectedBookingReference = null;
            state.selectedDoctorName = null;
            state.selectedDoctorBookingMode = null;
            state.selectedSpeciality = null;
            state.selectedClinicId = null;
            state.selectedTenantId = null;
            state.selectedClinicSlug = null;
            state.selectedClinicName = null;
        }
        state.clinicChoices = List.of();
        state.clinicOptions = List.of();
        clearSlotSelection(state);
        if (state.currentIntent == PatientPortalCareAiIntent.BOOK_APPOINTMENT) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY, "appointment-selection-cleared");
        } else if (state.currentIntent == PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_APPOINTMENT, "appointment-selection-cleared");
        } else if (state.currentIntent == PatientPortalCareAiIntent.CHECK_APPOINTMENT
                || state.currentIntent == PatientPortalCareAiIntent.CANCEL_APPOINTMENT) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_APPOINTMENT, "appointment-selection-cleared");
        }
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

    private void resetPromptRepetitionTracking(CareAiState state) {
        if (state == null) {
            return;
        }
        state.lastQuestionKey = null;
        state.repeatedQuestionCount = 0;
        state.timePromptCount = 0;
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
        state.workflowSubState = PatientPortalCareAiWorkflowSubState.COMPLETED;
        state.pendingWorkflowEventType = "WORKFLOW_COMPLETED";
        state.pendingWorkflowEventPayloadJson = workflowMetadataJson(state);
    }

    private boolean ensureAppointmentOptions(CareAiState state) {
        careAiTrace("ensureAppointmentOptions", "enter", state,
                "patientId=" + patientPortalService.currentPatientId()
                        + " patientMobile=" + redactedPatientDiagnosticId()
                        + " conversationTenantId=" + RequestContextHolder.requireTenantId()
                        + " tenantContextTenantId=" + (RequestContextHolder.get() == null ? null : RequestContextHolder.get().tenantId().value()));
        setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.RESOLVING, "appointment-check");
        PatientPortalCareAiSkillResult<List<PatientPortalCareAiAppointmentOption>> skillResult = toolRegistry.appointmentCheck().execute(
                new PatientPortalCareAiAppointmentCheckSkillInput(
                        patientPortalService.currentPatientId() == null ? null : String.valueOf(patientPortalService.currentPatientId()),
                        patientPortalService.currentPatientMobile()
                )
        );
        List<PatientPortalCareAiAppointmentOption> appointments = skillResult.value() == null ? List.of() : skillResult.value();
        logAppointmentLookup("ensureAppointmentOptions", state, appointments);
        List<AppointmentChoice> choices = appointments.stream()
                .filter(appointment -> !isCancelledAppointmentStatus(appointment.status()))
                .sorted(Comparator
                        .comparing(PatientPortalCareAiAppointmentOption::appointmentDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PatientPortalCareAiAppointmentOption::appointmentTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toAppointmentChoice)
                .toList();
        state.appointmentOptions = choices;
        setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.COMPLETED, "appointment-check-complete");
        careAiTrace("ensureAppointmentOptions", "exit", state,
                "skill=appointment.check outcome=" + skillResult.outcome()
                        + " resultCount=" + choices.size()
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
        List<DoctorChoice> doctors = searchPublicBookableDoctors(state, doctorHintValue, specialityHint);
        CanonicalResolution selectionResolution = doctorResolver.resolve(
                message,
                doctorCandidates(doctors),
                state.selectedDoctorId
        );
        if (selectionResolution.resolved()) {
            List<DoctorChoice> resolved = filterDoctorsByCandidateIds(doctors, selectionResolution.candidateIds());
            if (!resolved.isEmpty()) {
                logDoctorLookup("resolveDoctorMatches", state, doctorHintValue, specialityHint, doctors, resolved, selectionResolution.source());
                return resolved;
            }
        }
        if (selectionResolution.ambiguous()) {
            List<DoctorChoice> ambiguous = filterDoctorsByCandidateIds(doctors, selectionResolution.candidateIds());
            if (!ambiguous.isEmpty()) {
                logDoctorLookup("resolveDoctorMatches", state, doctorHintValue, specialityHint, doctors, ambiguous, selectionResolution.source());
                return ambiguous;
            }
        }
        if (StringUtils.hasText(state.requestedSpeciality)) {
            List<DoctorChoice> specialityMatches = doctors.stream()
                    .filter(doctor -> containsIgnoreCase(doctor.speciality(), state.requestedSpeciality))
                    .sorted(Comparator.comparing(DoctorChoice::doctorName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            logDoctorLookup("resolveDoctorMatches", state, doctorHintValue, specialityHint, doctors, specialityMatches, "speciality-only");
            return specialityMatches;
        }
        String normalizedMessage = normalizeDoctorText(message);
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
            logDoctorLookup("resolveDoctorMatches", state, doctorHintValue, specialityHint, doctors, matches, "token-match");
            return matches;
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
        CanonicalResolution selectionResolution = clinicResolver.resolve(
                message,
                clinicCandidates(clinics),
                state.selectedClinicSlug
        );
        if (selectionResolution.resolved()) {
            List<ClinicChoice> resolved = filterClinicsByCandidateIds(clinics, selectionResolution.candidateIds());
            if (!resolved.isEmpty()) {
                careAiTrace("resolveClinicMatches", "exit", state,
                        "source=" + selectionResolution.source()
                                + " resultCount=" + resolved.size()
                                + " results=" + resolved.stream().limit(5).map(ClinicChoice::label).toList());
                return resolved;
            }
        }
        if (selectionResolution.ambiguous()) {
            List<ClinicChoice> ambiguous = filterClinicsByCandidateIds(clinics, selectionResolution.candidateIds());
            if (!ambiguous.isEmpty()) {
                careAiTrace("resolveClinicMatches", "exit", state,
                        "source=" + selectionResolution.source()
                                + " resultCount=" + ambiguous.size()
                                + " results=" + ambiguous.stream().limit(5).map(ClinicChoice::label).toList());
                return ambiguous;
            }
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
                null,
                doctor.doctorName(),
                doctor.specialization(),
                null,
                null,
                null,
                null,
                doctorLabel(doctor),
                null,
                false
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
                doctor.bookingReference(),
                doctor.doctorDisplayName(),
                doctor.speciality(),
                null,
                null,
                doctor.clinicSlug(),
                doctor.clinicDisplayName(),
                doctorLabel(doctor.doctorDisplayName(), doctor.speciality(), doctor.clinicDisplayName()),
                doctor.bookingMode(),
                doctor.canBookOnline()
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
        state.selectedBookingReference = selected.bookingReference();
        state.selectedDoctorName = selected.doctorName();
        state.selectedSpeciality = selected.speciality();
        state.selectedDoctorBookingMode = selected.bookingMode();
        state.selectedClinicId = selected.clinicId();
        state.selectedTenantId = selected.tenantId();
        state.selectedClinicSlug = selected.clinicSlug();
        state.selectedClinicName = selected.clinicName();
        state.timePromptCount = 0;
        state.doctorChoices = List.of();
        state.doctorOptions = List.of();
        state.lastSideTopic = null;
        clearSlotSelection(state);
        if (state.currentIntent == PatientPortalCareAiIntent.BOOK_APPOINTMENT) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_DATE, "doctor-selected");
        } else if (state.currentIntent == PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_NEW_DATE, "doctor-selected");
        } else if (state.currentIntent == PatientPortalCareAiIntent.FIND_DOCTOR) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.COMPLETED, "doctor-discovery-complete");
        }
        if ((state.currentIntent == PatientPortalCareAiIntent.BOOK_APPOINTMENT
                || state.currentIntent == PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT)
                && StringUtils.hasText(state.preferredDate)) {
            if (!isOnlineBookable(state)) {
                return;
            }
            refreshSlotChoicesAfterVoiceCorrection(state);
        }
        careAiTrace("selectDoctor", "exit", state,
                "selectedDoctorId=" + state.selectedDoctorId
                        + " selectedDoctorSlug=" + state.selectedDoctorSlug
                        + " selectedBookingReference=" + state.selectedBookingReference
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
        if (state.currentIntent == PatientPortalCareAiIntent.BOOK_APPOINTMENT) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION, "clinic-selected");
        } else if (state.currentIntent == PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_APPOINTMENT, "clinic-selected");
        } else if (state.currentIntent == PatientPortalCareAiIntent.FIND_CLINIC) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.COMPLETED, "clinic-discovery-complete");
        }
        careAiTrace("selectClinic", "exit", state,
                "selectedClinicSlug=" + state.selectedClinicSlug
                        + " selectedClinicName=" + state.selectedClinicName
                        + " selectedClinicId=" + state.selectedClinicId
                        + " selectedTenantId=" + state.selectedTenantId);
    }

    private List<CanonicalEntityCandidate> doctorCandidates(List<DoctorChoice> doctors) {
        if (doctors == null || doctors.isEmpty()) {
            return List.of();
        }
        return doctors.stream()
                .map(doctor -> new CanonicalEntityCandidate(
                        nullToBlank(doctor.publicDoctorId()),
                        doctor.doctorName(),
                        doctor.label(),
                        doctorCandidateAliases(doctor)
                ))
                .toList();
    }

    private List<String> doctorCandidateAliases(DoctorChoice doctor) {
        List<String> aliases = new ArrayList<>();
        if (StringUtils.hasText(doctor.doctorName())) {
            aliases.add(doctor.doctorName());
        }
        if (StringUtils.hasText(doctor.label())) {
            aliases.add(doctor.label());
        }
        if (StringUtils.hasText(doctor.speciality())) {
            aliases.add(doctor.speciality());
        }
        if (StringUtils.hasText(doctor.clinicName())) {
            aliases.add(doctor.clinicName());
        }
        return aliases.stream().filter(StringUtils::hasText).distinct().toList();
    }

    private List<CanonicalEntityCandidate> clinicCandidates(List<ClinicChoice> clinics) {
        if (clinics == null || clinics.isEmpty()) {
            return List.of();
        }
        return clinics.stream()
                .map(clinic -> new CanonicalEntityCandidate(
                        nullToBlank(clinic.clinicSlug()),
                        clinic.clinicName(),
                        clinic.label(),
                        clinicCandidateAliases(clinic)
                ))
                .toList();
    }

    private List<String> clinicCandidateAliases(ClinicChoice clinic) {
        List<String> aliases = new ArrayList<>();
        if (StringUtils.hasText(clinic.clinicName())) {
            aliases.add(clinic.clinicName());
        }
        if (StringUtils.hasText(clinic.label())) {
            aliases.add(clinic.label());
        }
        if (StringUtils.hasText(clinic.area())) {
            aliases.add(clinic.area());
        }
        if (StringUtils.hasText(clinic.city())) {
            aliases.add(clinic.city());
        }
        return aliases.stream().filter(StringUtils::hasText).distinct().toList();
    }

    private List<CanonicalEntityCandidate> slotCandidates(List<SlotChoice> slots) {
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }
        return slots.stream()
                .map(slot -> new CanonicalEntityCandidate(
                        slot.slotTime() == null ? null : slot.slotTime().format(TIME_FORMATTER),
                        slot.slotTime() == null ? null : slot.slotTime().format(TIME_FORMATTER),
                        slot.slotTime() == null ? null : slot.slotTime().format(TIME_FORMATTER),
                        slot.slotTime() == null ? List.of() : List.of(slot.slotTime().format(TIME_FORMATTER))
                ))
                .toList();
    }

    private List<DoctorChoice> filterDoctorsByCandidateIds(List<DoctorChoice> doctors, List<String> candidateIds) {
        if (doctors == null || doctors.isEmpty() || candidateIds == null || candidateIds.isEmpty()) {
            return List.of();
        }
        return doctors.stream()
                .filter(choice -> candidateIds.contains(choice.publicDoctorId()))
                .sorted(Comparator.comparing(DoctorChoice::doctorName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<ClinicChoice> filterClinicsByCandidateIds(List<ClinicChoice> clinics, List<String> candidateIds) {
        if (clinics == null || clinics.isEmpty() || candidateIds == null || candidateIds.isEmpty()) {
            return List.of();
        }
        return clinics.stream()
                .filter(choice -> candidateIds.contains(choice.clinicSlug()))
                .sorted(Comparator.comparing(ClinicChoice::clinicName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private DoctorChoice doctorChoiceById(List<DoctorChoice> doctors, String doctorId) {
        if (!StringUtils.hasText(doctorId) || doctors == null || doctors.isEmpty()) {
            return null;
        }
        return doctors.stream()
                .filter(choice -> doctorId.equals(choice.publicDoctorId()))
                .findFirst()
                .orElse(null);
    }

    private ClinicChoice clinicChoiceById(List<ClinicChoice> clinics, String clinicSlug) {
        if (!StringUtils.hasText(clinicSlug) || clinics == null || clinics.isEmpty()) {
            return null;
        }
        return clinics.stream()
                .filter(choice -> clinicSlug.equals(choice.clinicSlug()))
                .findFirst()
                .orElse(null);
    }

    private SlotChoice slotChoiceByCanonical(List<SlotChoice> slots, String canonicalValue) {
        if (!StringUtils.hasText(canonicalValue) || slots == null || slots.isEmpty()) {
            return null;
        }
        return slots.stream()
                .filter(choice -> canonicalValue.equalsIgnoreCase(choice.slotTime() == null ? null : choice.slotTime().format(TIME_FORMATTER)))
                .findFirst()
                .orElse(null);
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
            state.selectedDoctorBookingMode = null;
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
        String semanticReference = semanticReference(state, message, "doctor");
        CanonicalResolution resolution = doctorResolver.resolve(semanticReference, doctorCandidates(state.doctorChoices), state.selectedDoctorId);
        if (resolution.resolved()) {
            DoctorChoice selected = doctorChoiceById(state.doctorChoices, resolution.candidateIds().isEmpty() ? null : resolution.candidateIds().getFirst());
            if (selected != null) {
                return selected;
            }
        }
        if (resolution.ambiguous()) {
            DoctorChoice ambiguous = doctorChoiceById(state.doctorChoices, resolution.candidateIds().isEmpty() ? null : resolution.candidateIds().getFirst());
            if (ambiguous != null) {
                return ambiguous;
            }
        }
        return resolveIndexedOrNamedChoice(
                state.doctorChoices,
                semanticReference,
                choice -> choice.doctorName() + " " + nullToBlank(choice.speciality())
        );
    }

    private ClinicChoice resolveClinicChoice(CareAiState state, String message) {
        String semanticReference = semanticReference(state, message, "clinic");
        CanonicalResolution resolution = clinicResolver.resolve(semanticReference, clinicCandidates(state.clinicChoices), state.selectedClinicSlug);
        if (resolution.resolved()) {
            ClinicChoice selected = clinicChoiceById(state.clinicChoices, resolution.candidateIds().isEmpty() ? null : resolution.candidateIds().getFirst());
            if (selected != null) {
                return selected;
            }
        }
        if (resolution.ambiguous()) {
            ClinicChoice ambiguous = clinicChoiceById(state.clinicChoices, resolution.candidateIds().isEmpty() ? null : resolution.candidateIds().getFirst());
            if (ambiguous != null) {
                return ambiguous;
            }
        }
        if (state.clinicChoices.isEmpty()) {
            return null;
        }
        Integer index = parseSelectionIndex(semanticReference);
        if (index != null && index >= 1 && index <= state.clinicChoices.size()) {
            return state.clinicChoices.get(index - 1);
        }
        String normalized = normalizeDoctorText(semanticReference);
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
        String semanticReference = semanticReference(state, message, "slot");
        CanonicalResolution resolution = selectionResolver.resolve(semanticReference, slotCandidates(state.slotChoices), null, canonicalResolverSupport::normalize);
        if (resolution.resolved()) {
            SlotChoice selected = slotChoiceByCanonical(state.slotChoices, resolution.canonicalValue());
            if (selected != null) {
                return selected;
            }
        }
        Integer index = parseSelectionIndex(semanticReference);
        if (index != null && index >= 1 && index <= state.slotChoices.size()) {
            return state.slotChoices.get(index - 1);
        }
        String normalized = canonicalResolverSupport.normalize(semanticReference);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return state.slotChoices.stream()
                .filter(choice -> choice.slotTime().format(TIME_FORMATTER).equalsIgnoreCase(normalized))
                .findFirst()
                .orElseGet(() -> state.slotChoices.stream()
                        .filter(choice -> canonicalResolverSupport.normalize(choice.slotTime().format(TIME_FORMATTER)).contains(normalized))
                        .findFirst()
                .orElse(null));
    }

    private String semanticReference(CareAiState state, String fallback, String target) {
        if (state != null && state.lastCanonicalTurn != null) {
            PatientPortalCareAiCanonicalEntities entities = state.lastCanonicalTurn.entities();
            if ("doctor".equals(target) && StringUtils.hasText(entities.doctor())) {
                return entities.doctor();
            }
            if ("clinic".equals(target) && StringUtils.hasText(entities.clinic())) {
                return entities.clinic();
            }
            if ("slot".equals(target) && StringUtils.hasText(entities.exactTime())) {
                return entities.exactTime();
            }
            PatientPortalCareAiSelectionReference selection = state.lastCanonicalTurn.selection();
            if (selection.present() && (selection.target() == null || target.equalsIgnoreCase(selection.target()))) {
                if (selection.ordinal() != null) {
                    return switch (selection.ordinal()) {
                        case 1 -> "first";
                        case 2 -> "second";
                        case 3 -> "third";
                        case 4 -> "fourth";
                        default -> null;
                    };
                }
            }
            return null;
        }
        return fallback;
    }

    private String canonicalDoctorSearchText(PatientPortalCareAiCanonicalTurn turn, String fallback) {
        if (turn != null && StringUtils.hasText(turn.entities().doctor())) {
            return turn.entities().doctor();
        }
        return fallback;
    }

    private String canonicalClinicSearchText(PatientPortalCareAiCanonicalTurn turn, String fallback) {
        if (turn != null) {
            if (StringUtils.hasText(turn.entities().clinic())) {
                return turn.entities().clinic();
            }
            if (StringUtils.hasText(turn.entities().location())) {
                return turn.entities().location();
            }
        }
        return fallback;
    }

    private PatientPortalAppointmentResolverService.AppointmentResolution resolveAppointmentSelection(
            CareAiState state, PatientPortalCareAiCanonicalTurn turn) {
        if (state == null || state.appointmentOptions.isEmpty()) {
            return PatientPortalAppointmentResolverService.AppointmentResolution.none();
        }
        List<PatientPortalCareAiAppointmentOption> appointments = state.appointmentOptions.stream()
                .map(choice -> new PatientPortalCareAiAppointmentOption(
                        choice.appointmentId(),
                        choice.doctorUserId(),
                        choice.doctorName(),
                        choice.tenantId(),
                        choice.clinicName(),
                        choice.appointmentDate(),
                        choice.appointmentTime(),
                        choice.status(),
                        choice.reason()
                ))
                .toList();
        return appointmentResolverService.resolveCanonical(appointments, turn, state.language);
    }

    private boolean hasCanonicalAppointmentSignal(PatientPortalCareAiCanonicalTurn turn) {
        if (turn == null) {
            return false;
        }
        PatientPortalCareAiCanonicalEntities entities = turn.entities();
        return turn.selection().present()
                || StringUtils.hasText(entities.doctor())
                || StringUtils.hasText(entities.date())
                || StringUtils.hasText(entities.timeWindow())
                || StringUtils.hasText(entities.exactTime());
    }

    private AppointmentChoice findAppointmentChoice(CareAiState state, PatientPortalCareAiAppointmentOption appointment) {
        if (state == null || appointment == null || state.appointmentOptions.isEmpty()) {
            return null;
        }
        return state.appointmentOptions.stream()
                .filter(choice -> Objects.equals(choice.appointmentId(), appointment.appointmentId()))
                .findFirst()
                .orElse(null);
    }

    private String appointmentSelectionHelpPrompt(String language, String actionWord) {
        if (isHindi(language)) {
            return switch (actionWord) {
                case "cancel" -> "मुझे आपकी कुछ अपॉइंटमेंट दिखाई दे रही हैं। कृपया बताइए कि आप किस अपॉइंटमेंट को रद्द करना चाहते हैं। आप डॉक्टर का नाम, तारीख, समय या सूची का क्रमांक भी बता सकते हैं।";
                case "reschedule" -> "मुझे आपकी कुछ अपॉइंटमेंट दिखाई दे रही हैं। कृपया बताइए कि आप किस अपॉइंटमेंट को रीशेड्यूल करना चाहते हैं। आप डॉक्टर का नाम, तारीख, समय या सूची का क्रमांक भी बता सकते हैं।";
                default -> "मुझे आपकी कुछ अपॉइंटमेंट दिखाई दे रही हैं। कृपया डॉक्टर का नाम, तारीख, समय या सूची का क्रमांक बताइए।";
            };
        }
        return switch (actionWord) {
            case "cancel" -> "I found one or more appointments. Please tell me which appointment you want to cancel by doctor name, date, time, or list number.";
            case "reschedule" -> "I found one or more appointments. Please tell me which appointment you want to reschedule by doctor name, date, time, or list number.";
            default -> "I found one or more appointments. Please tell me the doctor name, date, time, or list number.";
        };
    }

    private String appointmentMultipleMatchPrompt(CareAiState state, List<PatientPortalCareAiAppointmentOption> matches, String actionWord) {
        if (matches == null || matches.isEmpty()) {
            return appointmentSelectionHelpPrompt(state.language, actionWord);
        }
        List<String> labels = matches.stream()
                .map(this::appointmentLabel)
                .toList();
        return numberedChoicePrompt(
                state.language,
                appointmentSelectionHelpPrompt(state.language, actionWord),
                appointmentSelectionHelpPrompt(state.language, actionWord),
                labels
        );
    }

    private String appointmentNoMatchPrompt(String language) {
        return isHindi(language)
                ? "मुझे उस विवरण से कोई अपॉइंटमेंट नहीं मिली। कृपया डॉक्टर का नाम, तारीख, समय या सूची का क्रमांक बताइए।"
                : "I could not find a matching appointment. Please share the doctor name, date, time, or list number.";
    }

    private String appointmentLabel(PatientPortalCareAiAppointmentOption appointment) {
        return safe(appointment.doctorName()) + " · "
                + safe(appointment.appointmentDate() == null ? null : DATE_FORMATTER.format(appointment.appointmentDate())) + " · "
                + safe(appointment.appointmentTime() == null ? null : appointment.appointmentTime().format(TIME_FORMATTER));
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
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.matches("\\d{1,2}")) {
            try {
                return Integer.parseInt(normalized);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
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
        Matcher matcher = Pattern.compile("(?i)^(?:slot|option|number|book)\\s*(\\d{1,2})$").matcher(normalized);
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

    private boolean shouldUsePlanner(CareAiState state, String message) {
        if (planner == null || !StringUtils.hasText(message)) {
            return false;
        }
        if (isSelectionOnlyMessage(message) && !state.confirmationPending) {
            return false;
        }
        return isSemanticallyComplexTurn(message)
                || state.currentIntent == null
                || state.confirmationPending
                || state.unresolvedTurns > 0
                || StringUtils.hasText(state.preferredDate) && !StringUtils.hasText(state.preferredTimeWindow)
                || StringUtils.hasText(state.selectedDoctorId) && !StringUtils.hasText(state.preferredDate)
                || containsMonthNameWithoutYear(message)
                || (!StringUtils.hasText(state.preferredDate) && !looksLikeDateAbsent(message))
                || (!StringUtils.hasText(state.preferredTimeWindow) && mayContainTimePreference(message))
                || (!StringUtils.hasText(state.requestedDoctorName) && !StringUtils.hasText(state.selectedDoctorId));
    }

    private boolean isSemanticallyComplexTurn(String message) {
        String normalized = message == null ? "" : message.trim();
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        int wordCount = normalized.split("\\s+").length;
        String lower = normalized.toLowerCase(Locale.ROOT);
        boolean question = normalized.contains("?") || lower.startsWith("can ") || lower.startsWith("could ")
                || lower.startsWith("please ") || lower.startsWith("what ") || lower.startsWith("which ");
        boolean correctionOrAlternative = lower.contains("actually") || lower.contains("instead")
                || lower.contains("another") || lower.contains("different") || lower.contains("someone else")
                || lower.contains("switch") || normalized.contains("किसी और") || normalized.contains("दूसरा")
                || normalized.contains("दूसरे") || normalized.contains("दूसरी") || normalized.contains("चाहता हूँ");
        boolean multiEntity = lower.contains("doctor") || lower.contains("dr ") || lower.contains("appointment")
                || lower.contains("available") || lower.contains("after lunch") || normalized.contains("डॉक्टर")
                || normalized.contains("अपॉइंटमेंट") || normalized.contains("अवेलेबल");
        return wordCount >= 4 || question || correctionOrAlternative || (multiEntity && wordCount >= 3);
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
                || message.contains("सुबह")
                || message.contains("दोपहर")
                || message.contains("शाम")
                || message.contains("रात")
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

    private boolean isTimeOnlyAdjustment(String message, CareAiState state) {
        return state != null
                && StringUtils.hasText(state.preferredDate)
                && StringUtils.hasText(message)
                && looksLikeDateAbsent(message)
                && mayContainTimePreference(message);
    }

    private boolean containsMonthNameWithoutYear(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return MONTH_NAME_MAP.keySet().stream().anyMatch(lower::contains) && !DMY_DATE_PATTERN.matcher(message).find() && !MDY_DATE_PATTERN.matcher(message).find();
    }

    private SessionKey currentSessionKey() {
        return new SessionKey(RequestContextHolder.requireTenantId(), RequestContextHolder.require().appUserId());
    }

    private VoiceSessionKey currentVoiceSessionKey() {
        if (RequestContextHolder.get() == null
                || RequestContextHolder.require().appUserId() == null
                || !StringUtils.hasText(RequestContextHolder.require().correlationId())) {
            return null;
        }
        return new VoiceSessionKey(
                RequestContextHolder.requireTenantId(),
                RequestContextHolder.require().appUserId(),
                RequestContextHolder.require().correlationId()
        );
    }

    private String currentChatExternalSessionId() {
        return String.valueOf(RequestContextHolder.require().appUserId());
    }

    private String redactedPatientDiagnosticId() {
        return "[redacted]";
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
        PatientPortalCareAiExtractedEntities extractedEntities = extractEntities(transcript, language);
        if (StringUtils.hasText(extractedEntities.doctor())) {
            careAiTrace("findRequestedDoctorName", "exit", currentState(),
                    "source=entity-registry extractedDoctorName=" + extractedEntities.doctor());
            return extractedEntities.doctor();
        }
        careAiTrace("findRequestedDoctorName", "exit", currentState(),
                "source=none extractedDoctorName=null");
        return null;
    }

    private String findRequestedClinicName(String transcript, String language) {
        PatientPortalCareAiExtractedEntities extractedEntities = extractEntities(transcript, language);
        return StringUtils.hasText(extractedEntities.clinic()) ? extractedEntities.clinic() : null;
    }

    /** Compatibility-only lookup used by legacy doctor-correction prompts. */
    private String findDoctorNameFromFreeText(String transcript) {
        String correctedName = findCorrectedDoctorNameCandidate(transcript);
        if (StringUtils.hasText(correctedName)) {
            return correctedName;
        }
        String normalized = normalizeDoctorText(transcript);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        List<String> names = publicBookableDoctorChoices(currentState()).stream()
                .map(DoctorChoice::doctorName)
                .filter(StringUtils::hasText)
                .filter(name -> doctorQueryVariants(normalized).stream().anyMatch(candidate -> {
                    String normalizedName = normalizeDoctorText(name);
                    return normalizedName.contains(candidate) || candidate.contains(normalizedName);
                }))
                .toList();
        return names.size() == 1 ? names.getFirst() : null;
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

    /** Compatibility-only extraction for legacy correction fixtures; state promotion remains canonical. */
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
        for (String marker : List.of("i mean ", "no ", "doctor name is ")) {
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
        SpecialtyResolver.SpecialtyResolution resolution = resolveSpecialty(transcript, currentState());
        return resolution.resolved() ? resolution.canonicalSpecialty() : null;
    }

    private SpecialtyResolver.SpecialtyResolution resolveSpecialty(String transcript, CareAiState state) {
        return specialtyResolver.resolve(transcript, supportedSpecialties(state));
    }

    private List<String> supportedSpecialties(CareAiState state) {
        return publicBookableDoctorChoices(state).stream()
                .map(DoctorChoice::speciality)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String specialityClarificationPrompt(CareAiState state, List<String> specialties) {
        List<String> options = specialties == null ? List.of() : specialties.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .limit(5)
                .toList();
        if (options.isEmpty()) {
            return isHindi(state.language)
                    ? "मैं उस स्पेशियलिटी से मेल नहीं कर पाया। कृपया डॉक्टर का नाम या स्पेशियलिटी फिर से बताइए।"
                    : "I couldn't match that specialty. Please tell me the doctor name or specialty again.";
        }
        return numberedChoicePrompt(
                state.language,
                "I couldn't match that specialty. Available options include:",
                "मैं उस स्पेशियलिटी से मेल नहीं कर पाया। उपलब्ध विकल्प हैं:",
                options
        );
    }

    private String handleDoctorDiscovery(CareAiState state, String message,
                                         PatientPortalCareAiCanonicalTurn turn) {
        String specialityCandidate = turn == null ? null : turn.entities().speciality();
        SpecialtyResolver.SpecialtyResolution resolution = resolveSpecialty(specialityCandidate, state);
        if (resolution.status() == SpecialtyResolver.SpecialtyResolutionStatus.AMBIGUOUS) {
            return specialityClarificationPrompt(state, resolution.candidates());
        }
        if (resolution.resolved() && !resolution.canonicalSpecialty().equalsIgnoreCase(state.requestedSpeciality)) {
            state.requestedSpeciality = resolution.canonicalSpecialty();
        }
        if (StringUtils.hasText(specialityCandidate) && !resolution.resolved()) {
            return specialityClarificationPrompt(state, supportedSpecialties(state));
        }
        boolean availabilityFirst = !StringUtils.hasText(turn.entities().doctor())
                && StringUtils.hasText(turn.entities().date())
                && (StringUtils.hasText(turn.entities().timeWindow()) || StringUtils.hasText(turn.entities().exactTime()));
        List<DoctorChoice> matches = availabilityFirst
                ? searchPublicBookableDoctors(state, null, state.requestedSpeciality)
                : resolveDoctorMatches(state, canonicalDoctorSearchText(turn, message));
        if (matches.isEmpty()) {
            return askDoctorPrompt(state.language, false);
        }
        if (availabilityFirst) {
            return handleAvailabilityFirstDiscovery(state, matches, turn.entities());
        }
        state.doctorChoices = matches;
        state.doctorOptions = matches.stream().map(DoctorChoice::label).toList();
        if (matches.size() == 1) {
            return matches.getFirst().label();
        }
        return doctorChoicePrompt(state);
    }

    private String handleAvailabilityFirstDiscovery(CareAiState state,
                                                    List<DoctorChoice> matches,
                                                    PatientPortalCareAiCanonicalEntities entities) {
        LocalDate date = parseIsoDate(entities.date());
        String timePreference = StringUtils.hasText(entities.timeWindow())
                ? entities.timeWindow() : entities.exactTime();
        if (date == null || !StringUtils.hasText(timePreference)) {
            state.doctorChoices = matches;
            state.doctorOptions = matches.stream().map(DoctorChoice::label).toList();
            return doctorChoicePrompt(state);
        }
        List<DoctorAvailabilityMatch> available = new ArrayList<>();
        for (DoctorChoice doctor : matches.stream().limit(8).toList()) {
            if (!doctor.canBookOnline() || !StringUtils.hasText(doctor.publicDoctorId())) {
                continue;
            }
            PatientPortalCareAiExecutionIdentity execution = beginSkill(state, "availability.check", true);
            PatientPortalCareAiSkillResult<List<PatientPortalDoctorSlotResponse>> result;
            try {
                result = toolRegistry.availabilityCheck().execute(new PatientPortalCareAiAvailabilityCheckSkillInput(
                        doctor.bookingReference(),
                        doctor.publicDoctorId(),
                        doctor.clinicSlug(),
                        doctor.tenantId(),
                        doctor.clinicId(),
                        date
                ));
            } catch (RuntimeException ex) {
                result = unavailableSkillResult("Availability is temporarily unavailable.", ex);
            }
            PatientPortalCareAiSkillOutcome outcome = finishSkill(state, execution, result.outcome());
            if (outcome != PatientPortalCareAiSkillOutcome.SUCCESS && outcome != PatientPortalCareAiSkillOutcome.MULTIPLE_MATCHES) {
                continue;
            }
            List<PatientPortalDoctorSlotResponse> slots = result.value() == null ? List.of() : result.value().stream()
                    .filter(PatientPortalDoctorSlotResponse::selectable)
                    .filter(slot -> slot.appointmentDate() == null || date.equals(slot.appointmentDate()))
                    .filter(slot -> matchesTimePreference(slot, timePreference))
                    .limit(3)
                    .toList();
            if (!slots.isEmpty()) {
                available.add(new DoctorAvailabilityMatch(doctor, slots));
            }
        }
        state.doctorChoices = available.stream().map(DoctorAvailabilityMatch::doctor).toList();
        state.doctorOptions = state.doctorChoices.stream().map(DoctorChoice::label).toList();
        setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.COMPLETED, "availability-first-discovery-complete");
        if (available.isEmpty()) {
            return isHindi(state.language)
                    ? "मुझे उस तारीख और समय पर कोई उपलब्ध ऑनलाइन डॉक्टर नहीं मिला।"
                    : "I couldn't find an online-bookable doctor available at that date and time.";
        }
        String prefix = isHindi(state.language)
                ? "इस तारीख और समय पर उपलब्ध डॉक्टर:"
                : "Doctors available at that date and time:";
        return prefix + " " + numberedAvailabilityMatches(available);
    }

    private boolean matchesTimePreference(PatientPortalDoctorSlotResponse slot, String preference) {
        if (slot == null || slot.slotTime() == null || !StringUtils.hasText(preference)) {
            return false;
        }
        if (isExactTime(preference)) {
            return preference.equals(slot.slotTime().format(TIME_FORMATTER));
        }
        return filterSlots(List.of(slot), preference).stream().anyMatch(slot::equals);
    }

    private String numberedAvailabilityMatches(List<DoctorAvailabilityMatch> matches) {
        StringBuilder response = new StringBuilder();
        for (int index = 0; index < matches.size(); index++) {
            DoctorAvailabilityMatch match = matches.get(index);
            if (index > 0) {
                response.append(" ");
            }
            response.append(index + 1).append(". ").append(match.doctor().label()).append(" — ")
                    .append(match.slots().stream().map(slot -> slot.appointmentDate() + " " + slot.slotTime().format(TIME_FORMATTER)).toList());
        }
        return response.toString();
    }

    private String handleClinicDiscovery(CareAiState state, String message,
                                         PatientPortalCareAiCanonicalTurn turn) {
        List<ClinicChoice> matches = resolveClinicMatches(state, canonicalClinicSearchText(turn, message));
        if (matches.isEmpty()) {
            return isHindi(state.language)
                    ? "मुझे कोई मेल खाता क्लिनिक नहीं मिला। कृपया क्लिनिक का नाम या स्थान बताइए।"
                    : "I couldn't find a matching clinic. Please tell me the clinic name or location.";
        }
        state.clinicChoices = matches;
        state.clinicOptions = matches.stream().map(ClinicChoice::label).toList();
        if (matches.size() == 1) {
            ClinicChoice clinic = matches.getFirst();
            return clinic.label();
        }
        return clinicChoicePrompt(state);
    }

    private DateResolution findPreferredDate(String transcript, String language) {
        PatientPortalCareAiExtractedEntities extractedEntities = extractEntities(transcript, language);
        if (StringUtils.hasText(extractedEntities.dateIssue())) {
            return DateResolution.invalid(extractedEntities.dateIssue());
        }
        if (extractedEntities.requiresDateClarification()) {
            return DateResolution.invalid("ambiguous");
        }
        if (StringUtils.hasText(extractedEntities.date())) {
            return resolveAbsoluteDate(parseIsoDate(extractedEntities.date()), isExplicitDateExpression(transcript));
        }
        return DateResolution.none();
    }

    private boolean isExplicitDateExpression(String transcript) {
        if (!StringUtils.hasText(transcript)) {
            return false;
        }
        return ISO_DATE_PATTERN.matcher(transcript).find()
                || DMY_DATE_PATTERN.matcher(transcript).find()
                || MDY_DATE_PATTERN.matcher(transcript).find()
                || SLASH_DATE_PATTERN.matcher(transcript).find();
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
        PatientPortalCareAiExtractedEntities extractedEntities = extractEntities(transcript, language);
        if (StringUtils.hasText(extractedEntities.timeWindow())) {
            return extractedEntities.timeWindow();
        }
        if (StringUtils.hasText(extractedEntities.time())) {
            return extractedEntities.time();
        }
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

    private boolean isConversationAbandonment(String transcript, String language) {
        String lower = transcript == null ? "" : transcript.toLowerCase(Locale.ROOT).trim();
        return isPostCompletionCourtesy(transcript, language)
                || ABANDON_CONVERSATION_KEYWORDS.stream().anyMatch(lower::contains)
                || (isHindi(language) && (transcript.contains("नहीं धन्यवाद")
                || transcript.contains("नहीं चाहिए")
                || transcript.contains("बस धन्यवाद")
                || transcript.contains("रोकिए")));
    }

    private void abandonCurrentWorkflow(CareAiState state) {
        if (StringUtils.hasText(state.executionConversationId)) {
            executionTracker.invalidateConversation(state.executionConversationId);
        }
        clearCurrentConversation(state);
        state.workflowSubState = PatientPortalCareAiWorkflowSubState.CANCELLED;
        queueWorkflowEvent(state, "WORKFLOW_ABANDONED", workflowContextJson(state));
    }

    private String farewellPrompt(String language) {
        return isHindi(language) ? "आपका स्वागत है। अपना ध्यान रखिए।" : "You're welcome. Take care.";
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
        return isHindi(language) && (transcript.contains("अपॉइंटमेंट")
                || transcript.contains("बुक")
                || transcript.contains("मुलाकात")
                || transcript.contains("डॉक्टर से मिलना")
                || transcript.contains("डॉक्टर दिखाना"));
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
        PatientPortalCareAiExtractedEntities extractedEntities = extractEntities(transcript, language);
        if (extractedEntities.reset()) {
            return true;
        }
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
                || lower.contains("available doctor")
                || transcript.contains("डॉक्टर दिखाओ")
                || transcript.contains("डॉक्टर ढूंढो")
                || transcript.contains("डॉक्टर खोजो")
                || (specialtyResolver.extractCandidate(transcript).isPresent()
                && (lower.contains("find") || lower.contains("show") || lower.contains("need") || lower.contains("want") || lower.contains("doctor") || lower.contains("see")));
    }

    private boolean detectClinicSearchIntent(String transcript) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        return lower.contains("find clinic")
                || lower.contains("show clinics")
                || lower.contains("which clinic")
                || lower.contains("available clinic")
                || transcript.contains("क्लिनिक दिखाओ")
                || transcript.contains("क्लिनिक ढूंढो")
                || transcript.contains("क्लिनिक खोजो");
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

    private boolean isNextAppointmentQuery(String transcript) {
        String lower = transcript.toLowerCase(Locale.ROOT);
        return lower.contains("next appointment")
                || lower.contains("when is my next appointment")
                || lower.contains("what is my next appointment")
                || lower.contains("my next appointment");
    }

    private boolean isPositiveConfirmation(String message) {
        if (isNegativeConfirmation(message) || confirmationDecision(message) != ConfirmationDecision.POSITIVE) {
            return false;
        }
        return true;
    }

    private ConfirmationDecision confirmationDecision(String message) {
        if (!StringUtils.hasText(message)) {
            return ConfirmationDecision.AMBIGUOUS;
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (matchesConfirmationPhrase(normalized, NEGATIVE_CONFIRMATIONS)
                || NEGATIVE_CONFIRMATIONS_HI.stream().anyMatch(message::contains)) {
            return ConfirmationDecision.NEGATIVE;
        }
        if (matchesConfirmationPhrase(normalized, POSITIVE_CONFIRMATIONS)
                || POSITIVE_CONFIRMATIONS_HI.stream().anyMatch(message::contains)) {
            return ConfirmationDecision.POSITIVE;
        }
        return ConfirmationDecision.AMBIGUOUS;
    }

    private boolean matchesConfirmationPhrase(String normalized, List<String> phrases) {
        return phrases.stream().map(phrase -> phrase.toLowerCase(Locale.ROOT)
                        .replaceAll("[^\\p{L}\\p{N}]+", " ")
                        .replaceAll("\\s+", " ")
                        .trim())
                .anyMatch(normalized::equals);
    }

    private boolean isNegativeConfirmation(String message) {
        return confirmationDecisionWithoutRecursion(message) == ConfirmationDecision.NEGATIVE;
    }

    private ConfirmationDecision confirmationDecisionWithoutRecursion(String message) {
        PatientPortalCareAiExtractedEntities extractedEntities = extractEntities(message, "en");
        String normalized = message == null ? "" : message.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (matchesConfirmationPhrase(normalized, NEGATIVE_CONFIRMATIONS)
                || NEGATIVE_CONFIRMATIONS_HI.stream().anyMatch(message::contains)
                || extractedEntities.cancellation()) {
            return ConfirmationDecision.NEGATIVE;
        }
        if (matchesConfirmationPhrase(normalized, POSITIVE_CONFIRMATIONS)
                || POSITIVE_CONFIRMATIONS_HI.stream().anyMatch(message::contains)) {
            return ConfirmationDecision.POSITIVE;
        }
        return ConfirmationDecision.AMBIGUOUS;
    }

    private enum ConfirmationDecision { POSITIVE, NEGATIVE, AMBIGUOUS }

    private String negativeConfirmationResponse(String language) {
        return isHindi(language)
                ? "ठीक है, मैंने यह अनुरोध पूरा नहीं किया।"
                : "Okay, I did not make that change.";
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
        return StringUtils.hasText(language) && language.toLowerCase(Locale.ROOT).startsWith("hi");
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
        if (state.currentIntent == PatientPortalCareAiIntent.BOOK_APPOINTMENT) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.FINDING_PROVIDERS, "doctor-lookup");
        } else {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.RESOLVING, "doctor-lookup");
        }
        PatientPortalCareAiExecutionIdentity execution = beginSkill(state, "doctor.find", true);
        PatientPortalCareAiSkillResult<List<PublicDoctorSummaryResponse>> skillResult;
        try {
            skillResult = toolRegistry.doctorFind().execute(new PatientPortalCareAiDoctorFindSkillInput(
                    doctorQuery,
                    specialityQuery,
                    state.selectedClinicSlug,
                    state.requestedLocationName,
                    patientPortalService.currentPatientId() == null ? null : String.valueOf(patientPortalService.currentPatientId()),
                    safeTenantId()
            ));
        } catch (RuntimeException ex) {
            skillResult = unavailableSkillResult("Doctor search is temporarily unavailable.", ex);
        }
        PatientPortalCareAiSkillOutcome lifecycleOutcome = finishSkill(state, execution, skillResult.outcome());
        if (lifecycleOutcome == PatientPortalCareAiSkillOutcome.STALE) {
            return List.of();
        }
        List<DoctorChoice> choices = (skillResult.value() == null ? List.<PublicDoctorSummaryResponse>of() : skillResult.value()).stream()
                .map(this::toDoctorChoice)
                .toList();
        if (state.currentIntent == PatientPortalCareAiIntent.BOOK_APPOINTMENT) {
            if (skillResult.outcome() == PatientPortalCareAiSkillOutcome.MULTIPLE_MATCHES || choices.size() > 1) {
                setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION, "doctor-lookup-multiple");
            } else if (skillResult.outcome() == PatientPortalCareAiSkillOutcome.SUCCESS && choices.size() == 1) {
                setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_DATE, "doctor-lookup-single");
            } else if (skillResult.outcome() == PatientPortalCareAiSkillOutcome.NO_MATCH
                    || choices.isEmpty()) {
                setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY, "doctor-lookup-empty");
            } else if (skillResult.outcome() == PatientPortalCareAiSkillOutcome.FAILED
                    || skillResult.outcome() == PatientPortalCareAiSkillOutcome.NOT_AUTHORIZED
                    || skillResult.outcome() == PatientPortalCareAiSkillOutcome.TIMEOUT
                    || skillResult.outcome() == PatientPortalCareAiSkillOutcome.TEMPORARILY_UNAVAILABLE) {
                setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.FAILED, "doctor-lookup-failed");
            }
        } else if (state.currentIntent == PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT) {
            if (skillResult.outcome() == PatientPortalCareAiSkillOutcome.FAILED
                    || skillResult.outcome() == PatientPortalCareAiSkillOutcome.NOT_AUTHORIZED) {
                setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.FAILED, "doctor-lookup-failed");
            } else if (skillResult.outcome() == PatientPortalCareAiSkillOutcome.MULTIPLE_MATCHES || choices.size() > 1) {
                setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION, "doctor-lookup-multiple");
            } else if (skillResult.outcome() == PatientPortalCareAiSkillOutcome.SUCCESS && choices.size() == 1) {
                setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_NEW_DATE, "doctor-lookup-single");
            }
        } else if (state.currentIntent == PatientPortalCareAiIntent.FIND_DOCTOR) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.COMPLETED, "doctor-discovery-complete");
        }
        careAiTrace("lookupDoctors", "exit", state,
                "skill=doctor.find outcome=" + skillResult.outcome()
                        + " resultCount=" + choices.size()
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
            if (state.currentIntent == PatientPortalCareAiIntent.FIND_CLINIC) {
                setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.RESOLVING, "clinic-lookup");
            }
            PatientPortalCareAiSkillResult<List<PublicClinicSummaryResponse>> skillResult = toolRegistry.clinicFind().execute(new PatientPortalCareAiClinicFindSkillInput(
                    clinicQuery,
                    state.requestedSpeciality,
                    state.requestedLocationName,
                    patientPortalService.currentPatientId() == null ? null : String.valueOf(patientPortalService.currentPatientId()),
                    safeTenantId()
            ));
            List<ClinicChoice> choices = (skillResult.value() == null ? List.<PublicClinicSummaryResponse>of() : skillResult.value()).stream()
                    .map(this::toClinicChoice)
                    .toList();
            if (state.currentIntent == PatientPortalCareAiIntent.BOOK_APPOINTMENT) {
                if (skillResult.outcome() == PatientPortalCareAiSkillOutcome.FAILED
                        || skillResult.outcome() == PatientPortalCareAiSkillOutcome.NOT_AUTHORIZED) {
                    setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.FAILED, "clinic-lookup-failed");
                } else if (skillResult.outcome() == PatientPortalCareAiSkillOutcome.NO_MATCH || choices.isEmpty()) {
                    setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY, "clinic-lookup-empty");
                } else {
                    setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION, "clinic-lookup-results");
                }
            } else if (state.currentIntent == PatientPortalCareAiIntent.FIND_CLINIC) {
                setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.COMPLETED, "clinic-discovery-complete");
            }
            careAiTrace("lookupClinics", "exit", state,
                    "skill=clinic.find outcome=" + skillResult.outcome()
                            + " resultCount=" + choices.size()
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

    private boolean hasAppointmentSelectionSignal(String transcript) {
        PatientPortalCareAiExtractedEntities entities = extractEntities(transcript, "en");
        return entities.has(PatientPortalCareAiEntityType.DOCTOR)
                || entities.has(PatientPortalCareAiEntityType.DATE)
                || entities.has(PatientPortalCareAiEntityType.TIME)
                || entities.has(PatientPortalCareAiEntityType.TIME_SLOT)
                || entities.has(PatientPortalCareAiEntityType.TIME_WINDOW)
                || entities.has(PatientPortalCareAiEntityType.APPOINTMENT)
                || transcript.toLowerCase(Locale.ROOT).contains("appointment")
                || transcript.contains("अपॉइंटमेंट");
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
            return askDatePrompt(state);
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
        String english = switch (actionWord) {
            case "cancel" -> "Please choose which appointment you want to cancel:";
            case "reschedule" -> "Please choose which appointment you want to reschedule:";
            default -> "Please choose which appointment you want to view:";
        };
        String hindi = appointmentSelectionHelpPrompt(state.language, actionWord);
        return numberedChoicePrompt(state.language, english, hindi, state.appointmentOptions.stream().map(AppointmentChoice::label).toList());
    }

    private String slotChoicePrompt(CareAiState state) {
        if (state.currentIntent == PatientPortalCareAiIntent.BOOK_APPOINTMENT
                || state.currentIntent == PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION, "slot-choice-prompt");
        }
        if (state.slotOptions.isEmpty()) {
            return unavailablePreferredWindowPrompt(state, state.preferredTimeWindow, List.of());
        }
        String englishLead = StringUtils.hasText(state.slotPromptLead)
                ? state.slotPromptLead
                : "Please choose a slot by number or time:";
        String hindiLead = StringUtils.hasText(state.slotPromptLead)
                ? state.slotPromptLead
                : "कृपया इन उपलब्ध स्लॉट में से एक चुनिए:";
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

    private boolean isSlotContextControlTurn(PatientPortalCareAiCanonicalTurn turn) {
        if (turn == null) {
            return true;
        }
        PatientPortalCareAiCanonicalEntities entities = turn.entities();
        return !StringUtils.hasText(entities.doctor())
                && !StringUtils.hasText(entities.clinic())
                && !StringUtils.hasText(entities.speciality())
                && !StringUtils.hasText(entities.service())
                && !StringUtils.hasText(entities.location())
                && !StringUtils.hasText(entities.date())
                && !StringUtils.hasText(entities.timeWindow())
                && !StringUtils.hasText(entities.exactTime())
                && !turn.correction().present()
                && !turn.alternative().present()
                && turn.dialogAct() != PatientPortalCareAiDialogAct.CHANGE_INFORMATION
                && turn.dialogAct() != PatientPortalCareAiDialogAct.REQUEST_ALTERNATIVE;
    }

    private boolean isCurrentSlotContextQuestion(CareAiState state, PatientPortalCareAiCanonicalTurn turn) {
        return state != null
                && !state.slotOptions.isEmpty()
                && turn != null
                && turn.dialogAct() == PatientPortalCareAiDialogAct.ASK_QUESTION
                && !turn.selection().present()
                && isSlotContextControlTurn(turn);
    }

    private String currentSlotContextPrompt(CareAiState state) {
        String date = StringUtils.hasText(state.preferredDate) ? state.preferredDate : "the selected date";
        return isHindi(state.language)
                ? "ये स्लॉट " + date + " के अपॉइंटमेंट के लिए हैं।"
                : "These slots are for the appointment on " + date + ".";
    }

    private boolean isAnotherDateRequest(String message) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return ANOTHER_DATE_KEYWORDS.stream().anyMatch(lower::contains)
                || lower.contains("next available")
                || lower.contains("another day");
    }

    private boolean isAnotherTimeRequest(String message) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return ANOTHER_TIME_KEYWORDS.stream().anyMatch(lower::contains);
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

    private void refreshSlotChoicesAfterVoiceCorrection(CareAiState state) {
        if (state == null
                || !isOnlineBookable(state)
                || !StringUtils.hasText(state.preferredDate)) {
            return;
        }
        List<PatientPortalDoctorSlotResponse> voiceFallbackSlots = List.of();
        LocalDate date = LocalDate.parse(state.preferredDate);
        if (StringUtils.hasText(state.selectedDoctorId)) {
            try {
                voiceFallbackSlots = patientPortalService.doctorSlots(state.selectedBookingReference, state.selectedDoctorId, state.selectedClinicSlug, state.selectedTenantId, state.selectedClinicId, date);
            } catch (RuntimeException ex) {
                careAiTrace("refreshSlotChoicesAfterVoiceCorrection", "voice-fallback-error", state,
                        "doctorId=" + state.selectedDoctorId
                                + " date=" + date
                                + " error=" + ex.getMessage());
            }
        }
        if (!StringUtils.hasText(state.selectedDoctorId) && StringUtils.hasText(state.requestedDoctorName)) {
            List<DoctorChoice> doctorMatches = resolveDoctorMatches(state, state.requestedDoctorName);
            if (doctorMatches.size() == 1) {
                selectDoctor(state, doctorMatches.getFirst());
            }
        }
        if (!StringUtils.hasText(state.selectedDoctorId)) {
            return;
        }
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
        if (selectableSlots.isEmpty() && !voiceFallbackSlots.isEmpty()) {
            selectableSlots = voiceFallbackSlots.stream()
                    .filter(PatientPortalDoctorSlotResponse::selectable)
                    .sorted(Comparator.comparing(PatientPortalDoctorSlotResponse::slotTime))
                    .toList();
        }
        if (selectableSlots.isEmpty()) {
            clearSlotSelection(state);
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
                    state.slotPromptLead = exactTimeUnavailablePrompt(state, state.preferredTimeWindow, nearest);
                }
            }
        } else if (StringUtils.hasText(state.preferredTimeWindow) && filtered.isEmpty()) {
            candidates = selectableSlots.stream().limit(3).toList();
            state.slotPromptLead = broadTimeUnavailablePrompt(state, state.preferredTimeWindow, candidates);
        }
        state.allSlotChoices = candidates.stream()
                .map(slot -> new SlotChoice(slot.appointmentDate(), slot.slotTime()))
                .toList();
        state.shownSlotOffset = 0;
        renderSlotPage(state, 0);
        state.selectedSlot = null;
        state.confirmationPending = false;
        state.pendingAction = null;
        state.awaitingFreshConfirmation = false;
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

    private String askDatePrompt(CareAiState state) {
        setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_DATE, "ask-date-prompt");
        return askDatePrompt(state.language);
    }

    private String askRescheduleDatePrompt(CareAiState state) {
        setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_NEW_DATE, "ask-reschedule-date-prompt");
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
        if (state.currentIntent == PatientPortalCareAiIntent.BOOK_APPOINTMENT) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_DATE, "ask-time-prompt");
        } else if (state.currentIntent == PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_NEW_DATE, "ask-time-prompt");
        }
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
        if (state.currentIntent == PatientPortalCareAiIntent.BOOK_APPOINTMENT) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_DATE, "time-slot-unavailable");
        } else if (state.currentIntent == PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_NEW_DATE, "time-slot-unavailable");
        }
        if (StringUtils.hasText(state.preferredTimeWindow)) {
            String criteria = availabilityCriteria(state, parseIsoDate(state.preferredDate));
            if (criteria.equals(state.lastNoSlotPromptCriteria)) {
                return unavailablePreferredWindowPrompt(state, state.preferredTimeWindow, List.of());
            }
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
                ? preferredTimeWindow + " पर सटीक स्लॉट उपलब्ध नहीं है। नज़दीकी विकल्प ये हैं:\nकृपया इन स्लॉट में से एक चुनिए:"
                : "No exact slot is available at " + preferredTimeWindow + ". Please choose from the nearest available options:\nPlease choose a slot by number or time:";
    }

    private String bookingConfirmationPrompt(CareAiState state) {
        setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING, "booking-confirmation-prompt");
        if (isHindi(state.language)) {
            return "डॉक्टर " + safe(state.selectedDoctorName)
                    + " के साथ\n" + safe(state.preferredDate)
                    + "\n" + safe(state.selectedSlot)
                    + " का स्लॉट उपलब्ध है।\nक्या मैं यह अपॉइंटमेंट बुक कर दूँ?";
        }
        return "Should I book " + safe(state.selectedDoctorName) + " on " + safe(state.preferredDate) + " at " + safe(state.selectedSlot) + "?";
    }

    private String rescheduleConfirmationPrompt(CareAiState state) {
        setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING, "reschedule-confirmation-prompt");
        if (isHindi(state.language)) {
            return "क्या मैं " + safe(state.selectedAppointmentLabel)
                    + " को\n" + safe(state.preferredDate)
                    + "\n" + safe(state.selectedSlot)
                    + " पर रीशेड्यूल कर दूँ?";
        }
        return "Should I reschedule " + safe(state.selectedAppointmentLabel) + " to " + safe(state.preferredDate) + " at " + safe(state.selectedSlot) + "?";
    }

    private String cancellationConfirmationPrompt(CareAiState state) {
        setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING, "cancellation-confirmation-prompt");
        if (isHindi(state.language)) {
            return "क्या आप इस अपॉइंटमेंट को रद्द करना चाहते हैं? " + safe(state.selectedAppointmentLabel);
        }
        return "Should I cancel this appointment? " + safe(state.selectedAppointmentLabel);
    }

    private String appointmentStatusPrompt(AppointmentChoice appointment, String language) {
        return isHindi(language)
                ? "डॉक्टर " + safe(appointment.doctorName())
                + " के साथ आपकी अपॉइंटमेंट "
                + safe(appointment.appointmentDate() == null ? null : DATE_FORMATTER.format(appointment.appointmentDate()))
                + " को "
                + safe(appointment.appointmentTime() == null ? null : appointment.appointmentTime().format(TIME_FORMATTER))
                + " पर है"
                + (StringUtils.hasText(appointment.clinicName()) ? ". क्लिनिक: " + safe(appointment.clinicName()) : ".")
                : "Your appointment is with "
                + safe(appointment.doctorName())
                + " on "
                + safe(appointment.appointmentDate() == null ? null : DATE_FORMATTER.format(appointment.appointmentDate()))
                + " at "
                + safe(appointment.appointmentTime() == null ? null : appointment.appointmentTime().format(TIME_FORMATTER))
                + (StringUtils.hasText(appointment.clinicName()) ? " at " + safe(appointment.clinicName()) : ".");
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
        context.put("workflowSubState", state.workflowSubState == null ? null : state.workflowSubState.name());
        context.put("requestedDoctorName", state.requestedDoctorName);
        context.put("requestedSpeciality", state.requestedSpeciality);
        context.put("requestedClinicName", state.requestedClinicName);
        context.put("requestedServiceName", state.requestedServiceName);
        context.put("requestedLocationName", state.requestedLocationName);
        context.put("doctorId", state.selectedDoctorId);
        context.put("doctorSlug", state.selectedDoctorSlug);
        context.put("bookingReference", state.selectedBookingReference);
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
        context.put("doctorChoices", doctorChoicesContext(state.doctorChoices));
        context.put("clinicChoices", clinicChoicesContext(state.clinicChoices));
        context.put("candidateContext", candidateContext(state));
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
        context.put("executionConversationId", state.executionConversationId);
        context.put("activeTurnId", state.activeTurnId);
        context.put("pendingSkillId", state.pendingSkillId);
        context.put("pendingSkillExecutionId", state.pendingSkillExecutionId);
        context.put("lastSkillOutcome", state.lastSkillOutcome == null ? null : state.lastSkillOutcome.name());
        context.put("askedState", askedStateMap(state));
        context.put("answeredState", answeredStateMap(state));
        return toJson(context);
    }

    private String workflowMetadataJson(CareAiState state) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("language", state.language);
        metadata.put("workflowSubState", state.workflowSubState == null ? null : state.workflowSubState.name());
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

    private List<Map<String, Object>> doctorChoicesContext(List<DoctorChoice> choices) {
        if (choices == null) {
            return List.of();
        }
        return choices.stream().limit(12).map(choice -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("publicDoctorId", choice.publicDoctorId());
            row.put("doctorSlug", choice.doctorSlug());
            row.put("bookingReference", choice.bookingReference());
            row.put("doctorName", choice.doctorName());
            row.put("speciality", choice.speciality());
            row.put("clinicId", choice.clinicId());
            row.put("tenantId", choice.tenantId());
            row.put("clinicSlug", choice.clinicSlug());
            row.put("clinicName", choice.clinicName());
            row.put("label", choice.label());
            row.put("bookingMode", choice.bookingMode());
            row.put("canBookOnline", choice.canBookOnline());
            return row;
        }).toList();
    }

    private Map<String, Object> candidateContext(CareAiState state) {
        Map<String, Object> context = new LinkedHashMap<>();
        List<String> candidateIds;
        String candidateType;
        if (!state.doctorChoices.isEmpty()) {
            candidateType = "DOCTOR";
            candidateIds = state.doctorChoices.stream().map(DoctorChoice::stableId).toList();
        } else if (!state.clinicChoices.isEmpty()) {
            candidateType = "CLINIC";
            candidateIds = state.clinicChoices.stream().map(ClinicChoice::stableId).toList();
        } else if (!state.slotChoices.isEmpty()) {
            candidateType = "SLOT";
            candidateIds = state.slotChoices.stream().map(SlotChoice::stableId).toList();
        } else {
            candidateType = null;
            candidateIds = List.of();
        }
        context.put("workflow", state.currentIntent == null ? null : state.currentIntent.name());
        context.put("candidateType", candidateType);
        context.put("candidateIds", candidateIds);
        context.put("version", Integer.toHexString(Objects.hash(
                state.currentIntent,
                candidateType,
                candidateIds
        )));
        context.put("generatedAt", OffsetDateTime.now().toString());
        return context;
    }

    private List<Map<String, Object>> clinicChoicesContext(List<ClinicChoice> choices) {
        if (choices == null) {
            return List.of();
        }
        return choices.stream().limit(12).map(choice -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("clinicSlug", choice.clinicSlug());
            row.put("clinicName", choice.clinicName());
            row.put("area", choice.area());
            row.put("city", choice.city());
            row.put("tenantId", choice.tenantId());
            row.put("clinicId", choice.clinicId());
            row.put("label", choice.label());
            return row;
        }).toList();
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
        if (snapshot == null && channel == CareAiChannel.PATIENT_PORTAL_VOICE
                && RequestContextHolder.get() != null
                && StringUtils.hasText(RequestContextHolder.require().correlationId())
                && !RequestContextHolder.require().correlationId().equals(externalSessionId)) {
            snapshot = conversationPersistenceService.findLatestSessionSnapshot(
                    RequestContextHolder.requireTenantId(), channel, patientId,
                    RequestContextHolder.require().correlationId(), 8);
        }
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
        String persistedWorkflowSubState = stringValue(context, "workflowSubState");
        if (state.workflowSubState == null && StringUtils.hasText(persistedWorkflowSubState)) {
            try {
                state.workflowSubState = PatientPortalCareAiWorkflowSubState.valueOf(persistedWorkflowSubState);
            } catch (IllegalArgumentException ignored) {
                // Ignore unknown historical values.
            }
        }
        state.requestedDoctorName = coalesce(state.requestedDoctorName, stringValue(context, "requestedDoctorName"));
        state.requestedSpeciality = coalesce(state.requestedSpeciality, stringValue(context, "requestedSpeciality"));
        state.requestedClinicName = coalesce(state.requestedClinicName, stringValue(context, "requestedClinicName"));
        state.requestedServiceName = coalesce(state.requestedServiceName, stringValue(context, "requestedServiceName"));
        state.requestedLocationName = coalesce(state.requestedLocationName, stringValue(context, "requestedLocationName"));
        state.selectedDoctorId = coalesce(state.selectedDoctorId, stringValue(context, "doctorId"));
        state.selectedDoctorSlug = coalesce(state.selectedDoctorSlug, stringValue(context, "doctorSlug"));
        state.selectedBookingReference = coalesce(state.selectedBookingReference, stringValue(context, "bookingReference"));
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
        state.executionConversationId = coalesce(state.executionConversationId, stringValue(context, "executionConversationId"));
        state.activeTurnId = coalesce(state.activeTurnId, stringValue(context, "activeTurnId"));
        state.pendingSkillId = coalesce(state.pendingSkillId, stringValue(context, "pendingSkillId"));
        state.pendingSkillExecutionId = coalesce(state.pendingSkillExecutionId, stringValue(context, "pendingSkillExecutionId"));
        String persistedSkillOutcome = stringValue(context, "lastSkillOutcome");
        if (state.lastSkillOutcome == null && StringUtils.hasText(persistedSkillOutcome)) {
            try {
                state.lastSkillOutcome = PatientPortalCareAiSkillOutcome.valueOf(persistedSkillOutcome);
            } catch (IllegalArgumentException ignored) {
                // Ignore unknown lifecycle values from older snapshots.
            }
        }
        state.slotPromptLead = coalesce(state.slotPromptLead, stringValue(context, "slotPromptLead"));
        hydrateDoctorChoices(state, context.get("doctorChoices"));
        hydrateClinicChoices(state, context.get("clinicChoices"));
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

    private void hydrateDoctorChoices(CareAiState state, Object value) {
        if (!(value instanceof List<?> rows) || !state.doctorChoices.isEmpty()) {
            return;
        }
        List<DoctorChoice> hydrated = new ArrayList<>();
        for (Object row : rows) {
            Map<String, Object> map = nestedMap(row);
            String id = stringValue(map, "publicDoctorId");
            String name = stringValue(map, "doctorName");
            if (!StringUtils.hasText(id) || !StringUtils.hasText(name)) {
                continue;
            }
            hydrated.add(new DoctorChoice(id, stringValue(map, "doctorSlug"), stringValue(map, "bookingReference"),
                    name, stringValue(map, "speciality"), stringValue(map, "clinicId"), stringValue(map, "tenantId"),
                    stringValue(map, "clinicSlug"), stringValue(map, "clinicName"), stringValue(map, "label"),
                    stringValue(map, "bookingMode"), booleanValue(map.get("canBookOnline"))));
        }
        if (!hydrated.isEmpty()) {
            state.doctorChoices = List.copyOf(hydrated);
            state.doctorOptions = hydrated.stream().map(DoctorChoice::label).toList();
        }
    }

    private void hydrateClinicChoices(CareAiState state, Object value) {
        if (!(value instanceof List<?> rows) || !state.clinicChoices.isEmpty()) {
            return;
        }
        List<ClinicChoice> hydrated = new ArrayList<>();
        for (Object row : rows) {
            Map<String, Object> map = nestedMap(row);
            String slug = stringValue(map, "clinicSlug");
            String name = stringValue(map, "clinicName");
            if (!StringUtils.hasText(slug) || !StringUtils.hasText(name)) {
                continue;
            }
            hydrated.add(new ClinicChoice(slug, name, stringValue(map, "area"), stringValue(map, "city"),
                    stringValue(map, "tenantId"), stringValue(map, "clinicId"), stringValue(map, "label")));
        }
        if (!hydrated.isEmpty()) {
            state.clinicChoices = List.copyOf(hydrated);
            state.clinicOptions = hydrated.stream().map(ClinicChoice::label).toList();
        }
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
                                                    PatientPortalCareAiIntent classifiedIntent,
                                                    PatientPortalCareAiCanonicalTurn turn) {
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
        if (state.currentIntent != null && asksDoctorAvailability(message, state.language)
                && !isAvailabilityFirstTurn(turn)) {
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

    private boolean isAvailabilityFirstTurn(PatientPortalCareAiCanonicalTurn turn) {
        if (turn == null) {
            return false;
        }
        PatientPortalCareAiCanonicalEntities entities = turn.entities();
        return turn.intent() == PatientPortalCareAiIntent.FIND_DOCTOR
                || StringUtils.hasText(entities.date())
                || StringUtils.hasText(entities.timeWindow())
                || StringUtils.hasText(entities.exactTime())
                || StringUtils.hasText(entities.speciality());
    }

    private boolean hasCanonicalSemanticChange(PatientPortalCareAiCanonicalTurn turn) {
        if (turn == null) {
            return false;
        }
        return turn.intent() != null
                && turn.intent().isWorkflowIntent()
                || !turn.entities().isEmpty()
                || turn.dialogAct() == PatientPortalCareAiDialogAct.CHANGE_INFORMATION
                || turn.dialogAct() == PatientPortalCareAiDialogAct.REQUEST_ALTERNATIVE
                || turn.dialogAct() == PatientPortalCareAiDialogAct.SELECT_OPTION
                || turn.correction().present()
                || turn.alternative().present()
                || turn.selection().present();
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
                        + " patientMobile=" + redactedPatientDiagnosticId());
        PatientPortalCareAiSkillResult<List<PatientPortalCareAiAppointmentOption>> skillResult = toolRegistry.appointmentCheck().execute(
                new PatientPortalCareAiAppointmentCheckSkillInput(
                        patientPortalService.currentPatientId() == null ? null : String.valueOf(patientPortalService.currentPatientId()),
                        patientPortalService.currentPatientMobile()
                )
        );
        List<PatientPortalCareAiAppointmentOption> appointments = skillResult.value() == null ? List.of() : skillResult.value();
        logAppointmentLookup("appointmentStatusSideAnswer", state, appointments);
        if (appointments.isEmpty()) {
            careAiTrace("appointmentStatusSideAnswer", "exit", state,
                    "skill=appointment.check outcome=" + skillResult.outcome() + " resultCount=0 reason=no-appointments-found");
            return noUpcomingAppointmentsPrompt(state.language);
        }
        PatientPortalCareAiAppointmentOption next = appointments.getFirst();
        careAiTrace("appointmentStatusSideAnswer", "exit", state,
                "skill=appointment.check outcome=" + skillResult.outcome() + " resultCount=" + appointments.size()
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
                redactedPatientDiagnosticId(),
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
                redactedPatientDiagnosticId(),
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
                redactedPatientDiagnosticId(),
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
                redactedPatientDiagnosticId(),
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
                redactedPatientDiagnosticId(),
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
                "patient.portal.careai.booking.action source=web-public-patient-careai method={} conversationTenantId={} tenantContextTenantId={} patientPortalSessionId={} patientId={} patientMobile={} selectedBookingReference={} selectedDoctorId={} selectedDoctorSlug={} selectedDoctorName={} selectedClinicSlug={} selectedTenantId={} selectedClinicId={} selectedDate={} selectedSlot={} selectedAppointmentId={}",
                method,
                RequestContextHolder.requireTenantId(),
                RequestContextHolder.get() == null ? null : RequestContextHolder.get().tenantId().value(),
                RequestContextHolder.require().correlationId(),
                patientPortalService.currentPatientId(),
                redactedPatientDiagnosticId(),
                state.selectedBookingReference,
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

    private void clearEntityExtraction(CareAiState state) {
        if (state == null) {
            return;
        }
        state.lastEntityExtractionMessage = null;
        state.lastEntityExtraction = null;
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
                redactedPatientDiagnosticId(),
                state == null || state.currentIntent == null ? null : state.currentIntent.name(),
                state == null ? null : state.lastQuestionKey,
                details
        );
    }

    private void logSlotLookupTrace(String phase,
                                    CareAiState state,
                                    String doctorId,
                                    LocalDate appointmentDate,
                                    String preferredTimeWindow,
                                    boolean slotLookupTriggered,
                                    Integer availableSlotCount,
                                    String noSlotReason) {
        if (!log.isInfoEnabled()) {
            return;
        }
        log.info(
                "AIVA_SLOT_LOOKUP_TRACE phase={} conversationTenantId={} tenantContextTenantId={} patientPortalSessionId={} patientId={} patientMobile={} doctorId={} doctorName={} appointmentDate={} preferredTimeWindow={} slotLookupTriggered={} slotLookupDate={} slotLookupTimeWindow={} availableSlotCount={} noSlotReason={} repeatedQuestionCount={}",
                phase,
                RequestContextHolder.requireTenantId(),
                RequestContextHolder.get() == null ? null : RequestContextHolder.get().tenantId().value(),
                state == null ? null : state.lastExternalSessionId,
                state == null ? null : state.lastPatientId,
                redactedPatientDiagnosticId(),
                doctorId,
                state == null ? null : state.selectedDoctorName,
                appointmentDate,
                preferredTimeWindow,
                slotLookupTriggered,
                appointmentDate,
                preferredTimeWindow,
                availableSlotCount,
                noSlotReason,
                state == null ? null : state.repeatedQuestionCount
        );
    }

    private boolean isVoiceConversationChannel() {
        return ACTIVE_CHANNEL.get() == CareAiChannel.PATIENT_PORTAL_VOICE;
    }

    private PatientPortalCareAiExtractedEntities extractEntities(CareAiState state, String message, String language) {
        if (state != null
                && Objects.equals(state.lastEntityExtractionMessage, message)
                && state.lastEntityExtraction != null) {
            return state.lastEntityExtraction;
        }
        PatientPortalCareAiExtractedEntities extracted = entityExtractor.extract(message, language);
        if (state != null) {
            state.lastEntityExtractionMessage = message;
            state.lastEntityExtraction = extracted;
        }
        log.info(
                "CAREAI_TRACE_ENTITY_EXTRACTION userText={} entities={} confidence={}",
                trimToLength(message, 160),
                extracted.traceView(),
                extracted.confidence()
        );
        return extracted;
    }

    private PatientPortalCareAiExtractedEntities extractEntities(String message, String language) {
        return extractEntities(currentState(), message, language);
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
                ? preferredTimeWindow + " में स्लॉट उपलब्ध नहीं मिले। नज़दीकी विकल्प ये हैं:\nकृपया इन स्लॉट में से एक चुनिए:"
                : "I couldn't find an " + preferredTimeWindow + " slot. Here are the nearest available options:\nPlease choose a slot by number or time:";
    }

    private String unavailablePreferredWindowPrompt(CareAiState state, String preferredTimeWindow, List<?> nearestSlots) {
        String criteria = availabilityCriteria(state, parseIsoDate(state.preferredDate));
        if (criteria.equals(state.lastNoSlotPromptCriteria)) {
            return isHindi(state.language)
                    ? "इस तारीख और समय पर अभी भी स्लॉट नहीं मिला। कृपया दूसरी तारीख, दूसरा समय, या दूसरा डॉक्टर बताइए।"
                    : "I still couldn't find a slot for those criteria. You can say another date, another time, or another doctor.";
        }
        state.lastNoSlotPromptCriteria = criteria;
        if (state.currentIntent == PatientPortalCareAiIntent.BOOK_APPOINTMENT) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_DATE, "slot-unavailable");
        } else if (state.currentIntent == PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT) {
            setWorkflowSubState(state, PatientPortalCareAiWorkflowSubState.NEED_NEW_DATE, "slot-unavailable");
        }
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

    private String safeTenantId() {
        return RequestContextHolder.get() == null ? null : RequestContextHolder.get().tenantId().value().toString();
    }

    private boolean isCancelledAppointmentStatus(String status) {
        return StringUtils.hasText(status) && "CANCELLED".equalsIgnoreCase(status.trim());
    }

    private void clearCurrentConversation(CareAiState state) {
        PatientPortalCareAiIntent previousIntent = state.currentIntent;
        resetWorkflowState(state, previousIntent == null ? PatientPortalCareAiIntent.BOOK_APPOINTMENT : previousIntent);
        state.currentIntent = null;
        state.requestedDoctorName = null;
        state.requestedSpeciality = null;
        state.requestedClinicName = null;
        state.requestedServiceName = null;
        state.requestedLocationName = null;
        state.selectedDoctorId = null;
        state.selectedDoctorSlug = null;
        state.selectedDoctorName = null;
        state.selectedDoctorBookingMode = null;
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
        state.workflowSubState = PatientPortalCareAiWorkflowSubState.START;
        clearEntityExtraction(state);
    }

    private void ensureWorkflowSubState(CareAiState state) {
        if (state == null || state.workflowSubState != null) {
            return;
        }
        state.workflowSubState = initialWorkflowSubState(state.currentIntent);
    }

    private PatientPortalCareAiWorkflowSubState initialWorkflowSubState(PatientPortalCareAiIntent intent) {
        if (intent == null) {
            return PatientPortalCareAiWorkflowSubState.START;
        }
        return switch (PatientPortalCareAiIntent.normalize(intent)) {
            case BOOK_APPOINTMENT -> PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY;
            case RESCHEDULE_APPOINTMENT, CANCEL_APPOINTMENT -> PatientPortalCareAiWorkflowSubState.NEED_APPOINTMENT;
            case FIND_DOCTOR, FIND_CLINIC, CHECK_APPOINTMENT, APPOINTMENT_STATUS -> PatientPortalCareAiWorkflowSubState.RESOLVING;
            case RESET_CONVERSATION, GREETING, SMALL_TALK, UNKNOWN -> PatientPortalCareAiWorkflowSubState.START;
        };
    }

    private boolean setWorkflowSubState(CareAiState state, PatientPortalCareAiWorkflowSubState next, String reason) {
        if (state == null || next == null) {
            return false;
        }
        PatientPortalCareAiWorkflowSubState current = state.workflowSubState;
        PatientPortalCareAiWorkflowType effectiveWorkflowType = patientPortalWorkflowType(state);
        if (!workflowSubStateRegistry.allowsTransition(effectiveWorkflowType, current, next)) {
            careAiTrace("workflowSubState.transition", "rejected", state,
                    "workflowType=" + effectiveWorkflowType
                            + " from=" + current
                            + " to=" + next
                            + " reason=" + reason);
            return false;
        }
        if (current == next) {
            return false;
        }
        state.workflowSubState = next;
        careAiTrace("workflowSubState.transition", "exit", state,
                "workflowType=" + effectiveWorkflowType
                        + " from=" + current
                        + " to=" + next
                        + " reason=" + reason);
        return true;
    }

    private PatientPortalCareAiWorkflowType patientPortalWorkflowType(CareAiState state) {
        if (state == null || state.currentIntent == null) {
            return PatientPortalCareAiWorkflowType.NONE;
        }
        return switch (PatientPortalCareAiIntent.normalize(state.currentIntent)) {
            case BOOK_APPOINTMENT -> PatientPortalCareAiWorkflowType.BOOK_APPOINTMENT;
            case RESCHEDULE_APPOINTMENT -> PatientPortalCareAiWorkflowType.RESCHEDULE_APPOINTMENT;
            case CANCEL_APPOINTMENT -> PatientPortalCareAiWorkflowType.CANCEL_APPOINTMENT;
            case CHECK_APPOINTMENT, APPOINTMENT_STATUS -> PatientPortalCareAiWorkflowType.CHECK_APPOINTMENT;
            case FIND_DOCTOR -> PatientPortalCareAiWorkflowType.FIND_DOCTOR;
            case FIND_CLINIC -> PatientPortalCareAiWorkflowType.FIND_CLINIC;
            case RESET_CONVERSATION, GREETING, SMALL_TALK, UNKNOWN -> PatientPortalCareAiWorkflowType.NONE;
        };
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
        if (date.isBefore(currentClinicDate()) && !isVoiceConversationChannel()) {
            return DateResolution.invalid("past");
        }
        return DateResolution.valid(date.toString(), explicit);
    }

    private LocalDate parseIsoDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
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
        // Voice and chat are channel adapters over one patient conversation.
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
        private PatientPortalCareAiWorkflowSubState workflowSubState;
        private String requestedDoctorName;
        private String requestedSpeciality;
        private String requestedClinicName;
        private String requestedServiceName;
        private String requestedLocationName;
        private String selectedDoctorId;
        private String selectedDoctorSlug;
        private String selectedBookingReference;
        private String selectedDoctorName;
        private String selectedDoctorBookingMode;
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
        private String lastAvailabilityNoMatchCriteria;
        private String lastNoSlotPromptCriteria;
        private boolean futureAvailabilityFallbackAppliedThisTurn;
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
        private String lastEntityExtractionMessage;
        private PatientPortalCareAiExtractedEntities lastEntityExtraction;
        private PatientPortalCareAiCanonicalTurn lastCanonicalTurn;
        private boolean persistenceHydrated;
        private String persistedWorkflowContextJson;
        private List<String> recentMessages = List.of();
        private UUID currentConversationId;
        private UUID currentWorkflowId;
        private String suspendedIntent;
        private CareAiTopicClassification lastTopicClassification;
        private String lastSideTopic;
        private boolean awaitingFreshConfirmation;
        private boolean voiceSlotRefreshRequested;
        private String activeConfirmationScopeKey;
        private int confirmationVersion;
        private CareAiWorkflowType transientWorkflowType;
        private UUID activeTaskId;
        private CareAiReceptionistTaskType activeTaskType;
        private String pendingWorkflowEventType;
        private String pendingWorkflowEventPayloadJson;
        private String executionConversationId;
        private long turnSequence;
        private String activeTurnId;
        private String pendingSkillId;
        private String pendingSkillExecutionId;
        private long pendingSkillStartedAt;
        private PatientPortalCareAiSkillOutcome lastSkillOutcome;
        private PatientPortalCareAiFallbackAction lastFallbackAction = PatientPortalCareAiFallbackAction.NONE;
        private final Map<String, List<DoctorChoice>> doctorLookupCache = new HashMap<>();
        private final Map<String, List<ClinicChoice>> clinicLookupCache = new HashMap<>();
        private final Map<String, List<AppointmentChoice>> appointmentLookupCache = new HashMap<>();
    }

    private record CallbackPreference(String label, OffsetDateTime dueAt) {
    }

    private record SessionKey(UUID tenantId, UUID appUserId) {
    }

    private record VoiceSessionKey(UUID tenantId, UUID appUserId, String conversationId) {
    }

    private record DoctorChoice(
            String publicDoctorId,
            String doctorSlug,
            String bookingReference,
            String doctorName,
            String speciality,
            String clinicId,
            String tenantId,
            String clinicSlug,
            String clinicName,
            String label,
            String bookingMode,
            boolean canBookOnline
    ) {
        private String stableId() {
            return publicDoctorId != null ? publicDoctorId : doctorSlug;
        }
    }

    private record DoctorAvailabilityMatch(DoctorChoice doctor, List<PatientPortalDoctorSlotResponse> slots) {
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
        private String stableId() {
            return clinicId != null ? clinicId : clinicSlug;
        }
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
        private String stableId() {
            return String.valueOf(appointmentDate) + "@" + String.valueOf(slotTime);
        }
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
