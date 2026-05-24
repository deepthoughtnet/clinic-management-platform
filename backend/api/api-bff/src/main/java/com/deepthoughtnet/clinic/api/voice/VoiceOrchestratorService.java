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
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VoiceOrchestratorService {
    private static final Logger log = LoggerFactory.getLogger(VoiceOrchestratorService.class);
    private static final String VOICE_TEST_ENTITY_TYPE = "VOICE_TEST";
    private static final String VOICE_TEST_SUCCESS = "VOICE_TEST_COMPLETED";
    private static final String VOICE_TEST_FAILED = "VOICE_TEST_FAILED";

    private final List<SpeechToTextProvider> sttProviders;
    private final List<TextToSpeechProvider> ttsProviders;
    private final AiOrchestrationService aiOrchestrationService;
    private final AuditEventPublisher auditEventPublisher;
    private final VoiceTestProperties properties;
    private final ObjectMapper objectMapper;
    private final FasterWhisperSpeechToTextProvider fasterWhisperSpeechToTextProvider;
    private final PiperTextToSpeechProvider piperTextToSpeechProvider;

    public VoiceOrchestratorService(List<SpeechToTextProvider> sttProviders,
                                    List<TextToSpeechProvider> ttsProviders,
                                    AiOrchestrationService aiOrchestrationService,
                                    AuditEventPublisher auditEventPublisher,
                                    VoiceTestProperties properties,
                                    ObjectMapper objectMapper,
                                    FasterWhisperSpeechToTextProvider fasterWhisperSpeechToTextProvider,
                                    PiperTextToSpeechProvider piperTextToSpeechProvider) {
        this.sttProviders = sttProviders;
        this.ttsProviders = ttsProviders;
        this.aiOrchestrationService = aiOrchestrationService;
        this.auditEventPublisher = auditEventPublisher;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.fasterWhisperSpeechToTextProvider = fasterWhisperSpeechToTextProvider;
        this.piperTextToSpeechProvider = piperTextToSpeechProvider;
    }

    public VoiceTestResponse processAudio(MultipartFile audio, String context, String language) {
        if (!properties.isEnabled()) {
            throw new IllegalArgumentException("Voice test is disabled.");
        }
        if (audio == null || audio.isEmpty()) {
            throw new IllegalArgumentException("Audio file is required.");
        }
        try {
            return processBufferedAudio(audio.getBytes(), audio.getContentType(), audio.getOriginalFilename(), context, language);
        } catch (IOException ex) {
            throw new IllegalStateException("Voice test failed: " + ex.getMessage(), ex);
        }
    }

    public VoiceTestResponse processBufferedAudio(byte[] audioBytes,
                                                  String contentType,
                                                  String originalFilename,
                                                  String context,
                                                  String language) {
        if (!properties.isEnabled()) {
            throw new IllegalArgumentException("Voice test is disabled.");
        }
        if (audioBytes == null || audioBytes.length == 0) {
            throw new IllegalArgumentException("Audio file is required.");
        }

        RequestContext requestContext = RequestContextHolder.require();
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID requestId = UUID.randomUUID();
        String normalizedLanguage = StringUtils.hasText(language) ? language.trim() : "en";
        validateAudio(originalFilename, contentType);
        Instant requestStart = Instant.now();
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
                    )
            );
            log.info("voice.stt.complete requestId={} provider={} durationMs={} transcriptPreview={}",
                    requestId,
                    transcription.providerName(),
                    Duration.between(sttStart, Instant.now()).toMillis(),
                    preview(transcription.transcript()));
            Instant llmStart = Instant.now();
            AiOrchestrationResponse aiResponse = aiOrchestrationService.complete(new AiOrchestrationRequest(
                    AiProductCode.GENERIC,
                    tenantId,
                    requestContext.appUserId(),
                    AiTaskType.GENERIC_COPILOT,
                    "generic.copilot.v1",
                    llmInput(transcription.transcript(), context, normalizedLanguage),
                    List.of(),
                    512,
                    0.2d,
                    requestContext.correlationId(),
                    "voice.test"
            ));
            log.info("voice.llm.complete requestId={} provider={} durationMs={}",
                    requestId,
                    aiResponse.provider(),
                    Duration.between(llmStart, Instant.now()).toMillis());
            String assistantText = StringUtils.hasText(aiResponse.outputText())
                    ? aiResponse.outputText()
                    : "I heard your request. Please verify the transcript and continue the conversation.";
            Instant ttsStart = Instant.now();
            VoiceSynthesisResult synthesis = synthesize(new VoiceSynthesisRequest(tenantId, assistantText, normalizedLanguage));
            log.info("voice.tts.complete requestId={} provider={} durationMs={} playableAudio={}",
                    requestId,
                    synthesis.providerName(),
                    Duration.between(ttsStart, Instant.now()).toMillis(),
                    hasPlayableAudio(synthesis));

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
                    )
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
                    new VoiceTestResponse(requestId.toString(), null, null, null, null, new VoiceProviderTrace(null, null, null)),
                    false,
                    ex.getMessage()
            );
            throw ex instanceof IllegalArgumentException ? (IllegalArgumentException) ex : new IllegalStateException("Voice test failed: " + ex.getMessage(), ex);
        }
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
                )
        );
    }

    public VoiceLiveStatusResponse liveStatus() {
        return new VoiceLiveStatusResponse(
                properties.isEnabled(),
                "/ws/voice/test",
                "JWT_QUERY_TOKEN",
                "QUERY_TENANT_ID"
        );
    }

    private VoiceTranscriptionResult transcribe(VoiceTranscriptionRequest request) {
        List<String> providerOrder = properties.getStt().getProviderOrder();
        IllegalStateException lastFailure = null;
        for (SpeechToTextProvider provider : orderedSttProviders(providerOrder)) {
            if (!provider.isReady()) {
                log.info("voice.stt.skip provider={} reason=not-ready", provider.providerName());
                continue;
            }
            try {
                return provider.transcribe(request);
            } catch (RuntimeException ex) {
                log.warn("voice.stt.fallback provider={} reason={}", provider.providerName(), ex.getMessage());
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
        if (configuredOrder == null || configuredOrder.isEmpty()) {
            return sttProviders;
        }
        Map<String, SpeechToTextProvider> byName = new LinkedHashMap<>();
        sttProviders.forEach(provider -> byName.put(provider.providerName().toLowerCase(Locale.ROOT), provider));
        List<SpeechToTextProvider> ordered = new java.util.ArrayList<>();
        for (String name : configuredOrder) {
            SpeechToTextProvider provider = byName.remove(name.toLowerCase(Locale.ROOT));
            if (provider != null) {
                ordered.add(provider);
            }
        }
        ordered.addAll(byName.values());
        return ordered;
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

    private Map<String, Object> llmInput(String transcript, String context, String language) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("transcript", transcript);
        input.put("context", StringUtils.hasText(context) ? context.trim() : "General voice test harness conversation.");
        input.put("language", language);
        input.put("instruction", "Respond like a concise AI receptionist assistant. Keep the answer clear and short.");
        return input;
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
}
