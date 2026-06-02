package com.deepthoughtnet.clinic.api.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class VoiceTestWebSocketHandlerTest {
    private static final String TENANT_ID = "11111111-1111-1111-1111-111111111111";

    @Test
    void allowedRoleCanStartAndReceiveAssistantEvents() throws Exception {
        VoiceOrchestratorService orchestrator = mock(VoiceOrchestratorService.class);
        when(orchestrator.processBufferedAudio(any(), any(), any(), any(), any(), any(), any())).thenReturn(
                new VoiceTestResponse(
                        "req-1",
                        "Hello, I want to book an appointment.",
                        "I can help you with that.",
                        null,
                        null,
                        new VoiceProviderTrace("mock", "gemini", "mock"),
                        List.of()
                )
        );
        VoiceTestWebSocketHandler handler = new VoiceTestWebSocketHandler(new ObjectMapper(), orchestrator, new VoiceTestProperties());
        SessionFixture fixture = new SessionFixture(TENANT_ID, Set.of("CLINIC_ADMIN"), "session-1");
        String audioBase64 = Base64.getEncoder().encodeToString("voice".getBytes(StandardCharsets.UTF_8));

        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\",\"language\":\"en-IN\",\"context\":\"clinic receptionist test\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"sequence\":1,\"totalChunks\":1,\"filename\":\"voice-live-1.webm\",\"audioBase64Chunk\":\"" + audioBase64 + "\",\"contentType\":\"audio/webm\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.end\",\"filename\":\"voice-live-1.webm\",\"contentType\":\"audio/webm\",\"totalChunks\":1}"));

        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"session.started\""));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"audio.chunk.received\""));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"audio.buffer.complete\""));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"transcript.final\"") && payload.contains("book an appointment"));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"assistant.text\"") && payload.contains("help you with that"));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"turn.complete\""));
        assertThat(fixture.payloads()).noneMatch(payload -> payload.contains("\"type\":\"session.closed\""));
        verify(orchestrator).processBufferedAudio(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void secondTurnReusesSameSessionAndCarriesConversationHistory() throws Exception {
        VoiceOrchestratorService orchestrator = mock(VoiceOrchestratorService.class);
        when(orchestrator.processBufferedAudio(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(
                        new VoiceTestResponse(
                                "req-1",
                                "I want to book an appointment.",
                                "Sure, which day works for you?",
                                null,
                                null,
                                new VoiceProviderTrace("FASTER_WHISPER", "GEMINI", "piper"),
                                List.of()
                        ),
                        new VoiceTestResponse(
                                "req-2",
                                "Tomorrow morning with Dr Sharma.",
                                "I can note tomorrow morning with Dr Sharma.",
                                null,
                                null,
                                new VoiceProviderTrace("FASTER_WHISPER", "GROQ", "piper"),
                                List.of()
                        )
                );
        VoiceTestWebSocketHandler handler = new VoiceTestWebSocketHandler(new ObjectMapper(), orchestrator, new VoiceTestProperties());
        SessionFixture fixture = new SessionFixture(TENANT_ID, Set.of("CLINIC_ADMIN"), "session-10");
        String audioBase64 = Base64.getEncoder().encodeToString("voice".getBytes(StandardCharsets.UTF_8));

        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\",\"language\":\"en-IN\",\"context\":\"clinic receptionist test\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"sequence\":1,\"totalChunks\":1,\"filename\":\"voice-live-1.webm\",\"audioBase64Chunk\":\"" + audioBase64 + "\",\"contentType\":\"audio/webm\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.end\",\"filename\":\"voice-live-1.webm\",\"contentType\":\"audio/webm\",\"totalChunks\":1}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"sequence\":1,\"totalChunks\":1,\"filename\":\"voice-live-2.webm\",\"audioBase64Chunk\":\"" + audioBase64 + "\",\"contentType\":\"audio/webm\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.end\",\"filename\":\"voice-live-2.webm\",\"contentType\":\"audio/webm\",\"totalChunks\":1}"));

        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        verify(orchestrator).processBufferedAudio(any(), eq("audio/webm"), eq("voice-live-1.webm"), contextCaptor.capture(), eq("en-IN"), eq("generic"), any());
        verify(orchestrator).processBufferedAudio(any(), eq("audio/webm"), eq("voice-live-2.webm"), contextCaptor.capture(), eq("en-IN"), eq("generic"), any());
        List<String> contexts = contextCaptor.getAllValues();
        assertThat(contexts).hasSize(2);
        assertThat(contexts.get(0)).contains("clinic receptionist test");
        assertThat(contexts.get(1)).contains("Recent conversation:");
        assertThat(contexts.get(1)).contains("I want to book an appointment.");
        assertThat(contexts.get(1)).contains("Sure, which day works for you?");
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"turn.complete\"") && payload.contains("\"turnIndex\":1"));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"turn.complete\"") && payload.contains("\"turnIndex\":2"));
        assertThat(fixture.payloads()).noneMatch(payload -> payload.contains("\"type\":\"session.closed\""));
    }

    @Test
    void missingChunkReturnsClearError() throws Exception {
        VoiceTestWebSocketHandler handler = new VoiceTestWebSocketHandler(new ObjectMapper(), mock(VoiceOrchestratorService.class), new VoiceTestProperties());
        SessionFixture fixture = new SessionFixture(TENANT_ID, Set.of("CLINIC_ADMIN"), "session-4");
        String audioBase64 = Base64.getEncoder().encodeToString("voice".getBytes(StandardCharsets.UTF_8));

        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"sequence\":1,\"totalChunks\":2,\"filename\":\"voice-live-2.webm\",\"audioBase64Chunk\":\"" + audioBase64 + "\",\"contentType\":\"audio/webm\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.end\",\"filename\":\"voice-live-2.webm\",\"contentType\":\"audio/webm\",\"totalChunks\":2}"));

        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"error\"") && payload.contains("Missing chunks"));
    }

    @Test
    void oversizedChunkReturnsClearError() throws Exception {
        VoiceTestWebSocketHandler handler = new VoiceTestWebSocketHandler(new ObjectMapper(), mock(VoiceOrchestratorService.class), new VoiceTestProperties());
        SessionFixture fixture = new SessionFixture(TENANT_ID, Set.of("CLINIC_ADMIN"), "session-7");
        String oversizedChunk = "A".repeat((32 * 1024) + 1);

        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"sequence\":1,\"totalChunks\":1,\"filename\":\"voice-live-4.webm\",\"audioBase64Chunk\":\"" + oversizedChunk + "\",\"contentType\":\"audio/webm\"}"));

        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"error\"") && payload.contains("supported websocket size"));
    }

    @Test
    void streamedChunksCanDeclareTotalOnlyAtAudioEnd() throws Exception {
        VoiceOrchestratorService orchestrator = mock(VoiceOrchestratorService.class);
        when(orchestrator.processBufferedAudio(any(), any(), any(), any(), any(), any(), any())).thenReturn(
                new VoiceTestResponse(
                        "req-2",
                        "Live websocket transcript.",
                        "Here is the live response.",
                        null,
                        null,
                        new VoiceProviderTrace("faster-whisper", "gemini", "piper"),
                        List.of()
                )
        );
        VoiceTestWebSocketHandler handler = new VoiceTestWebSocketHandler(new ObjectMapper(), orchestrator, new VoiceTestProperties());
        SessionFixture fixture = new SessionFixture(TENANT_ID, Set.of("CLINIC_ADMIN"), "session-5");

        String fullBase64 = Base64.getEncoder().encodeToString("voice".getBytes(StandardCharsets.UTF_8));
        String partOne = fullBase64.substring(0, fullBase64.length() / 2);
        String partTwo = fullBase64.substring(fullBase64.length() / 2);

        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"sequence\":1,\"filename\":\"voice-live-3.webm\",\"audioBase64Chunk\":\"" + partOne + "\",\"contentType\":\"audio/webm;codecs=opus\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"sequence\":2,\"filename\":\"voice-live-3.webm\",\"audioBase64Chunk\":\"" + partTwo + "\",\"contentType\":\"audio/webm;codecs=opus\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.end\",\"filename\":\"voice-live-3.webm\",\"contentType\":\"audio/webm;codecs=opus\",\"totalChunks\":2}"));

        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"audio.buffer.complete\""));
        verify(orchestrator).processBufferedAudio(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void dataUrlPrefixedChunksAreHandledDefensively() throws Exception {
        VoiceOrchestratorService orchestrator = mock(VoiceOrchestratorService.class);
        when(orchestrator.processBufferedAudio(any(), any(), any(), any(), any(), any(), any())).thenReturn(
                new VoiceTestResponse(
                        "req-4",
                        "Transcript.",
                        "Assistant response.",
                        null,
                        null,
                        new VoiceProviderTrace("FASTER_WHISPER", "gemini", "piper"),
                        List.of()
                )
        );
        VoiceTestWebSocketHandler handler = new VoiceTestWebSocketHandler(new ObjectMapper(), orchestrator, new VoiceTestProperties());
        SessionFixture fixture = new SessionFixture(TENANT_ID, Set.of("CLINIC_ADMIN"), "session-8");
        String audioBase64 = Base64.getEncoder().encodeToString("voice".getBytes(StandardCharsets.UTF_8));

        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"sequence\":1,\"totalChunks\":1,\"filename\":\"voice-live-5.webm\",\"audioBase64Chunk\":\"data:audio/webm;base64," + audioBase64 + "\",\"contentType\":\"audio/webm\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.end\",\"filename\":\"voice-live-5.webm\",\"contentType\":\"audio/webm\",\"totalChunks\":1}"));

        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"audio.decoded\""));
        verify(orchestrator).processBufferedAudio(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void invalidBase64ReturnsClearError() throws Exception {
        VoiceTestWebSocketHandler handler = new VoiceTestWebSocketHandler(new ObjectMapper(), mock(VoiceOrchestratorService.class), new VoiceTestProperties());
        SessionFixture fixture = new SessionFixture(TENANT_ID, Set.of("CLINIC_ADMIN"), "session-9");

        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"sequence\":1,\"totalChunks\":1,\"filename\":\"voice-live-6.webm\",\"audioBase64Chunk\":\"not-valid-base64###\",\"contentType\":\"audio/webm\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.end\",\"filename\":\"voice-live-6.webm\",\"contentType\":\"audio/webm\",\"totalChunks\":1}"));

        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"error\"") && payload.contains("could not be decoded"));
    }

    @Test
    void assistantAudioIsReturnedAsChunkedEvents() throws Exception {
        VoiceOrchestratorService orchestrator = mock(VoiceOrchestratorService.class);
        String largeAudioBase64 = "A".repeat(40_000);
        when(orchestrator.processBufferedAudio(any(), any(), any(), any(), any(), any(), any())).thenReturn(
                new VoiceTestResponse(
                        "req-3",
                        "Transcript.",
                        "Assistant response.",
                        "audio/wav",
                        largeAudioBase64,
                        new VoiceProviderTrace("faster-whisper", "gemini", "piper"),
                        List.of()
                )
        );
        VoiceTestWebSocketHandler handler = new VoiceTestWebSocketHandler(new ObjectMapper(), orchestrator, new VoiceTestProperties());
        SessionFixture fixture = new SessionFixture(TENANT_ID, Set.of("CLINIC_ADMIN"), "session-6");
        String audioBase64 = Base64.getEncoder().encodeToString("voice".getBytes(StandardCharsets.UTF_8));

        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"sequence\":1,\"totalChunks\":1,\"filename\":\"voice-live-1.webm\",\"audioBase64Chunk\":\"" + audioBase64 + "\",\"contentType\":\"audio/webm\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.end\",\"filename\":\"voice-live-1.webm\",\"contentType\":\"audio/webm\",\"totalChunks\":1}"));

        assertThat(fixture.payloads()).noneMatch(payload -> payload.contains("\"type\":\"assistant.audio\""));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"assistant.audio.chunk\"") && payload.contains("\"sequence\":1"));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"assistant.audio.chunk\"") && payload.contains("\"sequence\":2"));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"assistant.audio.end\"") && payload.contains("\"contentType\":\"audio/wav\""));
    }

    @Test
    void heartbeatReturnsAck() throws Exception {
        VoiceTestWebSocketHandler handler = new VoiceTestWebSocketHandler(new ObjectMapper(), mock(VoiceOrchestratorService.class), new VoiceTestProperties());
        SessionFixture fixture = new SessionFixture(TENANT_ID, Set.of("CLINIC_ADMIN"), "session-heartbeat");

        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"heartbeat\"}"));

        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"heartbeat\""));
    }

    @Test
    void maxTurnsPerSessionIsEnforced() throws Exception {
        VoiceOrchestratorService orchestrator = mock(VoiceOrchestratorService.class);
        when(orchestrator.processBufferedAudio(any(), any(), any(), any(), any(), any(), any())).thenReturn(
                new VoiceTestResponse(
                        "req-1",
                        "Hello",
                        "Hi",
                        null,
                        null,
                        new VoiceProviderTrace("FASTER_WHISPER", "GEMINI", "piper"),
                        List.of()
                )
        );
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getLive().setMaxTurnsPerSession(1);
        VoiceTestWebSocketHandler handler = new VoiceTestWebSocketHandler(new ObjectMapper(), orchestrator, properties);
        SessionFixture fixture = new SessionFixture(TENANT_ID, Set.of("CLINIC_ADMIN"), "session-max-turns");
        String audioBase64 = Base64.getEncoder().encodeToString("voice".getBytes(StandardCharsets.UTF_8));

        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"sequence\":1,\"totalChunks\":1,\"filename\":\"voice-live-1.webm\",\"audioBase64Chunk\":\"" + audioBase64 + "\",\"contentType\":\"audio/webm\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.end\",\"filename\":\"voice-live-1.webm\",\"contentType\":\"audio/webm\",\"totalChunks\":1}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"sequence\":1,\"totalChunks\":1,\"filename\":\"voice-live-2.webm\",\"audioBase64Chunk\":\"" + audioBase64 + "\",\"contentType\":\"audio/webm\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.end\",\"filename\":\"voice-live-2.webm\",\"contentType\":\"audio/webm\",\"totalChunks\":1}"));

        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"error\"") && payload.contains("turn limit"));
    }

    @Test
    void appointmentWorkflowModeIsPropagatedAcrossSessionTurns() throws Exception {
        VoiceOrchestratorService orchestrator = mock(VoiceOrchestratorService.class);
        VoiceWorkflowSummary summary = new VoiceWorkflowSummary(
                "appointment-booking",
                "COLLECTING_DETAILS",
                "COLLECTING_DETAILS",
                "en",
                "VOICE_TEST",
                null,
                null,
                null,
                null,
                "PENDING",
                null,
                null,
                "PENDING",
                null,
                null,
                null,
                List.of("doctorName", "preferredDate", "preferredTimeWindow"),
                null,
                List.of(),
                false,
                false,
                false,
                null,
                false,
                null,
                "Which doctor would you like to see?",
                0,
                List.of(),
                List.of()
        );
        when(orchestrator.processBufferedAudio(any(), any(), any(), any(), any(), any(), any())).thenReturn(
                new VoiceTestResponse(
                        "req-workflow",
                        "I want to book an appointment.",
                        "Which doctor would you like to see?",
                        null,
                        null,
                        new VoiceProviderTrace("FASTER_WHISPER", "GEMINI", "piper"),
                        List.of(),
                        summary
                )
        );
        VoiceTestWebSocketHandler handler = new VoiceTestWebSocketHandler(new ObjectMapper(), orchestrator, new VoiceTestProperties());
        SessionFixture fixture = new SessionFixture(TENANT_ID, Set.of("CLINIC_ADMIN"), "session-workflow");
        String audioBase64 = Base64.getEncoder().encodeToString("voice".getBytes(StandardCharsets.UTF_8));

        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\",\"workflowMode\":\"appointment-booking\",\"language\":\"en\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"sequence\":1,\"totalChunks\":1,\"filename\":\"voice-live-1.webm\",\"audioBase64Chunk\":\"" + audioBase64 + "\",\"contentType\":\"audio/webm\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.end\",\"filename\":\"voice-live-1.webm\",\"contentType\":\"audio/webm\",\"totalChunks\":1}"));

        verify(orchestrator).processBufferedAudio(any(), eq("audio/webm"), eq("voice-live-1.webm"), any(), eq("en"), eq("appointment-booking"), any());
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"workflowMode\":\"appointment-booking\""));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"turn.complete\"") && payload.contains("\"intentState\":\"COLLECTING_DETAILS\""));
    }

    @Test
    void unauthorizedRoleIsClosedOnConnect() throws Exception {
        VoiceTestWebSocketHandler handler = new VoiceTestWebSocketHandler(new ObjectMapper(), mock(VoiceOrchestratorService.class), new VoiceTestProperties());
        SessionFixture fixture = new SessionFixture(TENANT_ID, Set.of("VIEWER"), "session-2");

        handler.afterConnectionEstablished(fixture.session);

        verify(fixture.session).close(any(CloseStatus.class));
    }

    @Test
    void receptionistIsAuthorizedOnConnect() throws Exception {
        VoiceTestWebSocketHandler handler = new VoiceTestWebSocketHandler(new ObjectMapper(), mock(VoiceOrchestratorService.class), new VoiceTestProperties());
        SessionFixture fixture = new SessionFixture(TENANT_ID, Set.of("RECEPTIONIST"), "session-receptionist");

        handler.afterConnectionEstablished(fixture.session);

        verify(fixture.session, never()).close(any(CloseStatus.class));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"session.connected\""));
    }

    @Test
    void tenantAdminIsAuthorizedOnConnect() throws Exception {
        VoiceTestWebSocketHandler handler = new VoiceTestWebSocketHandler(new ObjectMapper(), mock(VoiceOrchestratorService.class), new VoiceTestProperties());
        SessionFixture fixture = new SessionFixture(TENANT_ID, Set.of("TENANT_ADMIN"), "session-tenant-admin");

        handler.afterConnectionEstablished(fixture.session);

        verify(fixture.session, never()).close(any(CloseStatus.class));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"session.connected\""));
    }

    @Test
    void missingTenantIsRejected() throws Exception {
        VoiceTestWebSocketHandler handler = new VoiceTestWebSocketHandler(new ObjectMapper(), mock(VoiceOrchestratorService.class), new VoiceTestProperties());
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
