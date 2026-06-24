package com.deepthoughtnet.clinic.api.patientportal.voice;

import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiMessageRequest;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiMessageResponse;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiService;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiStateResponse;
import com.deepthoughtnet.clinic.api.voice.VoiceOrchestratorService;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisResult;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionResult;
import java.util.Base64;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PatientPortalVoiceAssistantService {
    private static final String CAREAI_PROVIDER = "PATIENT_PORTAL_CAREAI";
    private static final Logger log = LoggerFactory.getLogger(PatientPortalVoiceAssistantService.class);

    private final VoiceOrchestratorService voiceOrchestratorService;
    private final PatientPortalCareAiService patientPortalCareAiService;

    public PatientPortalVoiceAssistantService(
            VoiceOrchestratorService voiceOrchestratorService,
            PatientPortalCareAiService patientPortalCareAiService
    ) {
        this.voiceOrchestratorService = voiceOrchestratorService;
        this.patientPortalCareAiService = patientPortalCareAiService;
    }

    public PatientPortalVoiceTurnResponse processAudioTurn(
            byte[] audioBytes,
            String contentType,
            String originalFilename,
            String language
    ) {
        Instant requestStart = Instant.now();
        log.info("patient.voice.turn.request.received contentType={} sizeBytes={} language={}",
                contentType,
                audioBytes == null ? 0 : audioBytes.length,
                language);
        Instant sttStart = Instant.now();
        log.info("patient.voice.stt.start");
        VoiceTranscriptionResult transcription = voiceOrchestratorService.transcribeBufferedAudio(
                audioBytes,
                contentType,
                originalFilename,
                language
        );
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
        if (!StringUtils.hasText(transcription.transcript())) {
            throw new IllegalStateException("No speech was captured. Please try again.");
        }

        Instant careAiStart = Instant.now();
        log.info("patient.voice.careai.start");
        PatientPortalCareAiMessageResponse careAiResponse = patientPortalCareAiService.messageFromVoice(
                new PatientPortalCareAiMessageRequest(transcription.transcript(), language)
        );
        long careAiDurationMs = Duration.between(careAiStart, Instant.now()).toMillis();
        log.info("patient.voice.careai.complete durationMs={}", careAiDurationMs);
        VoiceSynthesisResult synthesis = null;
        long ttsDurationMs = 0L;
        String ttsFallbackReason = null;
        Instant ttsStart = Instant.now();
        try {
            log.info("patient.voice.tts.start");
            synthesis = voiceOrchestratorService.synthesizeAssistantText(
                    careAiResponse.assistantMessage(),
                    careAiResponse.state() == null ? language : careAiResponse.state().language()
            );
            ttsDurationMs = Duration.between(ttsStart, Instant.now()).toMillis();
            log.info("patient.voice.tts.complete provider={} durationMs={} playableAudio={}",
                    synthesis == null ? null : synthesis.providerName(),
                    ttsDurationMs,
                    playableAudioBytes(synthesis) != null);
        } catch (RuntimeException ex) {
            ttsDurationMs = Duration.between(ttsStart, Instant.now()).toMillis();
            ttsFallbackReason = ex.getMessage();
            log.warn("patient.voice.tts.fallback reason={} durationMs={}", ttsFallbackReason, ttsDurationMs);
        }
        byte[] audioPayload = playableAudioBytes(synthesis);
        long totalDurationMs = Duration.between(requestStart, Instant.now()).toMillis();
        log.info("patient.voice.turn.request.complete totalDurationMs={} sttDurationMs={} careAiDurationMs={} ttsDurationMs={} hasAudio={}",
                totalDurationMs, sttDurationMs, careAiDurationMs, ttsDurationMs, audioPayload != null);

        return new PatientPortalVoiceTurnResponse(
                UUID.randomUUID().toString(),
                transcription.transcript(),
                careAiResponse.assistantMessage(),
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
            String ttsFallbackReason
    ) {
    }
}
