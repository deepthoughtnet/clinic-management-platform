package com.deepthoughtnet.clinic.api.patientportal.voice;

import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiMessageRequest;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiMessageResponse;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiProgressEvent;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiResponseComposerService;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiService;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiStateResponse;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalConversationStateService;
import com.deepthoughtnet.clinic.api.voice.VoiceOrchestratorService;
import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2VoiceTurnConnector;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2ProgressEvent;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageResponse;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisResult;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionResult;
import com.deepthoughtnet.clinic.tts.spi.VoiceTextNormalizer;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PatientPortalVoiceAssistantService {
    private static final String CAREAI_PROVIDER = "PATIENT_PORTAL_CAREAI";
    private static final Logger log = LoggerFactory.getLogger(PatientPortalVoiceAssistantService.class);

    private final VoiceOrchestratorService voiceOrchestratorService;
    private final PatientPortalCareAiService patientPortalCareAiService;
    private final PatientPortalCareAiResponseComposerService responseComposerService;
    private final PatientPortalConversationStateService conversationStateService;
    private final VoiceTestProperties voiceProperties;
    private final AivaV2VoiceTurnConnector aivaV2VoiceTurnConnector;
    private final VoiceTextNormalizer voiceTextNormalizer = new VoiceTextNormalizer();

    public PatientPortalVoiceAssistantService(
            VoiceOrchestratorService voiceOrchestratorService,
            PatientPortalCareAiService patientPortalCareAiService
    ) {
        this(voiceOrchestratorService, patientPortalCareAiService, new VoiceTestProperties(), null, null, null);
    }

    public PatientPortalVoiceAssistantService(
            VoiceOrchestratorService voiceOrchestratorService,
            PatientPortalCareAiService patientPortalCareAiService,
            VoiceTestProperties voiceProperties,
            PatientPortalCareAiResponseComposerService responseComposerService
    ) {
        this(voiceOrchestratorService, patientPortalCareAiService, voiceProperties, responseComposerService, null, null);
    }

    @Autowired
    public PatientPortalVoiceAssistantService(
            VoiceOrchestratorService voiceOrchestratorService,
            PatientPortalCareAiService patientPortalCareAiService,
            VoiceTestProperties voiceProperties,
            PatientPortalCareAiResponseComposerService responseComposerService,
            PatientPortalConversationStateService conversationStateService,
            AivaV2VoiceTurnConnector aivaV2VoiceTurnConnector
    ) {
        this.voiceOrchestratorService = voiceOrchestratorService;
        this.patientPortalCareAiService = patientPortalCareAiService;
        this.voiceProperties = voiceProperties;
        this.responseComposerService = responseComposerService;
        this.conversationStateService = conversationStateService;
        this.aivaV2VoiceTurnConnector = aivaV2VoiceTurnConnector;
    }

    public PatientPortalVoiceAssistantService(
            VoiceOrchestratorService voiceOrchestratorService,
            PatientPortalCareAiService patientPortalCareAiService,
            VoiceTestProperties voiceProperties,
            PatientPortalCareAiResponseComposerService responseComposerService,
            PatientPortalConversationStateService conversationStateService
    ) {
        this(voiceOrchestratorService, patientPortalCareAiService, voiceProperties,
                responseComposerService, conversationStateService, null);
    }

    /** Processes a finalized Care voice transcript through the certified V2 boundary. */
    public PatientPortalVoiceTurnResponse processAudioTurnV2(
            byte[] audioBytes,
            String contentType,
            String originalFilename,
            String language,
            String conversationId,
            String clientTurnId,
            long clientTurnSequence
    ) {
        return processAudioTurnV2(audioBytes, contentType, originalFilename, language,
                conversationId, clientTurnId, clientTurnSequence, null);
    }

    public PatientPortalVoiceTurnResponse processAudioTurnV2(
            byte[] audioBytes,
            String contentType,
            String originalFilename,
            String language,
            String conversationId,
            String clientTurnId,
            long clientTurnSequence,
            Consumer<AivaV2ProgressEvent> progressSink
    ) {
        if (aivaV2VoiceTurnConnector == null) {
            throw new IllegalStateException("AIVA V2 voice connector is unavailable.");
        }
        Instant requestStart = Instant.now();
        Instant sttStart = Instant.now();
        VoiceTranscriptionResult transcription = voiceOrchestratorService.transcribeBufferedAudio(
                audioBytes, contentType, originalFilename, language);
        long sttDurationMs = Duration.between(sttStart, Instant.now()).toMillis();
        // Empty STT finals are transport noise, not patient turns.  Ignore them before
        // the V2 connector so no semantic turn, Redis sequence, or fallback response is created.
        if (!StringUtils.hasText(transcription.transcript())) {
            log.info("patient.voice.v2.stt.blank conversationIdHash={} engine=v2 transcriptLength={} blank=true clientSequence={}",
                    conversationId == null ? null : Integer.toHexString(conversationId.hashCode()),
                    transcription.transcript() == null ? 0 : transcription.transcript().length(),
                    clientTurnSequence);
            return null;
        }
        MessageResponse response = progressSink == null
                ? aivaV2VoiceTurnConnector.submitFinalTranscript(
                        conversationId, transcription.transcript(), language, clientTurnId, clientTurnSequence)
                : aivaV2VoiceTurnConnector.submitFinalTranscript(
                        conversationId, transcription.transcript(), language, clientTurnId, clientTurnSequence, progressSink);
        String assistantText = response.assistantMessage();
        Instant ttsStart = Instant.now();
        VoiceSynthesisResult synthesis = null;
        String ttsFallbackReason = null;
        try {
            String voiceLanguage = resolveV2VoiceLanguage(transcription.transcript(), language);
            synthesis = voiceOrchestratorService.synthesizeRenderedAssistantText(
                    voiceTextNormalizer.normalizeTemporalValuesForVoice(assistantText, voiceLanguage), voiceLanguage);
        } catch (RuntimeException ex) {
            ttsFallbackReason = "Voice playback unavailable";
        }
        long ttsDurationMs = Duration.between(ttsStart, Instant.now()).toMillis();
        byte[] audioPayload = playableAudioBytes(synthesis);
        return new PatientPortalVoiceTurnResponse(
                response.turnId(),
                transcription.transcript(),
                assistantText,
                null,
                synthesis == null ? null : synthesis.contentType(),
                audioPayload == null ? null : Base64.getEncoder().encodeToString(audioPayload),
                transcription.providerName(),
                "AIVA_V2",
                synthesis == null ? null : synthesis.providerName(),
                sttDurationMs,
                0L,
                ttsDurationMs,
                Duration.between(requestStart, Instant.now()).toMillis(),
                audioBytes == null ? 0 : audioBytes.length,
                ttsFallbackReason,
                "CONVERSATION_CLOSED".equals(response.responseCategory())
        );
    }

    private String resolveV2VoiceLanguage(String transcript, String requestedLanguage) {
        String responseLanguage = aivaV2VoiceTurnConnector.responseLanguageForVoice(transcript, requestedLanguage);
        return responseLanguage != null && responseLanguage.toLowerCase(java.util.Locale.ROOT).startsWith("hi")
                ? "hi-IN" : "en-IN";
    }

    public PatientPortalVoiceTurnResponse processAudioTurn(
            byte[] audioBytes,
            String contentType,
            String originalFilename,
            String language
    ) {
        return processAudioTurn(audioBytes, contentType, originalFilename, language, null);
    }

    /** Synthesizes only ephemeral progress speech through the existing voice provider path. */
    public PatientPortalVoiceProgressAudio synthesizeProgressAudio(
            PatientPortalCareAiProgressEvent progress,
            String language
    ) {
        if (progress == null || !StringUtils.hasText(progress.acknowledgement())) {
            return null;
        }
        return synthesizeProgressAudio(progress.acknowledgement(), language, progress.skillExecutionId());
    }

    public PatientPortalVoiceProgressAudio synthesizeV2ProgressAudio(String acknowledgement, String language) {
        return synthesizeProgressAudio(acknowledgement, language, "aiva-v2-progress");
    }

    private PatientPortalVoiceProgressAudio synthesizeProgressAudio(String acknowledgement, String language,
                                                                     String diagnosticId) {
        try {
            String voiceText = voiceTextNormalizer.normalizeForVoice(acknowledgement, language);
            VoiceSynthesisResult synthesis = voiceOrchestratorService.synthesizeAssistantText(voiceText, language);
            byte[] audio = playableAudioBytes(synthesis);
            if (audio == null || audio.length == 0 || !StringUtils.hasText(synthesis.contentType())) {
                return null;
            }
            return new PatientPortalVoiceProgressAudio(
                    synthesis.contentType(),
                    Base64.getEncoder().encodeToString(audio),
                    synthesis.providerName()
            );
        } catch (RuntimeException ex) {
            log.debug("patient.voice.progress.tts.unavailable skillExecutionId={} reason={}",
                    diagnosticId, ex.getClass().getSimpleName());
            return null;
        }
    }

    public PatientPortalVoiceTurnResponse processAudioTurn(
            byte[] audioBytes,
            String contentType,
            String originalFilename,
            String language,
            Consumer<PatientPortalCareAiProgressEvent> progressSink
    ) {
        Instant requestStart = Instant.now();
        log.info("patient.voice.turn.request.received contentType={} sizeBytes={} language={}",
                contentType,
                audioBytes == null ? 0 : audioBytes.length,
                language);
        Instant sttStart = Instant.now();
        log.info("patient.voice.stt.start");
        VoiceTranscriptionResult transcription;
        try {
            transcription = voiceOrchestratorService.transcribeBufferedAudio(
                    audioBytes,
                    contentType,
                    originalFilename,
                    language
            );
        } catch (RuntimeException ex) {
            long sttDurationMs = Duration.between(sttStart, Instant.now()).toMillis();
            String assistantText = "Voice transcription is temporarily unavailable. Please type your request or try again.";
            log.warn("patient.voice.stt.unavailable contentType={} sizeBytes={} durationMs={} exception={} reason={}",
                    contentType,
                    audioBytes == null ? 0 : audioBytes.length,
                    sttDurationMs,
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
            return new PatientPortalVoiceTurnResponse(
                    UUID.randomUUID().toString(),
                    "",
                    assistantText,
                    null,
                    null,
                    null,
                    null,
                    CAREAI_PROVIDER,
                    null,
                    sttDurationMs,
                    0L,
                    0L,
                    Duration.between(requestStart, Instant.now()).toMillis(),
                    audioBytes == null ? 0 : audioBytes.length,
                    ex.getMessage() == null ? "Speech recognition unavailable" : ex.getMessage()
            );
        }
        long sttDurationMs = Duration.between(sttStart, Instant.now()).toMillis();
        log.info("patient.voice.stt.complete provider={} durationMs={}", transcription.providerName(), sttDurationMs);
        if ("mock".equalsIgnoreCase(transcription.providerName())) {
            String assistantText = "Voice transcription is temporarily unavailable. Please type your request or try again.";
            log.warn("patient.voice.stt.mock-guard provider={} durationMs={} reason={}",
                    transcription.providerName(),
                    sttDurationMs,
                    transcription.providerMessage());
            return new PatientPortalVoiceTurnResponse(
                    UUID.randomUUID().toString(),
                    "",
                    assistantText,
                    null,
                    null,
                    null,
                    transcription.providerName(),
                    CAREAI_PROVIDER,
                    null,
                    sttDurationMs,
                    0L,
                    0L,
                    Duration.between(requestStart, Instant.now()).toMillis(),
                    audioBytes == null ? 0 : audioBytes.length,
                    transcription.providerMessage()
            );
        }
        String conversationId = currentConversationId();
        boolean resetRequested = conversationStateService != null
                && StringUtils.hasText(conversationId)
                && conversationStateService.isResetRequest(transcription.transcript());
        if (conversationStateService != null && StringUtils.hasText(conversationId)) {
            if (conversationStateService.isExpired(conversationId)) {
                patientPortalCareAiService.resetVoiceConversation();
                conversationStateService.clear(conversationId);
            } else if (resetRequested) {
                patientPortalCareAiService.resetVoiceConversation();
                conversationStateService.clear(conversationId);
            }
        }
        if (!StringUtils.hasText(transcription.transcript())) {
            throw new IllegalStateException("No speech was captured. Please try again.");
        }

        Instant careAiStart = Instant.now();
        log.info("patient.voice.careai.start");
        PatientPortalCareAiMessageRequest careAiRequest =
                new PatientPortalCareAiMessageRequest(transcription.transcript(), language);
        PatientPortalCareAiMessageResponse careAiResponse = progressSink == null
                ? patientPortalCareAiService.messageFromVoice(careAiRequest)
                : patientPortalCareAiService.messageFromVoice(careAiRequest, progressSink);
        long careAiDurationMs = Duration.between(careAiStart, Instant.now()).toMillis();
        log.info("patient.voice.careai.complete durationMs={}", careAiDurationMs);
        String assistantText = responseComposerService == null
                ? careAiResponse.assistantMessage()
                : responseComposerService.compose(
                        careAiResponse.assistantMessage(),
                        resolveResponseType(careAiResponse.state()),
                        resolveVoiceLanguage(careAiResponse.state() == null ? language : careAiResponse.state().language()),
                        resolveWorkflow(careAiResponse.state()),
                        safeStructuredFacts(careAiResponse.state())
                );
        String voiceAssistantText = voiceTextNormalizer.normalizeForVoice(
                assistantText,
                resolveVoiceLanguage(careAiResponse.state() == null ? language : careAiResponse.state().language())
        );
        if (conversationStateService != null && StringUtils.hasText(conversationId)) {
            conversationStateService.recordTurn(
                    conversationId,
                    conversationStateService.turn(
                            transcription.transcript(),
                            resolveVoiceLanguage(careAiResponse.state() == null ? language : careAiResponse.state().language()),
                            careAiResponse.state() == null ? null : careAiResponse.state().currentIntent(),
                            careAiResponse.state() == null ? null : careAiResponse.state().currentIntent(),
                            currentEntities(careAiResponse.state()),
                            pendingEntities(careAiResponse.state()),
                            completedSteps(careAiResponse.state()),
                            careAiResponse.state() != null && careAiResponse.state().confirmationPending(),
                            assistantText,
                            resolveResponseType(careAiResponse.state()),
                            resetRequested
                    )
            );
        }
        VoiceSynthesisResult synthesis = null;
        long ttsDurationMs = 0L;
        String ttsFallbackReason = null;
        Instant ttsStart = Instant.now();
        try {
            log.info("patient.voice.tts.start");
            synthesis = voiceOrchestratorService.synthesizeAssistantText(
                    voiceAssistantText,
                    resolveVoiceLanguage(careAiResponse.state() == null ? language : careAiResponse.state().language())
            );
            ttsDurationMs = Duration.between(ttsStart, Instant.now()).toMillis();
            log.info("patient.voice.tts.complete provider={} durationMs={} playableAudio={}",
                    synthesis == null ? null : synthesis.providerName(),
                    ttsDurationMs,
                    playableAudioBytes(synthesis) != null);
        } catch (RuntimeException ex) {
            ttsDurationMs = Duration.between(ttsStart, Instant.now()).toMillis();
            ttsFallbackReason = ex.getMessage() == null ? "Voice playback unavailable" : ex.getMessage();
            log.warn("patient.voice.tts.fallback reason={} durationMs={}", ttsFallbackReason, ttsDurationMs);
        }
        byte[] audioPayload = playableAudioBytes(synthesis);
        long totalDurationMs = Duration.between(requestStart, Instant.now()).toMillis();
        log.info("patient.voice.turn.request.complete totalDurationMs={} sttDurationMs={} careAiDurationMs={} ttsDurationMs={} hasAudio={}",
                totalDurationMs, sttDurationMs, careAiDurationMs, ttsDurationMs, audioPayload != null);

        return new PatientPortalVoiceTurnResponse(
                UUID.randomUUID().toString(),
                transcription.transcript(),
                assistantText,
                careAiResponse.state(),
                synthesis == null ? null : synthesis.contentType(),
                audioPayload == null ? null : Base64.getEncoder().encodeToString(audioPayload),
                transcription.providerName(),
                CAREAI_PROVIDER,
                synthesis == null ? null : synthesis.providerName(),
                sttDurationMs,
                careAiDurationMs,
                ttsDurationMs,
                totalDurationMs,
                audioBytes == null ? 0 : audioBytes.length,
                ttsFallbackReason
        );
    }

    private byte[] playableAudioBytes(VoiceSynthesisResult synthesis) {
        if (synthesis == null
                || synthesis.audioBytes() == null
                || synthesis.audioBytes().length == 0
                || !StringUtils.hasText(synthesis.contentType())
                || !synthesis.contentType().startsWith("audio/")) {
            return null;
        }
        return synthesis.audioBytes();
    }

    private String resolveVoiceLanguage(String candidate) {
        if (StringUtils.hasText(candidate)) {
            String normalized = candidate.trim();
            if (!"auto".equalsIgnoreCase(normalized) && !"auto-detect".equalsIgnoreCase(normalized)) {
                return normalized;
            }
        }
        if (StringUtils.hasText(voiceProperties.getResponseLanguage())) {
            return voiceProperties.getResponseLanguage().trim();
        }
        return "hi-IN";
    }

    private String resolveResponseType(PatientPortalCareAiStateResponse state) {
        if (state == null) {
            return "general";
        }
        if (state.handoffRequired()) {
            return "handoff";
        }
        if (state.booked()) {
            return "booked_success";
        }
        if (state.confirmationPending()) {
            return "confirmation_prompt";
        }
        if (state.slotOptions() != null && !state.slotOptions().isEmpty()) {
            return "slot_list";
        }
        return StringUtils.hasText(state.currentIntent()) ? state.currentIntent().toLowerCase() : "general";
    }

    private String resolveWorkflow(PatientPortalCareAiStateResponse state) {
        if (state == null || !StringUtils.hasText(state.currentIntent())) {
            return "general";
        }
        return state.currentIntent();
    }

    private PatientPortalCareAiResponseComposerService.SafeStructuredFacts safeStructuredFacts(PatientPortalCareAiStateResponse state) {
        if (state == null) {
            return new PatientPortalCareAiResponseComposerService.SafeStructuredFacts(
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    false,
                    null
            );
        }
        return new PatientPortalCareAiResponseComposerService.SafeStructuredFacts(
                state.doctorName(),
                null,
                state.booked() ? state.bookedAppointmentDate() : state.preferredDate(),
                state.booked() ? state.bookedAppointmentTime() : state.suggestedSlot(),
                state.slotOptions(),
                state.appointmentOptions(),
                state.confirmationPending(),
                state.lastAction()
        );
    }

    private Map<String, Object> currentEntities(PatientPortalCareAiStateResponse state) {
        if (state == null) {
            return Map.of();
        }
        Map<String, Object> entities = new LinkedHashMap<>();
        entities.put("doctorName", state.doctorName());
        entities.put("speciality", state.speciality());
        entities.put("selectedAppointment", state.selectedAppointment());
        entities.put("preferredDate", state.preferredDate());
        entities.put("preferredTimeWindow", state.preferredTimeWindow());
        entities.put("suggestedSlot", state.suggestedSlot());
        entities.put("bookedAppointmentDate", state.bookedAppointmentDate());
        entities.put("bookedAppointmentTime", state.bookedAppointmentTime());
        entities.put("bookingStatus", state.bookingStatus());
        return entities;
    }

    private Map<String, Object> pendingEntities(PatientPortalCareAiStateResponse state) {
        if (state == null) {
            return Map.of();
        }
        Map<String, Object> entities = new LinkedHashMap<>();
        entities.put("confirmationPending", state.confirmationPending());
        entities.put("handoffRequired", state.handoffRequired());
        return entities;
    }

    private List<String> completedSteps(PatientPortalCareAiStateResponse state) {
        if (state == null) {
            return List.of();
        }
        List<String> steps = new java.util.ArrayList<>();
        if (state.booked()) {
            steps.add("booked");
        }
        if (state.actionCompleted()) {
            steps.add("actionCompleted");
        }
        if (StringUtils.hasText(state.bookingStatus())) {
            steps.add(state.bookingStatus());
        }
        return List.copyOf(steps);
    }

    private String currentConversationId() {
        return com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.get() == null
                ? null
                : com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.get().correlationId();
    }

    public record PatientPortalVoiceTurnResponse(
            String requestId,
            String transcript,
            String assistantText,
            PatientPortalCareAiStateResponse state,
            String audioContentType,
            String audioBase64,
            String sttProvider,
            String llmProvider,
            String ttsProvider,
            long sttDurationMs,
            long careAiDurationMs,
            long ttsDurationMs,
            long totalDurationMs,
            long captureBytes,
            String ttsFallbackReason,
            boolean conversationClosed
    ) {
        public PatientPortalVoiceTurnResponse(String requestId, String transcript, String assistantText,
                                              PatientPortalCareAiStateResponse state, String audioContentType,
                                              String audioBase64, String sttProvider, String llmProvider,
                                              String ttsProvider, long sttDurationMs, long careAiDurationMs,
                                              long ttsDurationMs, long totalDurationMs, long captureBytes,
                                              String ttsFallbackReason) {
            this(requestId, transcript, assistantText, state, audioContentType, audioBase64, sttProvider,
                    llmProvider, ttsProvider, sttDurationMs, careAiDurationMs, ttsDurationMs, totalDurationMs,
                    captureBytes, ttsFallbackReason, false);
        }
    }

    public record PatientPortalVoiceProgressAudio(
            String contentType,
            String audioBase64,
            String provider
    ) {
    }
}
