package com.deepthoughtnet.clinic.api.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilityRecord;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.api.voice.spi.SpeechToTextProvider;
import com.deepthoughtnet.clinic.api.voice.spi.TextToSpeechProvider;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisRequest;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisResult;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionRequest;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionResult;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class VoiceOrchestratorServiceTest {

    @AfterEach
    void clear() {
        RequestContextHolder.clear();
    }

    @Test
    void processAudioReturnsTranscriptAssistantTextAndFallbackTts() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid-1"));

        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getStt().setProviderOrder(List.of("mock"));
        properties.getTts().setProviderOrder(List.of("mock"));
        AiOrchestrationService aiOrchestrationService = mock(AiOrchestrationService.class);
        when(aiOrchestrationService.complete(any())).thenReturn(new AiOrchestrationResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AiProductCode.GENERIC,
                AiTaskType.GENERIC_COPILOT,
                "gemini",
                "gemini-2.5-flash",
                "I can help you book an appointment tomorrow.",
                null,
                BigDecimal.valueOf(0.91),
                List.of(),
                List.of(),
                List.of(),
                null,
                25L,
                false,
                null
        ));

        VoiceOrchestratorService service = new VoiceOrchestratorService(
                List.of(new MockVoiceSpeechToTextProvider()),
                List.of(new MockVoiceTextToSpeechProvider()),
                aiOrchestrationService,
                mock(AuditEventPublisher.class),
                properties,
                new ObjectMapper(),
                mock(FasterWhisperSpeechToTextProvider.class),
                mock(PiperTextToSpeechProvider.class)
        );

        VoiceTestResponse response = service.processAudio(
                new MockMultipartFile("audio", "sample.webm", "audio/webm", "voice".getBytes()),
                "Booking intent",
                "en"
        );

        assertThat(response.transcript()).isEqualTo("Hello, I want to book an appointment.");
        assertThat(response.assistantText()).contains("book an appointment");
        assertThat(response.audioBase64()).isNull();
        assertThat(response.providerTrace().sttProvider()).isEqualTo("mock");
        assertThat(response.providerTrace().llmProvider()).isEqualTo("gemini");
        assertThat(response.providerTrace().ttsProvider()).isEqualTo("mock");
    }

    @Test
    void processAudioFallsBackToSecondProviderWhenFirstFails() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid-2"));

        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getStt().setProviderOrder(List.of("broken", "mock"));
        properties.getTts().setProviderOrder(List.of("mock"));
        AiOrchestrationService aiOrchestrationService = mock(AiOrchestrationService.class);
        when(aiOrchestrationService.complete(any())).thenReturn(new AiOrchestrationResponse(
                UUID.randomUUID(), UUID.randomUUID(), AiProductCode.GENERIC, AiTaskType.GENERIC_COPILOT,
                "groq", "llama", "Fallback answer", null, BigDecimal.ONE, List.of(), List.of(), List.of(), null, 10L, true, null
        ));

        SpeechToTextProvider broken = new SpeechToTextProvider() {
            @Override public String providerName() { return "broken"; }
            @Override public boolean isReady() { return true; }
            @Override public VoiceTranscriptionResult transcribe(VoiceTranscriptionRequest request) { throw new IllegalStateException("boom"); }
        };

        VoiceOrchestratorService service = new VoiceOrchestratorService(
                List.of(broken, new MockVoiceSpeechToTextProvider()),
                List.of(new MockVoiceTextToSpeechProvider()),
                aiOrchestrationService,
                mock(AuditEventPublisher.class),
                properties,
                new ObjectMapper(),
                mock(FasterWhisperSpeechToTextProvider.class),
                mock(PiperTextToSpeechProvider.class)
        );

        VoiceTestResponse response = service.processAudio(
                new MockMultipartFile("audio", "sample.wav", "audio/wav", "voice".getBytes()),
                null,
                null
        );

        assertThat(response.transcript()).isEqualTo("Hello, I want to book an appointment.");
        assertThat(response.providerTrace().sttProvider()).isEqualTo("mock");
    }

    @Test
    void processAudioReflectsGroqWhenLlmFallbackIsUsed() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid-groq"));

        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getStt().setProviderOrder(List.of("mock"));
        properties.getTts().setProviderOrder(List.of("mock"));
        AiOrchestrationService aiOrchestrationService = mock(AiOrchestrationService.class);
        when(aiOrchestrationService.complete(any())).thenReturn(new AiOrchestrationResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AiProductCode.GENERIC,
                AiTaskType.GENERIC_COPILOT,
                "GROQ",
                "llama-3.1-8b-instant",
                "Groq handled the response after Gemini fallback.",
                null,
                BigDecimal.valueOf(0.87),
                List.of(),
                List.of(),
                List.of("Fallback provider was used. Please verify before acting."),
                null,
                25L,
                true,
                null
        ));

        VoiceOrchestratorService service = new VoiceOrchestratorService(
                List.of(new MockVoiceSpeechToTextProvider()),
                List.of(new MockVoiceTextToSpeechProvider()),
                aiOrchestrationService,
                mock(AuditEventPublisher.class),
                properties,
                new ObjectMapper(),
                mock(FasterWhisperSpeechToTextProvider.class),
                mock(PiperTextToSpeechProvider.class)
        );

        VoiceTestResponse response = service.processAudio(
                new MockMultipartFile("audio", "sample.m4a", "audio/mp4", "voice".getBytes()),
                "Voice fallback validation",
                "en"
        );

        assertThat(response.assistantText()).contains("Groq handled the response");
        assertThat(response.providerTrace().llmProvider()).isEqualTo("GROQ");
    }

    @Test
    void missingTenantContextIsRejected() {
        VoiceTestProperties properties = new VoiceTestProperties();
        VoiceOrchestratorService service = new VoiceOrchestratorService(
                List.of(new MockVoiceSpeechToTextProvider()),
                List.of(new MockVoiceTextToSpeechProvider()),
                mock(AiOrchestrationService.class),
                mock(AuditEventPublisher.class),
                properties,
                new ObjectMapper(),
                mock(FasterWhisperSpeechToTextProvider.class),
                mock(PiperTextToSpeechProvider.class)
        );

        assertThatThrownBy(() -> service.processAudio(
                new MockMultipartFile("audio", "sample.wav", "audio/wav", "voice".getBytes()),
                null,
                null
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Missing RequestContext");
    }

    @Test
    void missingAudioIsRejected() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid-3"));

        VoiceOrchestratorService service = new VoiceOrchestratorService(
                List.of(new MockVoiceSpeechToTextProvider()),
                List.of(new MockVoiceTextToSpeechProvider()),
                mock(AiOrchestrationService.class),
                mock(AuditEventPublisher.class),
                new VoiceTestProperties(),
                new ObjectMapper(),
                mock(FasterWhisperSpeechToTextProvider.class),
                mock(PiperTextToSpeechProvider.class)
        );

        assertThatThrownBy(() -> service.processAudio(
                new MockMultipartFile("audio", "sample.wav", "audio/wav", new byte[0]),
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Audio file is required");
    }

    @Test
    void configFasterWhisperSelectsFasterWhisperProvider() {
        VoiceTestResponse response = runWithConfiguredProvider("faster-whisper");
        assertThat(response.providerTrace().sttProvider()).isEqualTo("FASTER_WHISPER");
        assertThat(response.voiceDebugTrace()).extracting(VoiceDebugTraceEntry::stage)
                .contains("FASTER_WHISPER_REQUEST", "FASTER_WHISPER_RESPONSE", "STT_RESULT");
    }

    @Test
    void configUpperSnakeFasterWhisperSelectsFasterWhisperProvider() {
        VoiceTestResponse response = runWithConfiguredProvider("FASTER_WHISPER");
        assertThat(response.providerTrace().sttProvider()).isEqualTo("FASTER_WHISPER");
    }

    @Test
    void configLowerSnakeFasterWhisperSelectsFasterWhisperProvider() {
        VoiceTestResponse response = runWithConfiguredProvider("faster_whisper");
        assertThat(response.providerTrace().sttProvider()).isEqualTo("FASTER_WHISPER");
    }

    @Test
    void configuredFasterWhisperUsesDedicatedBeanEvenIfInjectedProviderListMissesIt() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid-dedicated"));

        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getStt().setProviderOrder(List.of("faster-whisper", "mock"));
        properties.getTts().setProviderOrder(List.of("mock"));
        properties.getLlm().setProviderOrder(List.of("gemini", "groq", "mock"));
        AiOrchestrationService ai = mock(AiOrchestrationService.class);
        when(ai.complete(any())).thenReturn(new AiOrchestrationResponse(
                UUID.randomUUID(), UUID.randomUUID(), AiProductCode.GENERIC, AiTaskType.GENERIC_COPILOT,
                "gemini", "gemini", "Handled", null, BigDecimal.ONE, List.of(), List.of(), List.of(), null, 1L, false, null
        ));

        FasterWhisperSpeechToTextProvider fasterWhisper = mock(FasterWhisperSpeechToTextProvider.class);
        when(fasterWhisper.providerName()).thenReturn("faster-whisper");
        when(fasterWhisper.isReady()).thenReturn(true);
        when(fasterWhisper.transcribeWithDebug(any(), any())).thenReturn(new VoiceTranscriptionResult("hello", "FASTER_WHISPER", null));

        MockVoiceSpeechToTextProvider mockProvider = spy(new MockVoiceSpeechToTextProvider());

        VoiceOrchestratorService service = new VoiceOrchestratorService(
                List.of(mockProvider),
                List.of(new MockVoiceTextToSpeechProvider()),
                ai,
                mock(AuditEventPublisher.class),
                properties,
                new ObjectMapper(),
                fasterWhisper,
                mock(PiperTextToSpeechProvider.class)
        );

        VoiceTestResponse response = service.processAudio(
                new MockMultipartFile("audio", "sample.m4a", "audio/mp4", "voice".getBytes()),
                null,
                null
        );

        assertThat(response.providerTrace().sttProvider()).isEqualTo("FASTER_WHISPER");
        verify(fasterWhisper).transcribeWithDebug(any(), any());
        verify(mockProvider, never()).transcribe(any());
    }

    @Test
    void llmProviderOrderChangesDoNotAffectSttProviderSelection() {
        VoiceTestResponse response = runWithConfiguredProvider("faster-whisper", List.of("groq", "gemini", "mock"));
        assertThat(response.providerTrace().sttProvider()).isEqualTo("FASTER_WHISPER");
    }

    @Test
    void voiceLlmRequestUsesCompactPromptAndVoiceSpecificMaxTokens() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid-voice-llm"));

        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getStt().setProviderOrder(List.of("mock"));
        properties.getTts().setProviderOrder(List.of("mock"));
        properties.getLlm().setMaxOutputTokens(1536);
        properties.getLlm().setMaxAnswerWords(35);
        properties.getLlm().setMaxHistoryChars(120);
        AiOrchestrationService ai = mock(AiOrchestrationService.class);
        when(ai.complete(any())).thenReturn(new AiOrchestrationResponse(
                UUID.randomUUID(), UUID.randomUUID(), AiProductCode.GENERIC, AiTaskType.GENERIC_COPILOT,
                "GEMINI", "gemini", "{\"answer\":\"Sure, what day works best for you?\",\"suggestedActions\":[]}",
                null, BigDecimal.ONE, List.of(), List.of(), List.of(), null, 1L, false, null
        ));

        VoiceOrchestratorService service = new VoiceOrchestratorService(
                List.of(new MockVoiceSpeechToTextProvider()),
                List.of(new MockVoiceTextToSpeechProvider()),
                ai,
                mock(AuditEventPublisher.class),
                properties,
                new ObjectMapper(),
                mock(FasterWhisperSpeechToTextProvider.class),
                mock(PiperTextToSpeechProvider.class)
        );

        service.processAudio(
                new MockMultipartFile("audio", "sample.webm", "audio/webm", "voice".getBytes()),
                "Conversation history: User: hello Assistant: hello again and again and again and again and again.",
                "en"
        );

        ArgumentCaptor<AiOrchestrationRequest> requestCaptor = ArgumentCaptor.forClass(AiOrchestrationRequest.class);
        verify(ai).complete(requestCaptor.capture());
        AiOrchestrationRequest request = requestCaptor.getValue();
        assertThat(request.maxTokens()).isEqualTo(1536);
        assertThat(request.inputVariables()).containsKeys("transcript", "conversationContext", "instruction", "responseContract");
        assertThat(String.valueOf(request.inputVariables().get("instruction"))).contains("Return ONLY compact valid JSON");
        assertThat(String.valueOf(request.inputVariables().get("instruction"))).contains("under 35 words");
        assertThat(String.valueOf(request.inputVariables().get("conversationContext")).length()).isLessThanOrEqualTo(123);
    }

    @Test
    void truncatedVoiceJsonFallsBackToShortSpokenReply() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid-voice-fallback"));

        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getStt().setProviderOrder(List.of("mock"));
        properties.getTts().setProviderOrder(List.of("mock"));
        AiOrchestrationService ai = mock(AiOrchestrationService.class);
        when(ai.complete(any())).thenReturn(new AiOrchestrationResponse(
                UUID.randomUUID(), UUID.randomUUID(), AiProductCode.GENERIC, AiTaskType.GENERIC_COPILOT,
                "GEMINI", "gemini", "AI response was incomplete. Please retry.",
                null, BigDecimal.ONE, List.of(), List.of(), List.of(), null, 1L, false, null
        ));

        VoiceOrchestratorService service = new VoiceOrchestratorService(
                List.of(new MockVoiceSpeechToTextProvider()),
                List.of(new MockVoiceTextToSpeechProvider()),
                ai,
                mock(AuditEventPublisher.class),
                properties,
                new ObjectMapper(),
                mock(FasterWhisperSpeechToTextProvider.class),
                mock(PiperTextToSpeechProvider.class)
        );

        VoiceTestResponse response = service.processAudio(
                new MockMultipartFile("audio", "sample.webm", "audio/webm", "voice".getBytes()),
                null,
                "en"
        );

        assertThat(response.assistantText()).isEqualTo("Sorry, I missed that. Could you please repeat?");
    }

    @Test
    void processAudioPropagatesHindiLanguageToTtsProvider() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid-hi-tts"));

        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getStt().setProviderOrder(List.of("mock"));
        properties.getTts().setProviderOrder(List.of("capture"));
        AiOrchestrationService ai = mock(AiOrchestrationService.class);
        when(ai.complete(any())).thenReturn(new AiOrchestrationResponse(
                UUID.randomUUID(), UUID.randomUUID(), AiProductCode.GENERIC, AiTaskType.GENERIC_COPILOT,
                "GEMINI", "gemini", "{\"answer\":\"Namaste, aap kis samay aana chahenge?\",\"suggestedActions\":[]}",
                null, BigDecimal.ONE, List.of(), List.of(), List.of(), null, 1L, false, null
        ));

        AtomicReference<String> capturedLanguage = new AtomicReference<>();
        TextToSpeechProvider captureTts = new TextToSpeechProvider() {
            @Override
            public String providerName() {
                return "capture";
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public VoiceSynthesisResult synthesize(VoiceSynthesisRequest request) {
                capturedLanguage.set(request.language());
                return new VoiceSynthesisResult("wav".getBytes(StandardCharsets.UTF_8), "audio/wav", "capture", null);
            }
        };

        VoiceOrchestratorService service = new VoiceOrchestratorService(
                List.of(new MockVoiceSpeechToTextProvider()),
                List.of(captureTts),
                ai,
                mock(AuditEventPublisher.class),
                properties,
                new ObjectMapper(),
                mock(FasterWhisperSpeechToTextProvider.class),
                mock(PiperTextToSpeechProvider.class)
        );

        VoiceTestResponse response = service.processAudio(
                new MockMultipartFile("audio", "sample.webm", "audio/webm", "voice".getBytes()),
                null,
                "hi"
        );

        assertThat(capturedLanguage.get()).isEqualTo("hi");
        assertThat(response.providerTrace().ttsProvider()).isEqualTo("capture");
    }

    @Test
    void voiceLlmRequestPinsHindiResponsesToSpokenHindi() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid-voice-hi"));

        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getStt().setProviderOrder(List.of("mock"));
        properties.getTts().setProviderOrder(List.of("mock"));
        AiOrchestrationService ai = mock(AiOrchestrationService.class);
        when(ai.complete(any())).thenReturn(new AiOrchestrationResponse(
                UUID.randomUUID(), UUID.randomUUID(), AiProductCode.GENERIC, AiTaskType.GENERIC_COPILOT,
                "GEMINI", "gemini", "{\"answer\":\"नमस्ते, आप किस समय आना चाहेंगे?\",\"suggestedActions\":[]}",
                null, BigDecimal.ONE, List.of(), List.of(), List.of(), null, 1L, false, null
        ));

        VoiceOrchestratorService service = new VoiceOrchestratorService(
                List.of(new MockVoiceSpeechToTextProvider()),
                List.of(new MockVoiceTextToSpeechProvider()),
                ai,
                mock(AuditEventPublisher.class),
                properties,
                new ObjectMapper(),
                mock(FasterWhisperSpeechToTextProvider.class),
                mock(PiperTextToSpeechProvider.class)
        );

        service.processAudio(
                new MockMultipartFile("audio", "sample.webm", "audio/webm", "voice".getBytes()),
                "Hindi appointment booking",
                "hi"
        );

        ArgumentCaptor<AiOrchestrationRequest> requestCaptor = ArgumentCaptor.forClass(AiOrchestrationRequest.class);
        verify(ai).complete(requestCaptor.capture());
        AiOrchestrationRequest request = requestCaptor.getValue();
        assertThat(String.valueOf(request.inputVariables().get("language"))).isEqualTo("hi");
        assertThat(String.valueOf(request.inputVariables().get("instruction"))).contains("Respond only in simple spoken Hindi written in Devanagari.");
    }

    @Test
    void appointmentWorkflowModeAddsStructuredWorkflowStateToLlmInput() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid-voice-appointment"));

        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getStt().setProviderOrder(List.of("mock"));
        properties.getTts().setProviderOrder(List.of("mock"));
        AiOrchestrationService ai = mock(AiOrchestrationService.class);
        when(ai.complete(any())).thenReturn(new AiOrchestrationResponse(
                UUID.randomUUID(), UUID.randomUUID(), AiProductCode.GENERIC, AiTaskType.GENERIC_COPILOT,
                "GEMINI", "gemini", "{\"answer\":\"कल सुबह डॉक्टर एबीसी उपलब्ध हैं। क्या मैं इसे कन्फर्म कर दूँ?\",\"suggestedActions\":[]}",
                null, BigDecimal.ONE, List.of(), List.of(), List.of(), null, 1L, false, null
        ));
        AppointmentService appointmentService = mock(AppointmentService.class);
        when(appointmentService.listAvailabilities(tenantId)).thenReturn(List.of(
                new DoctorAvailabilityRecord(
                        UUID.randomUUID(),
                        tenantId,
                        doctorUserId,
                        "Dr ABC",
                        DayOfWeek.MONDAY,
                        LocalTime.of(9, 0),
                        LocalTime.of(12, 0),
                        null,
                        null,
                        15,
                        1,
                        true,
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                )
        ));
        PatientService patientService = mock(PatientService.class);

        VoiceOrchestratorService service = new VoiceOrchestratorService(
                List.of(new MockVoiceSpeechToTextProvider()),
                List.of(new MockVoiceTextToSpeechProvider()),
                ai,
                mock(AuditEventPublisher.class),
                properties,
                new ObjectMapper(),
                mock(FasterWhisperSpeechToTextProvider.class),
                mock(PiperTextToSpeechProvider.class),
                new VoiceAppointmentWorkflowService(appointmentService, patientService, mock(TenantUserManagementService.class))
        );

        service.processAudio(
                new MockMultipartFile("audio", "sample.webm", "audio/webm", "voice".getBytes()),
                "Clinic receptionist appointment test",
                "hi",
                "appointment-booking"
        );

        ArgumentCaptor<AiOrchestrationRequest> requestCaptor = ArgumentCaptor.forClass(AiOrchestrationRequest.class);
        verify(ai).complete(requestCaptor.capture());
        AiOrchestrationRequest request = requestCaptor.getValue();
        assertThat(String.valueOf(request.inputVariables().get("workflowMode"))).isEqualTo("appointment-booking");
        assertThat(String.valueOf(request.inputVariables().get("instruction"))).contains("appointment booking workflow mode");
        assertThat(String.valueOf(request.inputVariables().get("instruction"))).contains("Respond only in simple spoken Hindi written in Devanagari.");
        assertThat(request.inputVariables()).containsKey("workflowState");
    }

    @Test
    void genericModeRemainsUnchangedAndDoesNotInjectAppointmentWorkflowState() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid-generic"));

        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getStt().setProviderOrder(List.of("mock"));
        properties.getTts().setProviderOrder(List.of("mock"));
        AiOrchestrationService ai = mock(AiOrchestrationService.class);
        when(ai.complete(any())).thenReturn(new AiOrchestrationResponse(
                UUID.randomUUID(), UUID.randomUUID(), AiProductCode.GENERIC, AiTaskType.GENERIC_COPILOT,
                "GEMINI", "gemini", "Generic assistant answer.", null, BigDecimal.ONE, List.of(), List.of(), List.of(), null, 1L, false, null
        ));

        VoiceOrchestratorService service = new VoiceOrchestratorService(
                List.of(new MockVoiceSpeechToTextProvider()),
                List.of(new MockVoiceTextToSpeechProvider()),
                ai,
                mock(AuditEventPublisher.class),
                properties,
                new ObjectMapper(),
                mock(FasterWhisperSpeechToTextProvider.class),
                mock(PiperTextToSpeechProvider.class),
                new VoiceAppointmentWorkflowService(mock(AppointmentService.class), mock(PatientService.class), mock(TenantUserManagementService.class))
        );

        VoiceTestResponse response = service.processAudio(
                new MockMultipartFile("audio", "sample.webm", "audio/webm", "voice".getBytes()),
                "General clinic question",
                "en",
                "generic"
        );

        ArgumentCaptor<AiOrchestrationRequest> requestCaptor = ArgumentCaptor.forClass(AiOrchestrationRequest.class);
        verify(ai).complete(requestCaptor.capture());
        AiOrchestrationRequest request = requestCaptor.getValue();
        assertThat(request.inputVariables().get("workflowMode")).isNull();
        assertThat(request.inputVariables()).doesNotContainKey("workflowState");
        assertThat(response.workflowSummary()).isNull();
    }

    @Test
    void missingConfiguredProviderIsRecordedInDebugTrace() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid-4"));

        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getStt().setProviderOrder(List.of("not-a-provider", "mock"));
        properties.getTts().setProviderOrder(List.of("mock"));
        AiOrchestrationService ai = mock(AiOrchestrationService.class);
        when(ai.complete(any())).thenReturn(new AiOrchestrationResponse(
                UUID.randomUUID(), UUID.randomUUID(), AiProductCode.GENERIC, AiTaskType.GENERIC_COPILOT,
                "gemini", "gemini", "Fallback", null, BigDecimal.ONE, List.of(), List.of(), List.of(), null, 1L, false, null
        ));

        VoiceOrchestratorService service = new VoiceOrchestratorService(
                List.of(new MockVoiceSpeechToTextProvider()),
                List.of(new MockVoiceTextToSpeechProvider()),
                ai,
                mock(AuditEventPublisher.class),
                properties,
                new ObjectMapper(),
                mock(FasterWhisperSpeechToTextProvider.class),
                mock(PiperTextToSpeechProvider.class)
        );

        VoiceTestResponse response = service.processAudio(
                new MockMultipartFile("audio", "sample.wav", "audio/wav", "voice".getBytes()),
                null,
                null
        );

        assertThat(response.voiceDebugTrace()).anySatisfy(entry -> {
            assertThat(entry.stage()).isEqualTo("STT_PROVIDER_MISSING");
            assertThat(entry.provider()).isEqualTo("not-a-provider");
        });
    }

    private VoiceTestResponse runWithConfiguredProvider(String configuredProvider) {
        return runWithConfiguredProvider(configuredProvider, List.of("gemini", "groq", "mock"));
    }

    private VoiceTestResponse runWithConfiguredProvider(String configuredProvider, List<String> llmOrder) {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid-provider"));

        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getStt().setProviderOrder(List.of(configuredProvider, "mock"));
        properties.getTts().setProviderOrder(List.of("mock"));
        properties.getLlm().setProviderOrder(llmOrder);
        AiOrchestrationService ai = mock(AiOrchestrationService.class);
        when(ai.complete(any())).thenReturn(new AiOrchestrationResponse(
                UUID.randomUUID(), UUID.randomUUID(), AiProductCode.GENERIC, AiTaskType.GENERIC_COPILOT,
                "gemini", "gemini", "Handled", null, BigDecimal.ONE, List.of(), List.of(), List.of(), null, 1L, false, null
        ));

        FasterWhisperSpeechToTextProvider fasterWhisper = mock(FasterWhisperSpeechToTextProvider.class);
        when(fasterWhisper.providerName()).thenReturn("faster-whisper");
        when(fasterWhisper.isReady()).thenReturn(true);
        when(fasterWhisper.transcribeWithDebug(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<VoiceDebugTraceEntry> trace = (List<VoiceDebugTraceEntry>) invocation.getArgument(1);
            trace.add(new VoiceDebugTraceEntry("FASTER_WHISPER_REQUEST", true, "faster-whisper", null, null, "sample.wav", "audio/wav", 5L, "http://faster-whisper:8000/transcribe", "file", null, null, null, null, null, null));
            trace.add(new VoiceDebugTraceEntry("FASTER_WHISPER_RESPONSE", true, "faster-whisper", null, null, "sample.wav", "audio/wav", 5L, "http://faster-whisper:8000/transcribe", "file", 200, "{\"text\":\"hello\"}", 5L, null, null, null));
            trace.add(new VoiceDebugTraceEntry("STT_RESULT", true, "FASTER_WHISPER", null, null, "sample.wav", "audio/wav", 5L, null, null, 200, null, 5L, 5, null, null));
            return new VoiceTranscriptionResult("hello", "FASTER_WHISPER", null);
        });
        PiperTextToSpeechProvider piper = mock(PiperTextToSpeechProvider.class);

        MockVoiceSpeechToTextProvider mockProvider = spy(new MockVoiceSpeechToTextProvider());

        VoiceOrchestratorService service = new VoiceOrchestratorService(
                List.of(fasterWhisper, mockProvider),
                List.of(new MockVoiceTextToSpeechProvider()),
                ai,
                mock(AuditEventPublisher.class),
                properties,
                new ObjectMapper(),
                fasterWhisper,
                piper
        );

        VoiceTestResponse response = service.processAudio(
                new MockMultipartFile("audio", "sample.wav", "audio/wav", "voice".getBytes()),
                null,
                null
        );

        verify(fasterWhisper).transcribeWithDebug(any(), any());
        verify(mockProvider, never()).transcribe(any());
        return response;
    }
}
