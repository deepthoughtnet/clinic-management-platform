package com.deepthoughtnet.clinic.api.voice;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class VoiceTestControllerTest {

    @AfterEach
    void clear() {
        RequestContextHolder.clear();
    }

    @Test
    void testEndpointDelegatesToService() {
        VoiceOrchestratorService service = mock(VoiceOrchestratorService.class);
        VoiceTestController controller = new VoiceTestController(service);
        RequestContextHolder.set(new RequestContext(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));
        MockMultipartFile file = new MockMultipartFile("audio", "sample.webm", "audio/webm", "voice".getBytes());
        when(service.processAudio(file, "context", "en")).thenReturn(
                new VoiceTestResponse("req-1", "hello", "hi", null, null, new VoiceProviderTrace("mock", "gemini", "mock"), List.of())
        );

        controller.test(file, "context", "en");

        verify(service).processAudio(file, "context", "en");
    }

    @Test
    void missingAudioIsRejectedByServiceContract() {
        VoiceOrchestratorService service = mock(VoiceOrchestratorService.class);
        VoiceTestController controller = new VoiceTestController(service);
        when(service.processAudio(null, null, null)).thenThrow(new IllegalArgumentException("Audio file is required."));

        assertThatThrownBy(() -> controller.test(null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Audio file is required");
    }

    @Test
    void statusEndpointDelegatesToService() {
        VoiceOrchestratorService service = mock(VoiceOrchestratorService.class);
        VoiceTestController controller = new VoiceTestController(service);
        when(service.status(true)).thenReturn(new VoiceStatusResponse(
                true,
                new VoiceServiceStatus("FASTER_WHISPER", true, true, "ready"),
                new VoiceServiceStatus("PIPER", true, true, "ready"),
                new VoiceProviderTrace("faster-whisper", "gemini", "piper"),
                "en",
                "en_US-lessac-medium",
                java.util.Map.of("default", "en_US-lessac-medium", "en", "en_US-lessac-medium"),
                false,
                true
        ));

        controller.status(true);

        verify(service).status(true);
    }

    @Test
    void debugSttEndpointDelegatesToService() {
        VoiceOrchestratorService service = mock(VoiceOrchestratorService.class);
        VoiceTestController controller = new VoiceTestController(service);
        MockMultipartFile file = new MockMultipartFile("audio", "sample.wav", "audio/wav", "voice".getBytes());
        when(service.debugStt(file, "en")).thenReturn(new VoiceSttDebugResponse("req-2", "hello", "FASTER_WHISPER", List.of()));

        controller.debugStt(file, "en");

        verify(service).debugStt(file, "en");
    }
}
