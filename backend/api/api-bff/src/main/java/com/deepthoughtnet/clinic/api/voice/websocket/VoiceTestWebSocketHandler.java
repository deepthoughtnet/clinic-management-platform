package com.deepthoughtnet.clinic.api.voice;

import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.security.PermissionEvaluator;
import com.deepthoughtnet.clinic.platform.security.Permissions;
import com.deepthoughtnet.clinic.platform.security.RolePermissionEvaluator;
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
    private static final String REQUIRED_PERMISSION = Permissions.AI_VOICE_TEST;
    private static final int MAX_CHUNK_BASE64_CHARS = 32 * 1024;
    private static final int RESPONSE_CHUNK_BASE64_CHARS = 24 * 1024;
    private static final int MAX_HISTORY_TURNS = 4;
    private static final int MAX_HISTORY_ENTRY_CHARS = 160;

    private final ObjectMapper objectMapper;
    private final VoiceOrchestratorService voiceOrchestratorService;
    private final VoiceTestProperties properties;
    private final PermissionEvaluator permissionEvaluator;
    private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();

    public VoiceTestWebSocketHandler(ObjectMapper objectMapper, VoiceOrchestratorService voiceOrchestratorService, VoiceTestProperties properties) {
        this(objectMapper, voiceOrchestratorService, properties, new RolePermissionEvaluator());
    }

    public VoiceTestWebSocketHandler(ObjectMapper objectMapper, VoiceOrchestratorService voiceOrchestratorService,
                                     VoiceTestProperties properties, PermissionEvaluator permissionEvaluator) {
        this.objectMapper = objectMapper;
        this.voiceOrchestratorService = voiceOrchestratorService;
        this.properties = properties;
        this.permissionEvaluator = permissionEvaluator;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        @SuppressWarnings("unchecked")
        Set<String> roles = (Set<String>) session.getAttributes().getOrDefault("roles", Set.of());
        String tenantId = String.valueOf(session.getAttributes().get("tenantId"));
        String subject = String.valueOf(session.getAttributes().getOrDefault("sub", "unknown"));
        String userIdentifier = String.valueOf(session.getAttributes().getOrDefault("userIdentifier", "unknown"));
        log.info("voice.websocket.connect.attempt sessionId={} tenantId={} subject={} userIdentifier={} roles={}",
                session.getId(), tenantId, subject, userIdentifier, roles);
        if (!permissionEvaluator.hasPermission(roles, REQUIRED_PERMISSION)) {
            log.warn("voice.websocket.auth.failed sessionId={} tenantId={} subject={} userIdentifier={} reason=unauthorized-role roles={} requiredPermission={}",
                    session.getId(), tenantId, subject, userIdentifier, roles, REQUIRED_PERMISSION);
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized role"));
            return;
        }
        if (tenantId == null || tenantId.isBlank() || "null".equalsIgnoreCase(tenantId)) {
            log.warn("voice.websocket.auth.failed sessionId={} subject={} userIdentifier={} tenantId={} reason=missing-tenant roles={} requiredPermission={}",
                    session.getId(), subject, userIdentifier, tenantId, roles, REQUIRED_PERMISSION);
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Missing tenant context"));
            return;
        }
        log.info("voice.websocket.tenant.resolved sessionId={} tenantId={}", session.getId(), tenantId);
        sessionStates.put(session.getId(), new SessionState(session.getId()));
        log.info("voice.websocket.auth.success sessionId={} tenantId={} roles={}", session.getId(), tenantId, roles);
        log.info("voice.session.started sessionId={} tenantId={}", session.getId(), tenantId);
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
            case "heartbeat" -> handleHeartbeat(session);
            case "session.close" -> handleSessionClose(session);
            default -> sendError(session, "Unsupported websocket message type.");
        }
    }

    void handleForTest(WebSocketSession session, TextMessage message) throws Exception {
        handleTextMessage(session, message);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SessionState state = sessionStates.get(session.getId());
        if (state != null) {
            log.info("voice.session.ended sessionId={} turnCount={} reason=socket_closed code={}",
                    state.sessionId, state.turnCount, status.getCode());
        }
        sessionStates.remove(session.getId());
    }

    private void handleSessionStart(WebSocketSession session, JsonNode root) throws IOException {
        SessionState state = requireState(session);
        state.touch();
        state.language = root.path("language").asText("auto");
        state.context = root.path("context").asText("General voice test harness conversation.");
        state.workflowMode = VoiceWorkflowMode.from(root.path("workflowMode").asText(null));
        state.workflowSummary = null;
        state.contentType = null;
        state.startedAt = Instant.now();
        state.lastActivityAt = Instant.now();
        state.lastHeartbeatAt = Instant.now();
        state.closed = false;
        state.filename = null;
        state.expectedTotalChunks = 0;
        state.chunkCount = 0;
        state.base64CharsReceived = 0;
        state.turnInProgress = false;
        state.turnCount = 0;
        state.history.clear();
        state.audioChunks.clear();
        log.info("voice.session.workflow sessionId={} workflowMode={} language={}",
                state.sessionId, state.workflowMode.configValue(), state.language);
        sendEvent(session, Map.of(
                "type", "session.started",
                "sessionId", state.sessionId,
                "language", state.language,
                "context", state.context,
                "workflowMode", state.workflowMode.configValue()
        ));
    }

    private void handleAudioChunk(WebSocketSession session, JsonNode root) throws IOException {
        SessionState state = requireState(session);
        if (enforceSessionPolicies(session, state)) {
            return;
        }
        state.touch();
        if (state.closed) {
            sendError(session, "Session is closed. Start a new session.");
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
        if (sequence > properties.getLive().getMaxTurnsPerSession() * MAX_CHUNK_BASE64_CHARS) {
            sendError(session, "Audio chunk metadata is invalid.");
            clearAudioBuffer(state);
            return;
        }
        if (sequence <= 0) {
            sendError(session, "Audio chunk metadata is invalid.");
            return;
        }
        if (sequence > maxTotalChunks()) {
            sendError(session, "Audio recording exceeds the supported chunk count.");
            clearAudioBuffer(state);
            return;
        }
        if (totalChunks > maxTotalChunks()) {
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
        if (estimatedDecodedBytes > properties.getLive().getMaxAudioBytesPerTurn()) {
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
        if (enforceSessionPolicies(session, state)) {
            return;
        }
        state.touch();
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
        if (state.turnCount >= properties.getLive().getMaxTurnsPerSession()) {
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
        if (audioBytes.length > properties.getLive().getMaxAudioBytesPerTurn()) {
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
        Instant turnStartedAt = Instant.now();
        log.info("voice.turn.started sessionId={} turnIndex={} workflowMode={} language={} contentType={} filename={} sizeBytes={}",
                state.sessionId, turnIndex, state.workflowMode.configValue(), state.language, state.contentType, state.filename, audioBytes.length);

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
                    state.language,
                    state.workflowMode.configValue(),
                    state.workflowSummary
            );
            state.turnCount = turnIndex;
            state.workflowSummary = response.workflowSummary();
            state.history.add(new TurnHistoryEntry(
                    turnIndex,
                    response.transcript() == null ? "" : response.transcript(),
                    response.assistantText() == null ? "" : response.assistantText(),
                    OffsetDateTime.now()
            ));
            trimHistory(state.history);
            VoiceTurnMetrics metrics = buildMetrics(response.voiceDebugTrace(), response.providerTrace(), state.language, audioBytes.length,
                    Duration.between(turnStartedAt, Instant.now()).toMillis());
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
            Map<String, Object> assistantTextEvent = new LinkedHashMap<>();
            assistantTextEvent.put("type", "assistant.text");
            assistantTextEvent.put("text", response.assistantText() == null ? "" : response.assistantText());
            assistantTextEvent.put("providerTrace", response.providerTrace());
            assistantTextEvent.put("requestId", response.requestId());
            assistantTextEvent.put("turnIndex", turnIndex);
            assistantTextEvent.put("workflowSummary", response.workflowSummary());
            sendEvent(session, assistantTextEvent);
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
            Map<String, Object> sessionCompletedEvent = new LinkedHashMap<>();
            sessionCompletedEvent.put("type", "session.completed");
            sessionCompletedEvent.put("sessionId", state.sessionId);
            sessionCompletedEvent.put("turnIndex", turnIndex);
            sessionCompletedEvent.put("requestId", response.requestId());
            sessionCompletedEvent.put("providerTrace", response.providerTrace());
            sessionCompletedEvent.put("workflowSummary", response.workflowSummary());
            sendEvent(session, sessionCompletedEvent);
            Map<String, Object> turnCompletedEvent = new LinkedHashMap<>();
            turnCompletedEvent.put("type", "turn.complete");
            turnCompletedEvent.put("sessionId", state.sessionId);
            turnCompletedEvent.put("turnIndex", turnIndex);
            turnCompletedEvent.put("requestId", response.requestId());
            turnCompletedEvent.put("providerTrace", response.providerTrace());
            turnCompletedEvent.put("workflowSummary", response.workflowSummary());
            turnCompletedEvent.put("metrics", metrics);
            sendEvent(session, turnCompletedEvent);
            log.info("voice.turn.completed sessionId={} turnIndex={} requestId={} totalDurationMs={} sttProvider={} llmProvider={} ttsProvider={}",
                    state.sessionId, turnIndex, response.requestId(), metrics.totalDurationMs(),
                    metrics.sttProvider(), metrics.llmProvider(), metrics.ttsProvider());
            log.info("voice.turn.metrics sessionId={} turnIndex={} captureDurationMs={} sttDurationMs={} llmDurationMs={} ttsDurationMs={} totalDurationMs={} language={}",
                    state.sessionId, turnIndex, metrics.captureDurationMs(), metrics.sttDurationMs(), metrics.llmDurationMs(),
                    metrics.ttsDurationMs(), metrics.totalDurationMs(), metrics.language());
        } catch (Exception ex) {
            log.warn("voice.turn.failed sessionId={} turnIndex={} error={}", state.sessionId, turnIndex, ex.getMessage());
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
        log.info("voice.session.ended sessionId={} turnCount={} reason=user_closed durationMs={}",
                state.sessionId, state.turnCount, state.startedAt == null ? 0L : Duration.between(state.startedAt, Instant.now()).toMillis());
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

    private void handleHeartbeat(WebSocketSession session) throws IOException {
        SessionState state = requireState(session);
        if (enforceSessionPolicies(session, state)) {
            return;
        }
        state.touchHeartbeat();
        log.info("voice.session.heartbeat sessionId={}", state.sessionId);
        sendEvent(session, Map.of(
                "type", "heartbeat",
                "sessionId", state.sessionId,
                "serverTime", Instant.now().toString()
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

    private boolean enforceSessionPolicies(WebSocketSession session, SessionState state) throws IOException {
        Instant now = Instant.now();
        if (state.startedAt != null
                && Duration.between(state.startedAt, now).compareTo(Duration.ofSeconds(properties.getLive().getMaxSessionDurationSeconds())) > 0) {
            log.warn("voice.session.timeout sessionId={} reason=max_session_duration", state.sessionId);
            state.closed = true;
            clearAudioBuffer(state);
            sendEvent(session, Map.of("type", "session.timeout", "reason", "max_session_duration"));
            sendError(session, "Live voice session exceeded the supported duration.");
            return true;
        }
        if (state.lastActivityAt != null
                && Duration.between(state.lastActivityAt, now).compareTo(Duration.ofSeconds(properties.getLive().getMaxIdleSeconds())) > 0) {
            log.warn("voice.session.timeout sessionId={} reason=idle_timeout", state.sessionId);
            state.closed = true;
            clearAudioBuffer(state);
            sendEvent(session, Map.of("type", "session.timeout", "reason", "idle_timeout"));
            sendError(session, "Live voice session timed out due to inactivity.");
            return true;
        }
        return false;
    }

    private int maxTotalChunks() {
        return 1000;
    }

    private VoiceTurnMetrics buildMetrics(List<VoiceDebugTraceEntry> debugTrace,
                                          VoiceProviderTrace providerTrace,
                                          String language,
                                          long captureBytes,
                                          long totalDurationMs) {
        long sttDurationMs = duration(debugTrace, "FASTER_WHISPER_RESPONSE");
        long llmDurationMs = duration(debugTrace, "LLM_RESULT");
        long ttsDurationMs = duration(debugTrace, "TTS_RESULT");
        String fallbackReason = reason(debugTrace, "STT_FALLBACK");
        return new VoiceTurnMetrics(
                0L,
                sttDurationMs,
                llmDurationMs,
                ttsDurationMs,
                totalDurationMs,
                language,
                providerTrace == null ? null : providerTrace.sttProvider(),
                providerTrace == null ? null : providerTrace.llmProvider(),
                providerTrace == null ? null : providerTrace.ttsProvider(),
                fallbackReason,
                captureBytes
        );
    }

    private long duration(List<VoiceDebugTraceEntry> debugTrace, String stage) {
        if (debugTrace == null) {
            return 0L;
        }
        return debugTrace.stream()
                .filter(entry -> stage.equals(entry.stage()) && entry.durationMs() != null)
                .map(VoiceDebugTraceEntry::durationMs)
                .findFirst()
                .orElse(0L);
    }

    private String reason(List<VoiceDebugTraceEntry> debugTrace, String stage) {
        if (debugTrace == null) {
            return null;
        }
        return debugTrace.stream()
                .filter(entry -> stage.equals(entry.stage()) && entry.reason() != null && !entry.reason().isBlank())
                .map(VoiceDebugTraceEntry::reason)
                .findFirst()
                .orElse(null);
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
        if (totalChunks > maxTotalChunks()) {
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
        if (state.workflowMode == VoiceWorkflowMode.APPOINTMENT_BOOKING && state.workflowSummary != null) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append("Workflow mode: appointment-booking.\n");
            builder.append("Intent state: ").append(state.workflowSummary.intentState()).append("\n");
            if (state.workflowSummary.missingFields() != null && !state.workflowSummary.missingFields().isEmpty()) {
                builder.append("Missing fields: ").append(String.join(", ", state.workflowSummary.missingFields())).append("\n");
            }
            if (state.workflowSummary.suggestedSlot() != null) {
                builder.append("Suggested slot: ")
                        .append(state.workflowSummary.suggestedSlot().doctorName()).append(" on ")
                        .append(state.workflowSummary.suggestedSlot().appointmentDate()).append(" at ")
                        .append(state.workflowSummary.suggestedSlot().slotTime()).append("\n");
            }
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
        private VoiceWorkflowMode workflowMode = VoiceWorkflowMode.GENERIC;
        private VoiceWorkflowSummary workflowSummary;
        private String contentType;
        private String filename;
        private Instant startedAt;
        private Instant lastActivityAt;
        private Instant lastHeartbeatAt;
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

        private void touch() {
            this.lastActivityAt = Instant.now();
        }

        private void touchHeartbeat() {
            Instant now = Instant.now();
            this.lastHeartbeatAt = now;
            this.lastActivityAt = now;
        }
    }

    private record TurnHistoryEntry(int turnIndex, String userTranscript, String assistantReply, OffsetDateTime timestamp) {
    }

    private record VoiceTurnMetrics(
            long captureDurationMs,
            long sttDurationMs,
            long llmDurationMs,
            long ttsDurationMs,
            long totalDurationMs,
            String language,
            String sttProvider,
            String llmProvider,
            String ttsProvider,
            String fallbackReason,
            long audioBytes
    ) {
    }
}
