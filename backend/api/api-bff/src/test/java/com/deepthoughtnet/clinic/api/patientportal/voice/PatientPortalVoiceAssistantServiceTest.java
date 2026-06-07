package com.deepthoughtnet.clinic.api.patientportal.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiMessageRequest;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiMessageResponse;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiService;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiStateResponse;
import com.deepthoughtnet.clinic.api.voice.VoiceOrchestratorService;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisResult;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionResult;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PatientPortalVoiceAssistantServiceTest {

    @Test
    void voiceTurnUsesCareAiEngineAndSynthesizesResponse() {
        VoiceOrchestratorService orchestratorService = mock(VoiceOrchestratorService.class);
        PatientPortalCareAiService careAiService = mock(PatientPortalCareAiService.class);
        PatientPortalVoiceAssistantService service = new PatientPortalVoiceAssistantService(orchestratorService, careAiService);
        PatientPortalCareAiStateResponse state = new PatientPortalCareAiStateResponse(
                "en",
                "BOOK_APPOINTMENT",
                "Dr Neha Mehta",
                "Dermatology",
                null,
                "2026-06-10",
                "morning",
                "10:30",
                true,
                false,
                false,
                null,
                null,
                null,
                null,
                false,
                null,
                List.of("Dr Neha Mehta"),
                List.of(),
                List.of("10:30")
        );
        when(orchestratorService.transcribeBufferedAudio(any(), any(), any(), any()))
                .thenReturn(new VoiceTranscriptionResult("I want to book appointment", "faster-whisper", "ok"));
        when(careAiService.messageFromVoice(any(PatientPortalCareAiMessageRequest.class)))
                .thenReturn(new PatientPortalCareAiMessageResponse("Please confirm the 10:30 slot.", state));
        when(orchestratorService.synthesizeAssistantText("Please confirm the 10:30 slot.", "en"))
                .thenReturn(new VoiceSynthesisResult("voice".getBytes(StandardCharsets.UTF_8), "audio/wav", "piper", "ok"));

        var response = service.processAudioTurn("audio".getBytes(StandardCharsets.UTF_8), "audio/webm", "voice.webm", "auto");

        ArgumentCaptor<PatientPortalCareAiMessageRequest> requestCaptor = ArgumentCaptor.forClass(PatientPortalCareAiMessageRequest.class);
        verify(careAiService).messageFromVoice(requestCaptor.capture());
        assertThat(requestCaptor.getValue().message()).isEqualTo("I want to book appointment");
        assertThat(response.transcript()).isEqualTo("I want to book appointment");
        assertThat(response.assistantText()).isEqualTo("Please confirm the 10:30 slot.");
        assertThat(response.state()).isEqualTo(state);
        assertThat(response.audioContentType()).isEqualTo("audio/wav");
        assertThat(response.audioBase64()).isEqualTo(Base64.getEncoder().encodeToString("voice".getBytes(StandardCharsets.UTF_8)));
        assertThat(response.llmProvider()).isEqualTo("PATIENT_PORTAL_CAREAI");
        assertThat(response.sttDurationMs()).isGreaterThanOrEqualTo(0L);
        assertThat(response.careAiDurationMs()).isGreaterThanOrEqualTo(0L);
        assertThat(response.ttsDurationMs()).isGreaterThanOrEqualTo(0L);
        assertThat(response.totalDurationMs()).isGreaterThanOrEqualTo(0L);
        assertThat(response.captureBytes()).isEqualTo(5L);
        assertThat(response.ttsFallbackReason()).isNull();
    }

    @Test
    void emptyTranscriptFailsBeforeCareAiMutation() {
        VoiceOrchestratorService orchestratorService = mock(VoiceOrchestratorService.class);
        PatientPortalCareAiService careAiService = mock(PatientPortalCareAiService.class);
        PatientPortalVoiceAssistantService service = new PatientPortalVoiceAssistantService(orchestratorService, careAiService);
        when(orchestratorService.transcribeBufferedAudio(any(), any(), any(), any()))
                .thenReturn(new VoiceTranscriptionResult("   ", "faster-whisper", "ok"));

        assertThatThrownBy(() -> service.processAudioTurn("audio".getBytes(StandardCharsets.UTF_8), "audio/webm", "voice.webm", "auto"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No speech was captured");
    }

    @Test
    void ttsFailureFallsBackToTextOnlyResponse() {
        VoiceOrchestratorService orchestratorService = mock(VoiceOrchestratorService.class);
        PatientPortalCareAiService careAiService = mock(PatientPortalCareAiService.class);
        PatientPortalVoiceAssistantService service = new PatientPortalVoiceAssistantService(orchestratorService, careAiService);
        PatientPortalCareAiStateResponse state = new PatientPortalCareAiStateResponse(
                "en",
                "APPOINTMENT_STATUS",
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                null,
                null,
                null,
                null,
                false,
                null,
                List.of(),
                List.of(),
                List.of()
        );
        when(orchestratorService.transcribeBufferedAudio(any(), any(), any(), any()))
                .thenReturn(new VoiceTranscriptionResult("What is my appointment status?", "faster-whisper", "ok"));
        when(careAiService.messageFromVoice(any(PatientPortalCareAiMessageRequest.class)))
                .thenReturn(new PatientPortalCareAiMessageResponse("Your next appointment is tomorrow at 10 AM.", state));
        when(orchestratorService.synthesizeAssistantText("Your next appointment is tomorrow at 10 AM.", "en"))
                .thenThrow(new IllegalStateException("tts timeout"));

        var response = service.processAudioTurn("audio".getBytes(StandardCharsets.UTF_8), "audio/webm", "voice.webm", "auto");

        assertThat(response.assistantText()).isEqualTo("Your next appointment is tomorrow at 10 AM.");
        assertThat(response.audioContentType()).isNull();
        assertThat(response.audioBase64()).isNull();
        assertThat(response.ttsProvider()).isNull();
        assertThat(response.ttsFallbackReason()).contains("tts timeout");
    }
}
