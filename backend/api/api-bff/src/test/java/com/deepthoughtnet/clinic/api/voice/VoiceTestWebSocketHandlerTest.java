package com.deepthoughtnet.clinic.api.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class VoiceTestWebSocketHandlerTest {
    private static final String TENANT_ID = "11111111-1111-1111-1111-111111111111";

    @Test
    void allowedRoleCanStartAndReceiveAssistantEvents() throws Exception {
        VoiceOrchestratorService orchestrator = mock(VoiceOrchestratorService.class);
        when(orchestrator.processBufferedAudio(any(), any(), any(), any(), any())).thenReturn(
                new VoiceTestResponse(
                        "req-1",
                        "Hello, I want to book an appointment.",
                        "I can help you with that.",
                        null,
                        null,
                        new VoiceProviderTrace("mock", "gemini", "mock")
                )
        );
        VoiceTestWebSocketHandler handler = new VoiceTestWebSocketHandler(new ObjectMapper(), orchestrator);
        SessionFixture fixture = new SessionFixture(TENANT_ID, Set.of("CLINIC_ADMIN"), "session-1");

        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\",\"language\":\"en-IN\",\"context\":\"clinic receptionist test\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"audioBase64\":\"" + Base64.getEncoder().encodeToString("voice".getBytes(StandardCharsets.UTF_8)) + "\",\"contentType\":\"audio/webm\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.end\"}"));

        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"session.started\""));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"transcript.final\"") && payload.contains("book an appointment"));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"assistant.text\"") && payload.contains("help you with that"));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"session.closed\""));
        verify(orchestrator).processBufferedAudio(any(), any(), any(), any(), any());
    }

    @Test
    void unauthorizedRoleIsClosedOnConnect() throws Exception {
        VoiceTestWebSocketHandler handler = new VoiceTestWebSocketHandler(new ObjectMapper(), mock(VoiceOrchestratorService.class));
        SessionFixture fixture = new SessionFixture(TENANT_ID, Set.of("DOCTOR"), "session-2");

        handler.afterConnectionEstablished(fixture.session);

        verify(fixture.session).close(any(CloseStatus.class));
    }

    @Test
    void missingTenantIsRejected() throws Exception {
        VoiceTestWebSocketHandler handler = new VoiceTestWebSocketHandler(new ObjectMapper(), mock(VoiceOrchestratorService.class));
        SessionFixture fixture = new SessionFixture(null, Set.of("CLINIC_ADMIN"), "session-3");

        handler.afterConnectionEstablished(fixture.session);

        verify(fixture.session).close(any(CloseStatus.class));
    }

    private static final class SessionFixture {
        private final List<String> payloads = new ArrayList<>();
        private final WebSocketSession session;

        private SessionFixture(String tenantId, Set<String> roles, String sessionId) throws Exception {
            this.session = mock(WebSocketSession.class);
            Map<String, Object> attrs = new HashMap<>();
            if (tenantId != null) {
                attrs.put("tenantId", tenantId);
            }
            attrs.put("roles", roles);
            attrs.put("sub", "subject-1");
            when(session.getId()).thenReturn(sessionId);
            when(session.getAttributes()).thenReturn(attrs);
            when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/voice/test"));
            when(session.isOpen()).thenReturn(true);
            doAnswer(invocation -> {
                TextMessage message = invocation.getArgument(0);
                payloads.add(message.getPayload());
                return null;
            }).when(session).sendMessage(any(TextMessage.class));
        }

        private List<String> payloads() {
            return payloads;
        }
    }
}
