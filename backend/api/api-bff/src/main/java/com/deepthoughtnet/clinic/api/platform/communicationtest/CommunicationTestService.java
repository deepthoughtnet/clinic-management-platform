package com.deepthoughtnet.clinic.api.platform.communicationtest;

import com.deepthoughtnet.clinic.api.carepilot.CarePilotMessagingStatusService;
import com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderStatusResponse;
import com.deepthoughtnet.clinic.carepilot.messaging.service.MessageOrchestratorService;
import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageRecipient;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import com.deepthoughtnet.clinic.messaging.email.Msg91EmailMessageProvider;
import com.deepthoughtnet.clinic.carepilot.ai_call.provider.DotVoiceMediaStreamClient;
import com.deepthoughtnet.clinic.carepilot.ai_call.provider.G711MuLawAudioConverter;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.VoiceConversationState;
import com.deepthoughtnet.clinic.carepilot.ai_call.service.VoiceConversationContextRegistry;
import com.deepthoughtnet.clinic.carepilot.ai_call.service.VoiceResponseInterpreter;
import com.deepthoughtnet.clinic.communication.service.CommunicationChannelResolver;
import com.deepthoughtnet.clinic.communication.service.CommunicationChannelModels.Resolution;
import com.deepthoughtnet.clinic.communication.service.PhoneAddressNormalizer;
import com.deepthoughtnet.clinic.communication.service.CommunicationInteractionService;
import com.deepthoughtnet.clinic.communication.model.ConversationPartyType;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.api.voice.VoiceOrchestratorService;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionResult;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallProvider;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallRequest;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.deepthoughtnet.clinic.api.platform.communicationtest.CommunicationTestDtos.*;

@Service
public class CommunicationTestService {
    private static final Logger log = LoggerFactory.getLogger(CommunicationTestService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9][0-9()\\-\\s]{6,19}$");

    private final CarePilotMessagingStatusService messagingStatusService;
    private final MessageOrchestratorService messageOrchestratorService;
    private final List<MessageProvider> messageProviders;
    private final List<VoiceCallProvider> voiceProviders;
    private final Msg91EmailMessageProvider msg91EmailMessageProvider;
    private final ReminderVoiceRenderer reminderVoiceRenderer;
    private final DotVoiceMediaStreamClient dotVoiceMediaStreamClient;
    private final VoiceOrchestratorService voiceOrchestratorService;
    private final VoiceConversationContextRegistry contextRegistry;
    private final VoiceResponseInterpreter responseInterpreter;
    private final CommunicationChannelResolver communicationChannelResolver;
    private final PatientService patientService;
    private final CommunicationInteractionService interactionService;
    private VoiceReadOnlyAivaService voiceReadOnlyAivaService;
    private final ConcurrentHashMap<String, Result> voiceResults = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, InboundVoiceResult> inboundVoiceResults = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> inboundCaptureStarted = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> inboundTurnCounts = new ConcurrentHashMap<>();

    public CommunicationTestService(
            CarePilotMessagingStatusService messagingStatusService,
            MessageOrchestratorService messageOrchestratorService,
            List<MessageProvider> messageProviders,
            List<VoiceCallProvider> voiceProviders
    ) {
        this(messagingStatusService, messageOrchestratorService, messageProviders, voiceProviders, null, null, null, null, null, null, null);
    }

    public CommunicationTestService(
            CarePilotMessagingStatusService messagingStatusService,
            MessageOrchestratorService messageOrchestratorService,
            List<MessageProvider> messageProviders,
            List<VoiceCallProvider> voiceProviders,
            Msg91EmailMessageProvider msg91EmailMessageProvider
    ) {
        this(messagingStatusService, messageOrchestratorService, messageProviders, voiceProviders,
                msg91EmailMessageProvider, null, null, null, null, null, null);
    }

    public CommunicationTestService(
            CarePilotMessagingStatusService messagingStatusService,
            MessageOrchestratorService messageOrchestratorService,
            List<MessageProvider> messageProviders,
            List<VoiceCallProvider> voiceProviders,
            Msg91EmailMessageProvider msg91EmailMessageProvider,
            ReminderVoiceRenderer reminderVoiceRenderer,
            DotVoiceMediaStreamClient dotVoiceMediaStreamClient
    ) {
        this(messagingStatusService, messageOrchestratorService, messageProviders, voiceProviders,
                msg91EmailMessageProvider, reminderVoiceRenderer, dotVoiceMediaStreamClient, null, null, null, null);
    }

    public CommunicationTestService(
            CarePilotMessagingStatusService messagingStatusService,
            MessageOrchestratorService messageOrchestratorService,
            List<MessageProvider> messageProviders,
            List<VoiceCallProvider> voiceProviders,
            Msg91EmailMessageProvider msg91EmailMessageProvider,
            ReminderVoiceRenderer reminderVoiceRenderer,
            DotVoiceMediaStreamClient dotVoiceMediaStreamClient,
            VoiceOrchestratorService voiceOrchestratorService,
            VoiceConversationContextRegistry contextRegistry,
            VoiceResponseInterpreter responseInterpreter
    ) {
        this(messagingStatusService, messageOrchestratorService, messageProviders, voiceProviders,
                msg91EmailMessageProvider, reminderVoiceRenderer, dotVoiceMediaStreamClient,
                voiceOrchestratorService, contextRegistry, responseInterpreter, null, null);
    }

    public CommunicationTestService(
            CarePilotMessagingStatusService messagingStatusService,
            MessageOrchestratorService messageOrchestratorService,
            List<MessageProvider> messageProviders,
            List<VoiceCallProvider> voiceProviders,
            Msg91EmailMessageProvider msg91EmailMessageProvider,
            ReminderVoiceRenderer reminderVoiceRenderer,
            DotVoiceMediaStreamClient dotVoiceMediaStreamClient,
            VoiceOrchestratorService voiceOrchestratorService,
            VoiceConversationContextRegistry contextRegistry,
            VoiceResponseInterpreter responseInterpreter,
            CommunicationChannelResolver communicationChannelResolver
    ) {
        this(messagingStatusService, messageOrchestratorService, messageProviders, voiceProviders,
                msg91EmailMessageProvider, reminderVoiceRenderer, dotVoiceMediaStreamClient,
                voiceOrchestratorService, contextRegistry, responseInterpreter, communicationChannelResolver, null);
    }

    public CommunicationTestService(
            CarePilotMessagingStatusService messagingStatusService,
            MessageOrchestratorService messageOrchestratorService,
            List<MessageProvider> messageProviders,
            List<VoiceCallProvider> voiceProviders,
            Msg91EmailMessageProvider msg91EmailMessageProvider,
            ReminderVoiceRenderer reminderVoiceRenderer,
            DotVoiceMediaStreamClient dotVoiceMediaStreamClient,
            VoiceOrchestratorService voiceOrchestratorService,
            VoiceConversationContextRegistry contextRegistry,
            VoiceResponseInterpreter responseInterpreter,
            CommunicationChannelResolver communicationChannelResolver,
            PatientService patientService
    ) {
        this(messagingStatusService, messageOrchestratorService, messageProviders, voiceProviders,
                msg91EmailMessageProvider, reminderVoiceRenderer, dotVoiceMediaStreamClient,
                voiceOrchestratorService, contextRegistry, responseInterpreter, communicationChannelResolver,
                patientService, null, null);
    }

