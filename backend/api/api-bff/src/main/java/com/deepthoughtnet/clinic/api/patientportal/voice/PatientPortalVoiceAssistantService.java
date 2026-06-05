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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PatientPortalVoiceAssistantService {
    private static final String CAREAI_PROVIDER = "PATIENT_PORTAL_CAREAI";

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
        VoiceTranscriptionResult transcription = voiceOrchestratorService.transcribeBufferedAudio(
                audioBytes,
                contentType,
                originalFilename,
                language
        );
        if (!StringUtils.hasText(transcription.transcript())) {
            throw new IllegalStateException("No speech was captured. Please try again.");
        }

        PatientPortalCareAiMessageResponse careAiResponse = patientPortalCareAiService.message(
                new PatientPortalCareAiMessageRequest(transcription.transcript(), language)
        );
        VoiceSynthesisResult synthesis = voiceOrchestratorService.synthesizeAssistantText(
                careAiResponse.assistantMessage(),
                careAiResponse.state() == null ? language : careAiResponse.state().language()
        );
        byte[] audioPayload = playableAudioBytes(synthesis);

        return new PatientPortalVoiceTurnResponse(
                UUID.randomUUID().toString(),
                transcription.transcript(),
                careAiResponse.assistantMessage(),
                careAiResponse.state(),
                synthesis == null ? null : synthesis.contentType(),
                audioPayload == null ? null : Base64.getEncoder().encodeToString(audioPayload),
                transcription.providerName(),
                CAREAI_PROVIDER,
                synthesis == null ? null : synthesis.providerName()
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
            String ttsProvider
    ) {
    }
}
