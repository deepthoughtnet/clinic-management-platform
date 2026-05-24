package com.deepthoughtnet.clinic.api.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.api.voice.spi.SpeechToTextProvider;
import com.deepthoughtnet.clinic.api.voice.spi.TextToSpeechProvider;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisRequest;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisResult;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionRequest;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionResult;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
}