    public CommunicationTestService(
            CarePilotMessagingStatusService messagingStatusService,
            MessageOrchestratorService messageOrchestratorService,
            List<MessageProvider> messageProviders,
            List<VoiceCallProvider> voiceProviders,
            Msg91EmailMessageProvider msg91EmailMessageProvider,
            ReminderVoiceRenderer reminderVoiceRenderer,
            DotVoiceMediaStreamClient dotVoiceMediaStreamClient,
            VoiceOrchestratorService voiceOrchestratorService,
            VoiceConversationContextRegistry contextRegistry,
            VoiceResponseInterpreter responseInterpreter,
            CommunicationChannelResolver communicationChannelResolver,
            PatientService patientService,
            CommunicationInteractionService interactionService
    ) {
        this(messagingStatusService, messageOrchestratorService, messageProviders, voiceProviders,
                msg91EmailMessageProvider, reminderVoiceRenderer, dotVoiceMediaStreamClient,
                voiceOrchestratorService, contextRegistry, responseInterpreter, communicationChannelResolver,
                patientService, interactionService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CommunicationTestService(
            CarePilotMessagingStatusService messagingStatusService,
            MessageOrchestratorService messageOrchestratorService,
            List<MessageProvider> messageProviders,
            List<VoiceCallProvider> voiceProviders,
            Msg91EmailMessageProvider msg91EmailMessageProvider,
            ReminderVoiceRenderer reminderVoiceRenderer,
            DotVoiceMediaStreamClient dotVoiceMediaStreamClient,
            VoiceOrchestratorService voiceOrchestratorService,
            VoiceConversationContextRegistry contextRegistry,
            VoiceResponseInterpreter responseInterpreter,
            CommunicationChannelResolver communicationChannelResolver,
            PatientService patientService,
            CommunicationInteractionService interactionService,
            VoiceReadOnlyAivaService voiceReadOnlyAivaService
    ) {
        this.messagingStatusService = messagingStatusService;
        this.messageOrchestratorService = messageOrchestratorService;
        this.messageProviders = messageProviders == null ? List.of() : List.copyOf(messageProviders);
        this.voiceProviders = voiceProviders == null ? List.of() : List.copyOf(voiceProviders);
        this.msg91EmailMessageProvider = msg91EmailMessageProvider;
        this.reminderVoiceRenderer = reminderVoiceRenderer;
        this.dotVoiceMediaStreamClient = dotVoiceMediaStreamClient;
        this.voiceOrchestratorService = voiceOrchestratorService;
        this.contextRegistry = contextRegistry;
        this.responseInterpreter = responseInterpreter;
        this.communicationChannelResolver = communicationChannelResolver;
        this.patientService = patientService;
        this.interactionService = interactionService;
        this.voiceReadOnlyAivaService = voiceReadOnlyAivaService;
        log.info("DOTVOICE_INBOUND_ROUTING_READY resolverPresent={} aivaReadOnlyPresent={}",
                communicationChannelResolver != null, voiceReadOnlyAivaService != null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setVoiceReadOnlyAivaService(VoiceReadOnlyAivaService service) {
        this.voiceReadOnlyAivaService = service;
    }

    boolean isVoiceReadOnlyAivaEnabled() {
        return voiceReadOnlyAivaService != null;
    }

    public Result voiceResult(String testExecutionId) {
        return testExecutionId == null ? null : voiceResults.get(testExecutionId);
    }

    public Result voiceResultByProviderCallId(String providerCallId) {
        if (providerCallId == null) return null;
        return voiceResults.values().stream()
                .filter(result -> providerCallId.equals(result.providerRequestId()))
                .findFirst().orElse(null);
    }

    public InboundVoiceResult latestInboundVoiceResult() {
        return inboundVoiceResults.values().stream()
                .max(java.util.Comparator.comparing(InboundVoiceResult::receivedAt))
                .orElse(null);
    }

    public String inboundVoiceWebsocketStatus() {
        return dotVoiceMediaStreamClient == null ? "DISCONNECTED" : dotVoiceMediaStreamClient.connectionState();
    }

    public void registerInboundCall(String callId, String callerPhoneNumber, String didNumber) {
        if (!StringUtils.hasText(callId)) return;
        OffsetDateTime receivedAt = OffsetDateTime.now();
        log.info("DOTVOICE_INBOUND_ROUTING_START callId={} did={}", callId, maskPhone(didNumber));
        var routing = communicationChannelResolver == null
                ? java.util.Optional.<Resolution>empty()
                : communicationChannelResolver.resolveInboundVoiceByDidWithFallback(didNumber);
        String normalizedDid = routing.map(Resolution::normalizedAddress).orElse(null);
        String routingStatus = communicationChannelResolver == null ? "ROUTING_FAILED" : routing.isPresent() ? "RESOLVED" : "ROUTING_FAILED";
        String routingFailureReason = communicationChannelResolver == null ? "ROUTING_RESOLVER_UNAVAILABLE" : routing.isPresent() ? null : "UNKNOWN_DID";
        String status = "ROUTING_FAILED".equals(routingStatus) ? "ROUTING_FAILED" : "RINGING";
        PatientIdentification identification = identifyInboundPatient(callId, callerPhoneNumber, routing);
        InboundVoiceResult result = new InboundVoiceResult(callId, callerPhoneNumber, didNumber, "INBOUND", status,
                "hi-IN", "Sarvam", null, receivedAt, null, null, null, null, inboundVoiceWebsocketStatus(),
                normalizedDid, routing.map(Resolution::channelId).orElse(null),
                routing.map(Resolution::tenantId).orElse(null),
                routing.map(Resolution::clinicId).orElse(null),
                routing.map(Resolution::locationId).orElse(null),
                routing.map(Resolution::provider).orElse(null),
                routingStatus, routingFailureReason, identification.normalizedCallerPhone(), identification.status(),
                identification.patientId(), identification.patientName(), identification.candidateCount(), "CALLER_PHONE",
                null, identification.party().name());
        UUID interactionId = persistInboundInteraction(result);
        result = result.withInteraction(interactionId);
        inboundVoiceResults.put(callId, result);
        inboundCaptureStarted.put(callId, new AtomicBoolean(false));
        inboundTurnCounts.put(callId, new java.util.concurrent.atomic.AtomicInteger(0));
        log.info("DOTVOICE_INBOUND_CALL_RECEIVED callId={} from={} to={}", callId, maskPhone(callerPhoneNumber), maskPhone(didNumber));
        if (routing.isPresent()) {
            log.info("DOTVOICE_INBOUND_ROUTING_RESOLVED callId={} channelId={} tenantId={} clinicId={} locationId={} did={}",
                    callId, result.communicationChannelId(), result.tenantId(), result.clinicId(), result.locationId(), maskPhone(normalizedDid));
        } else {
            log.info("DOTVOICE_INBOUND_ROUTING_FAILED callId={} did={} reason={}", callId, maskPhone(didNumber), routingFailureReason);
        }
    }

    public InboundVoiceResult inboundVoiceResult(String callId) {
        return callId == null ? null : inboundVoiceResults.get(callId);
    }

    public void markInboundAnswered(String callId) {
        log.info("DOTVOICE_INBOUND_CALL_ANSWERED callId={}", callId);
        updateInbound(callId, result -> result.withLifecycle("ANSWERED", result.sttProvider(), result.firstTranscript(),
                result.receivedAt(), OffsetDateTime.now(), result.capturedAt(), result.endedAt(), result.hangupCause()));
        persistInteractionUpdate(callId, row -> row.markAnswered(OffsetDateTime.now()));
    }

    public void startInboundGreeting(String callId) {
        InboundVoiceResult result = inboundVoiceResult(callId);
        String greeting = result == null ? genericInboundGreeting()
                : "MULTIPLE_MATCHES".equals(result.patientIdentificationStatus())
                ? multipleMatchGreeting()
                : "SINGLE_MATCH".equals(result.patientIdentificationStatus()) && StringUtils.hasText(result.identifiedPatientName())
                ? "नमस्ते " + result.identifiedPatientName().trim() + " जी। जीवनम हेल्थकेयर में आपका स्वागत है। कृपया बताइए, हम आपकी क्या सहायता कर सकते हैं?"
                : genericInboundGreeting();
        playInboundAudio(callId, greeting, false);
    }

    private PatientIdentification identifyInboundPatient(String callId, String callerPhoneNumber,
            java.util.Optional<Resolution> routing) {
        if (routing.isEmpty() || routing.get().tenantId() == null) {
            return new PatientIdentification(null, "NOT_CHECKED", null, null, 0, ConversationPartyType.GUEST_CALLER);
        }
        UUID tenantId = routing.get().tenantId();
        log.info("VOICE_PATIENT_IDENTIFICATION_START callId={} tenantId={} caller={}", callId, tenantId, maskPhone(callerPhoneNumber));
        try {
            String normalized = normalizeCallerPhone(callerPhoneNumber, routing.get().normalizedAddress());
            if (!StringUtils.hasText(normalized) || patientService == null) {
                throw new IllegalStateException(patientService == null ? "PATIENT_SERVICE_UNAVAILABLE" : "CALLER_PHONE_INVALID");
            }
            String storedMobile = patientLookupMobile(normalized);
            List<PatientRecord> matches = patientService.findActiveByTenantAndMobile(tenantId, storedMobile);
            if (matches.isEmpty()) {
                log.info("VOICE_PATIENT_IDENTIFICATION_NO_MATCH callId={} tenantId={}", callId, tenantId);
                return new PatientIdentification(normalized, "NO_MATCH", null, null, 0, ConversationPartyType.GUEST_CALLER);
            }
            if (matches.size() > 1) {
                log.info("VOICE_PATIENT_IDENTIFICATION_MULTIPLE_MATCHES callId={} tenantId={} candidateCount={}", callId, tenantId, matches.size());
                return new PatientIdentification(normalized, "MULTIPLE_MATCHES", null, null, matches.size(), ConversationPartyType.PATIENT_UNRESOLVED);
            }
            PatientRecord patient = matches.get(0);
            String name = StringUtils.hasText(patient.firstName()) ? patient.firstName().trim() : null;
            log.info("VOICE_PATIENT_IDENTIFICATION_SINGLE_MATCH callId={} tenantId={} patientId={}", callId, tenantId, patient.id());
            return new PatientIdentification(normalized, "SINGLE_MATCH", patient.id(), name, 1, ConversationPartyType.IDENTIFIED_PATIENT);
        } catch (RuntimeException ex) {
            log.warn("VOICE_PATIENT_IDENTIFICATION_FAILED callId={} tenantId={} reason={}", callId, tenantId, safeIdentificationFailureReason(ex));
            return new PatientIdentification(safeNormalizeCallerPhone(callerPhoneNumber, routing.get().normalizedAddress()),
                    "LOOKUP_FAILED", null, null, 0, ConversationPartyType.GUEST_CALLER);
        }
    }

    private String normalizeCallerPhone(String callerPhoneNumber, String normalizedDid) {
        if (!StringUtils.hasText(callerPhoneNumber)) throw new IllegalArgumentException("CALLER_PHONE_MISSING");
        String compact = callerPhoneNumber.trim().replaceAll("[() .\\-]", "");
        if (normalizedDid != null && normalizedDid.startsWith("+91")) {
            if (compact.startsWith("0091")) compact = "+" + compact.substring(2);
            if (compact.startsWith("+91")) return PhoneAddressNormalizer.normalize(compact);
            if (compact.matches("0[0-9]{10}")) return "+91" + compact.substring(1);
            if (compact.matches("[0-9]{10}")) return "+91" + compact;
        }
        return PhoneAddressNormalizer.normalize(compact);
    }

    private String safeNormalizeCallerPhone(String callerPhoneNumber, String normalizedDid) {
        try { return normalizeCallerPhone(callerPhoneNumber, normalizedDid); }
        catch (RuntimeException ignored) { return null; }
    }

    private String patientLookupMobile(String normalizedCallerPhone) {
        if (normalizedCallerPhone != null && normalizedCallerPhone.startsWith("+91")
                && normalizedCallerPhone.length() == 13) {
            return normalizedCallerPhone.substring(3);
        }
        return normalizedCallerPhone;
    }

    private String safeIdentificationFailureReason(RuntimeException ex) {
        String reason = ex.getMessage();
        if ("CALLER_PHONE_INVALID".equals(reason) || "PATIENT_SERVICE_UNAVAILABLE".equals(reason)) {
            return reason;
        }
        return ex.getClass().getSimpleName();
    }

    private String genericInboundGreeting() {
        return "नमस्ते। जीवनम हेल्थकेयर में आपका स्वागत है। कृपया बताइए, हम आपकी क्या सहायता कर सकते हैं?";
    }

    private String multipleMatchGreeting() {
        return "नमस्ते। इस मोबाइल नंबर से एक से अधिक मरीज जुड़े हुए हैं। कृपया बताइए, आप किसके लिए सहायता चाहते हैं?";
    }

    private record PatientIdentification(String normalizedCallerPhone, String status, UUID patientId,
                                         String patientName, int candidateCount, ConversationPartyType party) {}

    public void markInboundListening(String callId) {
        updateInbound(callId, result -> result.withLifecycle("LISTENING", result.sttProvider(), result.firstTranscript(),
                result.receivedAt(), result.answeredAt(), result.capturedAt(), result.endedAt(), result.hangupCause()));
        log.info("DOTVOICE_INBOUND_GREETING_COMPLETE callId={}", callId);
    }

    public void processInboundFirstUtterance(String callId, byte[] wavAudio) {
        InboundVoiceResult current = inboundVoiceResult(callId);
        if (current == null || "CLOSING".equals(current.status()) || "HANGUP_REQUESTED".equals(current.status())) return;
        AtomicBoolean captureStarted = inboundCaptureStarted.computeIfAbsent(callId, ignored -> new AtomicBoolean(false));
        if (!captureStarted.compareAndSet(false, true)) return;
        log.info("DOTVOICE_INBOUND_STT_DISPATCH callId={} audioBytes={} language={}", callId, wavAudio == null ? 0 : wavAudio.length, current.language());
        if (wavAudio == null || wavAudio.length == 0) {
            captureStarted.set(false);
            return;
        }
        try {
            if (!G711MuLawAudioConverter.isPcm16Mono8kWav(wavAudio)) throw new IllegalArgumentException("Invalid inbound WAV payload.");
            VoiceTranscriptionResult transcription = voiceOrchestratorService.transcribeBufferedAudio(
                    null, wavAudio, "audio/wav", "dotvoice-inbound.wav", current.language());
            String transcript = transcription == null || transcription.transcript() == null ? "" : transcription.transcript().trim();
            if (!StringUtils.hasText(transcript)) {
                captureStarted.set(false);
                return;
            }
            updateInbound(callId, result -> result.withLifecycle("CAPTURED", transcription == null ? null : transcription.providerName(),
                    transcript, result.receivedAt(), result.answeredAt(), OffsetDateTime.now(), result.endedAt(), result.hangupCause()));
            persistInteractionUpdate(callId, row -> row.markTranscriptPresent());
            log.info("DOTVOICE_INBOUND_STT_RESULT callId={} provider={} success=true transcriptPresent={} language={}",
                    callId, transcription == null ? null : transcription.providerName(), StringUtils.hasText(transcript), current.language());
            log.info("DOTVOICE_INBOUND_FIRST_UTTERANCE_CAPTURED callId={}", callId);
            log.info("DOTVOICE_INBOUND_AIVA_CONTINUATION_ENABLED callId={} enabled={}", callId, voiceReadOnlyAivaService != null);
            if (voiceReadOnlyAivaService == null) {
                log.warn("DOTVOICE_INBOUND_AIVA_CONTINUATION_DISABLED callId={} reason=AIVA_SERVICE_NOT_WIRED", callId);
                playInboundAudio(callId, "धन्यवाद। आपका संदेश प्राप्त हो गया है।", true);
            } else {
                VoiceReadOnlyAivaService.Context context = new VoiceReadOnlyAivaService.Context(
                        callId, current.interactionId(), current.tenantId(), current.communicationChannelId(),
                        current.clinicId(), current.locationId(), current.language(), current.patientIdentificationStatus(), current.conversationPartyType(),
                        current.identifiedPatientId());
                VoiceReadOnlyAivaService.TurnResult turn = voiceReadOnlyAivaService.continueTurn(context, transcript);
                int turnCount = inboundTurnCounts.computeIfAbsent(callId,
                        ignored -> new java.util.concurrent.atomic.AtomicInteger(0)).incrementAndGet();
                updateInbound(callId, result -> result.withAivaTurn("voice-" + callId, turnCount,
                        turn.intent(), turn.tool(), turn.response(), transcript,
                        transcription == null ? current.sttProvider() : transcription.providerName(),
                        turn.policyDecision(), turn.toolExecutionStatus(), turn.toolInputSpeciality(),
                        turn.toolResultCount(), turn.toolResultSummary()));
                log.info("VOICE_AIVA_TURN_START callId={} turnNumber={}", callId, turnCount);
                log.info("VOICE_AIVA_INTENT callId={} intent={} tool={}", callId, turn.intent(), turn.tool());
                if (turn.mutationBlocked()) {
                    log.info("VOICE_AIVA_MUTATION_BLOCKED callId={} tool={}", callId, turn.tool());
                }
                log.info("VOICE_AIVA_RESPONSE_READY callId={} turnNumber={}", callId, turnCount);
                playInboundAudio(callId, turn.response(), turn.closing());
            }
            captureStarted.set(false);
        } catch (RuntimeException ex) {
            captureStarted.set(false);
            log.info("DOTVOICE_INBOUND_STT_RESULT callId={} provider={} success=false transcriptPresent=false language={}",
                    callId, current.sttProvider(), current.language());
            log.warn("DOTVOICE_INBOUND_STT_FAILED callId={} exception={} reason={}", callId, ex.getClass().getSimpleName(), safeSttFailureReason(ex));
            persistInteractionUpdate(callId, row -> row.markEnded("FAILED", "STT_FAILED", null, OffsetDateTime.now()));
        }
    }

    public void onInboundClosingPlaybackComplete(String callId) {
        InboundVoiceResult result = inboundVoiceResult(callId);
        if (result == null) return;
        updateInbound(callId, current -> current.withLifecycle("HANGUP_REQUESTED", current.sttProvider(), current.firstTranscript(),
                current.receivedAt(), current.answeredAt(), current.capturedAt(), current.endedAt(), current.hangupCause()));
        log.info("DOTVOICE_HANGUP_REQUESTED providerCallId={}", callId);
        dotVoiceMediaStreamClient.hangup(callId);
    }

    public void onInboundStreamStopped(String callId, String cause) {
        InboundVoiceResult before = inboundVoiceResult(callId);
        String finalStatus = before != null && "ROUTING_FAILED".equals(before.routingStatus()) ? "ROUTING_FAILED" : "ENDED";
        String outcome = before == null ? "UNKNOWN" : "ROUTING_FAILED".equals(before.routingStatus()) ? "ROUTING_FAILED"
                : before.firstTranscript() != null ? "COMPLETED" : "NO_SPEECH";
        updateInbound(callId, result -> result.withLifecycle(finalStatus, result.sttProvider(), result.firstTranscript(),
                result.receivedAt(), result.answeredAt(), result.capturedAt(), OffsetDateTime.now(), cause));
        persistInteractionUpdate(callId, row -> row.markEnded(finalStatus, outcome, cause, OffsetDateTime.now()));
        if (voiceReadOnlyAivaService != null && before != null && before.tenantId() != null) {
            voiceReadOnlyAivaService.endSession(before.tenantId(), callId);
        }
        inboundTurnCounts.remove(callId);
        log.info("COMM_TEST_CONTEXT_CLEANUP providerCallId={}", callId);
    }

    private UUID persistInboundInteraction(InboundVoiceResult result) {
        if (interactionService == null) return null;
        return interactionService.startInbound(result.callId(), result.tenantId(), result.communicationChannelId(),
                result.clinicId(), result.locationId(), result.provider() == null ? "DOTVOICE" : result.provider(),
                result.normalizedDid(), maskPhone(result.callerPhoneNumber()), result.patientIdentificationStatus(),
                result.identifiedPatientId(), partyType(result.conversationPartyType()), result.candidateCount(),
                result.status(), result.receivedAt());
    }

    private void persistInteractionUpdate(String callId, java.util.function.Consumer<com.deepthoughtnet.clinic.communication.db.CommunicationInteractionEntity> update) {
        if (interactionService != null) interactionService.updateInbound(callId, update);
    }

    private ConversationPartyType partyType(String value) {
        try { return ConversationPartyType.valueOf(value); }
        catch (RuntimeException ignored) { return ConversationPartyType.GUEST_CALLER; }
    }

    private void playInboundAudio(String callId, String text, boolean closing) {
        InboundVoiceResult result = inboundVoiceResult(callId);
        if (result == null || reminderVoiceRenderer == null || dotVoiceMediaStreamClient == null) return;
        updateInbound(callId, current -> current.withTtsDetails(text, null, closing));
        if (closing) {
            updateInbound(callId, current -> current.withLifecycle("CLOSING", current.sttProvider(), current.firstTranscript(),
                    current.receivedAt(), current.answeredAt(), current.capturedAt(), current.endedAt(), current.hangupCause()));
        }
        ReminderVoiceRenderer.RenderedReminder rendered = reminderVoiceRenderer.render(text, result.language());
        updateInbound(callId, current -> current.withTtsDetails(text, rendered.provider(), closing));
        if (rendered.audio() == null) {
            if (closing) onInboundClosingPlaybackComplete(callId);
            return;
        }
        try {
            var pcm = ReminderAudioNormalizer.normalize(rendered.audio().audioBytes(), rendered.audio().contentType(), rendered.sourceSampleRate());
            List<byte[]> frames = G711MuLawAudioConverter.toFramesPcm(pcm.bytes(), pcm.sampleRate(), pcm.channels());
            if (!dotVoiceMediaStreamClient.startPlayback(callId, frames) && closing) onInboundClosingPlaybackComplete(callId);
        } catch (RuntimeException ex) {
            log.warn("DOTVOICE_INBOUND_TTS_FAILED callId={} reason={}", callId, safeSttFailureReason(ex));
            if (closing) onInboundClosingPlaybackComplete(callId);
        }
    }

    private void updateInbound(String callId, java.util.function.UnaryOperator<InboundVoiceResult> updater) {
        inboundVoiceResults.computeIfPresent(callId, (ignored, current) -> updater.apply(current));
    }

    private String maskPhone(String value) {
        if (!StringUtils.hasText(value) || value.length() <= 4) return value;
        return "***" + value.substring(value.length() - 4);
    }

    public void markVoiceAnswered(String providerCallId) {
        Result current = voiceResultByProviderCallId(providerCallId);
        if (current == null || "RESPONSE_CAPTURED".equals(current.status())) return;
        voiceResults.put(current.testExecutionId(), new Result(
                current.requestId(), current.correlationId(), current.channel(), current.provider(), current.providerRequestId(),
                "ANSWERED", true, current.startedAt(), current.completedAt(), current.failureCategory(), current.failureMessage(),
                current.testExecutionId(), current.sttProvider(), current.rawTranscript(), current.detectedLanguage(), current.sttConfidence(),
                current.normalizedIntent(), current.attemptNumber(), current.wouldExecuteAction(), OffsetDateTime.now(), current.hangupCause()));
        log.info("COMM_TEST_RESULT_UPDATED testExecutionId={} status={} intent={}",
                current.testExecutionId(), "ANSWERED", current.normalizedIntent());
    }

    public void markVoiceStatus(String providerCallId, String status) {
        Result current = voiceResultByProviderCallId(providerCallId);
        if (current == null || status == null || status.equals(current.status())
                || "RESPONSE_CAPTURED".equals(current.status()) || "FAILED".equals(current.status())) return;
        voiceResults.put(current.testExecutionId(), new Result(
                current.requestId(), current.correlationId(), current.channel(), current.provider(), current.providerRequestId(),
                status, true, current.startedAt(), current.completedAt(), current.failureCategory(), current.failureMessage(),
                current.testExecutionId(), current.sttProvider(), current.rawTranscript(), current.detectedLanguage(), current.sttConfidence(),
                current.normalizedIntent(), current.attemptNumber(), current.wouldExecuteAction(), current.answeredAt(), current.hangupCause()));
        log.info("COMM_TEST_RESULT_UPDATED testExecutionId={} status={} intent={}",
                current.testExecutionId(), status, current.normalizedIntent());
    }

    public void completeVoiceResult(String providerCallId, byte[] wavAudio, String hangupCause) {
        if (contextRegistry == null || providerCallId == null) return;
        var context = contextRegistry.find(providerCallId).orElse(null);
        if (context == null) return;
        Result current = voiceResults.get(context.executionId().toString());
        if (current == null || current.rawTranscript() != null) return;
        if (wavAudio == null || wavAudio.length == 0) {
            voiceResults.put(context.executionId().toString(), new Result(
                    current.requestId(), current.correlationId(), current.channel(), current.provider(), current.providerRequestId(),
                    "RESPONSE_CAPTURED", true, current.startedAt(), OffsetDateTime.now(), null, null,
                    current.testExecutionId(), "sarvam", "", context.language(), null, "NO_RESPONSE", 1, "NONE", current.answeredAt(), hangupCause));
            contextRegistry.remove(providerCallId);
            log.info("COMM_TEST_RESULT_UPDATED testExecutionId={} status={} intent={}",
                    context.executionId(), "RESPONSE_CAPTURED", "NO_RESPONSE");
            log.info("COMM_TEST_CONTEXT_CLEANUP testExecutionId={} providerCallId={}",
                    context.executionId(), providerCallId);
            return;
        }
        try {
            log.info("COMM_TEST_STT_DISPATCH testExecutionId={} providerCallId={} bufferedBytes={} language={}",
                    context.executionId(), providerCallId, wavAudio.length, context.language());
            if (!G711MuLawAudioConverter.isPcm16Mono8kWav(wavAudio)) {
                throw new IllegalArgumentException("Invalid PCM16 mono 8 kHz WAV payload.");
            }
            VoiceTranscriptionResult transcription = voiceOrchestratorService.transcribeBufferedAudio(
                    context.tenantId(), wavAudio, "audio/wav", "dotvoice-inbound.wav", context.language());
            String transcript = transcription == null ? "" : transcription.transcript();
            String normalizedTranscript = responseInterpreter.normalizeForClassification(transcript);
            log.info("COMM_TEST_RESPONSE_INPUT testExecutionId={} providerCallId={} normalizedTranscript={}",
                    context.executionId(), providerCallId, normalizedTranscript);
            String intent = responseInterpreter.interpret(transcript).name();
            log.info("COMM_TEST_STT_RESULT testExecutionId={} providerCallId={} sttProvider={} transcriptPresent={} transcriptChars={}",
                    context.executionId(), providerCallId, transcription == null ? null : transcription.providerName(),
                    StringUtils.hasText(transcript), transcript == null ? 0 : transcript.length());
            log.info("COMM_TEST_RESPONSE_CLASSIFIED testExecutionId={} providerCallId={} intent={} attempt={}",
                    context.executionId(), providerCallId, intent, 1);
            voiceResults.put(context.executionId().toString(), new Result(
                    current.requestId(), current.correlationId(), current.channel(), current.provider(),
                    current.providerRequestId(), "RESPONSE_CAPTURED", true, current.startedAt(), OffsetDateTime.now(),
                    null, null, current.testExecutionId(), transcription == null ? null : transcription.providerName(),
                    transcript, context.language(), null, intent, 1, actionFor(intent), current.answeredAt(), hangupCause));
            log.info("COMM_TEST_RESULT_UPDATED testExecutionId={} status={} intent={}",
                    context.executionId(), "RESPONSE_CAPTURED", intent);
        } catch (RuntimeException ex) {
            String safeReason = safeSttFailureReason(ex);
            log.warn("COMM_TEST_STT_FAILED testExecutionId={} providerCallId={} exception={} reason={} bufferedBytes={} language={}",
                    context.executionId(), providerCallId, ex.getClass().getSimpleName(), safeReason,
                    wavAudio.length, context.language());
            voiceResults.put(context.executionId().toString(), new Result(
                    current.requestId(), current.correlationId(), current.channel(), current.provider(),
                    current.providerRequestId(), "FAILED", false, current.startedAt(), OffsetDateTime.now(),
                    "STT_ERROR", "STT failed: " + safeReason, current.testExecutionId(), "sarvam", null,
                    context.language(), null, null, 1, "NONE", current.answeredAt(), hangupCause));
            log.info("COMM_TEST_RESULT_UPDATED testExecutionId={} status={} intent={}",
                    context.executionId(), "FAILED", null);
        } finally {
            contextRegistry.remove(providerCallId);
            log.info("COMM_TEST_CONTEXT_CLEANUP testExecutionId={} providerCallId={}",
                    context.executionId(), providerCallId);
        }
    }

    /** Processes a completed caller utterance without waiting for stream_stop. */
    public void processLiveUtterance(String providerCallId, byte[] wavAudio) {
        if (contextRegistry == null || providerCallId == null || wavAudio == null || wavAudio.length == 0) return;
        var context = contextRegistry.find(providerCallId).orElse(null);
        if (context == null) return;
        Result current = voiceResults.get(context.executionId().toString());
        if (current == null || "CLOSING".equals(current.status()) || "HANGUP_REQUESTED".equals(current.status())
                || "RESPONSE_CAPTURED".equals(current.status())) return;
        markVoiceStatus(providerCallId, "TRANSCRIBING");
        try {
            log.info("COMM_TEST_STT_DISPATCH testExecutionId={} providerCallId={} bufferedBytes={} language={}",
                    context.executionId(), providerCallId, wavAudio.length, context.language());
            if (!G711MuLawAudioConverter.isPcm16Mono8kWav(wavAudio)) {
                throw new IllegalArgumentException("Invalid PCM16 mono 8 kHz WAV payload.");
            }
            VoiceTranscriptionResult transcription = voiceOrchestratorService.transcribeBufferedAudio(
                    context.tenantId(), wavAudio, "audio/wav", "dotvoice-inbound.wav", context.language());
            String transcript = transcription == null ? "" : transcription.transcript();
            String normalizedTranscript = responseInterpreter.normalizeForClassification(transcript);
            log.info("COMM_TEST_RESPONSE_INPUT testExecutionId={} providerCallId={} normalizedTranscript={}",
                    context.executionId(), providerCallId, normalizedTranscript);
            String intent = responseInterpreter.interpret(transcript).name();
            int attempt = current.attemptNumber() == null ? 1 : current.attemptNumber();
            log.info("COMM_TEST_STT_RESULT testExecutionId={} providerCallId={} sttProvider={} transcriptPresent={} transcriptChars={}",
                    context.executionId(), providerCallId, transcription == null ? null : transcription.providerName(),
                    StringUtils.hasText(transcript), transcript == null ? 0 : transcript.length());
            log.info("COMM_TEST_RESPONSE_CLASSIFIED testExecutionId={} providerCallId={} intent={} attempt={}",
                    context.executionId(), providerCallId, intent, attempt);
            int resultAttempt = "UNCLEAR".equals(intent) && attempt <= context.maxAdditionalAttempts()
                    ? attempt + 1 : attempt;
            updateInteractiveResult(current, context, transcription, transcript, intent, resultAttempt,
                    "UNCLEAR".equals(intent) && attempt <= context.maxAdditionalAttempts() ? "REPROMPTING" : "RESPONSE_CAPTURED");
            if ("UNCLEAR".equals(intent) && attempt <= context.maxAdditionalAttempts()) {
                log.info("COMM_TEST_REPROMPT testExecutionId={} providerCallId={} attempt={}",
                        context.executionId(), providerCallId, attempt);
                playInteractiveAudio(providerCallId, reprompt(context.language()), context.language(), false);
            } else {
                playInteractiveAudio(providerCallId, closingMessage(intent, context.language()), context.language(), true);
            }
        } catch (RuntimeException ex) {
            String safeReason = safeSttFailureReason(ex);
            log.warn("COMM_TEST_STT_FAILED testExecutionId={} providerCallId={} exception={} reason={} bufferedBytes={} language={}",
                    context.executionId(), providerCallId, ex.getClass().getSimpleName(), safeReason,
                    wavAudio.length, context.language());
            voiceResults.put(context.executionId().toString(), new Result(
                    current.requestId(), current.correlationId(), current.channel(), current.provider(), current.providerRequestId(),
                    "FAILED", false, current.startedAt(), OffsetDateTime.now(), "STT_ERROR", "STT failed: " + safeReason,
                    current.testExecutionId(), "sarvam", null, context.language(), null, null, current.attemptNumber(), "NONE",
                    current.answeredAt(), current.hangupCause()));
            log.info("COMM_TEST_RESULT_UPDATED testExecutionId={} status={} intent={}", context.executionId(), "FAILED", null);
        }
    }

    public void processNoResponse(String providerCallId) {
        var context = contextRegistry.find(providerCallId).orElse(null);
        if (context == null) return;
        Result current = voiceResults.get(context.executionId().toString());
        if (current == null || "RESPONSE_CAPTURED".equals(current.status()) || "CLOSING".equals(current.status())) return;
        log.info("COMM_TEST_RESPONSE_CLASSIFIED testExecutionId={} providerCallId={} intent=NO_RESPONSE attempt={}",
                context.executionId(), providerCallId, current.attemptNumber());
        voiceResults.put(context.executionId().toString(), new Result(
                current.requestId(), current.correlationId(), current.channel(), current.provider(), current.providerRequestId(),
                "RESPONSE_CAPTURED", true, current.startedAt(), OffsetDateTime.now(), null, null, current.testExecutionId(),
                "sarvam", "", context.language(), null, "NO_RESPONSE", current.attemptNumber(), "NONE",
                current.answeredAt(), current.hangupCause()));
        log.info("COMM_TEST_RESULT_UPDATED testExecutionId={} status={} intent={}", context.executionId(), "RESPONSE_CAPTURED", "NO_RESPONSE");
        playInteractiveAudio(providerCallId, closingMessage("NO_RESPONSE", context.language()), context.language(), true);
    }

    public void onInteractivePlaybackComplete(String providerCallId) {
        markVoiceStatus(providerCallId, "LISTENING");
    }

    public void onClosingPlaybackComplete(String providerCallId) {
        var context = contextRegistry.find(providerCallId).orElse(null);
        if (context == null) return;
        log.info("COMM_TEST_CLOSING_TTS_COMPLETE testExecutionId={} providerCallId={}", context.executionId(), providerCallId);
        Result current = voiceResults.get(context.executionId().toString());
        if (current != null) {
            voiceResults.put(context.executionId().toString(), new Result(
                    current.requestId(), current.correlationId(), current.channel(), current.provider(), current.providerRequestId(),
                    "HANGUP_REQUESTED", true, current.startedAt(), current.completedAt(), current.failureCategory(), current.failureMessage(),
                    current.testExecutionId(), current.sttProvider(), current.rawTranscript(), current.detectedLanguage(), current.sttConfidence(),
                    current.normalizedIntent(), current.attemptNumber(), current.wouldExecuteAction(), current.answeredAt(), current.hangupCause()));
        }
        log.info("DOTVOICE_HANGUP_REQUESTED providerCallId={} testExecutionId={}", providerCallId, context.executionId());
        if (!dotVoiceMediaStreamClient.hangup(providerCallId)) {
            log.warn("DOTVOICE_HANGUP_FALLBACK providerCallId={} reason=control_and_rest_failed", providerCallId);
        }
    }

    public void onInteractiveStreamStopped(String providerCallId, String cause) {
        var context = contextRegistry.remove(providerCallId).orElse(null);
        if (context == null) return;
        Result current = voiceResults.get(context.executionId().toString());
        if (current != null) {
            String terminalStatus = "CLOSING".equals(current.status()) || "HANGUP_REQUESTED".equals(current.status())
                    ? "ENDED" : current.status();
            voiceResults.put(context.executionId().toString(), new Result(
                    current.requestId(), current.correlationId(), current.channel(), current.provider(), current.providerRequestId(),
                    terminalStatus, current.success(), current.startedAt(), current.completedAt() == null ? OffsetDateTime.now() : current.completedAt(),
                    current.failureCategory(), current.failureMessage(), current.testExecutionId(), current.sttProvider(), current.rawTranscript(),
                    current.detectedLanguage(), current.sttConfidence(), current.normalizedIntent(), current.attemptNumber(), current.wouldExecuteAction(),
                    current.answeredAt(), cause));
        }
        log.info("COMM_TEST_CONTEXT_CLEANUP testExecutionId={} providerCallId={}", context.executionId(), providerCallId);
    }

    private void updateInteractiveResult(Result current, VoiceConversationContextRegistry.VoiceConversationContext context,
                                         VoiceTranscriptionResult transcription, String transcript, String intent, int attempt,
                                         String status) {
        String action = "YES".equals(intent) ? "CONFIRM" : "NO".equals(intent) ? "DECLINE" : "NONE";
        voiceResults.put(context.executionId().toString(), new Result(
                current.requestId(), current.correlationId(), current.channel(), current.provider(), current.providerRequestId(),
                status, true, current.startedAt(), "REPROMPTING".equals(status) ? null : OffsetDateTime.now(), null, null,
                current.testExecutionId(), transcription == null ? null : transcription.providerName(), transcript, context.language(),
                null, intent, attempt, action, current.answeredAt(), current.hangupCause()));
        log.info("COMM_TEST_RESULT_UPDATED testExecutionId={} status={} intent={}", context.executionId(), status, intent);
    }

    private void playInteractiveAudio(String providerCallId, String text, String language, boolean closing) {
        var context = contextRegistry.find(providerCallId).orElse(null);
        if (context == null) return;
        ReminderVoiceRenderer.RenderedReminder rendered = reminderVoiceRenderer.render(text, language);
        if (rendered.audio() == null) {
            log.warn("COMM_TEST_CLOSING_TTS_FAILED testExecutionId={} providerCallId={} reason={}",
                    context.executionId(), providerCallId, rendered.failureCategory());
            if (closing) onClosingPlaybackComplete(providerCallId);
            return;
        }
        try {
            var pcm = ReminderAudioNormalizer.normalize(rendered.audio().audioBytes(), rendered.audio().contentType(), rendered.sourceSampleRate());
            List<byte[]> frames = G711MuLawAudioConverter.toFramesPcm(pcm.bytes(), pcm.sampleRate(), pcm.channels());
            log.info("COMM_TEST_TTS_AUDIO_FORMAT provider={} contentType={} format={} sampleRate={} audioBytes={}",
                    rendered.provider(), rendered.audio().contentType(), pcm.inputFormat(), pcm.sampleRate(), rendered.audio().audioBytes().length);
            log.info("DOTVOICE_PLAYBACK_START providerCallId={} purpose={} frameCount={} audioBytes={}",
                    providerCallId, closing ? "CLOSING" : "REPROMPT", frames.size(), frames.size() * 160);
            if (closing) {
                markClosing(providerCallId);
                log.info("COMM_TEST_CLOSING_TTS_START testExecutionId={} providerCallId={} language={}", context.executionId(), providerCallId, language);
            }
            if (!dotVoiceMediaStreamClient.startPlayback(providerCallId, frames) && closing) {
                onClosingPlaybackComplete(providerCallId);
            }
        } catch (RuntimeException ex) {
            log.warn("COMM_TEST_CLOSING_TTS_FAILED testExecutionId={} providerCallId={} reason={}",
                    context.executionId(), providerCallId, safeSttFailureReason(ex));
            if (closing) onClosingPlaybackComplete(providerCallId);
        }
    }

    private String reprompt(String language) {
        return language != null && language.toLowerCase(java.util.Locale.ROOT).startsWith("hi")
                ? "Maaf kijiye, main aapka jawab samajh nahi paaya. Kripya haan ya nahi kahiye."
                : "Sorry, I didn't understand. Please say yes or no.";
    }

    private void markClosing(String providerCallId) {
        var context = contextRegistry.find(providerCallId).orElse(null);
        if (context == null) return;
        Result current = voiceResults.get(context.executionId().toString());
        if (current == null) return;
        voiceResults.put(context.executionId().toString(), new Result(
                current.requestId(), current.correlationId(), current.channel(), current.provider(), current.providerRequestId(),
                "CLOSING", true, current.startedAt(), current.completedAt(), current.failureCategory(), current.failureMessage(),
                current.testExecutionId(), current.sttProvider(), current.rawTranscript(), current.detectedLanguage(), current.sttConfidence(),
                current.normalizedIntent(), current.attemptNumber(), current.wouldExecuteAction(), current.answeredAt(), current.hangupCause()));
        log.info("COMM_TEST_RESULT_UPDATED testExecutionId={} status={} intent={}", context.executionId(), "CLOSING", current.normalizedIntent());
    }

    private String closingMessage(String intent, String language) {
        boolean hindi = language != null && language.toLowerCase(java.util.Locale.ROOT).startsWith("hi");
        if ("YES".equals(intent)) return hindi ? "Dhanyavaad. Aapki pushti darj kar li gayi hai." : "Thank you. Your confirmation has been recorded.";
        if ("NO".equals(intent)) return hindi ? "Dhanyavaad. Aapka jawab darj kar liya gaya hai." : "Thank you. Your response has been recorded.";
        return hindi ? "Dhanyavaad. Hamari clinic team aapse sampark karegi." : "Thank you. Our clinic team can follow up with you.";
    }

    private String actionFor(String intent) {
        return switch (intent) { case "YES" -> "CONFIRM"; case "NO" -> "DECLINE"; default -> "NONE"; };
    }

    private String safeSttFailureReason(RuntimeException ex) {
        String reason = ex == null || ex.getMessage() == null ? "unknown_error" : ex.getMessage();
        String sanitized = reason
                .replaceAll("(?i)https?://\\S+|wss?://\\S+", "[redacted-url]")
                .replaceAll("(?i)(api[_-]?key|token|secret|authorization)\\s*[:=]\\s*\\S+", "$1=[redacted]")
                .replaceAll("[\\r\\n\\t]", " ")
                .trim();
        return sanitized.length() <= 160 ? sanitized : sanitized.substring(0, 160);
    }

    public HealthResponse health(String correlationId) {
        List<ChannelHealth> channels = messagingStatusService.providerStatuses().stream()
                .filter(row -> row.channel() == MessageChannel.EMAIL || row.channel() == MessageChannel.WHATSAPP)
                .filter(row -> !"msg91-email-smtp".equalsIgnoreCase(row.providerName()))
                .map(this::mapHealth)
                .toList();
        channels = new java.util.ArrayList<>(channels);
        if (msg91EmailMessageProvider != null) channels.add(msg91Health());
        VoiceCallProvider operationalVoiceProvider = operationalVoiceProvider();
        if (operationalVoiceProvider == null) {
            channels.add(new ChannelHealth("VOICE", "VOICE_NOT_CONFIGURED", "NOT_CONFIGURED",
                    "No production voice provider is configured.", false, false, false, null,
                    List.of("production voice provider"), OffsetDateTime.now()));
        } else {
            boolean ready = operationalVoiceProvider.isReady();
            channels.add(new ChannelHealth("VOICE", operationalVoiceProvider.providerName(), ready ? "READY" : "PARTIAL",
                    ready ? "Voice provider is ready." : "Voice provider is registered but not ready.", ready, true,
                    ready, null, ready ? List.of() : List.of("provider readiness"), OffsetDateTime.now()));
        }
        List<CapabilityHealth> capabilities = new java.util.ArrayList<>();
        if (reminderVoiceRenderer != null) {
            ReminderVoiceRenderer.Readiness tts = reminderVoiceRenderer.readiness();
            capabilities.add(new CapabilityHealth("REMINDER_TTS", tts.provider(), tts.status(),
                    tts.message() == null ? "Deterministic reminder TTS is ready." : tts.message()));
        }
        capabilities.add(new CapabilityHealth("DOTVOICE_STREAM", "dotvoice",
                dotVoiceMediaStreamClient != null && dotVoiceMediaStreamClient.isReady() ? "READY" : "NOT_CONFIGURED",
                dotVoiceMediaStreamClient != null && dotVoiceMediaStreamClient.isReady()
                        ? "DotVoice media stream is configured." : "DotVoice media streaming is not configured."));
        return new HealthResponse(List.copyOf(channels), correlationId, List.copyOf(capabilities));
    }

    public Result email(UUID tenantId, TestRequest request, String correlationId) {
        validateEmail(request);
        OffsetDateTime started = OffsetDateTime.now();
        String requestId = UUID.randomUUID().toString();
        if (StringUtils.hasText(request.provider()) && !"carepilot-email-smtp".equals(request.provider())) {
            if (!"msg91-email-smtp".equals(request.provider()) || msg91EmailMessageProvider == null || !msg91EmailMessageProvider.isConfigured()) {
                return failed(requestId, correlationId, "EMAIL", StringUtils.hasText(request.provider()) ? request.provider() : "EMAIL_NOT_CONFIGURED", started, "NOT_CONFIGURED", "Selected email provider is not configured.");
            }
        } else if (operationalMessageProvider(MessageChannel.EMAIL) == null) {
            return failed(requestId, correlationId, "EMAIL", "EMAIL_NOT_CONFIGURED", started, "NOT_CONFIGURED", "No production email provider is configured.");
        }
        MessageResult result;
        try {
            MessageRequest messageRequest = new MessageRequest(
                    tenantId, MessageChannel.EMAIL,
                    new MessageRecipient(request.recipient().trim(), null),
                    request.subject() == null ? "Jeevanam Email Test" : request.subject().trim(),
                    request.message().trim(), null, correlationId, null, UUID.fromString(requestId),
                    Map.of("communicationTest", "true", "testMode", mode(request)));
            result = "msg91-email-smtp".equals(request.provider())
                    ? msg91EmailMessageProvider.send(messageRequest)
                    : messageOrchestratorService.send(messageRequest);
        } catch (RuntimeException ex) {
            return failed(requestId, correlationId, "EMAIL", "carepilot-orchestrator", started, "DISPATCH_EXCEPTION", "Email test could not be dispatched.");
        }
        return messageResult(requestId, correlationId, "EMAIL", started, result);
    }

    public Result whatsapp(UUID tenantId, TestRequest request, String correlationId) {
        validatePhone(request.recipient());
        OffsetDateTime started = OffsetDateTime.now();
        String requestId = UUID.randomUUID().toString();
        if (operationalMessageProvider(MessageChannel.WHATSAPP) == null) {
            return failed(requestId, correlationId, "WHATSAPP", "WHATSAPP_NOT_CONFIGURED", started, "NOT_CONFIGURED", "No production WhatsApp provider is configured.");
        }
        MessageResult result;
        try {
            result = messageOrchestratorService.send(new MessageRequest(
                    tenantId, MessageChannel.WHATSAPP,
                    new MessageRecipient(request.recipient().trim(), null), null, request.message().trim(), null,
                    correlationId, null, UUID.fromString(requestId), Map.of("communicationTest", "true", "testMode", mode(request))));
        } catch (RuntimeException ex) {
            return failed(requestId, correlationId, "WHATSAPP", "carepilot-orchestrator", started, "DISPATCH_EXCEPTION", "WhatsApp test could not be dispatched.");
        }
        return messageResult(requestId, correlationId, "WHATSAPP", started, result);
    }

    public Result voice(UUID tenantId, TestRequest request, String correlationId) {
        validatePhone(request.recipient());
        OffsetDateTime started = OffsetDateTime.now();
        String requestId = UUID.randomUUID().toString();
        VoiceCallProvider provider = operationalVoiceProvider(request.provider());
        log.info("COMM_TEST_VOICE_START testExecutionId={} provider={} mode={} language={}",
                requestId, provider == null ? request.provider() : provider.providerName(),
                request.mode() == null ? TestMode.PROVIDER_ONLY : request.mode(), request.language());
        if (provider == null) {
            return failed(requestId, correlationId, "VOICE", "VOICE_NOT_CONFIGURED", started, "NOT_CONFIGURED", "No production voice provider is configured.");
        }
        if (!provider.isReady()) {
            return failed(requestId, correlationId, "VOICE", provider.providerName(), started, "NOT_READY", "Voice provider is not ready for outbound calls.");
        }
        if (request.mode() == TestMode.REMINDER_SIMULATION) {
            return reminderVoice(tenantId, request, correlationId, provider, requestId, started);
        }
        if (request.mode() == TestMode.INTERACTIVE_RESPONSE
                && "dotvoice".equalsIgnoreCase(provider.providerName()) && voiceOrchestratorService != null
                && contextRegistry != null && responseInterpreter != null && reminderVoiceRenderer != null
                && dotVoiceMediaStreamClient != null) {
            return interactiveVoiceTest(tenantId, request, correlationId, provider, requestId, started);
        }
        try {
            VoiceCallResult result = provider.placeCall(new VoiceCallRequest(
                    tenantId, null, UUID.fromString(requestId), request.recipient().trim(), request.message().trim(), started,
                    Map.of("communicationTest", "true", "correlationId", correlationId)));
            log.info("COMM_TEST_CALL_CREATED testExecutionId={} providerCallId={} providerStatus={}",
                    requestId, result.providerCallId(), result.status());
            return new Result(requestId, correlationId, "VOICE", result.providerName(), result.providerCallId(),
                    result.status().name(), result.status() == com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus.COMPLETED
                            || result.status() == com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus.QUEUED,
                    result.startedAt() == null ? started : result.startedAt(), result.endedAt() == null ? OffsetDateTime.now() : result.endedAt(),
                    result.failureReason(), sanitize(result.failureReason()));
        } catch (RuntimeException ex) {
            return failed(requestId, correlationId, "VOICE", provider.providerName(), started, "PROVIDER_ERROR", "Voice provider test failed.");
        }
    }

    private Result interactiveVoiceTest(UUID tenantId, TestRequest request, String correlationId,
                                        VoiceCallProvider provider, String requestId, OffsetDateTime started) {
        ReminderVoiceRenderer.RenderedReminder rendered = reminderVoiceRenderer.render(request.message().trim(), request.language());
        if (rendered.audio() == null) return failed(requestId, correlationId, "VOICE", provider.providerName(), started,
                rendered.failureCategory(), "Test prompt audio could not be prepared.");
        List<byte[]> frames;
        try {
            var pcm = ReminderAudioNormalizer.normalize(rendered.audio().audioBytes(), rendered.audio().contentType(), rendered.sourceSampleRate());
            frames = G711MuLawAudioConverter.toFramesPcm(pcm.bytes(), pcm.sampleRate(), pcm.channels());
            log.info("COMM_TEST_TTS_AUDIO_FORMAT provider={} contentType={} format={} sampleRate={} audioBytes={}",
                    rendered.provider(), rendered.audio().contentType(), pcm.inputFormat(), pcm.sampleRate(), rendered.audio().audioBytes().length);
        } catch (RuntimeException ex) {
            return failed(requestId, correlationId, "VOICE", provider.providerName(), started, "AUDIO_FORMAT_FAILED", "Test prompt audio could not be prepared.");
        }
        if (!dotVoiceMediaStreamClient.prepare()) return failed(requestId, correlationId, "VOICE", provider.providerName(), started,
                "STREAM_UNAVAILABLE", "DotVoice media stream is unavailable.");
        VoiceCallResult call = provider.placeCall(new VoiceCallRequest(tenantId, null, UUID.fromString(requestId), request.recipient().trim(), request.message().trim(), started,
                Map.of("communicationTest", "true", "correlationId", correlationId, "interactiveResponseTest", "true")));
        log.info("COMM_TEST_CALL_CREATED testExecutionId={} providerCallId={} providerStatus={}",
                requestId, call.providerCallId(), call.status());
        if (!StringUtils.hasText(call.providerCallId())) return failed(requestId, correlationId, "VOICE", provider.providerName(), started, "CALL_NOT_INITIATED", "Provider did not return a call ID.");
        if (call.status() == com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus.NO_ANSWER
                || call.status() == com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus.BUSY
                || call.status() == com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus.FAILED) {
            return new Result(requestId, correlationId, "VOICE", call.providerName(), call.providerCallId(), call.status().name(), false,
                    started, OffsetDateTime.now(), call.failureReason(), sanitize(call.failureReason()));
        }
        contextRegistry.register(new VoiceConversationContextRegistry.VoiceConversationContext(call.providerCallId(), UUID.fromString(requestId), tenantId, null,
                com.deepthoughtnet.clinic.carepilot.ai_call.model.VoiceInteractionType.GENERIC_CONFIRMATION,
                request.language(), "platform-confirmation-test", 1));
        log.info("COMM_TEST_CONTEXT_REGISTERED testExecutionId={} providerCallId={} interactionType={}",
                requestId, call.providerCallId(), "GENERIC_CONFIRMATION");
        Result initial = new Result(requestId, correlationId, "VOICE", call.providerName(), call.providerCallId(), "INITIATED", true,
                started, null, null, null, requestId, null, null, request.language(), null, null, 1, "NONE", null, null);
        voiceResults.put(requestId, initial);
        log.info("COMM_TEST_RESULT_UPDATED testExecutionId={} status={} intent={}", requestId, "INITIATED", null);
        log.info("DOTVOICE_PLAYBACK_START providerCallId={} purpose={} frameCount={} audioBytes={}",
                call.providerCallId(), "PROMPT", frames.size(), frames.size() * 160);
        if (!dotVoiceMediaStreamClient.startPlayback(call.providerCallId(), frames)) {
            return failed(requestId, correlationId, "VOICE", provider.providerName(), started, "STREAM_UNAVAILABLE", "DotVoice prompt playback could not start.");
        }
        return initial;
    }

    private Result reminderVoice(UUID tenantId, TestRequest request, String correlationId,
                                 VoiceCallProvider provider, String requestId, OffsetDateTime started) {
        if (!"dotvoice".equalsIgnoreCase(provider.providerName()) || reminderVoiceRenderer == null || dotVoiceMediaStreamClient == null) {
            return failed(requestId, correlationId, "VOICE", provider.providerName(), started, "PLAYBACK_NOT_CONFIGURED", "Reminder playback is not configured.");
        }
        ReminderVoiceRenderer.RenderedReminder rendered = reminderVoiceRenderer.render(request.message().trim(), request.language());
        if (rendered.audio() == null) {
            return failed(requestId, correlationId, "VOICE", provider.providerName(), started, rendered.failureCategory(), "Reminder audio could not be prepared.");
        }
        List<byte[]> frames;
        try {
            ReminderAudioNormalizer.CanonicalPcm pcm = ReminderAudioNormalizer.normalize(
                    rendered.audio().audioBytes(), rendered.audio().contentType(), rendered.sourceSampleRate());
            frames = G711MuLawAudioConverter.toFramesPcm(pcm.bytes(), pcm.sampleRate(), pcm.channels());
            org.slf4j.LoggerFactory.getLogger(CommunicationTestService.class).info(
                    "REMINDER_AUDIO_PREPARED provider={} inputFormat={} inputBytes={} targetEncoding=mulaw targetSampleRate=8000 frameSize=160 frameCount={} ready=true",
                    rendered.provider(), pcm.inputFormat(), rendered.audio().audioBytes().length, frames.size());
        } catch (RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger(CommunicationTestService.class).warn(
                    "REMINDER_AUDIO_PREPARE_FAILED provider={} reason={}", provider.providerName(),
                    ex.getMessage() == null ? "audio_normalization_failed" : ex.getMessage());
            return failed(requestId, correlationId, "VOICE", provider.providerName(), started, "AUDIO_FORMAT_FAILED", "Reminder audio format could not be prepared.");
        }
        if (!dotVoiceMediaStreamClient.prepare()) {
            return failed(requestId, correlationId, "VOICE", provider.providerName(), started,
                    "STREAM_READY_NOT_RECEIVED", "DotVoice media stream is not ready; no call was placed.");
        }
        try {
            VoiceCallResult call = provider.placeCall(new VoiceCallRequest(
                    tenantId, null, UUID.fromString(requestId), request.recipient().trim(), request.message().trim(), started,
                    Map.of("communicationTest", "true", "correlationId", correlationId, "reminderPlayback", "true")));
            log.info("COMM_TEST_CALL_CREATED testExecutionId={} providerCallId={} providerStatus={}",
                    requestId, call.providerCallId(), call.status());
            if (call.providerCallId() == null || call.status() == com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus.NO_ANSWER
                    || call.status() == com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus.FAILED) {
                dotVoiceMediaStreamClient.abort();
                return new Result(requestId, correlationId, "VOICE", call.providerName(), call.providerCallId(), call.status().name(), false,
                        started, OffsetDateTime.now(), call.failureReason(), sanitize(call.failureReason()));
            }
            DotVoiceMediaStreamClient.PlaybackResult playback = dotVoiceMediaStreamClient.play(call.providerCallId(), frames);
            return new Result(requestId, correlationId, "VOICE", call.providerName(), call.providerCallId(), playback.status(), playback.success(),
                    started, OffsetDateTime.now(), playback.failureCategory(), sanitize(playback.failureCategory()));
        } catch (RuntimeException ex) {
            dotVoiceMediaStreamClient.abort();
            return failed(requestId, correlationId, "VOICE", provider.providerName(), started, "PLAYBACK_FAILED", "Reminder playback failed.");
        }
    }

    private ChannelHealth mapHealth(ProviderStatusResponse row) {
        String status = row.status().name();
        if ("DISABLED".equals(status)) status = "NOT_CONFIGURED";
        boolean operational = isOperationalProvider(row.providerName());
        if (!operational) status = "NOT_CONFIGURED";
        String message = operational ? row.message() : "No production " + row.channel().name().toLowerCase(java.util.Locale.ROOT) + " provider is configured.";
        return new ChannelHealth(row.channel().name(), row.providerName(), status, message, operational && row.configured(),
                operational && row.available(), operational && row.supportsTestSend(), row.channel() == MessageChannel.EMAIL && row.fromAddressConfigured() && operational ? "configured" : null,
                operational ? row.missingConfigurationKeys() : List.of("production provider"), row.lastCheckedAt());
    }

    private ChannelHealth msg91Health() {
        boolean ready = msg91EmailMessageProvider.isConfigured();
        List<String> missing = new java.util.ArrayList<>();
        if (!ready) {
            missing.add("clinic.carepilot.messaging.email.providers.msg91.enabled");
            if (!StringUtils.hasText(msg91EmailMessageProvider.host())) missing.add("clinic.carepilot.messaging.email.providers.msg91.host");
            if (msg91EmailMessageProvider.port() <= 0 || msg91EmailMessageProvider.port() > 65535) missing.add("clinic.carepilot.messaging.email.providers.msg91.port");
            if (!StringUtils.hasText(msg91EmailMessageProvider.username())) missing.add("clinic.carepilot.messaging.email.providers.msg91.username");
            if (!msg91EmailMessageProvider.passwordConfigured()) missing.add("clinic.carepilot.messaging.email.providers.msg91.password");
            if (!msg91EmailMessageProvider.fromConfigured()) missing.add("clinic.carepilot.messaging.email.providers.msg91.from");
        }
        return new ChannelHealth("EMAIL", msg91EmailMessageProvider.providerName(), ready ? "READY" : "NOT_CONFIGURED",
                ready ? "MSG91 SMTP provider is configured and ready." : "MSG91 SMTP configuration is incomplete.",
                ready, ready, ready, ready ? "configured" : null, List.copyOf(missing), OffsetDateTime.now());
    }

    private VoiceCallProvider operationalVoiceProvider() {
        return operationalVoiceProvider(null);
    }

    private VoiceCallProvider operationalVoiceProvider(String requestedProvider) {
        return voiceProviders.stream()
                .filter(provider -> isOperationalProvider(provider.providerName()))
                .filter(provider -> !StringUtils.hasText(requestedProvider)
                        || provider.providerName().equalsIgnoreCase(requestedProvider.trim()))
                .findFirst()
                .orElse(null);
    }

    private MessageProvider operationalMessageProvider(MessageChannel channel) {
        return messageProviders.stream()
                .filter(provider -> provider.supports(channel))
                // MSG91 is selected explicitly by the Communication Test request; keep it out
                // of the legacy implicit test-provider path.
                .filter(provider -> !"msg91-email-smtp".equalsIgnoreCase(provider.providerName()))
                .filter(provider -> isOperationalProvider(provider.providerName()))
                .findFirst()
                .orElse(null);
    }

    /** No provider metadata exists in the current SPIs; classify conventional test-only names locally. */
    private boolean isOperationalProvider(String providerName) {
        if (!StringUtils.hasText(providerName)) return false;
        String normalized = providerName.trim().toLowerCase(java.util.Locale.ROOT);
        return !normalized.matches(".*(?:^|[-_:])(mock|test|fake|stub)(?:$|[-_:]).*")
                && !normalized.matches("^(mock|test|fake|stub).*");
    }

    private void validateEmail(TestRequest request) {
        if (request == null || !StringUtils.hasText(request.recipient()) || !EMAIL.matcher(request.recipient().trim()).matches()) {
            throw new IllegalArgumentException("Recipient must be a valid email address");
        }
        if (!StringUtils.hasText(request.message())) throw new IllegalArgumentException("Message is required");
        if (request.subject() != null && request.subject().length() > 200) throw new IllegalArgumentException("Subject is too long");
    }

    private void validatePhone(String value) {
        if (!StringUtils.hasText(value) || !PHONE.matcher(value.trim()).matches()) throw new IllegalArgumentException("Recipient must be a valid phone number");
    }

    private Result messageResult(String requestId, String correlationId, String channel, OffsetDateTime started, MessageResult result) {
        return new Result(requestId, correlationId, channel, result.providerName(), result.providerMessageId(), result.status().name(), result.success(),
                started, OffsetDateTime.now(), result.errorCode(), sanitize(result.errorMessage()));
    }

    private Result failed(String requestId, String correlationId, String channel, String provider, OffsetDateTime started, String category, String message) {
        return new Result(requestId, correlationId, channel, provider, null, MessageDeliveryStatus.FAILED.name(), false, started, OffsetDateTime.now(), category, message);
    }

    private String mode(TestRequest request) { return request.mode() == null ? TestMode.PROVIDER_ONLY.name() : request.mode().name(); }
    private String sanitize(String value) { return value == null ? null : value.replaceAll("(?i)(api[-_ ]?key|auth[-_ ]?key|password|secret)[=:][^,; ]+", "$1=[redacted]"); }
}
