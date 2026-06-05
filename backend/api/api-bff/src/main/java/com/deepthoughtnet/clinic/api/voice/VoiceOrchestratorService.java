package com.deepthoughtnet.clinic.api.voice;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.api.voice.spi.SpeechToTextProvider;
import com.deepthoughtnet.clinic.api.voice.spi.TextToSpeechProvider;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisRequest;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisResult;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionRequest;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionResult;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VoiceOrchestratorService {
    private static final Logger log = LoggerFactory.getLogger(VoiceOrchestratorService.class);
    private static final String VOICE_TEST_ENTITY_TYPE = "VOICE_TEST";
    private static final String VOICE_TEST_SUCCESS = "VOICE_TEST_COMPLETED";
    private static final String VOICE_TEST_FAILED = "VOICE_TEST_FAILED";
    private static final Pattern JSON_ANSWER_PATTERN = Pattern.compile("\"answer\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private final List<SpeechToTextProvider> sttProviders;
    private final List<TextToSpeechProvider> ttsProviders;
    private final AiOrchestrationService aiOrchestrationService;
    private final AuditEventPublisher auditEventPublisher;
    private final VoiceTestProperties properties;
    private final ObjectMapper objectMapper;
    private final FasterWhisperSpeechToTextProvider fasterWhisperSpeechToTextProvider;
    private final PiperTextToSpeechProvider piperTextToSpeechProvider;
    private final VoiceAppointmentWorkflowService voiceAppointmentWorkflowService;

    public VoiceOrchestratorService(List<SpeechToTextProvider> sttProviders,
                                    List<TextToSpeechProvider> ttsProviders,
                                    AiOrchestrationService aiOrchestrationService,
                                    AuditEventPublisher auditEventPublisher,
                                    VoiceTestProperties properties,
                                    ObjectMapper objectMapper,
                                    FasterWhisperSpeechToTextProvider fasterWhisperSpeechToTextProvider,
                                    PiperTextToSpeechProvider piperTextToSpeechProvider) {
        this(sttProviders, ttsProviders, aiOrchestrationService, auditEventPublisher, properties, objectMapper,
                fasterWhisperSpeechToTextProvider, piperTextToSpeechProvider, null);
    }

    @Autowired
    public VoiceOrchestratorService(List<SpeechToTextProvider> sttProviders,
                                    List<TextToSpeechProvider> ttsProviders,
                                    AiOrchestrationService aiOrchestrationService,
                                    AuditEventPublisher auditEventPublisher,
                                    VoiceTestProperties properties,
                                    ObjectMapper objectMapper,
                                    FasterWhisperSpeechToTextProvider fasterWhisperSpeechToTextProvider,
                                    PiperTextToSpeechProvider piperTextToSpeechProvider,
                                    VoiceAppointmentWorkflowService voiceAppointmentWorkflowService) {
        this.sttProviders = sttProviders;
        this.ttsProviders = ttsProviders;
        this.aiOrchestrationService = aiOrchestrationService;
        this.auditEventPublisher = auditEventPublisher;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.fasterWhisperSpeechToTextProvider = fasterWhisperSpeechToTextProvider;
        this.piperTextToSpeechProvider = piperTextToSpeechProvider;
        this.voiceAppointmentWorkflowService = voiceAppointmentWorkflowService;
        log.info("voice.stt.providers.available={}", sttProviderRegistry().keySet().stream().toList());
    }

    public VoiceTestResponse processAudio(MultipartFile audio, String context, String language) {
        return processAudio(audio, context, language, null);
    }

    public VoiceTestResponse processAudio(MultipartFile audio, String context, String language, String workflowMode) {
        if (!properties.isEnabled()) {
            throw new IllegalArgumentException("Voice test is disabled.");
        }
        if (audio == null || audio.isEmpty()) {
            throw new IllegalArgumentException("Audio file is required.");
        }
        log.info("voice.audio.received filename={} contentType={} sizeBytes={}",
                safeFilename(audio.getOriginalFilename()),
                audio.getContentType(),
                audio.getSize());
        try {
            return processBufferedAudio(audio.getBytes(), audio.getContentType(), audio.getOriginalFilename(), context, language, workflowMode, null);
        } catch (IOException ex) {
            throw new IllegalStateException("Voice test failed: " + ex.getMessage(), ex);
        }
    }

    public VoiceSttDebugResponse debugStt(MultipartFile audio, String language) {
        if (!properties.isEnabled()) {
            throw new IllegalArgumentException("Voice test is disabled.");
        }
        if (audio == null || audio.isEmpty()) {
            throw new IllegalArgumentException("Audio file is required.");
        }
        try {
            byte[] audioBytes = audio.getBytes();
            if (audioBytes.length == 0) {
                throw new IllegalArgumentException("Audio file is required.");
            }
            RequestContextHolder.requireTenantId();
            UUID requestId = UUID.randomUUID();
            String normalizedLanguage = normalizeLanguageHint(language);
            validateAudio(audio.getOriginalFilename(), audio.getContentType());
            List<VoiceDebugTraceEntry> debugTrace = new ArrayList<>();
            debugTrace.add(trace("BACKEND_RECEIVED_AUDIO", true, null, null, null,
                    safeFilename(audio.getOriginalFilename()), audio.getContentType(), (long) audioBytes.length,
                    null, null, null, null, null, null, null, null));
            saveInspectionAudio(requestId, audioBytes, audio.getOriginalFilename(), audio.getContentType(), debugTrace);
            saveDebugAudioIfEnabled(requestId, audioBytes, audio.getOriginalFilename(), debugTrace);
            VoiceTranscriptionResult transcription = transcribe(
                    new VoiceTranscriptionRequest(
                            RequestContextHolder.requireTenantId(),
                            audioBytes,
                            audio.getContentType(),
                            audio.getOriginalFilename(),
                            normalizedLanguage
                    ),
                    debugTrace
            );
            return new VoiceSttDebugResponse(
                    requestId.toString(),
                    transcription.transcript(),
                    transcription.providerName(),
                    List.copyOf(debugTrace)
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Voice STT debug failed: " + ex.getMessage(), ex);
        }
    }

    public VoiceTestResponse processBufferedAudio(byte[] audioBytes,
                                                  String contentType,
                                                  String originalFilename,
                                                  String context,
                                                  String language) {
        return processBufferedAudio(audioBytes, contentType, originalFilename, context, language, null, null);
    }

    public VoiceTestResponse processBufferedAudio(byte[] audioBytes,
                                                  String contentType,
                                                  String originalFilename,
                                                  String context,
                                                  String language,
                                                  String workflowMode,
                                                  VoiceWorkflowSummary existingWorkflowSummary) {
        if (!properties.isEnabled()) {
            throw new IllegalArgumentException("Voice test is disabled.");
        }
        if (audioBytes == null || audioBytes.length == 0) {
            throw new IllegalArgumentException("Audio file is required.");
        }

        RequestContext requestContext = RequestContextHolder.require();
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID requestId = UUID.randomUUID();
        String normalizedLanguage = normalizeLanguageHint(language);
        validateAudio(originalFilename, contentType);
        Instant requestStart = Instant.now();
        List<VoiceDebugTraceEntry> debugTrace = new ArrayList<>();
        debugTrace.add(trace("BACKEND_RECEIVED_AUDIO", true, null, null, null,
                safeFilename(originalFilename), contentType, (long) audioBytes.length,
                null, null, null, null, null, null, null, null));
        saveInspectionAudio(requestId, audioBytes, originalFilename, contentType, debugTrace);
        saveDebugAudioIfEnabled(requestId, audioBytes, originalFilename, debugTrace);
        log.info("voice.request.start requestId={} tenantId={} contentType={} sizeBytes={} filename={}",
                requestId,
                tenantId,
                contentType,
                audioBytes.length,
                safeFilename(originalFilename));

        try {
            Instant sttStart = Instant.now();
            log.info("voice.stt.start requestId={} providerOrder={} sizeBytes={} contentType={}",
                    requestId,
                    properties.getStt().getProviderOrder(),
                    audioBytes.length,
                    contentType);
            VoiceTranscriptionResult transcription = transcribe(
                    new VoiceTranscriptionRequest(
                            tenantId,
                            audioBytes,
                            contentType,
                            originalFilename,
                            normalizedLanguage
                    ),
                    debugTrace
            );
            log.info("voice.stt.complete requestId={} provider={} durationMs={} transcriptPreview={}",
                    requestId,
                    transcription.providerName(),
                    Duration.between(sttStart, Instant.now()).toMillis(),
                    preview(transcription.transcript()));
            Instant llmStart = Instant.now();
            VoiceWorkflowMode resolvedWorkflowMode = VoiceWorkflowMode.from(workflowMode);
            VoiceWorkflowSummary workflowSummary = resolveWorkflowSummary(
                    resolvedWorkflowMode,
                    tenantId,
                    transcription.transcript(),
                    normalizedLanguage,
                    existingWorkflowSummary
            );
            logWorkflowState(requestId, resolvedWorkflowMode, workflowSummary);
            AiOrchestrationResponse aiResponse = aiOrchestrationService.complete(new AiOrchestrationRequest(
                    AiProductCode.GENERIC,
                    tenantId,
                    requestContext.appUserId(),
                    AiTaskType.GENERIC_COPILOT,
                    "generic.copilot.v1",
                    llmInput(transcription.transcript(), context, normalizedLanguage, resolvedWorkflowMode, workflowSummary),
                    List.of(),
                    properties.getLlm().getMaxOutputTokens(),
                    0.2d,
                    requestContext.correlationId(),
                    "voice.test"
            ));
            log.info("voice.llm.complete requestId={} provider={} durationMs={}",
                    requestId,
                    aiResponse.provider(),
                    Duration.between(llmStart, Instant.now()).toMillis());
            debugTrace.add(trace(
                    "LLM_RESULT",
                    true,
                    aiResponse.provider(),
                    null,
                    null,
                    safeFilename(originalFilename),
                    contentType,
                    (long) audioBytes.length,
                    null,
                    null,
                    null,
                    preview(aiResponse.outputText()),
                    Duration.between(llmStart, Instant.now()).toMillis(),
                    null,
                    null,
                    null
            ));
            String assistantText = sanitizeVoiceAssistantText(aiResponse.outputText());
            Instant ttsStart = Instant.now();
            VoiceSynthesisResult synthesis = synthesize(new VoiceSynthesisRequest(tenantId, assistantText, normalizedLanguage));
            log.info("voice.tts.complete requestId={} provider={} durationMs={} playableAudio={}",
                    requestId,
                    synthesis.providerName(),
                    Duration.between(ttsStart, Instant.now()).toMillis(),
                    hasPlayableAudio(synthesis));
            debugTrace.add(trace(
                    "TTS_RESULT",
                    synthesis != null,
                    synthesis == null ? null : synthesis.providerName(),
                    null,
                    null,
                    safeFilename(originalFilename),
                    contentType,
                    (long) audioBytes.length,
                    null,
                    null,
                    null,
                    synthesis == null ? null : synthesis.providerMessage(),
                    Duration.between(ttsStart, Instant.now()).toMillis(),
                    null,
                    null,
                    null
            ));

            byte[] audioPayload = hasPlayableAudio(synthesis) ? synthesis.audioBytes() : null;
            log.info("voice.response.serialize.start requestId={} hasAudio={} audioBytes={}",
                    requestId,
                    audioPayload != null,
                    audioPayload == null ? 0 : audioPayload.length);
            String audioBase64 = audioPayload == null ? null : Base64.getEncoder().encodeToString(audioPayload);
            log.info("voice.response.serialize.complete requestId={} audioBase64Chars={}",
                    requestId,
                    audioBase64 == null ? 0 : audioBase64.length());

            VoiceTestResponse response = new VoiceTestResponse(
                    requestId.toString(),
                    transcription.transcript(),
                    assistantText,
                    audioPayload == null ? null : synthesis.contentType(),
                    audioBase64,
                    new VoiceProviderTrace(
                            transcription.providerName(),
                            aiResponse.provider(),
                            synthesis.providerName()
                    ),
                    List.copyOf(debugTrace),
                    workflowSummary
            );
            log.info("voice.request.complete requestId={} durationMs={} sttProvider={} llmProvider={} ttsProvider={}",
                    requestId,
                    Duration.between(requestStart, Instant.now()).toMillis(),
                    response.providerTrace().sttProvider(),
                    response.providerTrace().llmProvider(),
                    response.providerTrace().ttsProvider());
            publishAudit(requestId, tenantId, requestContext.appUserId(), requestContext.correlationId(), response, true, null);
            return response;
        } catch (Exception ex) {
            log.warn("voice.request.failed requestId={} durationMs={} error={}",
                    requestId,
                    Duration.between(requestStart, Instant.now()).toMillis(),
                    ex.getMessage());
            publishAudit(
                    requestId,
                    tenantId,
                    requestContext.appUserId(),
                    requestContext.correlationId(),
                    new VoiceTestResponse(requestId.toString(), null, null, null, null, new VoiceProviderTrace(null, null, null), List.copyOf(debugTrace), existingWorkflowSummary),
                    false,
                    ex.getMessage()
            );
            throw ex instanceof IllegalArgumentException ? (IllegalArgumentException) ex : new IllegalStateException("Voice test failed: " + ex.getMessage(), ex);
        }
    }

    public VoiceTranscriptionResult transcribeBufferedAudio(byte[] audioBytes,
                                                            String contentType,
                                                            String originalFilename,
                                                            String language) {
        if (!properties.isEnabled()) {
            throw new IllegalArgumentException("Voice test is disabled.");
        }
        if (audioBytes == null || audioBytes.length == 0) {
            throw new IllegalArgumentException("Audio file is required.");
        }
        UUID tenantId = RequestContextHolder.requireTenantId();
        String normalizedLanguage = normalizeLanguageHint(language);
        validateAudio(originalFilename, contentType);
        return transcribe(
                new VoiceTranscriptionRequest(
                        tenantId,
                        audioBytes,
                        contentType,
                        originalFilename,
                        normalizedLanguage
                ),
                null
        );
    }

    public VoiceSynthesisResult synthesizeAssistantText(String assistantText, String language) {
        if (!properties.isEnabled()) {
            throw new IllegalArgumentException("Voice test is disabled.");
        }
        UUID tenantId = RequestContextHolder.requireTenantId();
        String normalizedLanguage = normalizeLanguageHint(language);
        return synthesize(new VoiceSynthesisRequest(tenantId, sanitizeVoiceAssistantText(assistantText), normalizedLanguage));
    }

    public VoiceStatusResponse status(boolean warmup) {
        VoiceServiceStatus sttStatus = fasterWhisperSpeechToTextProvider.status(warmup);
        VoiceServiceStatus ttsStatus = piperTextToSpeechProvider.status(warmup);
        return new VoiceStatusResponse(
                properties.isEnabled(),
                sttStatus,
                ttsStatus,
                new VoiceProviderTrace(
                        firstProvider(properties.getStt().getProviderOrder(), "mock"),
                        firstProvider(properties.getLlm().getProviderOrder(), "mock"),
                        firstProvider(properties.getTts().getProviderOrder(), "mock")
                ),
                properties.getStt().getFasterWhisper().getLanguage(),
                properties.getTts().getPiper().getVoice(),
                java.util.Map.copyOf(piperTextToSpeechProvider.configuredVoices()),
                piperTextToSpeechProvider.isLanguageVoiceConfigured("hi"),
                piperTextToSpeechProvider.isFallbackVoiceAllowed()
        );
    }

    public VoiceLiveStatusResponse liveStatus() {
        return new VoiceLiveStatusResponse(
                properties.isEnabled(),
                "/ws/voice/test",
                "JWT_QUERY_TOKEN",
                "QUERY_TENANT_ID",
                properties.getVad().isEnabled() ? "FRONTEND_RMS_READY" : "DISABLED",
                properties.getVad().getProvider(),
                properties.getLive().getHeartbeatIntervalMs(),
                properties.getLive().getStaleAfterMs(),
                properties.getLive().getMaxSessionDurationSeconds(),
                properties.getLive().getMaxIdleSeconds(),
                properties.getLive().getMaxTurnsPerSession(),
                properties.getLive().getMaxAudioBytesPerTurn()
        );
    }

    private VoiceTranscriptionResult transcribe(VoiceTranscriptionRequest request, List<VoiceDebugTraceEntry> debugTrace) {
        List<String> providerOrder = properties.getStt().getProviderOrder();
        log.info("voice.stt.provider-order={}", providerOrder);
        List<String> availableProviders = sttProviderRegistry().keySet().stream().toList();
        for (String configured : providerOrder) {
            String normalizedConfigured = normalizeProviderKey(configured);
            if (!availableProviders.contains(normalizedConfigured)) {
                log.warn("voice.stt.provider.missing configured={} available={}", normalizedConfigured, availableProviders);
                if (debugTrace != null) {
                    debugTrace.add(trace(
                            "STT_PROVIDER_MISSING",
                            false,
                            normalizedConfigured,
                            null,
                            null,
                            safeFilename(request.filename()),
                            request.contentType(),
                            sizeOf(request.audioBytes()),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            "Configured STT provider is not registered in the runtime.",
                            null
                    ));
                }
            }
        }
        IllegalStateException lastFailure = null;
        List<SpeechToTextProvider> orderedProviders = orderedSttProviders(providerOrder);
        for (int index = 0; index < orderedProviders.size(); index += 1) {
            SpeechToTextProvider provider = orderedProviders.get(index);
            log.info("voice.stt.provider.try provider={}", provider.providerName());
            if (!provider.isReady()) {
                log.info("voice.stt.skip provider={} reason=not-ready", provider.providerName());
                continue;
            }
            try {
                if (provider instanceof FasterWhisperSpeechToTextProvider fasterWhisperProvider) {
                    return fasterWhisperProvider.transcribeWithDebug(request, debugTrace);
                }
                VoiceTranscriptionResult result = provider.transcribe(request);
                if (debugTrace != null) {
                    debugTrace.add(trace("STT_RESULT", true, result.providerName(), null, null,
                            safeFilename(request.filename()), request.contentType(), sizeOf(request.audioBytes()),
                            null, null, null, null, null, result.transcript() == null ? 0 : result.transcript().length(), null, null));
                }
                return result;
            } catch (RuntimeException ex) {
                log.warn("voice.stt.fallback provider={} reason={}", provider.providerName(), ex.getMessage());
                if (debugTrace != null) {
                    debugTrace.add(trace(
                            "STT_FALLBACK",
                            false,
                            null,
                            provider.providerName(),
                            index + 1 < orderedProviders.size() ? orderedProviders.get(index + 1).providerName() : null,
                            safeFilename(request.filename()),
                            request.contentType(),
                            sizeOf(request.audioBytes()),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            ex.getMessage(),
                            null
                    ));
                }
                lastFailure = new IllegalStateException(ex.getMessage(), ex);
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new IllegalStateException("No STT provider is available.");
    }

    private VoiceSynthesisResult synthesize(VoiceSynthesisRequest request) {
        List<String> providerOrder = properties.getTts().getProviderOrder();
        IllegalStateException lastFailure = null;
        for (TextToSpeechProvider provider : orderedTtsProviders(providerOrder)) {
            if (!provider.isReady()) {
                log.info("voice.tts.skip provider={} reason=not-ready", provider.providerName());
                continue;
            }
            try {
                VoiceSynthesisResult result = provider.synthesize(request);
                if (result != null) {
                    return result;
                }
            } catch (RuntimeException ex) {
                log.warn("voice.tts.fallback provider={} reason={}", provider.providerName(), ex.getMessage());
                lastFailure = new IllegalStateException(ex.getMessage(), ex);
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        return new VoiceSynthesisResult(null, null, "none", "No TTS provider is available.");
    }

    private List<SpeechToTextProvider> orderedSttProviders(List<String> configuredOrder) {
        Map<String, SpeechToTextProvider> byName = sttProviderRegistry();
        if (configuredOrder == null || configuredOrder.isEmpty()) {
            return new java.util.ArrayList<>(byName.values());
        }
        List<SpeechToTextProvider> ordered = new java.util.ArrayList<>();
        for (String name : configuredOrder) {
            SpeechToTextProvider provider = byName.remove(normalizeProviderKey(name));
            if (provider != null) {
                ordered.add(provider);
            }
        }
        ordered.addAll(byName.values());
        return ordered;
    }

    private Map<String, SpeechToTextProvider> sttProviderRegistry() {
        Map<String, SpeechToTextProvider> byName = new LinkedHashMap<>();
        if (fasterWhisperSpeechToTextProvider != null) {
            byName.put(providerKey(fasterWhisperSpeechToTextProvider), fasterWhisperSpeechToTextProvider);
        }
        for (SpeechToTextProvider provider : sttProviders) {
            byName.putIfAbsent(providerKey(provider), provider);
        }
        return byName;
    }

    private List<TextToSpeechProvider> orderedTtsProviders(List<String> configuredOrder) {
        if (configuredOrder == null || configuredOrder.isEmpty()) {
            return ttsProviders;
        }
        Map<String, TextToSpeechProvider> byName = new LinkedHashMap<>();
        ttsProviders.forEach(provider -> byName.put(provider.providerName().toLowerCase(Locale.ROOT), provider));
        List<TextToSpeechProvider> ordered = new java.util.ArrayList<>();
        for (String name : configuredOrder) {
            TextToSpeechProvider provider = byName.remove(name.toLowerCase(Locale.ROOT));
            if (provider != null) {
                ordered.add(provider);
            }
        }
        ordered.addAll(byName.values());
        return ordered;
    }

    private VoiceWorkflowSummary resolveWorkflowSummary(VoiceWorkflowMode workflowMode,
                                                        UUID tenantId,
                                                        String transcript,
                                                        String language,
                                                        VoiceWorkflowSummary existingWorkflowSummary) {
        if (workflowMode != VoiceWorkflowMode.APPOINTMENT_BOOKING || voiceAppointmentWorkflowService == null) {
            return null;
        }
        return voiceAppointmentWorkflowService.resolve(tenantId, transcript, language, existingWorkflowSummary);
    }

    private void logWorkflowState(UUID requestId, VoiceWorkflowMode workflowMode, VoiceWorkflowSummary workflowSummary) {
        if (workflowMode != VoiceWorkflowMode.APPOINTMENT_BOOKING || workflowSummary == null) {
            return;
        }
        log.info("voice.workflow.state requestId={} workflowMode={} intentState={} missingFields={} suggestedSlot={} confirmationRequested={} handoffRequired={}",
                requestId,
                workflowMode.configValue(),
                workflowSummary.intentState(),
                workflowSummary.missingFields(),
                workflowSummary.suggestedSlot() == null
                        ? null
                        : workflowSummary.suggestedSlot().appointmentDate() + " " + workflowSummary.suggestedSlot().slotTime(),
                workflowSummary.confirmationRequested(),
                workflowSummary.handoffRequired());
    }

    private Map<String, Object> llmInput(String transcript,
                                         String context,
                                         String language,
                                         VoiceWorkflowMode workflowMode,
                                         VoiceWorkflowSummary workflowSummary) {
        String responseLanguage = StringUtils.hasText(language) ? language : "auto";
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("transcript", trimToSafeSpeech(transcript, 320));
        input.put("conversationContext", compactVoiceContext(context));
        input.put("language", responseLanguage);
        input.put("responseContract", "{\"answer\":\"short spoken response\",\"suggestedActions\":[]}");
        if (workflowMode == VoiceWorkflowMode.APPOINTMENT_BOOKING && workflowSummary != null) {
            input.put("workflowMode", workflowMode.configValue());
            input.put("workflowState", workflowSummary);
        }
        input.put(
                "instruction",
                "Return ONLY compact valid JSON. No markdown. No newlines. No extra keys. "
                        + "Use exactly {\"answer\":\"short spoken response\",\"suggestedActions\":[]}. "
                        + "Keep answer under " + (workflowMode == VoiceWorkflowMode.APPOINTMENT_BOOKING ? 35 : properties.getLlm().getMaxAnswerWords())
                        + " words. Speak like a helpful clinic receptionist. "
                        + workflowInstruction(workflowMode, workflowSummary)
                        + " "
                        + languageInstruction(responseLanguage)
        );
        return input;
    }

    private String workflowInstruction(VoiceWorkflowMode workflowMode, VoiceWorkflowSummary workflowSummary) {
        if (workflowMode != VoiceWorkflowMode.APPOINTMENT_BOOKING || workflowSummary == null) {
            return "";
        }
        StringBuilder instruction = new StringBuilder();
        instruction.append("This conversation is in appointment booking workflow mode. ");
        instruction.append("Current intent state is ").append(workflowSummary.intentState()).append(". ");
        if (workflowSummary.missingFields() != null && !workflowSummary.missingFields().isEmpty()) {
            instruction.append("Missing fields: ").append(String.join(", ", workflowSummary.missingFields())).append(". ");
        }
        if (workflowSummary.suggestedSlot() != null) {
            instruction.append("Suggested slot: ")
                    .append(workflowSummary.suggestedSlot().doctorName()).append(" on ")
                    .append(workflowSummary.suggestedSlot().appointmentDate()).append(" at ")
                    .append(workflowSummary.suggestedSlot().slotTime()).append(". ");
        }
        if (workflowSummary.confirmationRequested()) {
            instruction.append("Ask only for explicit confirmation and do not claim the appointment is booked. ");
        }
        if (workflowSummary.booked()) {
            instruction.append("The appointment is already booked. Confirm it briefly using the booked details. ");
        } else if (workflowSummary.bookingConfirmed()) {
            instruction.append("Acknowledge the confirmation, but do not claim the appointment is booked unless workflow state says booked. ");
        }
        if (workflowSummary.handoffRequired()) {
            instruction.append("Politely hand the conversation off to a human receptionist using the workflow guidance exactly. ");
        }
        if (StringUtils.hasText(workflowSummary.nextPrompt())) {
            instruction.append("Follow this workflow guidance exactly: ").append(workflowSummary.nextPrompt()).append(" ");
        }
        return instruction.toString().trim();
    }

    private String languageInstruction(String language) {
        String normalized = normalizeLanguageHint(language);
        if ("hi".equals(normalized)) {
            return "Respond only in simple spoken Hindi written in Devanagari.";
        }
        if ("en".equals(normalized)) {
            return "Respond only in English.";
        }
        return "Respond in the same language as the transcript.";
    }

    private String compactVoiceContext(String context) {
        String fallback = "General voice test harness conversation.";
        if (!StringUtils.hasText(context)) {
            return fallback;
        }
        String normalized = context.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
        int limit = Math.max(200, properties.getLlm().getMaxHistoryChars());
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit) + "...";
    }

    private String sanitizeVoiceAssistantText(String raw) {
        String candidate = extractAnswerCandidate(raw);
        if (!StringUtils.hasText(candidate)) {
            return "Sorry, I missed that. Could you please repeat?";
        }
        String cleaned = candidate
                .replaceAll("```+", " ")
                .replaceAll("[*_#>`-]+", " ")
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (!StringUtils.hasText(cleaned)
                || cleaned.equalsIgnoreCase("AI response was incomplete. Please retry.")
                || cleaned.equalsIgnoreCase("AI returned an invalid response. Please retry.")) {
            return "Sorry, I missed that. Could you please repeat?";
        }
        String[] words = cleaned.split("\\s+");
        int maxWords = Math.max(10, properties.getLlm().getMaxAnswerWords());
        if (words.length > maxWords) {
            cleaned = String.join(" ", java.util.Arrays.copyOf(words, maxWords)).trim();
            if (!cleaned.endsWith(".") && !cleaned.endsWith("?") && !cleaned.endsWith("!")) {
                cleaned = cleaned + ".";
            }
        }
        return cleaned;
    }

    private String extractAnswerCandidate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            var node = objectMapper.readTree(trimmed);
            if (node.isObject()) {
                String answer = firstNonBlank(
                        text(node, "answer"),
                        text(node, "outputText"),
                        text(node, "summary")
                );
                if (StringUtils.hasText(answer)) {
                    return answer;
                }
            }
        } catch (Exception ignored) {
        }
        Matcher matcher = JSON_ANSWER_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1)
                    .replace("\\n", " ")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return null;
        }
        return trimmed;
    }

    private String trimToSafeSpeech(String value, int maxChars) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars) + "...";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(com.fasterxml.jackson.databind.JsonNode node, String field) {
        if (node == null || !node.has(field) || !node.get(field).isValueNode()) {
            return null;
        }
        String value = node.get(field).asText(null);
        return StringUtils.hasText(value) ? value : null;
    }

    private String normalizeLanguageHint(String language) {
        if (!StringUtils.hasText(language)) {
            return null;
        }
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || "auto".equals(normalized) || "auto-detect".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private void validateAudio(String originalFilename, String rawContentType) {
        String filename = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        String contentType = rawContentType == null ? "" : rawContentType.toLowerCase(Locale.ROOT);
        boolean supported = filename.endsWith(".wav")
                || filename.endsWith(".webm")
                || filename.endsWith(".mp3")
                || filename.endsWith(".m4a")
                || contentType.contains("audio/wav")
                || contentType.contains("audio/webm")
                || contentType.contains("audio/mpeg")
                || contentType.contains("audio/mp3")
                || contentType.contains("audio/mp4")
                || contentType.contains("audio/x-m4a");
        if (!supported) {
            throw new IllegalArgumentException("Unsupported audio file. Use wav, webm, mp3, or m4a.");
        }
    }

    private boolean hasPlayableAudio(VoiceSynthesisResult synthesis) {
        return synthesis != null
                && synthesis.audioBytes() != null
                && synthesis.audioBytes().length > 0
                && StringUtils.hasText(synthesis.contentType())
                && synthesis.contentType().startsWith("audio/");
    }

    private void publishAudit(UUID requestId,
                              UUID tenantId,
                              UUID actorUserId,
                              String correlationId,
                              VoiceTestResponse response,
                              boolean success,
                              String error) {
        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("requestId", requestId.toString());
            details.put("correlationId", correlationId);
            details.put("sttProvider", response.providerTrace() == null ? null : response.providerTrace().sttProvider());
            details.put("llmProvider", response.providerTrace() == null ? null : response.providerTrace().llmProvider());
            details.put("ttsProvider", response.providerTrace() == null ? null : response.providerTrace().ttsProvider());
            details.put("success", success);
            details.put("error", error);
            auditEventPublisher.record(new AuditEventCommand(
                    tenantId,
                    VOICE_TEST_ENTITY_TYPE,
                    requestId,
                    success ? VOICE_TEST_SUCCESS : VOICE_TEST_FAILED,
                    actorUserId,
                    OffsetDateTime.now(),
                    success ? "Voice test harness request completed." : "Voice test harness request failed.",
                    toJson(details)
            ));
        } catch (Exception ignored) {
        }
    }

    private String toJson(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String firstProvider(List<String> providerOrder, String fallback) {
        return providerOrder == null || providerOrder.isEmpty() ? fallback : providerOrder.getFirst();
    }

    private String preview(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80) + "...";
    }

    private String safeFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "voice-input";
        }
        return originalFilename.replaceAll("[\\r\\n]", "_");
    }

    private void saveDebugAudioIfEnabled(UUID requestId,
                                         byte[] audioBytes,
                                         String originalFilename,
                                         List<VoiceDebugTraceEntry> debugTrace) {
        if (!properties.getDebug().isSaveAudio()) {
            return;
        }
        try {
            Path audioDir = Path.of(properties.getDebug().getAudioDir());
            Files.createDirectories(audioDir);
            String suffix = fileSuffix(originalFilename);
            Path output = audioDir.resolve(requestId + suffix);
            Files.write(output, audioBytes);
            log.info("voice.audio.saved requestId={} path={}", requestId, output);
            if (debugTrace != null) {
                debugTrace.add(trace("BACKEND_AUDIO_SAVED", true, null, null, null,
                        safeFilename(originalFilename), null, (long) audioBytes.length,
                        null, null, null, null, null, null, null, output.toString()));
            }
        } catch (IOException ex) {
            log.warn("voice.audio.save_failed requestId={} reason={}", requestId, ex.getMessage());
        }
    }

    private void saveInspectionAudio(UUID requestId,
                                     byte[] audioBytes,
                                     String originalFilename,
                                     String contentType,
                                     List<VoiceDebugTraceEntry> debugTrace) {
        try {
            Path audioDir = Path.of(properties.getDebug().getAudioDir());
            cleanupExpiredDebugAudio(audioDir);
            Files.createDirectories(audioDir);
            String suffix = fileSuffix(originalFilename);
            Path output = audioDir.resolve(requestId + suffix);
            Files.write(output, audioBytes);
            log.info("voice.audio.saved requestId={} filename={} contentType={} sizeBytes={} savedPath={}",
                    requestId,
                    safeFilename(originalFilename),
                    contentType,
                    audioBytes.length,
                    output);
            if (debugTrace != null) {
                debugTrace.add(trace("BACKEND_AUDIO_SAVED", true, null, null, null,
                        safeFilename(originalFilename), contentType, (long) audioBytes.length,
                        null, null, null, null, null, null, null, output.toString()));
            }
        } catch (IOException ex) {
            log.warn("voice.audio.save_failed requestId={} reason={}", requestId, ex.getMessage());
        }
    }

    private void cleanupExpiredDebugAudio(Path audioDir) {
        try {
            if (!Files.exists(audioDir)) {
                return;
            }
            Instant cutoff = Instant.now().minus(Duration.ofHours(1));
            try (Stream<Path> paths = Files.list(audioDir)) {
                paths.filter(Files::isRegularFile).forEach(path -> deleteIfOlderThan(path, cutoff));
            }
        } catch (IOException ex) {
            log.debug("voice.audio.cleanup_skipped reason={}", ex.getMessage());
        }
    }

    private void deleteIfOlderThan(Path path, Instant cutoff) {
        try {
            FileTime modifiedTime = Files.getLastModifiedTime(path);
            if (modifiedTime.toInstant().isBefore(cutoff)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ex) {
            log.debug("voice.audio.cleanup_failed path={} reason={}", path, ex.getMessage());
        }
    }

    private String fileSuffix(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return ".bin";
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            return ".bin";
        }
        return originalFilename.substring(dotIndex);
    }

    private Long sizeOf(byte[] audioBytes) {
        return audioBytes == null ? null : (long) audioBytes.length;
    }

    private VoiceDebugTraceEntry trace(String stage,
                                       boolean ok,
                                       String provider,
                                       String from,
                                       String to,
                                       String filename,
                                       String contentType,
                                       Long sizeBytes,
                                       String url,
                                       String multipartField,
                                       Integer status,
                                       String bodyPreview,
                                       Long durationMs,
                                       Integer transcriptLength,
                                       String reason,
                                       String savedPath) {
        return new VoiceDebugTraceEntry(
                stage,
                ok,
                provider,
                from,
                to,
                filename,
                contentType,
                sizeBytes,
                url,
                multipartField,
                status,
                bodyPreview,
                durationMs,
                transcriptLength,
                reason,
                savedPath
        );
    }

    private String providerKey(SpeechToTextProvider provider) {
        return normalizeProviderKey(provider.providerName());
    }

    private String normalizeProviderKey(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replace(' ', '-');
        if ("fasterwhisper".equals(normalized)) {
            return "faster-whisper";
        }
        return normalized;
    }
}
