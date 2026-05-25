package com.deepthoughtnet.clinic.api.voice;

import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class VoiceTestWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(VoiceTestWebSocketHandler.class);
    private static final Set<String> ALLOWED_ROLES = Set.of("PLATFORM_ADMIN", "TENANT_ADMIN", "CLINIC_ADMIN", "RECEPTIONIST");
    private static final int MAX_CHUNK_BASE64_CHARS = 32 * 1024;
    private static final int RESPONSE_CHUNK_BASE64_CHARS = 24 * 1024;
    private static final int MAX_TOTAL_CHUNKS = 1000;
    private static final int MAX_TOTAL_AUDIO_BYTES = 10 * 1024 * 1024;
    private static final Duration MAX_SESSION_DURATION = Duration.ofMinutes(15);
    private static final int MAX_TURNS_PER_SESSION = 20;
    private static final int MAX_HISTORY_TURNS = 4;
    private static final int MAX_HISTORY_ENTRY_CHARS = 160;

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
        String tenantId = String.valueOf(session.getAttributes().get("tenantId"));
        log.info("voice.websocket.connect.attempt sessionId={} tenantId={} roles={}", session.getId(), tenantId, roles);
        if (roles.stream().noneMatch(ALLOWED_ROLES::contains)) {
            log.warn("voice.websocket.auth.failed sessionId={} tenantId={} reason=unauthorized-role roles={}",
                    session.getId(), tenantId, roles);
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized role"));
            return;
        }
        if (tenantId == null || tenantId.isBlank() || "null".equalsIgnoreCase(tenantId)) {
            log.warn("voice.websocket.auth.failed sessionId={} reason=missing-tenant", session.getId());
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Missing tenant context"));
            return;
        }
        log.info("voice.websocket.tenant.resolved sessionId={} tenantId={}", session.getId(), tenantId);
        sessionStates.put(session.getId(), new SessionState(session.getId()));
        log.info("voice.websocket.auth.success sessionId={} tenantId={} roles={}", session.getId(), tenantId, roles);
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
            case "audio.end" -> handleAudioEnd(session, root);
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
        state.language = root.path("language").asText("auto");
        state.context = root.path("context").asText("General voice test harness conversation.");
        state.contentType = null;
        state.startedAt = Instant.now();
        state.closed = false;
        state.filename = null;
        state.expectedTotalChunks = 0;
        state.chunkCount = 0;
        state.base64CharsReceived = 0;
        state.turnInProgress = false;
        state.turnCount = 0;
        state.history.clear();
        state.audioChunks.clear();
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
        if (state.startedAt != null && Duration.between(state.startedAt, Instant.now()).compareTo(MAX_SESSION_DURATION) > 0) {
            sendError(session, "Live voice session exceeded the supported duration.");
            clearAudioBuffer(state);
            state.closed = true;
            return;
        }
        if (state.turnInProgress) {
            sendError(session, "A voice turn is already being processed.");
            return;
        }
        String audioBase64Chunk = root.path("audioBase64Chunk").asText("");
        if (audioBase64Chunk.isBlank()) {
            sendError(session, "Audio chunk is empty.");
            return;
        }
        audioBase64Chunk = stripDataUrlPrefix(audioBase64Chunk);
        int sequence = root.path("sequence").asInt(0);
        int totalChunks = root.path("totalChunks").asInt(0);
        if (sequence <= 0) {
            sendError(session, "Audio chunk metadata is invalid.");
            return;
        }
        if (sequence > MAX_TOTAL_CHUNKS) {
            sendError(session, "Audio recording exceeds the supported chunk count.");
            clearAudioBuffer(state);
            return;
        }
        if (totalChunks > MAX_TOTAL_CHUNKS) {
            sendError(session, "Audio recording exceeds the supported chunk count.");
            clearAudioBuffer(state);
            return;
        }
        if (audioBase64Chunk.length() > MAX_CHUNK_BASE64_CHARS) {
            sendError(session, "Audio chunk exceeds the supported websocket size.");
            clearAudioBuffer(state);
            return;
        }
        if (totalChunks > 0 && state.expectedTotalChunks == 0) {
            state.expectedTotalChunks = totalChunks;
        } else if (totalChunks > 0 && state.expectedTotalChunks != totalChunks) {
            sendError(session, "Audio chunk metadata changed mid-stream.");
            clearAudioBuffer(state);
            return;
        }
        if (totalChunks > 0 && sequence > totalChunks) {
            sendError(session, "Audio chunk sequence exceeds the declared total.");
            clearAudioBuffer(state);
            return;
        }
        String contentType = root.path("contentType").asText("audio/webm");
        String filename = root.path("filename").asText("voice-test-stream.webm");
        String previousChunk = state.audioChunks.put(sequence, audioBase64Chunk);
        if (previousChunk == null) {
            state.base64CharsReceived += audioBase64Chunk.length();
            state.chunkCount = state.audioChunks.size();
        } else {
            state.base64CharsReceived += audioBase64Chunk.length() - previousChunk.length();
        }
        long estimatedDecodedBytes = (state.base64CharsReceived * 3L) / 4L;
        if (estimatedDecodedBytes > MAX_TOTAL_AUDIO_BYTES) {
            sendError(session, "Audio recording exceeds the 10 MB limit.");
            clearAudioBuffer(state);
            return;
        }
        state.contentType = contentType;
        state.filename = filename;
        log.info("voice.ws.audio.chunk.received sessionId={} sequence={} chunkChars={} totalChunks={}",
                state.sessionId,
                sequence,
                audioBase64Chunk.length(),
                state.expectedTotalChunks > 0 ? state.expectedTotalChunks : state.chunkCount);
        sendEvent(session, Map.of(
                "type", "audio.chunk.received",
                "sequence", sequence,
                "totalChunks", state.expectedTotalChunks > 0 ? state.expectedTotalChunks : state.chunkCount
        ));
        sendEvent(session, Map.of(
                "type", "turn.audio.received",
                "sessionId", state.sessionId,
                "sequence", sequence,
                "totalChunks", state.expectedTotalChunks > 0 ? state.expectedTotalChunks : state.chunkCount
        ));
    }

    private void handleAudioEnd(WebSocketSession session, JsonNode root) throws IOException {
        SessionState state = requireState(session);
        int declaredTotalChunks = root.path("totalChunks").asInt(state.expectedTotalChunks);
        if (declaredTotalChunks > 0 && state.expectedTotalChunks > 0 && declaredTotalChunks != state.expectedTotalChunks) {
            sendError(session, "Audio end metadata did not match the received chunks.");
            clearAudioBuffer(state);
            return;
        }
        if (state.audioChunks.isEmpty()) {
            sendError(session, "No audio chunks were received.");
            return;
        }
        if (state.turnCount >= MAX_TURNS_PER_SESSION) {
            sendError(session, "Live voice session reached the supported turn limit.");
            clearAudioBuffer(state);
            state.closed = true;
            return;
        }
        int totalChunks = state.expectedTotalChunks > 0 ? state.expectedTotalChunks : declaredTotalChunks;
        if (totalChunks <= 0) {
            sendError(session, "Audio end is missing the total chunk count.");
            clearAudioBuffer(state);
            return;
        }
        Set<Integer> missingChunks = new HashSet<>();
        for (int index = 1; index <= totalChunks; index++) {
            if (!state.audioChunks.containsKey(index)) {
                missingChunks.add(index);
            }
        }
        if (!missingChunks.isEmpty()) {
            sendError(session, "Audio upload is incomplete. Missing chunks: " + missingChunks);
            clearAudioBuffer(state);
            return;
        }
        log.info("voice.ws.audio.buffer.ready sessionId={} chunks={} totalChars={}",
                state.sessionId,
                totalChunks,
                state.base64CharsReceived);
        byte[] audioBytes = decodeAudioChunks(state, totalChunks);
        if (audioBytes == null) {
            sendError(session, "Audio chunks could not be decoded.");
            clearAudioBuffer(state);
            return;
        }
        if (audioBytes.length > MAX_TOTAL_AUDIO_BYTES) {
            sendError(session, "Audio recording exceeds the 10 MB limit.");
            clearAudioBuffer(state);
            return;
        }
        log.info("voice.ws.audio.decode.success sessionId={} bytes={} contentType={} filename={}",
                state.sessionId,
                audioBytes.length,
                state.contentType,
                state.filename);
        sendEvent(session, Map.of(
                "type", "audio.buffer.complete",
                "totalChunks", totalChunks,
                "sizeBytes", audioBytes.length
        ));
        sendEvent(session, Map.of(
                "type", "audio.decoded",
                "sizeBytes", audioBytes.length,
                "contentType", state.contentType,
                "filename", state.filename
        ));
        UUID tenantId = UUID.fromString(String.valueOf(session.getAttributes().get("tenantId")));
        String subject = String.valueOf(session.getAttributes().getOrDefault("sub", "voice-user"));
        Set<String> roles = castRoles(session.getAttributes().get("roles"));
        int turnIndex = state.turnCount + 1;
        state.turnInProgress = true;

        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), null, subject, roles, firstRole(roles), state.sessionId));
        try {
            sendEvent(session, Map.of(
                    "type", "turn.started",
                    "sessionId", state.sessionId,
                    "turnIndex", turnIndex
            ));
            sendEvent(session, Map.of(
                    "type", "stt.started"
            ));
            VoiceTestResponse response = voiceOrchestratorService.processBufferedAudio(
                    audioBytes,
                    state.contentType == null ? "audio/webm" : state.contentType,
                    state.filename == null ? "voice-test-stream.webm" : state.filename,
                    buildConversationContext(state),
                    state.language
            );
            state.turnCount = turnIndex;
            state.history.add(new TurnHistoryEntry(
                    turnIndex,
                    response.transcript() == null ? "" : response.transcript(),
                    response.assistantText() == null ? "" : response.assistantText(),
                    OffsetDateTime.now()
            ));
            trimHistory(state.history);
            sendEvent(session, Map.of(
                    "type", "transcript.final",
                    "text", response.transcript() == null ? "" : response.transcript(),
                    "turnIndex", turnIndex
            ));
            sendEvent(session, Map.of(
                    "type", "stt.complete",
                    "provider", response.providerTrace() == null ? null : response.providerTrace().sttProvider(),
                    "turnIndex", turnIndex
            ));
            sendEvent(session, Map.of(
                    "type", "turn.stt.complete",
                    "sessionId", state.sessionId,
                    "turnIndex", turnIndex,
                    "provider", response.providerTrace() == null ? null : response.providerTrace().sttProvider()
            ));
            sendEvent(session, Map.of(
                    "type", "assistant.text",
                    "text", response.assistantText() == null ? "" : response.assistantText(),
                    "providerTrace", response.providerTrace(),
                    "requestId", response.requestId(),
                    "turnIndex", turnIndex
            ));
            sendEvent(session, Map.of(
                    "type", "turn.llm.complete",
                    "sessionId", state.sessionId,
                    "turnIndex", turnIndex,
                    "provider", response.providerTrace() == null ? null : response.providerTrace().llmProvider()
            ));
            if (response.audioBase64() != null && response.audioContentType() != null) {
                sendAssistantAudioChunks(session, response, turnIndex);
                sendEvent(session, Map.of(
                        "type", "turn.tts.complete",
                        "sessionId", state.sessionId,
                        "turnIndex", turnIndex,
                        "provider", response.providerTrace() == null ? null : response.providerTrace().ttsProvider()
                ));
            }
            sendEvent(session, Map.of(
                    "type", "session.completed",
                    "sessionId", state.sessionId,
                    "turnIndex", turnIndex,
                    "requestId", response.requestId(),
                    "providerTrace", response.providerTrace()
            ));
            sendEvent(session, Map.of(
                    "type", "turn.complete",
                    "sessionId", state.sessionId,
                    "turnIndex", turnIndex,
                    "requestId", response.requestId(),
                    "providerTrace", response.providerTrace()
            ));
        } catch (Exception ex) {
            sendError(session, ex.getMessage() == null ? "Voice websocket processing failed." : ex.getMessage());
        } finally {
            RequestContextHolder.clear();
            state.turnInProgress = false;
            clearAudioBuffer(state);
        }
    }

    private void handleSessionClose(WebSocketSession session) throws IOException {
        SessionState state = requireState(session);
        state.closed = true;
        clearAudioBuffer(state);
        sendEvent(session, Map.of(
                "type", "session.closed",
                "sessionId", state.sessionId,
                "durationMs", state.startedAt == null ? 0L : Duration.between(state.startedAt, Instant.now()).toMillis()
        ));
        sendEvent(session, Map.of(
                "type", "session.ended",
                "sessionId", state.sessionId,
                "turnCount", state.turnCount
        ));
    }

    private SessionState requireState(WebSocketSession session) {
        SessionState state = sessionStates.get(session.getId());
        if (state == null) {
            throw new IllegalStateException("Voice test session is not initialized.");
        }
        return state;
    }

    private void clearAudioBuffer(SessionState state) {
        state.audioChunks.clear();
        state.chunkCount = 0;
        state.expectedTotalChunks = 0;
        state.base64CharsReceived = 0;
        state.filename = null;
        state.contentType = null;
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

    private byte[] decodeAudioChunks(SessionState state, int totalChunks) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int index = 1; index <= totalChunks; index++) {
            String chunk = state.audioChunks.get(index);
            if (chunk == null) {
                log.warn("voice.ws.audio.decode.failed sessionId={} reason=missing-chunk sequence={}", state.sessionId, index);
                return null;
            }
            try {
                output.write(Base64.getDecoder().decode(stripDataUrlPrefix(chunk)));
            } catch (IllegalArgumentException | IOException ex) {
                log.warn("voice.ws.audio.decode.failed sessionId={} reason={} sequence={}", state.sessionId, ex.getMessage(), index);
                return null;
            }
        }
        return output.toByteArray();
    }

    private String stripDataUrlPrefix(String value) {
        int marker = value.indexOf("base64,");
        if (marker >= 0) {
            return value.substring(marker + 7);
        }
        return value;
    }

    private void sendAssistantAudioChunks(WebSocketSession session, VoiceTestResponse response, int turnIndex) throws IOException {
        String audioBase64 = response.audioBase64();
        if (audioBase64 == null || audioBase64.isBlank()) {
            return;
        }
        int totalChunks = (int) Math.ceil((double) audioBase64.length() / RESPONSE_CHUNK_BASE64_CHARS);
        if (totalChunks > MAX_TOTAL_CHUNKS) {
            throw new IllegalStateException("Assistant audio exceeds the supported websocket size.");
        }
        for (int index = 0; index < totalChunks; index++) {
            int start = index * RESPONSE_CHUNK_BASE64_CHARS;
            int end = Math.min(audioBase64.length(), start + RESPONSE_CHUNK_BASE64_CHARS);
            sendEvent(session, Map.of(
                    "type", "assistant.audio.chunk",
                    "sequence", index + 1,
                    "totalChunks", totalChunks,
                    "turnIndex", turnIndex,
                    "audioBase64Chunk", audioBase64.substring(start, end)
            ));
        }
        sendEvent(session, Map.of(
                "type", "assistant.audio.end",
                "contentType", response.audioContentType(),
                "turnIndex", turnIndex,
                "providerTrace", response.providerTrace(),
                "requestId", response.requestId()
        ));
    }

    private String buildConversationContext(SessionState state) {
        StringBuilder builder = new StringBuilder();
        String baseContext = state.context == null ? "" : state.context.trim();
        if (!baseContext.isEmpty()) {
            builder.append(baseContext);
        }
        if (!state.history.isEmpty()) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append("Recent conversation:\n");
            for (TurnHistoryEntry entry : state.history) {
                builder.append("Turn ").append(entry.turnIndex()).append(":\n");
                builder.append("User: ").append(trimHistoryText(entry.userTranscript())).append("\n");
                builder.append("Assistant: ").append(trimHistoryText(entry.assistantReply())).append("\n");
            }
        }
        return builder.isEmpty() ? "General voice test harness conversation." : builder.toString();
    }

    private void trimHistory(List<TurnHistoryEntry> history) {
        while (history.size() > MAX_HISTORY_TURNS) {
            history.removeFirst();
        }
    }

    private String trimHistoryText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
        return normalized.length() <= MAX_HISTORY_ENTRY_CHARS
                ? normalized
                : normalized.substring(0, MAX_HISTORY_ENTRY_CHARS) + "...";
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
        private final Map<Integer, String> audioChunks = new LinkedHashMap<>();
        private String language = "auto";
        private String context = "General voice test harness conversation.";
        private String contentType;
        private String filename;
        private Instant startedAt;
        private int expectedTotalChunks;
        private int chunkCount;
        private long base64CharsReceived;
        private boolean closed;
        private boolean turnInProgress;
        private int turnCount;
        private final List<TurnHistoryEntry> history = new ArrayList<>();

        private SessionState(String sessionId) {
            this.sessionId = sessionId;
        }
    }

    private record TurnHistoryEntry(int turnIndex, String userTranscript, String assistantReply, OffsetDateTime timestamp) {
    }
}
