package com.deepthoughtnet.clinic.api.realtime.websocket;

import com.deepthoughtnet.clinic.realtime.voice.events.VoiceRealtimeEvent;
import com.deepthoughtnet.clinic.realtime.voice.events.VoiceSessionEventBus;
import com.deepthoughtnet.clinic.realtime.voice.metrics.RealtimeVoiceGatewayMetrics;
import com.deepthoughtnet.clinic.realtime.voice.session.RealtimeVoiceSessionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Realtime session websocket transport with text and audio-frame ingestion foundation.
 */
public class VoiceSessionWebSocketHandler extends TextWebSocketHandler {
    private final VoiceSessionEventBus eventBus;
    private final RealtimeVoiceGatewayMetrics metrics;
    private final ObjectMapper objectMapper;
    private final RealtimeVoiceSessionService sessionService;

    public VoiceSessionWebSocketHandler(
            VoiceSessionEventBus eventBus,
            RealtimeVoiceGatewayMetrics metrics,
            ObjectMapper objectMapper,
            RealtimeVoiceSessionService sessionService
    ) {
        this.eventBus = eventBus;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.sessionService = sessionService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        UUID sessionId = parseSessionId(session);
        @SuppressWarnings("unchecked")
        Set<String> roles = (Set<String>) session.getAttributes().getOrDefault("roles", Set.of());
        if (roles.stream().noneMatch(r -> r.equals("PLATFORM_ADMIN") || r.equals("CLINIC_ADMIN") || r.equals("AUDITOR") || r.equals("RECEPTIONIST"))) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized role"));
            return;
        }
        AutoCloseable subscription = eventBus.subscribe(sessionId, event -> sendSafely(session, event));
        session.getAttributes().put("eventSubscription", subscription);
        session.sendMessage(new TextMessage("{\"type\":\"HEARTBEAT\",\"message\":\"connected\"}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = root.path("type").asText("PING");
        if ("PING".equalsIgnoreCase(type)) {
            session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
            return;
        }
        if ("AUDIO_CHUNK".equalsIgnoreCase(type)) {
            handleAudioChunk(session, root);
            return;
        }
        session.sendMessage(new TextMessage("{\"type\":\"ACK\",\"message\":\"Unsupported frame\"}"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Object sub = session.getAttributes().get("eventSubscription");
        if (sub instanceof AutoCloseable closeable) {
            closeable.close();
        }
        metrics.markWebsocketDisconnect();
    }

    private UUID parseSessionId(WebSocketSession session) {
        String[] segments = session.getUri().getPath().split("/");
        return UUID.fromString(segments[segments.length - 1]);
    }

    private void sendSafely(WebSocketSession session, VoiceRealtimeEvent event) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
        } catch (Exception ignored) {
        }
    }

    /**
     * AUDIO_CHUNK payload shape:
     * {"type":"AUDIO_CHUNK","audioBase64":"...","finalize":false,"promptKey":"...","patientContextJson":"{}","locale":"en"}
     */
    private void handleAudioChunk(WebSocketSession socketSession, JsonNode root) throws Exception {
        UUID sessionId = parseSessionId(socketSession);
        UUID tenantId = UUID.fromString(String.valueOf(socketSession.getAttributes().get("tenantId")));
        UUID appUserId = null;
        Object appUserIdAttr = socketSession.getAttributes().get("appUserId");
        if (appUserIdAttr != null) {
            try {
                appUserId = UUID.fromString(String.valueOf(appUserIdAttr));
            } catch (IllegalArgumentException ignored) {
                appUserId = null;
            }
        }

        String base64 = root.path("audioBase64").asText("");
        boolean finalize = root.path("finalize").asBoolean(false);
        String promptKey = root.path("promptKey").asText("realtime.voice.ai-receptionist.v1");
        String patientContextJson = root.path("patientContextJson").asText("{}");
        String locale = root.path("locale").asText("en");

        byte[] audio = base64.isBlank() ? new byte[0] : Base64.getDecoder().decode(base64);
        var result = sessionService.processAudioChunk(
                tenantId,
                sessionId,
                appUserId,
                audio,
                promptKey,
                patientContextJson,
                locale,
                finalize,
                UUID.randomUUID().toString()
        );
        if (result.interimTranscript() != null && !result.interimTranscript().isBlank()) {
            socketSession.sendMessage(new TextMessage("{\"type\":\"INTERIM_TRANSCRIPT\",\"text\":" + objectMapper.writeValueAsString(result.interimTranscript()) + "}"));
        }
    }
}
