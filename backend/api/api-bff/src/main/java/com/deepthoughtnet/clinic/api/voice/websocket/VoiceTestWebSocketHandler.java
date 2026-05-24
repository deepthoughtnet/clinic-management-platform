package com.deepthoughtnet.clinic.api.voice;

import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class VoiceTestWebSocketHandler extends TextWebSocketHandler {
    private static final Set<String> ALLOWED_ROLES = Set.of("PLATFORM_ADMIN", "TENANT_ADMIN", "CLINIC_ADMIN", "RECEPTIONIST");

    private final ObjectMapper objectMapper;
    private final VoiceOrchestratorService voiceOrchestratorService;
    private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();

    public VoiceTestWebSocketHandler(ObjectMapper objectMapper, VoiceOrchestratorService voiceOrchestratorService) {
        this.objectMapper = objectMapper;
        this.voiceOrchestratorService = voiceOrchestratorService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        @SuppressWarnings("unchecked")
        Set<String> roles = (Set<String>) session.getAttributes().getOrDefault("roles", Set.of());
        if (roles.stream().noneMatch(ALLOWED_ROLES::contains)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized role"));
            return;
        }
        String tenantId = String.valueOf(session.getAttributes().get("tenantId"));
        if (tenantId == null || tenantId.isBlank() || "null".equalsIgnoreCase(tenantId)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Missing tenant context"));
            return;
        }
        sessionStates.put(session.getId(), new SessionState(session.getId()));
        sendEvent(session, Map.of(
                "type", "session.connected",
                "message", "WebSocket connected"
        ));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = root.path("type").asText("");
        switch (type) {
            case "session.start" -> handleSessionStart(session, root);
            case "audio.chunk" -> handleAudioChunk(session, root);
            case "audio.end" -> handleAudioEnd(session);
            case "session.close" -> handleSessionClose(session);
            default -> sendError(session, "Unsupported websocket message type.");
        }
    }

    void handleForTest(WebSocketSession session, TextMessage message) throws Exception {
        handleTextMessage(session, message);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionStates.remove(session.getId());
    }

    private void handleSessionStart(WebSocketSession session, JsonNode root) throws IOException {
        SessionState state = requireState(session);
        state.language = root.path("language").asText("en");
        state.context = root.path("context").asText("General voice test harness conversation.");
        state.contentType = null;
        state.startedAt = Instant.now();
        state.closed = false;
        state.buffer.reset();
        state.chunkCount = 0;
        sendEvent(session, Map.of(
                "type", "session.started",
                "sessionId", state.sessionId,
                "language", state.language,
                "context", state.context
        ));
    }

    private void handleAudioChunk(WebSocketSession session, JsonNode root) throws IOException {
        SessionState state = requireState(session);
        if (state.closed) {
            sendError(session, "Session is closed. Start a new session.");
            return;
        }
        String audioBase64 = root.path("audioBase64").asText("");
        if (audioBase64.isBlank()) {
            sendError(session, "Audio chunk is empty.");
            return;
        }
        String contentType = root.path("contentType").asText("audio/webm");
        byte[] chunk = Base64.getDecoder().decode(audioBase64);
        state.buffer.write(chunk);
        state.contentType = contentType;
        state.chunkCount += 1;
        sendEvent(session, Map.of(
                "type", "transcript.partial",
                "text", "Captured audio chunk " + state.chunkCount
        ));
    }

    private void handleAudioEnd(WebSocketSession session) throws IOException {
        SessionState state = requireState(session);
        if (state.buffer.size() == 0) {
            sendError(session, "No audio chunks were received.");
            return;
        }
        UUID tenantId = UUID.fromString(String.valueOf(session.getAttributes().get("tenantId")));
        String subject = String.valueOf(session.getAttributes().getOrDefault("sub", "voice-user"));
        Set<String> roles = castRoles(session.getAttributes().get("roles"));

        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), null, subject, roles, firstRole(roles), state.sessionId));
        try {
            VoiceTestResponse response = voiceOrchestratorService.processBufferedAudio(
                    state.buffer.toByteArray(),
                    state.contentType == null ? "audio/webm" : state.contentType,
                    "voice-test-stream.webm",
                    state.context,
                    state.language
            );
            sendEvent(session, Map.of(
                    "type", "transcript.final",
                    "text", response.transcript() == null ? "" : response.transcript()
            ));
            sendEvent(session, Map.of(
                    "type", "assistant.text",
                    "text", response.assistantText() == null ? "" : response.assistantText(),
                    "providerTrace", response.providerTrace(),
                    "requestId", response.requestId()
            ));
            if (response.audioBase64() != null && response.audioContentType() != null) {
                sendEvent(session, Map.of(
                        "type", "assistant.audio",
                        "audioBase64", response.audioBase64(),
                        "contentType", response.audioContentType(),
                        "providerTrace", response.providerTrace(),
                        "requestId", response.requestId()
                ));
            }
        } catch (Exception ex) {
            sendError(session, ex.getMessage() == null ? "Voice websocket processing failed." : ex.getMessage());
        } finally {
            RequestContextHolder.clear();
            Duration duration = Duration.between(state.startedAt == null ? Instant.now() : state.startedAt, Instant.now());
            sendEvent(session, Map.of(
                    "type", "session.closed",
                    "sessionId", state.sessionId,
                    "durationMs", duration.toMillis()
            ));
            state.closed = true;
            state.buffer.reset();
        }
    }

    private void handleSessionClose(WebSocketSession session) throws IOException {
        SessionState state = requireState(session);
        state.closed = true;
        state.buffer.reset();
        sendEvent(session, Map.of(
                "type", "session.closed",
                "sessionId", state.sessionId,
                "durationMs", state.startedAt == null ? 0L : Duration.between(state.startedAt, Instant.now()).toMillis()
        ));
    }

    private SessionState requireState(WebSocketSession session) {
        SessionState state = sessionStates.get(session.getId());
        if (state == null) {
            throw new IllegalStateException("Voice test session is not initialized.");
        }
        return state;
    }

    private void sendError(WebSocketSession session, String message) throws IOException {
        sendEvent(session, Map.of(
                "type", "error",
                "message", message
        ));
    }

    private void sendEvent(WebSocketSession session, Object payload) throws IOException {
        if (!session.isOpen()) {
            return;
        }
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }

    @SuppressWarnings("unchecked")
    private Set<String> castRoles(Object roles) {
        return roles instanceof Set<?> set ? (Set<String>) set : Set.of();
    }

    private String firstRole(Set<String> roles) {
        return roles.stream().findFirst().orElse("UNKNOWN");
    }

    static final class SessionState {
        private final String sessionId;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private String language = "en";
        private String context = "General voice test harness conversation.";
        private String contentType;
        private Instant startedAt;
        private int chunkCount;
        private boolean closed;

        private SessionState(String sessionId) {
            this.sessionId = sessionId;
        }
    }
}
