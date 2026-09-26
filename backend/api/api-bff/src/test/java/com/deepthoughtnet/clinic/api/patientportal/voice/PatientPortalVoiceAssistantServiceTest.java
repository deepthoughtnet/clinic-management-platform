package com.deepthoughtnet.clinic.api.patientportal.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiMessageRequest;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiMessageResponse;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiResponseComposerService;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiService;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiStateResponse;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalConversationStateService;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageResponse;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2VoiceTurnConnector;
import com.deepthoughtnet.clinic.api.voice.VoiceOrchestratorService;
import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisResult;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionResult;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
    void v2EnglishRenderedResponseReachesTtsUnchangedWithEnglishLocale() {
        VoiceOrchestratorService orchestratorService = mock(VoiceOrchestratorService.class);
        PatientPortalCareAiService careAiService = mock(PatientPortalCareAiService.class);
        AivaV2VoiceTurnConnector connector = mock(AivaV2VoiceTurnConnector.class);
        PatientPortalVoiceAssistantService service = new PatientPortalVoiceAssistantService(
                orchestratorService, careAiService, new VoiceTestProperties(), null, null, connector);
        String rendered = "You have 2 upcoming appointments: 1. 23 September 2026 at 20:00.";
        when(orchestratorService.transcribeBufferedAudio(any(), any(), any(), any()))
                .thenReturn(new VoiceTranscriptionResult("Show my appointments", "sarvam", "ok"));
        when(connector.submitFinalTranscript(any(), any(), any(), any(), anyLong()))
                .thenReturn(new MessageResponse("voice-conversation", "turn-1", rendered, "APPOINTMENTS_FOUND",
                        null, "DETERMINISTIC", false, List.of()));
        when(connector.responseLanguageForVoice("Show my appointments", "auto")).thenReturn("en");
        when(orchestratorService.synthesizeRenderedAssistantText(rendered, "en-IN"))
                .thenReturn(new VoiceSynthesisResult("voice".getBytes(StandardCharsets.UTF_8), "audio/wav", "piper", "ok"));

        var response = service.processAudioTurnV2(
                "audio".getBytes(StandardCharsets.UTF_8), "audio/webm", "voice.webm", "auto",
                "voice-conversation", "voice-turn-1", 1L);

        assertThat(response.assistantText()).isEqualTo(rendered);
        verify(orchestratorService).synthesizeRenderedAssistantText(rendered, "en-IN");
        verify(orchestratorService, never()).synthesizeAssistantText(any(), any());
    }

    @Test
    void v2HindiRenderedResponseKeepsVisibleTextAndUsesHindiTemporalSpeechText() {
        VoiceOrchestratorService orchestratorService = mock(VoiceOrchestratorService.class);
        PatientPortalCareAiService careAiService = mock(PatientPortalCareAiService.class);
        AivaV2VoiceTurnConnector connector = mock(AivaV2VoiceTurnConnector.class);
        PatientPortalVoiceAssistantService service = new PatientPortalVoiceAssistantService(
                orchestratorService, careAiService, new VoiceTestProperties(), null, null, connector);
        String rendered = "आपकी अपॉइंटमेंट 23 सितंबर 2026 को 20:00 बजे है।";
        String speechText = "आपकी अपॉइंटमेंट तेईस सितंबर दो हज़ार छब्बीस को शाम आठ बजे है।";
        when(orchestratorService.transcribeBufferedAudio(any(), any(), any(), any()))
                .thenReturn(new VoiceTranscriptionResult("मेरी अपॉइंटमेंट दिखाओ", "sarvam", "ok"));
        when(connector.submitFinalTranscript(any(), any(), any(), any(), anyLong()))
                .thenReturn(new MessageResponse("voice-conversation", "turn-1", rendered, "APPOINTMENTS_FOUND",
                        null, "DETERMINISTIC", false, List.of()));
        when(connector.responseLanguageForVoice("मेरी अपॉइंटमेंट दिखाओ", "auto")).thenReturn("hi");
        when(orchestratorService.synthesizeRenderedAssistantText(speechText, "hi-IN"))
                .thenReturn(new VoiceSynthesisResult("voice".getBytes(StandardCharsets.UTF_8), "audio/wav", "piper", "ok"));

        var response = service.processAudioTurnV2(
                "audio".getBytes(StandardCharsets.UTF_8), "audio/webm", "voice.webm", "auto",
                "voice-conversation", "voice-turn-1", 1L);

        assertThat(response.assistantText()).isEqualTo(rendered);
        verify(orchestratorService).synthesizeRenderedAssistantText(speechText, "hi-IN");
        verify(orchestratorService, never()).synthesizeAssistantText(any(), any());
    }

    @Test
    void v2EmptySttResultIsIgnoredBeforeSemanticConnector() {
        VoiceOrchestratorService orchestratorService = mock(VoiceOrchestratorService.class);
        PatientPortalCareAiService careAiService = mock(PatientPortalCareAiService.class);
        AivaV2VoiceTurnConnector connector = mock(AivaV2VoiceTurnConnector.class);
        PatientPortalVoiceAssistantService service = new PatientPortalVoiceAssistantService(
                orchestratorService, careAiService, new VoiceTestProperties(), null, null, connector);
        when(orchestratorService.transcribeBufferedAudio(any(), any(), any(), any()))
                .thenReturn(new VoiceTranscriptionResult("  ", "sarvam", "ok"));

        assertThat(service.processAudioTurnV2("audio".getBytes(StandardCharsets.UTF_8), "audio/webm",
                "voice.webm", "auto", "voice-conversation", "voice-turn-1", 1L)).isNull();
        verifyNoInteractions(connector);
    }

    @Test
    void hindiVoiceTurnNormalizesAssistantTextBeforeTtsWithoutChangingReturnedText() {
        VoiceOrchestratorService orchestratorService = mock(VoiceOrchestratorService.class);
        PatientPortalCareAiService careAiService = mock(PatientPortalCareAiService.class);
        PatientPortalVoiceAssistantService service = new PatientPortalVoiceAssistantService(orchestratorService, careAiService);
        PatientPortalCareAiStateResponse state = new PatientPortalCareAiStateResponse(
                "hi-IN",
                "CHECK_APPOINTMENT",
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
                .thenReturn(new VoiceTranscriptionResult("show appointments", "faster-whisper", "ok"));
        when(careAiService.messageFromVoice(any(PatientPortalCareAiMessageRequest.class)))
                .thenReturn(new PatientPortalCareAiMessageResponse(
                        "Here are your upcoming appointments:\nVikas Singh · 27 Jun 2026 · 10:00",
                        state
                ));
        when(orchestratorService.synthesizeAssistantText(any(), eq("hi-IN")))
                .thenReturn(new VoiceSynthesisResult("voice".getBytes(StandardCharsets.UTF_8), "audio/wav", "piper", "ok"));

        var response = service.processAudioTurn("audio".getBytes(StandardCharsets.UTF_8), "audio/webm", "voice.webm", "auto");

        ArgumentCaptor<String> synthesizedTextCaptor = ArgumentCaptor.forClass(String.class);
        verify(orchestratorService).synthesizeAssistantText(synthesizedTextCaptor.capture(), eq("hi-IN"));
        assertThat(response.assistantText()).isEqualTo("Here are your upcoming appointments:\nVikas Singh · 27 Jun 2026 · 10:00");
        assertThat(synthesizedTextCaptor.getValue()).contains("आपकी आने वाली अपॉइंटमेंट ये हैं:");
        assertThat(synthesizedTextCaptor.getValue()).contains("विकास सिंह");
        assertThat(response.audioContentType()).isEqualTo("audio/wav");
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
    void mockSttDoesNotInvokeCareAiWorkflows() {
        VoiceOrchestratorService orchestratorService = mock(VoiceOrchestratorService.class);
        PatientPortalCareAiService careAiService = mock(PatientPortalCareAiService.class);
        PatientPortalVoiceAssistantService service = new PatientPortalVoiceAssistantService(orchestratorService, careAiService);
        when(orchestratorService.transcribeBufferedAudio(any(), any(), any(), any()))
                .thenReturn(new VoiceTranscriptionResult("Hello, I want to book an appointment.", "mock", "Mock STT transcript used because no live STT provider is configured."));

        var response = service.processAudioTurn("audio".getBytes(StandardCharsets.UTF_8), "audio/webm", "voice.webm", "auto");

        verify(careAiService, never()).messageFromVoice(any());
        assertThat(response.assistantText()).contains("temporarily unavailable");
        assertThat(response.transcript()).isEqualTo("");
        assertThat(response.state()).isNull();
        assertThat(response.ttsProvider()).isNull();
        assertThat(response.ttsFallbackReason()).contains("Mock STT transcript used because no live STT provider is configured.");
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

    @Test
    void sttFailureReturnsTypedFallbackWithoutInvokingCareAi() {
        VoiceOrchestratorService orchestratorService = mock(VoiceOrchestratorService.class);
        PatientPortalCareAiService careAiService = mock(PatientPortalCareAiService.class);
        PatientPortalVoiceAssistantService service = new PatientPortalVoiceAssistantService(orchestratorService, careAiService);
        when(orchestratorService.transcribeBufferedAudio(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("Local STT service unavailable"));

        var response = service.processAudioTurn("audio".getBytes(StandardCharsets.UTF_8), "audio/webm", "voice.webm", "auto");

        verify(careAiService, never()).messageFromVoice(any());
        assertThat(response.transcript()).isEmpty();
        assertThat(response.assistantText()).contains("temporarily unavailable");
        assertThat(response.sttProvider()).isNull();
        assertThat(response.ttsFallbackReason()).contains("Local STT service unavailable");
    }

    @Test
    void voiceTurnUsesComposerForTtsWhenConfigured() {
        VoiceOrchestratorService orchestratorService = mock(VoiceOrchestratorService.class);
        PatientPortalCareAiService careAiService = mock(PatientPortalCareAiService.class);
        PatientPortalCareAiResponseComposerService composerService = mock(PatientPortalCareAiResponseComposerService.class);
        PatientPortalVoiceAssistantService service = new PatientPortalVoiceAssistantService(
                orchestratorService,
                careAiService,
                new com.deepthoughtnet.clinic.api.voice.VoiceTestProperties(),
                composerService
        );
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
        when(composerService.compose(
                eq("Please confirm the 10:30 slot."),
                eq("confirmation_prompt"),
                eq("en"),
                eq("BOOK_APPOINTMENT"),
                any()
        )).thenReturn("डॉक्टर नेहा मेहता के लिए सुबह साढ़े नौ बजे का स्लॉट उपलब्ध है। क्या मैं यह अपॉइंटमेंट बुक कर दूँ?");
        when(orchestratorService.synthesizeAssistantText(
                "डॉक्टर नेहा मेहता के लिए सुबह साढ़े नौ बजे का स्लॉट उपलब्ध है। क्या मैं यह अपॉइंटमेंट बुक कर दूँ?",
                "en"
        )).thenReturn(new VoiceSynthesisResult("voice".getBytes(StandardCharsets.UTF_8), "audio/wav", "piper", "ok"));

        var response = service.processAudioTurn("audio".getBytes(StandardCharsets.UTF_8), "audio/webm", "voice.webm", "auto");

        assertThat(response.assistantText()).contains("सुबह साढ़े नौ बजे");
        verify(composerService).compose(
                eq("Please confirm the 10:30 slot."),
                eq("confirmation_prompt"),
                eq("en"),
                eq("BOOK_APPOINTMENT"),
                any()
        );
    }

    @Test
    void expiredConversationStateIsResetBeforeProcessingVoiceTurn() {
        VoiceOrchestratorService orchestratorService = mock(VoiceOrchestratorService.class);
        PatientPortalCareAiService careAiService = mock(PatientPortalCareAiService.class);
        PatientPortalConversationStateService conversationStateService = mock(PatientPortalConversationStateService.class);
        PatientPortalVoiceAssistantService service = new PatientPortalVoiceAssistantService(
                orchestratorService,
                careAiService,
                new com.deepthoughtnet.clinic.api.voice.VoiceTestProperties(),
                null,
                conversationStateService
        );
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), userId, "patient", Set.of("PATIENT"), "PATIENT", "voice-session-1"));
        try {
            when(conversationStateService.isExpired("voice-session-1")).thenReturn(true);
            when(orchestratorService.transcribeBufferedAudio(any(), any(), any(), any()))
                    .thenReturn(new VoiceTranscriptionResult("Book appointment with Dr Neha Mehta", "faster-whisper", "ok"));
            when(careAiService.messageFromVoice(any(PatientPortalCareAiMessageRequest.class)))
                    .thenReturn(new PatientPortalCareAiMessageResponse("Please confirm the 10:30 slot.", state()));
            when(orchestratorService.synthesizeAssistantText(any(), any()))
                    .thenReturn(new VoiceSynthesisResult("voice".getBytes(StandardCharsets.UTF_8), "audio/wav", "piper", "ok"));

            service.processAudioTurn("audio".getBytes(StandardCharsets.UTF_8), "audio/webm", "voice.webm", "auto");

            verify(careAiService).resetVoiceConversation();
            verify(conversationStateService).clear("voice-session-1");
        } finally {
            RequestContextHolder.clear();
        }
    }

    private PatientPortalCareAiStateResponse state() {
        return new PatientPortalCareAiStateResponse(
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
    }
}
