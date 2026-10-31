package com.deepthoughtnet.clinic.api.platform.communicationtest;

import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiExecutionContext;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiChannel;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiReadOnlyToolGateway;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiReadOnlyToolGateway.ToolInput;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaExecutionContext;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaTransactionAuthorization;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2VoiceConversationAdapter;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2VoiceFlowTrace;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2VoiceToolExecutor;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaSpokenDateFormatter;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilitySlot;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityTimeConstraint;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityTimeConstraintMode;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceAuthenticationState;
import com.deepthoughtnet.clinic.api.patientportal.auth.PatientPortalOtpService;
import com.deepthoughtnet.clinic.api.patientportal.booking.BookingExecutionContext;
import com.deepthoughtnet.clinic.api.patientportal.booking.BookingExecutionContextFactory;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.patient.service.model.PatientUpsertCommand;
import com.deepthoughtnet.clinic.patient.service.model.PatientConflictException;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceGuestRegistrationState;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceGuestRegistrationStatus;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiReadOnlyToolGateway.VoiceAvailabilityData;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiReadOnlyToolGateway.VoiceScheduleWindow;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiReadOnlyToolGateway.VoiceSlot;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import com.deepthoughtnet.clinic.api.voice.VoicePersona;
import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceTransactionAuthPolicy.Operation;
import java.time.ZoneId;
import java.time.LocalTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
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
    private final VoiceTransactionPolicyResolver transactionPolicyResolver;
    private final PatientService patientService;
    private final BookingExecutionContextFactory bookingExecutionContextFactory;

    @Autowired
    public VoiceReadOnlyAivaService(
            HealthcareAiReadOnlyToolGateway toolGateway,
            AivaV2VoiceConversationAdapter v2VoiceAdapter,
            VoiceTestProperties voiceTestProperties,
            ClinicTimeZoneResolver timeZoneResolver,
            VoiceTransactionPolicyResolver transactionPolicyResolver,
            PatientService patientService,
            ObjectProvider<BookingExecutionContextFactory> bookingContextFactoryProvider
    ) {
        this.toolGateway = toolGateway;
        this.v2VoiceAdapter = v2VoiceAdapter;
        this.voicePersona = voiceTestProperties == null || voiceTestProperties.getPersona() == null
                ? VoicePersona.FEMALE : voiceTestProperties.getPersona();
        this.timeZoneResolver = timeZoneResolver;
        this.transactionPolicyResolver = transactionPolicyResolver == null
                ? new VoiceTransactionPolicyResolver(new VoiceTransactionAuthProperties()) : transactionPolicyResolver;
        this.patientService = patientService;
        this.bookingExecutionContextFactory = bookingContextFactoryProvider == null ? null
                : bookingContextFactoryProvider.getIfAvailable();
    }

    public VoiceReadOnlyAivaService(HealthcareAiReadOnlyToolGateway toolGateway,
            AivaV2VoiceConversationAdapter v2VoiceAdapter, VoiceTestProperties voiceTestProperties,
            ClinicTimeZoneResolver timeZoneResolver, PatientPortalOtpService patientPortalOtpService,
            VoiceTransactionPolicyResolver transactionPolicyResolver, PatientService patientService) {
        this(toolGateway, v2VoiceAdapter, voiceTestProperties, timeZoneResolver,
                transactionPolicyResolver, patientService, null);
    }

    /** Compatibility constructor for existing callers; OTP service is intentionally unused. */
    public VoiceReadOnlyAivaService(HealthcareAiReadOnlyToolGateway toolGateway,
            AivaV2VoiceConversationAdapter v2VoiceAdapter, VoiceTestProperties voiceTestProperties,
            ClinicTimeZoneResolver timeZoneResolver, PatientPortalOtpService ignoredOtpService,
            VoiceTransactionPolicyResolver transactionPolicyResolver, PatientService patientService,
            ObjectProvider<BookingExecutionContextFactory> bookingContextFactoryProvider) {
        this(toolGateway, v2VoiceAdapter, voiceTestProperties, timeZoneResolver,
                transactionPolicyResolver, patientService, bookingContextFactoryProvider);
    }

    public VoiceReadOnlyAivaService(HealthcareAiReadOnlyToolGateway toolGateway,
            AivaV2VoiceConversationAdapter v2VoiceAdapter, VoiceTestProperties voiceTestProperties,
            ClinicTimeZoneResolver timeZoneResolver, PatientPortalOtpService patientPortalOtpService,
            VoiceTransactionPolicyResolver transactionPolicyResolver) {
        this(toolGateway, v2VoiceAdapter, voiceTestProperties, timeZoneResolver,
                transactionPolicyResolver, null, null);
    }

    public VoiceReadOnlyAivaService(HealthcareAiReadOnlyToolGateway toolGateway,
            AivaV2VoiceConversationAdapter v2VoiceAdapter, VoiceTestProperties voiceTestProperties,
            ClinicTimeZoneResolver timeZoneResolver, PatientPortalOtpService patientPortalOtpService) {
        this(toolGateway, v2VoiceAdapter, voiceTestProperties, timeZoneResolver,
                (VoiceTransactionPolicyResolver) null, null, null);
    }

    public VoiceReadOnlyAivaService(HealthcareAiReadOnlyToolGateway toolGateway,
                                    AivaV2VoiceConversationAdapter v2VoiceAdapter) {
        this(toolGateway, v2VoiceAdapter, new VoiceTestProperties(), null,
                (VoiceTransactionPolicyResolver) null, null, null);
    }

    public VoiceReadOnlyAivaService(
            HealthcareAiReadOnlyToolGateway toolGateway,
            AivaV2VoiceConversationAdapter v2VoiceAdapter,
            VoiceTestProperties voiceTestProperties
    ) {
        this(toolGateway, v2VoiceAdapter, voiceTestProperties, null,
                (VoiceTransactionPolicyResolver) null, null, null);
    }

    public VoiceReadOnlyAivaService(HealthcareAiReadOnlyToolGateway toolGateway,
            AivaV2VoiceConversationAdapter v2VoiceAdapter, VoiceTestProperties voiceTestProperties,
            ClinicTimeZoneResolver timeZoneResolver) {
        this(toolGateway, v2VoiceAdapter, voiceTestProperties, timeZoneResolver,
                (VoiceTransactionPolicyResolver) null, null, null);
    }

    public TurnResult continueTurn(Context context, String transcript) {
        if (context == null || !StringUtils.hasText(transcript)) {
            return new TurnResult(FAILURE_RESPONSE, "UNKNOWN", null, false, false, false);
        }
        try {
            AivaExecutionContext authContext = executionContext(context);
            if (v2VoiceAdapter.isCourtesyClose(authContext, transcript)) {
                AivaV2VoiceConversationAdapter.TurnResult close = v2VoiceAdapter.continueTurn(
                        authContext, transcript, (tool, execution, input) -> {
                            throw new IllegalStateException("Courtesy close must not execute a Voice tool");
                        });
                return new TurnResult(close.response(), close.intent(), null, false, false, false,
                        null, 0, "COURTESY_CLOSE", true, close.runtimeDiagnostics());
            }
            AivaV2VoiceConversationAdapter.VoiceScopeBoundaryResult scopeBoundary =
                    v2VoiceAdapter.unsupportedVoiceScopeRequest(authContext, transcript);
            if (scopeBoundary != null) {
                return new TurnResult(scopeBoundary.response(), scopeBoundary.category(), null,
                        false, false, false);
            }
            log.info("AIVA_V2_VOICE_SESSION_BINDING tenantId={} providerCallId={} sessionId={} sessionKey={} "
                            + "sessionIdMatchesProviderCallId={} channel={} language={}",
                    authContext.tenantId(), authContext.providerCallId(), authContext.sessionId(),
                    "VOICE|" + authContext.tenantId() + "|" + authContext.sessionId(),
                    java.util.Objects.equals(authContext.sessionId(), authContext.providerCallId()),
                    authContext.channel(), authContext.language());
            if (v2VoiceAdapter.voiceAuthenticationState(authContext) != null) {
                v2VoiceAdapter.updateVoiceAuthenticationState(authContext, null);
                authContext = executionContext(context);
            }
            AivaV2VoiceConversationAdapter.GuestPreparationResult completedFollowUp =
                    v2VoiceAdapter.completedBookingFollowUp(authContext, transcript);
            if (completedFollowUp != null) {
                return new TurnResult(completedFollowUp.response(), "COMPLETED_BOOKING",
                        "AIVA_V2_COMPLETED_BOOKING_OUTCOME", true, false, false, null, 0,
                        completedFollowUp.result(), false, completedFollowUp.diagnostics());
            }
            VoiceGuestRegistrationState guestState = v2VoiceAdapter.voiceGuestRegistrationState(authContext);
            VoiceTransactionAuthPolicy baselinePolicy = transactionPolicyResolver.resolve(
                    context.tenantId(), context.clinicId(), null);
            if (guestState != null && (guestState.status() == VoiceGuestRegistrationStatus.CONSENT_REQUESTED
                    || guestState.status() == VoiceGuestRegistrationStatus.MOBILE_CONFIRMATION_REQUESTED
                    || guestState.status() == VoiceGuestRegistrationStatus.COLLECTING_DETAILS
                    || guestState.status() == VoiceGuestRegistrationStatus.AWAITING_AGE
                    || guestState.status() == VoiceGuestRegistrationStatus.DUPLICATE_CHECK)) {
                return continueGuestRegistration(context, authContext, guestState, transcript);
            }
            if (guestState != null && guestState.status() == VoiceGuestRegistrationStatus.CAPABILITY_GRANTED) {
                if (!baselinePolicy.voiceQuickRegistrationEnabled() || !baselinePolicy.minimalRegistrationEnabled()) {
                    v2VoiceAdapter.updateVoiceGuestRegistrationState(authContext, null);
                    return guestTurn(context, "GUEST_PREPARATION_DENIED",
                            "Voice booking preparation is not currently enabled. No appointment was created.",
                            false, true);
                }
                VoiceTransactionAuthPolicy bookingPolicy = transactionPolicyResolver.resolve(context.tenantId(),
                        context.clinicId(), Operation.BOOK_APPOINTMENT);
                if (!bookingPolicy.bookingEnabled()) {
                    return bookingDenied(bookingPolicy, "BOOKING_FEATURE_DISABLED");
                }
                if (bookingPolicy.bookingEnabled() && bookingExecutionContextFactory != null) {
                    try {
                        BookingExecutionContext bookingContext = bookingExecutionContextFactory.quickRegisteredVoice(authContext, guestState);
                        return executeVoiceBookingTurn(context, authContext, transcript, bookingContext, bookingPolicy);
                    } catch (SecurityException denied) {
                        return bookingDenied(bookingPolicy, "CAPABILITY_DENIED");
                    }
                }
                AivaV2VoiceConversationAdapter.GuestPreparationResult preparation =
                        v2VoiceAdapter.prepareGuestBookingTurn(authContext, transcript);
                boolean executionBlocked = "TRANSACTION_EXECUTION_DISABLED".equals(preparation.result());
                Map<String, Object> diagnostics = new LinkedHashMap<>(preparation.diagnostics());
                diagnostics.put("transactionExecutionEnabled", false);
                diagnostics.put("patientContextType", "GUEST_REGISTERED");
                return new TurnResult(preparation.response(),
                        "TRANSACTION_EXECUTION_DISABLED".equals(preparation.result())
                                ? "BOOK_APPOINTMENT" : "GUEST_BOOKING_PREPARATION",
                        "AIVA_V2_BOOKING_PREPARATION", true, executionBlocked, false,
                        null, 0, preparation.result(), false, diagnostics);
            }
            VoiceTransactionAuthPolicy bookingPolicy = transactionPolicyResolver.resolve(context.tenantId(),
                    context.clinicId(), Operation.BOOK_APPOINTMENT);
            if (bookingPolicy.bookingEnabled() && bookingPolicy.bookingExecutionAllowed()
                    && v2VoiceAdapter.shouldRouteVoiceBookingTurn(authContext, transcript)) {
                BookingExecutionContext bookingContext = bookingExecutionContext(context, authContext,
                        guestState, bookingPolicy);
                if (bookingContext != null) {
                    return executeVoiceBookingTurn(context, authContext, transcript, bookingContext, bookingPolicy);
                }
            }
            ZoneId tenantZone = timeZoneResolver == null ? ZoneId.of("Asia/Kolkata")
                    : timeZoneResolver.resolve(context.tenantId());
            HealthcareAiExecutionContext executionContext = new HealthcareAiExecutionContext(
                    context.tenantId(), context.identifiedPatientId(), context.patientIdentificationStatus(),
                    HealthcareAiChannel.VOICE, context.interactionId(), context.clinicId(), context.locationId(),
                    context.language());
            AivaExecutionContext baseContext = new AivaExecutionContext(
                    context.tenantId(), context.identifiedPatientId(), "VOICE", context.language(),
                    tenantZone, context.providerCallId(), context.interactionId(),
                    context.clinicId(), context.locationId(),
                    HealthcareAiReadOnlyToolGateway.VOICE_READ_ONLY_ALLOWED_TOOLS,
                    context.providerCallId(), context.interactionId() == null ? context.providerCallId()
                    : context.interactionId().toString(),
                    AivaTransactionAuthorization.unauthenticated(context.tenantId(), context.providerCallId(),
                            context.identifiedPatientId()));
            AivaExecutionContext aivaContext = new AivaExecutionContext(baseContext.tenantId(), baseContext.identifiedPatientId(),
                    baseContext.channel(), baseContext.language(), baseContext.timezone(), baseContext.sessionId(),
                    baseContext.interactionId(), baseContext.clinicId(), baseContext.locationId(), baseContext.capabilities(),
                    baseContext.providerCallId(), baseContext.correlationId(), v2VoiceAdapter.currentVoiceAuthorization(baseContext));
            AtomicReference<HealthcareAiReadOnlyToolGateway.ToolResult> executed = new AtomicReference<>();
            AtomicReference<AivaV2VoiceToolExecutor.ToolInput> executedInput = new AtomicReference<>();
            AivaV2VoiceConversationAdapter.TurnResult turn = v2VoiceAdapter.continueTurn(
                    aivaContext, transcript, (tool, ignored, input) -> {
                        executedInput.set(input);
                        if ("FIND_DOCTOR".equals(tool)) {
                            log.info("VOICE_FIND_DOCTOR_INPUT providerCallId={} tenantId={} doctorQueryPresent={} "
                                            + "canonicalSpecialty={} clinicId={} locationId={}",
                                    context.providerCallId(), context.tenantId(), StringUtils.hasText(input.query()), input.speciality(),
                                    input.clinic(), input.location());
                        }
                        HealthcareAiReadOnlyToolGateway.ToolResult result = toolGateway.execute(tool,
                                executionContext, new ToolInput(input.query(), input.speciality(), input.clinic(),
                                        input.location(), input.doctorId(), input.date(), input.queryType(),
                                        input.daypart(), input.timezone(), input.pageOffset()));
                        result = applyCanonicalSlotConstraint(tool, result, input);
                        executed.set(result);
                        return new AivaV2VoiceToolExecutor.ToolResult(result.allowed(), result.success(),
                                result.value(), result.candidateCount(), result.sourceCandidateCount(),
                                result.diagnostics(), providerOptions(result), availabilityContext(result, input));
                    });
            if ("BOOK_APPOINTMENT".equals(turn.intent()) && bookingPolicy.bookingEnabled()) {
                BookingExecutionContext bookingContext = bookingExecutionContext(context, aivaContext,
                        guestState, bookingPolicy);
                if (bookingContext != null) {
                    return executeVoiceBookingTurn(context, aivaContext, transcript, bookingContext, bookingPolicy);
                }
            }
            HealthcareAiReadOnlyToolGateway.ToolResult result = executed.get();
            String response = turn.response();
            if (turn.toolExecuted() && result != null) response = render(turn.tool(), result,
                    turn.toolInputSpeciality(), turn.normalizedTurn(), tenantZone, executedInput.get());
            Operation transactionOperation = Operation.fromIntent(turn.intent());
            VoiceTransactionAuthPolicy transactionPolicy = transactionOperation == null ? null
                    : transactionPolicyResolver.resolve(context.tenantId(), context.clinicId(), transactionOperation);
            boolean guestConsentOffered = false;
            if (turn.mutationBlocked() && "BOOK_APPOINTMENT".equals(turn.intent())
                    && "NO_MATCH".equals(context.patientIdentificationStatus())
                    && "GUEST_CALLER".equals(context.conversationPartyType())
                    && context.identifiedPatientId() == null && transactionPolicy != null
                    && transactionPolicy.bookingEnabled() && transactionPolicy.voiceQuickRegistrationEnabled()
                    && transactionPolicy.minimalRegistrationEnabled()) {
                boolean alreadyDeclined = guestState != null
                        && guestState.status() == VoiceGuestRegistrationStatus.DECLINED;
                String normalizedMobile = guestMobile(context.registeredMobile());
                if (!alreadyDeclined && normalizedMobile != null && context.tenantId() != null
                    && StringUtils.hasText(context.providerCallId()) && context.interactionId() != null) {
                    v2VoiceAdapter.updateVoiceGuestRegistrationState(authContext, new VoiceGuestRegistrationState(
                            VoiceGuestRegistrationStatus.CONSENT_REQUESTED, context.tenantId(), null,
                            context.providerCallId(), context.interactionId(), null, false));
                    log.info("AIVA_VOICE_QUICK_REGISTRATION quickRegistrationFeatureEnabled=true "
                            + "quickRegistrationEligible=true quickRegistrationState=CONSENT_REQUESTED "
                            + "callerMobilePresent=true consent=false patientCreated=false");
                    response = guestConsentPrompt(registrationLanguage(context, authContext));
                    guestConsentOffered = true;
                }
            }
            if (turn.mutationBlocked()) {
                log.info("VOICE_AIVA_MUTATION_BLOCKED callId={} tool={}", context.providerCallId(), turn.intent());
            }
            Map<String, Object> turnDiagnostics = new LinkedHashMap<>(withResponseSource(
                    turn.runtimeDiagnostics(), turn, result, executedInput.get()));
            VoiceIdentityTrust effectiveIdentityTrust = context.identityTrust();
            boolean identifiedPatient = "SINGLE_MATCH".equals(context.patientIdentificationStatus())
                    && "IDENTIFIED_PATIENT".equals(context.conversationPartyType())
                    && context.identifiedPatientId() != null;
            Map<String, Object> identityDiagnostics = new LinkedHashMap<>();
            identityDiagnostics.put("identityState", identifiedPatient ? "IDENTIFIED_PATIENT"
                    : effectiveIdentityTrust.identityState().name());
            identityDiagnostics.put("assuranceState", effectiveIdentityTrust.identityState().name());
            identityDiagnostics.put("conversationParty", context.conversationPartyType());
            identityDiagnostics.put("patientIdentificationStatus", context.patientIdentificationStatus());
            identityDiagnostics.put("patientIdPresent", context.identifiedPatientId() != null);
            identityDiagnostics.put("callerPatientBinding", identifiedPatient
                    ? "TENANT_SCOPED_CALLER_MATCH" : "UNRESOLVED");
            identityDiagnostics.put("trustSource", effectiveIdentityTrust.trustSource().name());
            identityDiagnostics.put("phoneBindingVerified", effectiveIdentityTrust.phoneBindingVerified());
            identityDiagnostics.put("restrictedBookingAllowed", identifiedPatient
                    && transactionPolicy != null
                    && effectiveIdentityTrust.identityState() == VoiceIdentityTrust.IdentityState.REGISTERED_UNVERIFIED
                    && transactionPolicy.bookingExecutionAllowed());
            identityDiagnostics.put("personalDataAllowed", false);
            turnDiagnostics.put("voiceIdentity", Map.copyOf(identityDiagnostics));
            if (transactionPolicy != null) {
                turnDiagnostics.put("voiceTransactionPolicy", policyDiagnostics(transactionPolicy,
                        guestConsentOffered ? "GUEST_CONSENT_REQUESTED" : transactionPolicy.decision()));
            }
            if (guestConsentOffered) {
                var presentationMode = v2VoiceAdapter.presentationMode(authContext);
                turnDiagnostics.put("presentationMode", presentationMode.name());
                turnDiagnostics.put("ttsLanguage", presentationMode
                        == com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoicePresentationMode.ENGLISH
                        ? "en-IN" : "hi-IN");
                turnDiagnostics.put("ttsTextLanguageProfile", presentationMode
                        == com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoicePresentationMode.ENGLISH
                        ? "en-IN" : "hi-IN");
                turnDiagnostics.put("guestRegistration", Map.ofEntries(
                        Map.entry("state", "CONSENT_REQUESTED"),
                        Map.entry("intent", "GUEST_REGISTRATION_CONSENT"),
                        Map.entry("consentPending", true),
                        Map.entry("consentDecision", "PENDING"),
                        Map.entry("consentPersisted", false),
                        Map.entry("namePromptIssued", false),
                        Map.entry("bookingContextPreserved", v2VoiceAdapter.hasVoiceBookingWorkflow(authContext)),
                        Map.entry("patientCreated", false), Map.entry("authenticated", false),
                        Map.entry("capabilityGranted", false), Map.entry("mutationAllowed", false)));
            }
            String resultSummary = result == null ? turn.toolResultSummary() : summarizeResult(turn.tool(), result);
            log.info("AIVA_V2_VOICE_FLOW stage=VOICE_RESPONSE tenantId={} providerCallId={} conversationIdHash={} turnId={} turnNumber={} "
                            + "intent={} tool={} activeWorkflow={} decisionCategory={} executionDecision={} responseSource={} "
                            + "transactionAuthorizationSource={} mutationBlocked={} toolExecuted={}",
                    context.tenantId(), context.providerCallId(), AivaV2VoiceFlowTrace.conversationHash(aivaContext.sessionId()),
                    AivaV2VoiceFlowTrace.turnId(), AivaV2VoiceFlowTrace.turnNumber(), turn.intent(), turn.tool(),
                    v2VoiceAdapter.hasVoiceBookingWorkflow(aivaContext) ? "BOOKING" : "NONE", resultSummary,
                    turn.mutationBlocked() ? "BLOCKED" : turn.toolExecuted() ? "EXECUTED" : "NOT_EXECUTED",
                    turnDiagnostics.get("responseSource"), aivaContext.transactionAuthorization() == null ? null
                            : aivaContext.transactionAuthorization().authorizationSource(),
                    turn.mutationBlocked(), turn.toolExecuted());
            return new TurnResult(response, turn.intent(), turn.tool(), turn.aivaUsed(), turn.mutationBlocked(),
                    turn.toolExecuted(), turn.toolInputSpeciality(), turn.toolResultCount(),
                    resultSummary, turn.closing(),
                    Map.copyOf(turnDiagnostics));
        } catch (RuntimeException ex) {
            log.warn("VOICE_AIVA_TURN_FAILED callId={} exception={} reason={} failureSite={}",
                    context.providerCallId(), ex.getClass().getSimpleName(), safeReason(ex), safeFailureSite(ex));
            return new TurnResult(FAILURE_RESPONSE, "UNKNOWN", null, false, false, false);
        }
    }

    /** Canonical TTS locale for the current call, derived from the Voice session's presentation mode. */
    public String presentationLanguage(Context context) {
        if (context == null) return "hi-IN";
        var mode = v2VoiceAdapter.presentationMode(executionContext(context));
        return mode == com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoicePresentationMode.ENGLISH
                ? "en-IN" : "hi-IN";
    }

    public String presentationModeName(Context context) {
        if (context == null) return "HINDI";
        return v2VoiceAdapter.presentationMode(executionContext(context)).name();
    }

    private String safeFailureSite(RuntimeException failure) {
        if (failure == null || failure.getStackTrace().length == 0) return "unknown";
        StackTraceElement first = failure.getStackTrace()[0];
        return first.getClassName() + "#" + first.getMethodName() + ":" + first.getLineNumber();
    }

    private BookingExecutionContext bookingExecutionContext(Context context, AivaExecutionContext aivaContext,
            VoiceGuestRegistrationState guestState,
            VoiceTransactionAuthPolicy policy) {
        if (bookingExecutionContextFactory == null || !policy.bookingExecutionAllowed()) return null;
        try {
            if (guestState != null && guestState.status() == VoiceGuestRegistrationStatus.CAPABILITY_GRANTED) {
                if (!policy.voiceQuickRegistrationEnabled()) return null;
                return bookingExecutionContextFactory.quickRegisteredVoice(aivaContext, guestState);
            }
            if (context.identityTrust() != null
                    && context.identityTrust().identityState() == VoiceIdentityTrust.IdentityState.REGISTERED_UNVERIFIED) {
                return bookingExecutionContextFactory.restrictedUnverifiedVoice(aivaContext, context.identityTrust(),
                        policy, context.patientIdentificationStatus());
            }
        } catch (SecurityException denied) {
            return null;
        }
        return null;
    }

    private TurnResult executeVoiceBookingTurn(Context context, AivaExecutionContext aivaContext, String transcript,
            BookingExecutionContext bookingContext, VoiceTransactionAuthPolicy policy) {
        AivaV2VoiceConversationAdapter.GuestPreparationResult result =
                v2VoiceAdapter.prepareAuthorizedVoiceBookingTurn(aivaContext, transcript, bookingContext, policy);
        Map<String, Object> diagnostics = new LinkedHashMap<>(result.diagnostics());
        diagnostics.put("voiceTransactionPolicy", policyDiagnostics(policy, result.result()));
        diagnostics.put("bookingActorType", bookingContext.actorType().name());
        diagnostics.put("bookingAuthorizationSource", bookingContext.authorizationSource());
        diagnostics.put("transactionExecutionEnabled", policy.bookingEnabled());
        diagnostics.put("transactionMode", "NEW_BOOKING_TRANSACTION");
        diagnostics.put("effectiveAuthorizationDecision", "AUTHORIZED_RESTRICTED_BOOKING");
        diagnostics.put("effectiveAllowedOperation", "BOOK_APPOINTMENT");
        diagnostics.put("patientIdentityState", "IDENTIFIED_PATIENT");
        diagnostics.put("identityAssuranceState", bookingContext.authenticationState().name());
        boolean booked = "BOOKING_CONFIRMED".equals(result.result());
        String intent = result.result() != null && (result.result().startsWith("CLARIFY")
                || result.result().startsWith("PROVIDER_SWITCH")
                || result.result().startsWith("PROVIDER_IDENTITY")
                || "NO_MORE_SLOTS".equals(result.result())
                || "END_CONVERSATION".equals(result.result())) ? result.result() : "BOOK_APPOINTMENT";
        log.info("AIVA_V2_VOICE_FLOW stage=VOICE_RESPONSE tenantId={} providerCallId={} conversationIdHash={} turnId={} turnNumber={} "
                        + "intent={} tool=AIVA_V2_SHARED_BOOKING_BOUNDARY activeWorkflow={} decisionCategory={} "
                        + "executionDecision={} transactionAuthorizationSource={} mutationBlocked={} toolExecuted=false",
                context.tenantId(), context.providerCallId(), AivaV2VoiceFlowTrace.conversationHash(aivaContext.sessionId()),
                AivaV2VoiceFlowTrace.turnId(), AivaV2VoiceFlowTrace.turnNumber(), intent,
                v2VoiceAdapter.hasVoiceBookingWorkflow(aivaContext) ? "BOOKING" : "NONE", result.result(),
                diagnostics.getOrDefault("executionDecision", result.result()), bookingContext.authorizationSource(), !booked);
        return new TurnResult(result.response(), intent, "AIVA_V2_SHARED_BOOKING_BOUNDARY",
                true, !booked, booked, null, 0, result.result(), false, Map.copyOf(diagnostics));
    }

    private TurnResult bookingDenied(VoiceTransactionAuthPolicy policy, String reason) {
        return new TurnResult("Voice booking is not authorized for this call. No appointment was booked.",
                "BOOK_APPOINTMENT", null, true, true, false, null, 0, reason, false,
                Map.of("mutationAllowed", false, "executionDecision", reason,
                        "transactionMode", "NEW_BOOKING_TRANSACTION",
                        "effectiveAuthorizationDecision", "DENIED",
                        "effectiveAllowedOperation", "NONE",
                        "voiceTransactionPolicy", policyDiagnostics(policy, reason)));
    }

    Map<String, Object> withResponseSource(Map<String, Object> source,
            AivaV2VoiceConversationAdapter.TurnResult turn,
            HealthcareAiReadOnlyToolGateway.ToolResult toolResult,
            AivaV2VoiceToolExecutor.ToolInput input) {
        Map<String, Object> diagnostics = new LinkedHashMap<>(source == null ? Map.of() : source);
        boolean unsupportedTransaction = turn.intent() != null
                && Set.of("RESCHEDULE_NOT_SUPPORTED", "CANCEL_NOT_SUPPORTED", "PATIENT_DATA_NOT_SUPPORTED",
                        "OPERATION_NOT_BOOK", "CANCEL_APPOINTMENT", "RESCHEDULE_APPOINTMENT",
                        "FIND_APPOINTMENTS").contains(turn.intent());
        boolean newBooking = "BOOK_APPOINTMENT".equals(turn.intent());
        diagnostics.put("transactionMode", newBooking ? "NEW_BOOKING_TRANSACTION"
                : unsupportedTransaction ? "DEFERRED_UNSUPPORTED_TRANSACTION" : "READ_ONLY_DISCOVERY");
        diagnostics.put("effectiveAuthorizationDecision", newBooking
                ? turn.mutationBlocked() ? "DENIED" : "AUTHORIZED_RESTRICTED_BOOKING"
                : unsupportedTransaction ? "NOT_SUPPORTED" : "READ_ONLY_ALLOWED");
        if (newBooking && !turn.mutationBlocked()) diagnostics.put("effectiveAllowedOperation", "BOOK_APPOINTMENT");
        diagnostics.put("responseSource", turn.mutationBlocked() ? "BLOCKED_TRANSACTION_RESPONSE"
                : turn.toolExecuted() && toolResult != null ? "TOOL_RESULT_RENDERER"
                : turn.intent() != null && (turn.intent().startsWith("CLARIFY") || "UNKNOWN".equals(turn.intent())
                        || "ESCALATION_DECISION".equals(turn.intent())) ? "CLARIFICATION_RECOVERY"
                : "DETERMINISTIC_VOICE_ADAPTER");
        if (input != null && input.availabilityTimeConstraint() != null) {
            AvailabilityTimeConstraint constraint = input.availabilityTimeConstraint();
            diagnostics.put("slotSelectionSource", constraint.mode() == AvailabilityTimeConstraintMode.EXACT
                    ? "EXACT_TIME" : constraint.mode() == AvailabilityTimeConstraintMode.BETWEEN
                    ? "TIME_RANGE" : "DAYPART");
            diagnostics.put("requestedExactTime", constraint.mode() == AvailabilityTimeConstraintMode.EXACT
                    ? constraint.startTime().toString() : null);
            diagnostics.put("requestedRangeStart", constraint.mode() == AvailabilityTimeConstraintMode.BETWEEN
                    ? constraint.startTime().toString() : null);
            diagnostics.put("requestedRangeEnd", constraint.mode() == AvailabilityTimeConstraintMode.BETWEEN
                    ? constraint.endTime().toString() : null);
            diagnostics.put("exactSlotMatch", diagnosticBoolean(toolResult == null ? null : toolResult.diagnostics(),
                    "exactSlotMatch"));
            diagnostics.put("exactSlotCandidate", Boolean.TRUE.equals(diagnostics.get("exactSlotMatch")));
            diagnostics.put("matchedCanonicalSlot", diagnosticValue(toolResult == null ? null
                    : toolResult.diagnostics(), "matchedCanonicalSlot"));
            diagnostics.put("alternativeSlots", toolResult != null
                    && toolResult.value() instanceof VoiceAvailabilityData data
                    && constraint.mode() == AvailabilityTimeConstraintMode.EXACT
                    && !Boolean.TRUE.equals(diagnostics.get("exactSlotMatch"))
                    ? data.bookableSlots().stream().map(slot -> slot.startTime().toString()).toList() : List.of());
        }
        diagnostics.entrySet().removeIf(entry -> entry.getValue() == null);
        return Map.copyOf(diagnostics);
    }

    private Boolean diagnosticBoolean(String diagnostics, String name) {
        String value = diagnosticValue(diagnostics, name);
        return value == null ? null : Boolean.valueOf(value);
    }

    private String diagnosticValue(String diagnostics, String name) {
        if (diagnostics == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:^|;\\s*)" + name + "=([^;]*)")
                .matcher(diagnostics);
        return matcher.find() ? matcher.group(1) : null;
    }

    public void endSession(UUID tenantId, String providerCallId) {
        v2VoiceAdapter.endSession(tenantId, providerCallId);
    }

    private TurnResult continueGuestRegistration(Context context, AivaExecutionContext executionContext,
            VoiceGuestRegistrationState state, String transcript) {
        VoiceTransactionAuthPolicy policy = transactionPolicyResolver.resolve(context.tenantId(), context.clinicId(),
                Operation.BOOK_APPOINTMENT);
        if (!guestStateMatches(context, state) || !policy.voiceQuickRegistrationEnabled()
                || !policy.minimalRegistrationEnabled()) {
            v2VoiceAdapter.updateVoiceGuestRegistrationState(executionContext, null);
            return guestTurn(context, "GUEST_REGISTRATION_FAILED",
                    "Voice guest registration is not available. No patient record was created.", false, false);
        }
        String normalizedMobile = guestMobile(context.registeredMobile());
        if (normalizedMobile == null) {
            v2VoiceAdapter.updateVoiceGuestRegistrationState(executionContext,
                    guestState(state, VoiceGuestRegistrationStatus.FAILED, null, null, false));
            return guestTurn(context, "GUEST_REGISTRATION_FAILED",
                    "I cannot confirm the calling number for registration. Please contact clinic reception.", false, false);
        }
        switch (state.status()) {
            case CONSENT_REQUESTED -> {
                if (isExplicitNo(transcript)) {
                    v2VoiceAdapter.updateVoiceGuestRegistrationState(executionContext,
                            guestState(state, VoiceGuestRegistrationStatus.DECLINED, null, null, false));
                    return guestTurn(context, "GUEST_REGISTRATION_DECLINED",
                            "No patient record was created.", false, false,
                            consentDiagnostics(context, state, VoiceGuestRegistrationStatus.DECLINED,
                                    "DECLINED", true, false));
                }
                if (!isExplicitYes(transcript)) return guestTurn(context, "GUEST_REGISTRATION_CONSENT",
                        guestConsentClarification(registrationLanguage(context, executionContext)), false, false,
                        consentDiagnostics(context, state, VoiceGuestRegistrationStatus.CONSENT_REQUESTED,
                                "UNCLEAR", false, false));
                VoiceGuestRegistrationStatus next = VoiceGuestRegistrationStatus.COLLECTING_DETAILS;
                Instant consentTimestamp = Instant.now();
                v2VoiceAdapter.updateVoiceGuestRegistrationState(executionContext,
                        new VoiceGuestRegistrationState(next, state.tenantId(), null, state.providerCallId(),
                                state.interactionId(), null, false, null, null, consentTimestamp, true, "VOICE"));
                log.info("AIVA_VOICE_QUICK_REGISTRATION quickRegistrationConsent=true consentSource=VOICE "
                        + "quickRegistrationState=AWAIT_QUICK_REGISTRATION_NAME patientCreated=false");
                return guestTurn(context, "GUEST_REGISTRATION_CONSENTED",
                        guestNamePrompt(registrationLanguage(context, executionContext)),
                        false, false,
                        consentDiagnostics(context, state, next, "ACCEPTED", true, true));
            }
            case MOBILE_CONFIRMATION_REQUESTED -> {
                if (isExplicitNo(transcript)) {
                    v2VoiceAdapter.updateVoiceGuestRegistrationState(executionContext,
                            guestState(state, VoiceGuestRegistrationStatus.FAILED, null, null, false));
                    return guestTurn(context, "GUEST_MOBILE_NOT_CONFIRMED",
                            "No patient record was created because the mobile number was not confirmed.", false, false);
                }
                if (!isExplicitYes(transcript)) return guestTurn(context, "GUEST_MOBILE_CONFIRMATION",
                        mobileConfirmationPrompt(registrationLanguage(context, executionContext)), false, false);
                v2VoiceAdapter.updateVoiceGuestRegistrationState(executionContext,
                        guestState(state, VoiceGuestRegistrationStatus.COLLECTING_DETAILS, null, null, false));
                return guestTurn(context, "GUEST_COLLECTING_DETAILS",
                        guestNamePrompt(registrationLanguage(context, executionContext)), false, false);
            }
            case COLLECTING_DETAILS -> {
                if (isExplicitNo(transcript)) {
                    v2VoiceAdapter.updateVoiceGuestRegistrationState(executionContext,
                            guestState(state, VoiceGuestRegistrationStatus.DECLINED, null, null, false));
                    return guestTurn(context, "GUEST_REGISTRATION_DECLINED",
                            declinedRegistrationResponse(registrationLanguage(context, executionContext)), false, false);
                }
                if (isExplicitYes(transcript)) return guestTurn(context, "GUEST_COLLECTING_DETAILS",
                        guestNamePrompt(registrationLanguage(context, executionContext)), false, false,
                        Map.of("namePromptIssued", true, "consentPending", false));
                String name = parseGuestName(transcript);
                if (name == null) return guestTurn(context, "GUEST_COLLECTING_DETAILS",
                        guestNamePrompt(registrationLanguage(context, executionContext)), false, false);
                v2VoiceAdapter.updateVoiceGuestRegistrationState(executionContext,
                        new VoiceGuestRegistrationState(VoiceGuestRegistrationStatus.AWAITING_AGE, state.tenantId(),
                                null, state.providerCallId(), state.interactionId(), null, false,
                                name, null, state.consentTimestamp()));
                return guestTurn(context, "GUEST_AGE_REQUESTED",
                        guestAgePrompt(registrationLanguage(context, executionContext)), false, false);
            }
            case AWAITING_AGE -> {
                Integer age = parseGuestAge(transcript);
                if (age == null) return guestTurn(context, "GUEST_AGE_CLARIFICATION",
                        guestAgeClarification(registrationLanguage(context, executionContext)), false, false,
                        ageDiagnostics(state, transcript, null, false, state.status()));
                VoiceGuestRegistrationState duplicateCheck = new VoiceGuestRegistrationState(
                        VoiceGuestRegistrationStatus.DUPLICATE_CHECK, state.tenantId(), null,
                        state.providerCallId(), state.interactionId(), null, false,
                        state.capturedFirstName(), age, state.consentTimestamp());
                v2VoiceAdapter.updateVoiceGuestRegistrationState(executionContext, duplicateCheck);
                log.info("AIVA_VOICE_QUICK_REGISTRATION ageParsed=true ageValid=true "
                        + "quickRegistrationState=DUPLICATE_RECHECK capturedAgePresent=true");
                if (patientService == null) {
                    v2VoiceAdapter.updateVoiceGuestRegistrationState(executionContext,
                            guestState(state, VoiceGuestRegistrationStatus.FAILED, null, null, false));
                    return guestTurn(context, "GUEST_REGISTRATION_FAILED",
                            "I cannot register the patient right now. Please contact clinic reception.", false, false);
                }
                List<PatientRecord> matches;
                try {
                    matches = patientService.findActiveByTenantAndMobile(context.tenantId(), normalizedMobile);
                } catch (RuntimeException lookupFailure) {
                    log.warn("VOICE_GUEST_DUPLICATE_LOOKUP_FAILED callId={} tenantId={} reason={}",
                            context.providerCallId(), context.tenantId(), lookupFailure.getClass().getSimpleName());
                    v2VoiceAdapter.updateVoiceGuestRegistrationState(executionContext,
                            guestState(state, VoiceGuestRegistrationStatus.FAILED, null, null, false));
                    return guestTurn(context, "GUEST_DUPLICATE_CHECK_FAILED",
                            "I could not safely verify whether a patient record already exists. Please contact clinic reception.", false, false,
                            ageOutcomeDiagnostics(state, transcript, age, VoiceGuestRegistrationStatus.FAILED,
                                    "LOOKUP_FAILED", false, false));
                }
                if (matches.size() == 1) return existingPatientPath(context, executionContext, state, policy,
                        matches.getFirst(), transcript, age);
                if (matches.size() > 1) {
                    v2VoiceAdapter.updateVoiceGuestRegistrationState(executionContext,
                            guestState(state, VoiceGuestRegistrationStatus.FAILED, null, null, false));
                    return guestTurn(context, "GUEST_DUPLICATE_AMBIGUOUS",
                            "More than one patient record may match this number. Please contact clinic reception.", false, false,
                            ageOutcomeDiagnostics(state, transcript, age, VoiceGuestRegistrationStatus.FAILED,
                                    "MULTIPLE_MATCHES_FAIL_CLOSED", false, false));
                }
                try {
                    PatientUpsertCommand command = new PatientUpsertCommand(state.capturedFirstName(), null,
                            PatientGender.UNKNOWN, null, age, normalizedMobile, null, null, null, null, null,
                            null, null, null, null, null, null, null, null, null, null, true);
                    if (!state.consentGiven() || state.consentTimestamp() == null
                            || !"VOICE".equals(state.consentSource())) {
                        throw new IllegalStateException("quick registration consent is missing");
                    }
                    PatientRecord created = patientService.createVoiceQuickRegistration(context.tenantId(), command,
                            context.interactionId(), java.time.OffsetDateTime.ofInstant(state.consentTimestamp(),
                                    java.time.ZoneOffset.UTC));
                    Instant expiry = Instant.now().plusSeconds(policy.guestBookingCapabilityTtlMinutes() * 60L);
                    VoiceGuestRegistrationState granted = new VoiceGuestRegistrationState(
                            VoiceGuestRegistrationStatus.CAPABILITY_GRANTED, context.tenantId(), created.id(),
                            context.providerCallId(), context.interactionId(), expiry, true,
                            state.capturedFirstName(), age, state.consentTimestamp(), state.consentGiven(),
                            state.consentSource());
                    v2VoiceAdapter.updateVoiceGuestRegistrationState(executionContext, granted);
                    log.info("VOICE_GUEST_REGISTRATION_COMPLETE callId={} tenantId={} interactionIdPresent={} capability=GUEST_BOOK_APPOINTMENT",
                            context.providerCallId(), context.tenantId(), context.interactionId() != null);
                    Map<String, Object> diagnostics = ageDiagnostics(state, transcript, age, true,
                            VoiceGuestRegistrationStatus.CAPABILITY_GRANTED);
                    diagnostics.put("duplicateRecheckResult", "ZERO_MATCHES_CREATED");
                    diagnostics.put("patientCreated", true);
                    return guestTurn(context, "GUEST_REGISTRATION_CREATED",
                            guestCreatedResponse(registrationLanguage(context, executionContext)), true, true,
                            diagnostics);
                } catch (PatientConflictException conflict) {
                    try {
                        List<PatientRecord> existingMatches = patientService.findActiveByTenantAndMobile(
                                context.tenantId(), normalizedMobile);
                        if (existingMatches.size() == 1) return existingPatientPath(context, executionContext, state,
                                policy, existingMatches.getFirst(), transcript, age);
                    } catch (RuntimeException ignored) {
                        // Fail closed below; neither a duplicate nor lookup error may create another patient.
                    }
                    v2VoiceAdapter.updateVoiceGuestRegistrationState(executionContext,
                            guestState(state, VoiceGuestRegistrationStatus.FAILED, null, null, false));
                    return guestTurn(context, "GUEST_DUPLICATE_CHECK_FAILED",
                            "I could not safely verify whether a patient record already exists. Please contact clinic reception.", false, false,
                            ageOutcomeDiagnostics(state, transcript, age, VoiceGuestRegistrationStatus.FAILED,
                                    "CREATE_CONFLICT_UNRESOLVED", false, false));
                } catch (RuntimeException failure) {
                    log.warn("VOICE_GUEST_REGISTRATION_FAILED callId={} tenantId={} reason={}",
                            context.providerCallId(), context.tenantId(), failure.getClass().getSimpleName());
                    v2VoiceAdapter.updateVoiceGuestRegistrationState(executionContext,
                            guestState(state, VoiceGuestRegistrationStatus.FAILED, null, null, false));
                    return guestTurn(context, "GUEST_REGISTRATION_FAILED",
                            "I cannot register the patient right now. Please contact clinic reception.", false, false,
                            ageOutcomeDiagnostics(state, transcript, age, VoiceGuestRegistrationStatus.FAILED,
                                    "CREATE_FAILED", false, false));
                }
            }
            case DECLINED -> {
                return guestTurn(context, "GUEST_REGISTRATION_DECLINED",
                        declinedRegistrationResponse(registrationLanguage(context, executionContext)), false, false);
            }
            default -> { return null; }
        }
    }

    private TurnResult existingPatientPath(Context context, AivaExecutionContext executionContext,
            VoiceGuestRegistrationState previous, VoiceTransactionAuthPolicy policy, PatientRecord patient,
            String ageTranscript, Integer age) {
        Instant expiry = Instant.now().plusSeconds(policy.guestBookingCapabilityTtlMinutes() * 60L);
        v2VoiceAdapter.updateVoiceGuestRegistrationState(executionContext,
                guestState(previous, VoiceGuestRegistrationStatus.CAPABILITY_GRANTED, patient.id(), expiry, true));
        return guestTurn(context, "EXISTING_PATIENT_REUSED",
                existingPatientFoundResponse(registrationLanguage(context, executionContext)), true, true,
                ageOutcomeDiagnostics(previous, ageTranscript, age, VoiceGuestRegistrationStatus.CAPABILITY_GRANTED,
                        "ONE_MATCH_REUSED", false, true));
    }

    private String registrationLanguage(Context context, AivaExecutionContext executionContext) {
        var mode = v2VoiceAdapter.presentationMode(executionContext);
        if (mode == com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoicePresentationMode.ENGLISH) {
            return "en";
        }
        if (mode == com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoicePresentationMode.HINGLISH) {
            return "hinglish";
        }
        if (mode == com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoicePresentationMode.HINDI) {
            return "hi";
        }
        return context.language();
    }

    private String existingPatientFoundResponse(String language) {
        if (language != null && language.toLowerCase(java.util.Locale.ROOT).startsWith("en")) {
            return "An existing patient profile matches this number. You can continue with a new appointment.";
        }
        return "इस नंबर से पहले से patient profile जुड़ी है। नया appointment book करने के लिए आगे बताइए।";
    }

    private boolean guestStateMatches(Context context, VoiceGuestRegistrationState state) {
        return state != null && java.util.Objects.equals(context.tenantId(), state.tenantId())
                && java.util.Objects.equals(context.providerCallId(), state.providerCallId())
                && java.util.Objects.equals(context.interactionId(), state.interactionId());
    }

    private VoiceGuestRegistrationState guestState(VoiceGuestRegistrationState prior,
            VoiceGuestRegistrationStatus status, UUID patientId, Instant expiry, boolean granted) {
        return new VoiceGuestRegistrationState(status, prior.tenantId(), patientId, prior.providerCallId(),
                prior.interactionId(), expiry, granted, prior.capturedFirstName(), prior.capturedAgeYears(),
                prior.consentTimestamp(), prior.consentGiven(), prior.consentSource());
    }

    private String guestMobile(String mobile) {
        if (!StringUtils.hasText(mobile)) return null;
        String normalized = mobile.trim().replaceAll("[\\s()\\-]", "");
        if (normalized.startsWith("+91") && normalized.length() == 13) normalized = normalized.substring(3);
        else if (normalized.startsWith("0091") && normalized.length() == 14) normalized = normalized.substring(4);
        return normalized.matches("[0-9]{10}") ? normalized : null;
    }

    private String parseGuestName(String transcript) {
        if (!StringUtils.hasText(transcript)) return null;
        String value = transcript.trim().replaceAll("(?iu)^(my name is|mera naam hai|मेरा नाम है)\\s+", "").trim();
        if (value.length() > 128 || !value.matches("[\\p{L}][\\p{L}\\p{M} .'-]*")) return null;
        return value;
    }

    private Integer parseGuestAge(String transcript) {
        if (!StringUtils.hasText(transcript)) return null;
        String normalized = java.text.Normalizer.normalize(transcript, java.text.Normalizer.Form.NFKC)
                .toLowerCase(java.util.Locale.ROOT).trim();
        // Preserve decimal separators so a non-whole age such as 20.5 cannot
        // become 205 when voice punctuation is normalized.
        if (normalized.matches(".*\\d\\s*[.,]\\s*\\d.*")) return null;
        StringBuilder ascii = new StringBuilder();
        normalized.codePoints().forEach(cp -> {
            int digit = Character.digit(cp, 10);
            ascii.append(digit >= 0 ? (char) ('0' + digit) : new String(Character.toChars(cp)));
        });
        normalized = ascii.toString().replaceAll("[!?;,:।]+", " ").replaceAll("\\.(?!\\d)", " ")
                .replaceAll("\\s+", " ").trim();
        java.util.regex.Matcher number = java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}])([0-9]{1,4})(?![\\p{L}\\p{N}])")
                .matcher(normalized);
        Integer value = null;
        if (number.find()) {
            value = Integer.valueOf(number.group(1));
            String residue = (normalized.substring(0, number.start()) + " " + normalized.substring(number.end()))
                    .replaceAll("(?iu)(?<![\\p{L}])(i am|i'm|my age is|years old|years|year|साल|वर्ष|मेरी उम्र|उम्र|है|हूँ|हूं|old)(?![\\p{L}])", " ")
                    .replaceAll("\\s+", " ").trim();
            if (!residue.isEmpty()) return null;
        } else {
            String words = normalized.replaceAll("(?iu)(?<![\\p{L}])(years old|years|year|साल|वर्ष|मेरी उम्र|उम्र|है|हूँ|हूं|old)(?![\\p{L}])", " ")
                    .replaceAll("\\s+", " ").trim();
            value = ageNumberWord(words);
        }
        return value != null && value >= 0 && value <= 120 ? value : null;
    }

    private Integer ageNumberWord(String text) {
        return switch (text) {
            case "zero", "न शून्य" -> 0;
            case "one", "एक" -> 1; case "two", "दो" -> 2; case "three", "तीन" -> 3;
            case "four", "चार" -> 4; case "five", "पाँच", "पांच" -> 5; case "six", "छह" -> 6;
            case "seven", "सात" -> 7; case "eight", "आठ" -> 8; case "nine", "नौ" -> 9;
            case "ten", "दस" -> 10; case "eleven", "ग्यारह" -> 11; case "twelve", "बारह" -> 12;
            case "thirteen", "तेरह" -> 13; case "fourteen", "चौदह" -> 14;
            case "fifteen", "पंद्रह", "पन्द्रह" -> 15; case "sixteen", "सोलह" -> 16;
            case "seventeen", "सत्रह" -> 17; case "eighteen", "अठारह" -> 18;
            case "nineteen", "उन्नीस" -> 19; case "twenty", "बीस" -> 20;
            case "twenty one", "इक्कीस" -> 21; case "twenty two", "बाईस" -> 22;
            case "twenty three", "तेईस" -> 23; case "twenty four", "चौबीस" -> 24;
            case "twenty five", "पच्चीस" -> 25; case "twenty six", "छब्बीस" -> 26;
            case "twenty seven", "सत्ताईस" -> 27; case "twenty eight", "अट्ठाईस", "अट्ठाइस" -> 28;
            case "twenty nine", "उनतीस" -> 29; case "thirty", "तीस" -> 30;
            default -> null;
        };
    }

    private Map<String, Object> ageDiagnostics(VoiceGuestRegistrationState before, String raw, Integer age,
            boolean valid, VoiceGuestRegistrationStatus after) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("guestRegistrationStateBefore", publicRegistrationState(before));
        diagnostics.put("ageRawRecognized", StringUtils.hasText(raw));
        diagnostics.put("ageParsed", age == null ? "NOT_PARSED" : age);
        diagnostics.put("ageValid", valid);
        diagnostics.put("capturedAgePresent", valid && age != null);
        diagnostics.put("guestRegistrationStateAfter", publicRegistrationState(after));
        diagnostics.put("duplicateRecheckResult", valid ? "PENDING" : "NOT_RUN");
        diagnostics.put("patientCreated", false);
        diagnostics.put("existingPatientReused", false);
        return diagnostics;
    }

    private Map<String, Object> ageOutcomeDiagnostics(VoiceGuestRegistrationState before, String raw, Integer age,
            VoiceGuestRegistrationStatus after, String duplicateResult, boolean created, boolean reused) {
        Map<String, Object> diagnostics = ageDiagnostics(before, raw, age, true, after);
        diagnostics.put("duplicateRecheckResult", duplicateResult);
        diagnostics.put("patientCreated", created);
        diagnostics.put("existingPatientReused", reused);
        return diagnostics;
    }

    private TurnResult guestTurn(Context context, String intent, String response,
            boolean capabilityGranted, boolean mutationBlocked) {
        return guestTurn(context, intent, response, capabilityGranted, mutationBlocked, Map.of());
    }

    private TurnResult guestTurn(Context context, String intent, String response,
            boolean capabilityGranted, boolean mutationBlocked, Map<String, Object> flowDiagnostics) {
        var presentationMode = v2VoiceAdapter.presentationMode(executionContext(context));
        String ttsLanguage = presentationMode == com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoicePresentationMode.ENGLISH
                ? "en-IN" : "hi-IN";
        VoiceGuestRegistrationState currentState = v2VoiceAdapter.voiceGuestRegistrationState(executionContext(context));
        Map<String, Object> registration = new LinkedHashMap<>();
        registration.put("state", publicRegistrationState(currentState));
        registration.put("intent", intent);
        registration.put("consentPending", currentState != null
                && currentState.status() == VoiceGuestRegistrationStatus.CONSENT_REQUESTED);
        registration.put("consent", currentState != null && currentState.consentGiven());
        if (currentState != null && currentState.consentSource() != null) {
            registration.put("consentSource", currentState.consentSource());
        }
        registration.put("consentTimestampPresent", currentState != null && currentState.consentTimestamp() != null);
        registration.put("patientCreated", capabilityGranted);
        registration.put("capability", capabilityGranted ? "BOOK_APPOINTMENT" : "NONE");
        registration.put("capabilityGranted", capabilityGranted);
        registration.put("namePromptIssued", "GUEST_REGISTRATION_CONSENTED".equals(intent)
                || "GUEST_COLLECTING_DETAILS".equals(intent));
        registration.put("mutationAllowed", false);
        registration.put("bookingContextPreserved", v2VoiceAdapter.hasVoiceBookingWorkflow(executionContext(context)));
        registration.put("authenticated", false);
        registration.put("otpVerified", false);
        registration.put("careSession", false);
        registration.put("restrictedBookingAllowed", capabilityGranted);
        registration.put("transactionExecutionEnabled", false);
        registration.put("phoneBindingVerified", false);
        registration.put("personalDataAllowed", false);
        registration.putAll(flowDiagnostics);
        Map<String, Object> diagnostics = Map.of(
                "presentationMode", presentationMode.name(),
                "ttsLanguage", ttsLanguage,
                "ttsTextLanguageProfile", presentationMode.name(),
                "guestRegistration", Map.copyOf(registration));
        return new TurnResult(response, intent, null, true, mutationBlocked, false,
                null, 0, null, false, diagnostics);
    }

    private Map<String, Object> consentDiagnostics(Context context, VoiceGuestRegistrationState before,
            VoiceGuestRegistrationStatus after, String decision, boolean consentPersisted,
            boolean namePromptIssued) {
        boolean accepted = "ACCEPTED".equals(decision);
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("guestRegistrationStateBefore", publicRegistrationState(before));
        diagnostics.put("guestRegistrationStateAfter", publicRegistrationState(after));
        diagnostics.put("consentPending", after == VoiceGuestRegistrationStatus.CONSENT_REQUESTED);
        diagnostics.put("consentDecision", decision);
        diagnostics.put("consentPersisted", consentPersisted);
        diagnostics.put("namePromptIssued", namePromptIssued);
        diagnostics.put("bookingContextPreserved", v2VoiceAdapter.hasVoiceBookingWorkflow(executionContext(context)));
        diagnostics.put("consent", accepted);
        if (accepted) diagnostics.put("consentSource", "VOICE");
        return diagnostics;
    }

    private String publicRegistrationState(VoiceGuestRegistrationState state) {
        if (state == null) return "NOT_STARTED";
        return publicRegistrationState(state.status());
    }

    private String publicRegistrationState(VoiceGuestRegistrationStatus state) {
        if (state == null) return "NOT_STARTED";
        return switch (state) {
            case COLLECTING_DETAILS -> "AWAIT_QUICK_REGISTRATION_NAME";
            case AWAITING_AGE -> "AWAIT_QUICK_REGISTRATION_AGE";
            case DUPLICATE_CHECK -> "DUPLICATE_RECHECK";
            case CAPABILITY_GRANTED, CREATED -> "REGISTERED_UNVERIFIED";
            default -> state.name();
        };
    }

    private String guestConsentClarification(String language) {
        if (english(language)) return "I’m not sure. Would you like me to create the basic registration so we can continue?";
        if (hinglish(language)) return "Mujhe clear nahi hua. Kya main appointment continue karne ke liye basic registration bana doon?";
        return "मुझे स्पष्ट नहीं हुआ। क्या मैं appointment जारी रखने के लिए basic registration बना दूँ?";
    }

    private String declinedRegistrationResponse(String language) {
        if (english(language)) return "Okay. I will not create a patient record. You can continue with general questions, but I cannot book this appointment through Voice without registration.";
        if (hinglish(language)) return "Theek hai, main patient record nahi banaunga. General questions pooch sakte hain, lekin registration ke bina Voice se appointment book nahi kar paunga.";
        return "ठीक है, मैं patient record नहीं बनाऊँगा। आप सामान्य जानकारी पूछ सकते हैं, लेकिन registration के बिना Voice से appointment book नहीं कर पाऊँगा।";
    }

    private String guestConsentPrompt(String language) {
        if (english(language)) return "I couldn't find a patient record for this number. May I create a basic registration so I can continue your appointment booking?";
        if (hinglish(language)) return "Is number se koi patient record nahi mila. Appointment book karne ke liye kya main aapka basic registration bana doon?";
        return "इस नंबर से कोई patient record नहीं मिला। Appointment book करने के लिए क्या मैं आपका basic registration बना दूँ?";
    }

    private String mobileConfirmationPrompt(String language) {
        if (english(language)) return "Please confirm that the mobile number you are calling from is correct for this patient record. Should I use it?";
        return "कृपया पुष्टि करें कि जिस mobile number से आप call कर रहे हैं, उसे patient record में इस्तेमाल करूँ?";
    }

    private String guestNamePrompt(String language) {
        if (english(language)) return "Please tell me your first name.";
        if (hinglish(language)) return "Please apna first name batayein.";
        return "कृपया अपना पहला नाम बताइए।";
    }

    private String guestAgePrompt(String language) {
        if (english(language)) return "About how old are you in years?";
        if (hinglish(language)) return "Aapki age lagbhag kitni hai?";
        return "आपकी उम्र लगभग कितनी है?";
    }

    private String guestAgeClarification(String language) {
        if (english(language)) return "Please tell me your approximate age as a whole number, from 0 to 120.";
        if (hinglish(language)) return "Please apni lagbhag age poore saal mein batayein.";
        return "कृपया अपनी लगभग उम्र पूरे सालों में बताइए।";
    }

    private String guestCreatedResponse(String language) {
        if (english(language)) return "Your name has been recorded. Please check it once at reception on your first clinic visit. I can continue with your appointment booking.";
        if (hinglish(language)) return "Aapka naam record ho gaya hai. Clinic pehli baar aane par reception par ek baar check kar lijiye. Ab appointment booking aage badhate hain.";
        return "आपका नाम रिकॉर्ड हो गया है। पहली बार क्लिनिक आने पर कृपया रिसेप्शन पर एक बार अपना नाम चेक कर लें। अब appointment booking आगे बढ़ाते हैं।";
    }

    private boolean english(String language) {
        return language != null && language.toLowerCase(java.util.Locale.ROOT).startsWith("en");
    }

    private boolean hinglish(String language) {
        return language != null && language.toUpperCase(java.util.Locale.ROOT).contains("HINGLISH");
    }


    private java.util.List<AivaV2VoiceToolExecutor.ProviderOption> providerOptions(
            HealthcareAiReadOnlyToolGateway.ToolResult result) {
        if (result.value() instanceof VoiceAvailabilityData availability && !availability.doctorCandidates().isEmpty()) {
            return availability.doctorCandidates().stream().map(doctor -> new AivaV2VoiceToolExecutor.ProviderOption(
                    doctor.doctorId(), doctor.displayName(), doctor.specialty(), null, null, null, null,
                    com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource.CARE_PRIVATE,
                    null, null)).toList();
        }
        if (result.value() instanceof VoiceAvailabilityData availability && availability.doctorId() != null) {
            return List.of(new AivaV2VoiceToolExecutor.ProviderOption(availability.doctorId(),
                    availability.doctorName(), null, null, null, null, null,
                    com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource.CARE_PRIVATE,
                    null, null));
        }
        if (!(result.value() instanceof java.util.List<?> values)) return java.util.List.of();
        return values.stream().filter(PublicDoctorSummaryResponse.class::isInstance)
                .map(PublicDoctorSummaryResponse.class::cast)
                .map(doctor -> new AivaV2VoiceToolExecutor.ProviderOption(
                        doctor.publicDoctorId(), doctor.doctorDisplayName(), doctor.speciality(), null, null,
                        doctor.clinicSlug(), doctor.clinicDisplayName(),
                        com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource.DISCOVER_PUBLIC,
                        new com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCapabilities(
                                doctor.canBookOnline(), doctor.canBookOnline(),
                                "CALL_TO_BOOK".equalsIgnoreCase(doctor.bookingMode()),
                                StringUtils.hasText(doctor.contactPhone()), StringUtils.hasText(doctor.publicPath()),
                                false, true), doctor.bookingReference()))
                .toList();
    }

    AvailabilityResult availabilityContext(HealthcareAiReadOnlyToolGateway.ToolResult result,
                                                   AivaV2VoiceToolExecutor.ToolInput input) {
        if (!(result.value() instanceof VoiceAvailabilityData data) || data.doctorId() == null) return null;
        List<AvailabilitySlot> slots = data.bookableSlots().stream().map(slot ->
                new AvailabilitySlot(null, slot.startTime(), slot.endTime(), displayTime(slot.startTime()))).toList();
        int offset = data.displayOffset();
        String daypart = input.daypart() == null ? null : input.daypart().name().toLowerCase(java.util.Locale.ROOT);
        boolean exactCandidate = input.availabilityTimeConstraint() != null
                && input.availabilityTimeConstraint().mode() == AvailabilityTimeConstraintMode.EXACT
                && result.diagnostics() != null && result.diagnostics().contains("exactMatchCount=1");
        String cursor = exactCandidate ? "VOICE_EXACT_SLOT_CANDIDATE"
                : data.queryType() == AivaV2VoiceToolExecutor.QueryType.RECURRING_SCHEDULE
                || data.queryType() == AivaV2VoiceToolExecutor.QueryType.EFFECTIVE_DATE_SCHEDULE
                ? data.configuredScheduleWindows().isEmpty() || data.unavailableForDate() ? "VOICE_CONTEXT"
                : daypart == null ? "VOICE_CONFIRM_SCHEDULE" : "VOICE_CONFIRM_SCHEDULE_DAYPART:" + input.daypart().name()
                : daypart == null && data.availableDayparts().size() > 1 ? "VOICE_PENDING_DAYPART"
                : daypart == null && data.availableDayparts().size() == 1
                ? "VOICE_CONFIRM_DAYPART:" + data.availableDayparts().getFirst().name()
                : daypart == null ? "VOICE_CONTEXT" : "VOICE_DAYPART:" + daypart.toUpperCase(java.util.Locale.ROOT);
        return new AvailabilityResult(UUID.randomUUID(), null, 0, "voice-read-only", data.doctorId(),
                data.doctorId(), null, data.date(), daypart, input.availabilityTimeConstraint(),
                input.timezone().getId(), slots, cursor,
                !exactCandidate && offset + 3 < slots.size(), Instant.now(), Instant.now().plusSeconds(5 * 60), offset, 3);
    }

    HealthcareAiReadOnlyToolGateway.ToolResult applyCanonicalSlotConstraint(String tool,
            HealthcareAiReadOnlyToolGateway.ToolResult result, AivaV2VoiceToolExecutor.ToolInput input) {
        AvailabilityTimeConstraint constraint = input.availabilityTimeConstraint();
        if (!"FIND_SLOTS".equals(tool) || constraint == null
                || !(result.value() instanceof VoiceAvailabilityData data)) return result;

        List<VoiceSlot> canonical = data.bookableSlots();
        List<VoiceSlot> matching;
        List<VoiceSlot> alternatives = List.of();
        if (constraint.mode() == AvailabilityTimeConstraintMode.EXACT) {
            matching = canonical.stream().filter(slot -> constraint.startTime().equals(slot.startTime())).toList();
            if (matching.isEmpty()) {
                alternatives = canonical.stream().sorted(java.util.Comparator
                        .comparingLong((VoiceSlot slot) -> Math.abs(java.time.Duration.between(
                                constraint.startTime(), slot.startTime()).toMinutes()))
                        .thenComparing(VoiceSlot::startTime)).limit(3).toList();
                matching = alternatives;
            }
        } else if (constraint.mode() == AvailabilityTimeConstraintMode.BETWEEN) {
            matching = canonical.stream().filter(slot -> !slot.startTime().isBefore(constraint.startTime())
                    && !slot.startTime().isAfter(constraint.endTime())).toList();
        } else {
            return result;
        }

        int exactMatchCount = constraint.mode() == AvailabilityTimeConstraintMode.EXACT
                ? (alternatives.isEmpty() ? matching.size() : 0) : -1;
        List<VoiceSlot> filtered = constraint.mode() == AvailabilityTimeConstraintMode.EXACT
                && exactMatchCount > 1 ? List.of() : matching;
        int offset = exactMatchCount == 0 || exactMatchCount > 1 ? 0 : data.displayOffset();
        VoiceAvailabilityData filteredData = new VoiceAvailabilityData(data.doctorId(), data.doctorName(),
                data.queryType(), data.date(), data.requestedDaypart(), data.configuredScheduleWindows(),
                filtered, filtered.isEmpty() ? List.of() : data.availableDayparts(), data.doctorCandidates(),
                data.ambiguousDoctor(), offset, data.unavailableForDate(), data.onlyPastSlots(), data.fullSlotsOnly());
        String matched = exactMatchCount == 1 ? filtered.getFirst().date() + "T" + filtered.getFirst().startTime() : "";
        String alternativeTimes = alternatives.stream().map(slot -> slot.startTime().toString()).toList().toString();
        String diagnostic = (result.diagnostics() == null ? "" : result.diagnostics() + "; ")
                + "slotSelectionSource=" + (constraint.mode() == AvailabilityTimeConstraintMode.EXACT
                ? "EXACT_TIME" : "TIME_RANGE") + "; exactMatchCount=" + exactMatchCount
                + "; exactSlotMatch=" + (exactMatchCount == 1) + "; matchedCanonicalSlot=" + matched
                + "; alternativeSlots=" + alternativeTimes;
        return new HealthcareAiReadOnlyToolGateway.ToolResult(result.tool(), result.allowed(), result.success(),
                filteredData, filtered.size(), result.sourceCandidateCount(), diagnostic);
    }

    private String render(String tool, HealthcareAiReadOnlyToolGateway.ToolResult result, String speciality,
                          com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn turn,
                          ZoneId timezone, AivaV2VoiceToolExecutor.ToolInput input) {
        if (result.value() instanceof VoiceAvailabilityData availability) {
            logScheduleTrace(availability, Math.max(0, Math.min(3,
                    availability.bookableSlots().size() - availability.displayOffset())));
            String language = turn == null ? "hi-IN" : turn.responseLanguage();
            String timeFilteredResponse = renderTimeFilteredSlots(result, availability, input, language,
                    turn == null ? null : turn.responseStyle(), timezone);
            if (timeFilteredResponse != null) return timeFilteredResponse;
            return renderAvailability(availability, language, turn == null ? null : turn.responseStyle(), timezone);
        }
        if (result.candidateCount() == 0) {
            if ("FIND_DOCTOR".equals(tool) && turn != null
                    && turn.intent() == com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn.Intent.CONSULTATION_REQUEST) {
                return doctorResponse(result, speciality, turn, input == null ? null : input.date());
            }
            if ("FIND_DOCTOR".equals(tool) && StringUtils.hasText(speciality)) {
                return "क्षमा कीजिए, इस विशेषज्ञता के लिए कोई डॉक्टर नहीं मिला।";
            }
            return "क्षमा कीजिए, मुझे अभी कोई जानकारी नहीं मिली।";
        }
        return switch (tool) {
            case "FIND_DOCTOR" -> doctorResponse(result, speciality, turn,
                    input == null ? null : input.date());
            case "FIND_CLINIC" -> "मुझे " + result.candidateCount() + " क्लिनिक मिले हैं।";
            case "FIND_SERVICE" -> "यह सेवा उपलब्ध है।";
            case "FIND_SLOTS" -> "" + result.candidateCount() + " उपलब्ध समय मिले हैं।";
            default -> FAILURE_RESPONSE;
        };
    }

    String renderTimeFilteredSlots(HealthcareAiReadOnlyToolGateway.ToolResult result,
            VoiceAvailabilityData data, AivaV2VoiceToolExecutor.ToolInput input, String language,
            com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle style, ZoneId timezone) {
        if (input == null || input.availabilityTimeConstraint() == null) return null;
        AvailabilityTimeConstraint constraint = input.availabilityTimeConstraint();
        boolean english = language != null && language.toLowerCase(java.util.Locale.ROOT).startsWith("en");
        boolean hinglish = !english && style == com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.HINGLISH;
        String count = result.diagnostics() == null ? "" : result.diagnostics();
        int exactMatchCount = diagnosticInt(count, "exactMatchCount", -1);
        if (constraint.mode() == AvailabilityTimeConstraintMode.EXACT) {
            if (exactMatchCount > 1) return english
                    ? "More than one appointment option matches that time. Which clinic or location do you mean?"
                    : hinglish ? "Is time par ek se zyada slot match ho rahe hain. Aap kis clinic ya location ki baat kar rahe hain?"
                    : "इस समय पर एक से अधिक slots मिले हैं। कृपया बताइए, आप किस clinic या location की बात कर रहे हैं?";
            String requestedTime = spokenExactTime(constraint.startTime(), english);
            String date = data.date() == null ? "" : datePrefix(data.date(), timezone, !english, hinglish);
            String period = data.requestedDaypart() == null ? "" : daypartName(data.requestedDaypart(), !english, hinglish) + " ";
            if (exactMatchCount == 1 && !data.bookableSlots().isEmpty()) return english
                    ? date + period + requestedTime + " slot is available."
                    : hinglish ? date + period + requestedTime + " ka slot available hai."
                    : date + period + requestedTime + " बजे का slot उपलब्ध है।";
            List<VoiceSlot> alternatives = data.bookableSlots().stream().limit(3).toList();
            if (alternatives.isEmpty()) return english
                    ? date + "The requested " + requestedTime + " slot is not available, and there are no nearby alternatives."
                    : hinglish ? date + requestedTime + " ka slot available nahi hai, aur nearby alternatives nahi mile."
                    : date + requestedTime + " बजे का slot उपलब्ध नहीं है और पास के दूसरे समय भी नहीं मिले।";
            String times = alternatives.stream().map(slot -> slotVoiceTime(slot.startTime(), !english, hinglish))
                    .reduce((left, right) -> left + (english ? ", " : hinglish ? " aur " : " और ") + right).orElse("");
            return english ? date + requestedTime + " is not available. Nearby options are " + times + "."
                    : hinglish ? date + requestedTime + " ka slot available nahi hai. Nearby slots " + times + " hain."
                    : date + requestedTime + " बजे का slot उपलब्ध नहीं है। पास के slots " + times + " उपलब्ध हैं।";
        }
        if (constraint.mode() == AvailabilityTimeConstraintMode.BETWEEN && data.bookableSlots().isEmpty()) {
            String from = spokenExactTime(constraint.startTime(), english);
            String to = spokenExactTime(constraint.endTime(), english);
            return english ? "No appointment slots are available between " + from + " and " + to + "."
                    : hinglish ? from + " se " + to + " ke beech koi appointment slot available nahi hai."
                    : from + " से " + to + " बजे के बीच कोई appointment slot उपलब्ध नहीं है।";
        }
        return null;
    }

    private int diagnosticInt(String diagnostics, String name, int fallback) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:^|;\\s*)" + name + "=(\\d+)")
                .matcher(diagnostics);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private String spokenExactTime(LocalTime time, boolean english) {
        if (time == null) return "";
        if (english) return time.getHour() % 12 == 0 ? "12" + (time.getMinute() == 0 ? "" : ":" + String.format("%02d", time.getMinute()))
                + (time.getHour() < 12 ? " AM" : " PM")
                : (time.getHour() % 12) + (time.getMinute() == 0 ? "" : ":" + String.format("%02d", time.getMinute()))
                + (time.getHour() < 12 ? " AM" : " PM");
        int hour = time.getHour() % 12 == 0 ? 12 : time.getHour() % 12;
        return hour + (time.getMinute() == 0 ? "" : ":" + String.format("%02d", time.getMinute()));
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
        String times = page.stream().map(slot -> slotVoiceTime(slot.startTime(), hindi, hinglish))
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

    private String slotVoiceTime(java.time.LocalTime time, boolean hindi, boolean hinglish) {
        var mode = !hindi ? com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoicePresentationMode.ENGLISH
                : hinglish ? com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoicePresentationMode.HINGLISH
                : com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoicePresentationMode.HINDI;
        return new com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2VoiceTimePresentationFormatter()
                .format(time, mode);
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
        return AivaSpokenDateFormatter.format(date, hindi ? "hi" : "en",
                hinglish ? com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.HINGLISH
                        : com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.STANDARD) + " ";
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
                                  com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn turn,
                                  java.time.LocalDate availabilityDate) {
        String responseLanguage = turn == null ? "hi" : turn.responseLanguage();
        boolean english = responseLanguage != null && responseLanguage.toLowerCase(java.util.Locale.ROOT).startsWith("en");
        boolean hinglish = !english && turn != null
                && turn.responseStyle() == com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.HINGLISH;
        String datePrefix = doctorAvailabilityDatePrefix(turn, result, english, hinglish, availabilityDate);
        if (turn != null && turn.intent()
                == com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn.Intent.CONSULTATION_REQUEST) {
            return consultationResponse(result, speciality, datePrefix, english, hinglish);
        }
        if (result.candidateCount() == 0 && result.sourceCandidateCount() > 0
                && result.diagnostics() != null && result.diagnostics().contains("availabilityDate=")) {
            java.time.LocalDate requestedDate = availabilityDate != null ? availabilityDate
                    : turn == null || turn.temporal() == null ? null : turn.temporal().localDate();
            if (requestedDate == null && result.diagnostics() != null) {
                java.util.regex.Matcher dateMatch = java.util.regex.Pattern.compile("(?:^|;\\s*)availabilityDate=([0-9]{4}-[0-9]{2}-[0-9]{2})")
                        .matcher(result.diagnostics());
                if (dateMatch.find()) requestedDate = java.time.LocalDate.parse(dateMatch.group(1));
            }
            String date = requestedDate == null ? ""
                    : com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaSpokenDateFormatter.format(
                            requestedDate, turn == null ? "hi" : turn.responseLanguage(),
                            turn == null ? com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.STANDARD
                                    : turn.responseStyle());
            return english ? "No " + (StringUtils.hasText(speciality) ? englishSpecialty(speciality) + "s" : "doctors")
                    + " are available" + (date.isBlank() ? "." : " on " + date + ".")
                    : hinglish ? datePrefix + "koi "
                    + (StringUtils.hasText(speciality) ? englishSpecialty(speciality) : "doctor") + " available nahi hain."
                    : datePrefix + "कोई डॉक्टर उपलब्ध नहीं हैं।";
        }
        if (result.value() instanceof java.util.List<?> doctors && !doctors.isEmpty()
                && doctors.getFirst() instanceof PublicDoctorSummaryResponse doctor) {
            String doctorName = voiceDoctorDisplayName(doctor.doctorDisplayName());
            String spokenDoctorName = voiceDoctorSpeechName(doctorName, hinglish);
            if (result.candidateCount() == 1) {
                if (english) return datePrefix + (StringUtils.hasText(speciality)
                        ? "Dr. " + doctorName + " is available as a "
                        + englishSpecialty(speciality) + "."
                        : "Dr. " + doctorName + " is available.");
                if (hinglish) return datePrefix + (StringUtils.hasText(speciality)
                        ? "Dr. " + doctorName + " " + englishSpecialty(speciality)
                        + " doctor ke roop mein available hain."
                        : "Dr. " + doctorName + " available hain.");
                String role = "Pediatrics".equalsIgnoreCase(speciality)
                        ? "बाल रोग विशेषज्ञ" : "General Medicine".equalsIgnoreCase(speciality)
                        ? "सामान्य चिकित्सा विशेषज्ञ" : speciality + " विशेषज्ञ";
                if (!StringUtils.hasText(speciality)) {
                    return datePrefix + "डॉक्टर " + spokenDoctorName + " उपलब्ध हैं।";
                }
                return datePrefix + "डॉक्टर " + spokenDoctorName + " " + role + " के रूप में उपलब्ध हैं।";
            }
            String names = doctors.stream()
                    .limit(3)
                    .filter(PublicDoctorSummaryResponse.class::isInstance)
                    .map(PublicDoctorSummaryResponse.class::cast)
                    .map(item -> voiceDoctorDisplayName(item.doctorDisplayName()))
                    .reduce((left, right) -> left + " और " + right)
                    .orElse(null);
            if (english && StringUtils.hasText(names)) {
                String englishNames = doctors.stream().limit(3)
                        .filter(PublicDoctorSummaryResponse.class::isInstance)
                        .map(PublicDoctorSummaryResponse.class::cast)
                        .map(PublicDoctorSummaryResponse::doctorDisplayName)
                        .reduce((left, right) -> left + " and " + right).orElse(names);
                return datePrefix + "available doctors include " + englishNames + ".";
            }
            if (hinglish && StringUtils.hasText(names)) return datePrefix + "available doctors hain: "
                    + names.replace(" और ", " aur ") + ".";
            if (StringUtils.hasText(names)) {
                String localizedNames = doctors.stream().limit(3).filter(PublicDoctorSummaryResponse.class::isInstance)
                        .map(PublicDoctorSummaryResponse.class::cast)
                        .map(item -> voiceDoctorSpeechName(item.doctorDisplayName(), false))
                        .reduce((left, right) -> left + " और " + right).orElse(names);
                String specialtyText = "Pediatrics".equalsIgnoreCase(speciality) ? " बाल रोग विशेषज्ञ" :
                        "General Medicine".equalsIgnoreCase(speciality) ? " सामान्य चिकित्सा विशेषज्ञ" : "";
                return datePrefix + "उपलब्ध डॉक्टर हैं: " + localizedNames + specialtyText + "।";
            }
        }
        return "मुझे " + Math.min(result.candidateCount(), 3) + " डॉक्टर मिले हैं। कृपया बताइए, क्या मैं उपलब्ध समय भी देखूँ?";
    }

    private String consultationResponse(HealthcareAiReadOnlyToolGateway.ToolResult result, String speciality,
                                        String datePrefix, boolean english, boolean hinglish) {
        java.util.List<PublicDoctorSummaryResponse> doctors = result.value() instanceof java.util.List<?> values
                ? values.stream().filter(PublicDoctorSummaryResponse.class::isInstance)
                .map(PublicDoctorSummaryResponse.class::cast).toList() : java.util.List.of();
        if (doctors.isEmpty()) return english ? datePrefix + "I could not find an available doctor. Would you like to try another specialty?"
                : hinglish ? datePrefix + "Abhi koi doctor available nahi mila. Kya aap koi aur specialty try karna chahenge?"
                : datePrefix + "अभी कोई डॉक्टर उपलब्ध नहीं मिला। क्या आप कोई दूसरी specialty देखना चाहेंगे?";
        java.util.List<String> specialties = doctors.stream().map(PublicDoctorSummaryResponse::speciality)
                .filter(StringUtils::hasText).map(String::trim).distinct().limit(3).toList();
        if (!StringUtils.hasText(speciality) && specialties.size() > 1) {
            String choices = specialties.stream().map(value -> consultationSpecialty(value, english, hinglish))
                    .reduce((left, right) -> left + (english ? " or " : hinglish ? " ya " : " या ") + right)
                    .orElse("");
            return english ? datePrefix + "Which type of doctor would you like to consult—" + choices + "?"
                    : hinglish ? datePrefix + "Aap kis specialty ke doctor se consult karna chahte hain—" + choices + "?"
                    : datePrefix + "आप किस प्रकार के डॉक्टर से मिलना चाहते हैं—" + choices + "?";
        }
        if (doctors.size() > 1) {
            String names = doctors.stream().limit(3).map(PublicDoctorSummaryResponse::doctorDisplayName)
                    .reduce((left, right) -> left + (english ? " and " : hinglish ? " aur " : " और ") + right)
                    .orElse("");
            return english ? datePrefix + "There are several " + (StringUtils.hasText(speciality) ? englishSpecialty(speciality) : "")
                    + " doctors available: " + names + ". Which doctor would you prefer?"
                    : hinglish ? datePrefix + (StringUtils.hasText(speciality) ? englishSpecialty(speciality) + " mein " : "")
                    + "doctors available hain: " + names + ". Aap kis doctor se consult karna chahenge?"
                    : datePrefix + (StringUtils.hasText(speciality) ? consultationSpecialty(speciality, false, false) + " mein " : "")
                    + "kai doctor available hain: " + names + "। Aap kis doctor se milna chahenge?";
        }
        String name = voiceDoctorDisplayName(doctors.getFirst().doctorDisplayName());
        String spokenName = voiceDoctorSpeechName(name, hinglish);
        String specialtyValue = StringUtils.hasText(speciality) ? speciality : doctors.getFirst().speciality();
        if (english) return datePrefix + "Dr. " + name + " is available. Would you like me to check the available slots?";
        if (hinglish) return datePrefix + "Doctor " + name + " available hain. Kya aap unke slots dekhna chahenge?";
        return datePrefix + "डॉक्टर " + spokenName + " उपलब्ध हैं। क्या आप उनके उपलब्ध समय देखना चाहेंगे?";
    }

    private String consultationSpecialty(String value, boolean english, boolean hinglish) {
        if (english || hinglish) {
            if ("General Medicine".equalsIgnoreCase(value)) return "General Medicine";
            if ("Pediatrics".equalsIgnoreCase(value)) return "Pediatrics";
            return value;
        }
        if ("General Medicine".equalsIgnoreCase(value)) return "जनरल मेडिसिन";
        if ("Pediatrics".equalsIgnoreCase(value)) return "बच्चों के डॉक्टर";
        return value;
    }

    private String doctorAvailabilityDatePrefix(
            com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn turn,
            HealthcareAiReadOnlyToolGateway.ToolResult result, boolean english, boolean hinglish,
            java.time.LocalDate requestedDate) {
        java.time.LocalDate dateValue = requestedDate != null ? requestedDate : turn == null || turn.temporal() == null
                ? null : turn.temporal().localDate();
        if (dateValue == null && result != null && result.diagnostics() != null) {
            java.util.regex.Matcher dateMatch = java.util.regex.Pattern.compile("(?:^|;\\s*)availabilityDate=([0-9]{4}-[0-9]{2}-[0-9]{2})")
                    .matcher(result.diagnostics());
            if (dateMatch.find()) dateValue = java.time.LocalDate.parse(dateMatch.group(1));
        }
        if (dateValue == null) return "";
        String date = com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaSpokenDateFormatter.format(
                dateValue, turn == null ? "hi" : turn.responseLanguage(),
                turn == null ? com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.STANDARD
                        : turn.responseStyle());
        return english ? "On " + date + ", " : hinglish ? date + " ko " : date + " को ";
    }

    private String englishSpecialty(String specialty) {
        return "Pediatrics".equalsIgnoreCase(specialty) ? "pediatrician" : specialty.toLowerCase(java.util.Locale.ROOT) + " specialist";
    }

    private String voiceDoctorSpeechName(String name, boolean hinglish) {
        if (!StringUtils.hasText(name)) return name;
        String displayName = voiceDoctorDisplayName(name);
        if (hinglish) return displayName;
        return switch (displayName.toLowerCase(java.util.Locale.ROOT)) {
            case "amit verma" -> "अमित वर्मा";
            case "ravi mehta" -> "रवि मेहता";
            case "ravi sharma" -> "रवि शर्मा";
            case "pooja rao" -> "पूजा राव";
            default -> displayName;
        };
    }

    private String voiceDoctorDisplayName(String name) {
        if (!StringUtils.hasText(name)) return name;
        return name.trim().replaceFirst("(?iu)^(?:dr\\.?|doctor)\\s+", "").trim();
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

    public boolean isAwaitingOtp(Context context) {
        return false;
    }

    public VoiceIdentityTrust assessIdentityTrust(UUID tenantId, UUID clinicId, UUID patientId,
            String inboundMobile, String providerCallId) {
        if (patientId == null) return VoiceIdentityTrust.guest();
        return VoiceIdentityTrust.registeredUnverified();
    }

    private AivaExecutionContext executionContext(Context context) {
        ZoneId zone = timeZoneResolver == null ? ZoneId.of("Asia/Kolkata") : timeZoneResolver.resolve(context.tenantId());
        AivaExecutionContext base = new AivaExecutionContext(context.tenantId(), context.identifiedPatientId(), "VOICE",
                context.language(), zone, context.providerCallId(), context.interactionId(), context.clinicId(),
                context.locationId(), HealthcareAiReadOnlyToolGateway.VOICE_READ_ONLY_ALLOWED_TOOLS,
                context.providerCallId(), context.interactionId() == null ? context.providerCallId()
                : context.interactionId().toString(), AivaTransactionAuthorization.unauthenticated(context.tenantId(),
                context.providerCallId(), context.identifiedPatientId()));
        return new AivaExecutionContext(base.tenantId(), base.identifiedPatientId(), base.channel(), base.language(),
                base.timezone(), base.sessionId(), base.interactionId(), base.clinicId(), base.locationId(),
                base.capabilities(), base.providerCallId(), base.correlationId(), v2VoiceAdapter.currentVoiceAuthorization(base));
    }

    private boolean isExplicitYes(String input) {
        String text = normalizeConsentText(input);
        if (text.isEmpty()) return false;
        if (isExplicitNo(input)) return false;
        return java.util.Set.of("yes", "yes please", "yeah", "yeah please", "yep", "yep please", "sure",
                "okay", "ok", "go ahead", "please do", "create it", "हाँ", "हां", "जी हाँ", "जी हां",
                "हाँ जी", "हां जी", "कर दीजिए", "कर दीजिये", "रजिस्ट्रेशन कर दीजिए",
                "रजिस्ट्रेशन कर दीजिये", "registration कर दीजिए", "registration कर दीजिये",
                "हाँ रजिस्ट्रेशन कर दीजिए", "हां रजिस्ट्रेशन कर दीजिए",
                "हाँ registration कर दीजिए", "हां registration कर दीजिए", "बना दीजिए", "बना दीजिये",
                "yes कर दीजिए", "yes कर दीजिये", "haan", "haan please", "ji haan",
                "kar dijiye", "registration kar dijiye", "haan registration kar dijiye",
                "bana dijiye", "create it please", "yes please create", "yes create it", "yes please do",
                "go ahead and create it", "please create the registration", "please create registration")
                .contains(text);
    }

    private boolean isExplicitNo(String input) {
        String text = normalizeConsentText(input);
        return java.util.Set.of("no", "no thanks", "no thank you", "nah", "don't create", "do not create",
                "no please don't", "no please do not", "no don't create", "नहीं", "नही", "नहीं चाहिए",
                "नही चाहिए", "मत बनाइए", "मत बनाईए", "nahi", "nahi chahiye", "mat banaiye",
                "mat banayiye").contains(text);
    }

    private String normalizeConsentText(String input) {
        if (input == null) return "";
        return java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFKC)
                .toLowerCase(java.util.Locale.ROOT).replace('’', '\'')
                .replaceAll("[,;.!?।]+", " ").replaceAll("\\s+", " ").trim();
    }

    private Map<String, Object> policyDiagnostics(VoiceTransactionAuthPolicy policy, String decision) {
        if (policy == null) return Map.of();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("effectiveScope", policy.effectiveScope());
        boolean newBooking = policy.requestedOperation()
                == VoiceTransactionAuthPolicy.Operation.BOOK_APPOINTMENT;
        details.put("configuredAuthenticationMode", policy.authenticationMode().name());
        details.put("configuredOtpRequired", policy.otpRequired());
        details.put("authenticationMode", newBooking ? "NOT_REQUIRED_FOR_NEW_BOOKING"
                : "UNAVAILABLE_NO_VOICE_OTP");
        details.put("trustMode", policy.trustMode() == null ? "DISABLED" : policy.trustMode().name());
        details.put("authRequired", policy.authRequired());
        details.put("authTtlMinutes", policy.authTtlMinutes());
        details.put("requestedOperation", policy.requestedOperation() == null ? null
                : policy.requestedOperation().name());
        details.put("transactionFeatureEnabled", policy.transactionFeatureEnabled());
        details.put("bookingEnabled", policy.bookingEnabled());
        details.put("otpRequired", false);
        details.put("effectiveOtpChallengeRequired", false);
        details.put("bookingExecutionAllowed", policy.bookingExecutionAllowed());
        details.put("guestBookingEnabled", policy.guestBookingEnabled());
        details.put("voiceQuickRegistrationEnabled", policy.voiceQuickRegistrationEnabled());
        details.put("minimalRegistrationEnabled", policy.minimalRegistrationEnabled());
        details.put("guestBookingRequiresMobileConfirmation", policy.guestBookingRequiresMobileConfirmation());
        details.put("guestBookingCapabilityTtlMinutes", policy.guestBookingCapabilityTtlMinutes());
        details.put("testBypassActive", false);
        details.put("decision", decision == null ? policy.decision() : decision);
        details.entrySet().removeIf(entry -> entry.getValue() == null);
        return Map.copyOf(details);
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
            UUID identifiedPatientId,
            String registeredMobile,
            VoiceIdentityTrust identityTrust
    ) {
        public Context(String providerCallId, UUID interactionId, UUID tenantId, UUID communicationChannelId,
                UUID clinicId, UUID locationId, String language, String patientIdentificationStatus,
                String conversationPartyType, UUID identifiedPatientId) {
            this(providerCallId, interactionId, tenantId, communicationChannelId, clinicId, locationId, language,
                    patientIdentificationStatus, conversationPartyType, identifiedPatientId, null,
                    identifiedPatientId == null ? VoiceIdentityTrust.guest() : VoiceIdentityTrust.registeredUnverified());
        }
        public Context(String providerCallId, UUID interactionId, UUID tenantId, UUID communicationChannelId,
                UUID clinicId, UUID locationId, String language, String patientIdentificationStatus,
                String conversationPartyType, UUID identifiedPatientId, String registeredMobile) {
            this(providerCallId, interactionId, tenantId, communicationChannelId, clinicId, locationId, language,
                    patientIdentificationStatus, conversationPartyType, identifiedPatientId, registeredMobile,
                    identifiedPatientId == null ? VoiceIdentityTrust.guest() : VoiceIdentityTrust.registeredUnverified());
        }
        public Context {
            if (!"SINGLE_MATCH".equals(patientIdentificationStatus)) identifiedPatientId = null;
            if (conversationPartyType == null) conversationPartyType = "GUEST_CALLER";
            if (identityTrust == null) identityTrust = identifiedPatientId == null
                    ? VoiceIdentityTrust.guest() : VoiceIdentityTrust.registeredUnverified();
            if (identifiedPatientId == null && identityTrust.identityState() != VoiceIdentityTrust.IdentityState.GUEST)
                throw new IllegalArgumentException("A registered Voice trust state requires a resolved patient");
            if (identityTrust.identityState() == VoiceIdentityTrust.IdentityState.REGISTERED_PHONE_VERIFIED
                    && !identityTrust.matchesCall(tenantId, identifiedPatientId, registeredMobile, providerCallId))
                throw new IllegalArgumentException("Preverified phone trust does not match this Voice call context");
        }
    }

    public record TurnResult(String response, String intent, String tool, boolean aivaUsed,
                             boolean mutationBlocked, boolean toolExecuted, String toolInputSpeciality,
                             int toolResultCount, String toolResultSummary, boolean closing,
                             Map<String, Object> runtimeDiagnostics) {
        public TurnResult(String response, String intent, String tool, boolean aivaUsed,
                          boolean mutationBlocked, boolean toolExecuted) {
            this(response, intent, tool, aivaUsed, mutationBlocked, toolExecuted, null, 0, null, false, Map.of());
        }

        public TurnResult(String response, String intent, String tool, boolean aivaUsed,
                          boolean mutationBlocked, boolean toolExecuted, String toolInputSpeciality,
                          int toolResultCount, String toolResultSummary) {
            this(response, intent, tool, aivaUsed, mutationBlocked, toolExecuted,
                    toolInputSpeciality, toolResultCount, toolResultSummary, false, Map.of());
        }
        public TurnResult(String response, String intent, String tool, boolean aivaUsed,
                          boolean mutationBlocked, boolean toolExecuted, String toolInputSpeciality,
                          int toolResultCount, String toolResultSummary, boolean closing) {
            this(response, intent, tool, aivaUsed, mutationBlocked, toolExecuted,
                    toolInputSpeciality, toolResultCount, toolResultSummary, closing, Map.of());
        }
        public TurnResult {
            runtimeDiagnostics = runtimeDiagnostics == null ? Map.of() : Map.copyOf(runtimeDiagnostics);
        }
        public String policyDecision() {
            Object effective = runtimeDiagnostics.get("effectiveAuthorizationDecision");
            if (effective != null) return String.valueOf(effective);
            Object mode = runtimeDiagnostics.get("transactionMode");
            if (mode != null) return String.valueOf(mode);
            return mutationBlocked ? "DENIED" : "READ_ONLY_ALLOWED";
        }

        public String toolExecutionStatus() {
            if (mutationBlocked) return "BLOCKED";
            if (tool == null) return "NOT_EXECUTED";
            return toolExecuted ? "EXECUTED" : "FAILED";
        }
    }

}
