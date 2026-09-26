package com.deepthoughtnet.clinic.api.patientportal.voice;

import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiChannel;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiConversationPersistenceService;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiConversationSessionSnapshot;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiTransport;
import com.deepthoughtnet.clinic.api.patientportal.voice.PatientPortalVoiceAssistantService.PatientPortalVoiceTurnResponse;
import com.deepthoughtnet.clinic.api.patientportal.voice.PatientPortalVoiceAssistantService.PatientPortalVoiceProgressAudio;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiProgressEvent;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2VoiceTurnConnector;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2ProgressEvent;
import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class PatientPortalVoiceWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(PatientPortalVoiceWebSocketHandler.class);
    private static final int MAX_CHUNK_BASE64_CHARS = 32 * 1024;
    private static final int RESPONSE_CHUNK_BASE64_CHARS = 24 * 1024;
    private static final String INSTANCE_ID = buildInstanceId();
    private static final ScheduledExecutorService TERMINAL_CLOSE_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "aiva-voice-terminal-close");
                thread.setDaemon(true);
                return thread;
            });

    private final ObjectMapper objectMapper;
    private final PatientPortalVoiceAssistantService voiceAssistantService;
    private final VoiceTestProperties properties;
    private final CareAiConversationPersistenceService conversationPersistenceService;
    private final AivaV2VoiceTurnConnector aivaV2VoiceTurnConnector;
    private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();

    public PatientPortalVoiceWebSocketHandler(
            ObjectMapper objectMapper,
            PatientPortalVoiceAssistantService voiceAssistantService,
            VoiceTestProperties properties,
            CareAiConversationPersistenceService conversationPersistenceService
    ) {
        this(objectMapper, voiceAssistantService, properties, conversationPersistenceService, null);
    }

    public PatientPortalVoiceWebSocketHandler(
            ObjectMapper objectMapper,
            PatientPortalVoiceAssistantService voiceAssistantService,
            VoiceTestProperties properties,
            CareAiConversationPersistenceService conversationPersistenceService,
            AivaV2VoiceTurnConnector aivaV2VoiceTurnConnector
    ) {
        this.objectMapper = objectMapper;
        this.voiceAssistantService = voiceAssistantService;
        this.properties = properties;
        this.conversationPersistenceService = conversationPersistenceService;
        this.aivaV2VoiceTurnConnector = aivaV2VoiceTurnConnector;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String tenantId = String.valueOf(session.getAttributes().get("tenantId"));
        String patientId = String.valueOf(session.getAttributes().get("patientId"));
        if (tenantId == null || tenantId.isBlank() || "null".equalsIgnoreCase(tenantId)
                || patientId == null || patientId.isBlank() || "null".equalsIgnoreCase(patientId)) {
            log.warn("patient.voice.websocket.connect.rejected sessionId={} reason=missing-session-context", session.getId());
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Missing patient session context"));
            return;
        }
        sessionStates.put(session.getId(), new SessionState(session.getId()));
        log.info("patient.voice.websocket.connect.accepted sessionId={} tenantId={} patientId={}",
                session.getId(), tenantId, patientId);
        sendEvent(session, Map.of(
                "type", "session.connected",
                "message", "Patient voice websocket connected"
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
            case "audio.playback.complete" -> handleAudioPlaybackComplete(session, root);
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
        SessionState state = sessionStates.remove(session.getId());
        if (state != null) {
            cancelTerminalClose(state);
            markVoiceDisconnected(session, state);
        }
        log.info("patient.voice.websocket.closed sessionId={} code={} reason={}",
                session.getId(),
                status == null ? null : status.getCode(),
                status == null ? null : status.getReason());
    }

    private void handleSessionStart(WebSocketSession session, JsonNode root) throws IOException {
        SessionState state = requireState(session);
        state.touch();
        String requestedResumeSessionId = root.path("resumeSessionId").asText("").trim();
        state.logicalSessionId = requestedResumeSessionId.isBlank() ? state.logicalSessionId : requestedResumeSessionId;
        String requestedEngine = root.path("engine").asText("v2").trim().toLowerCase();
        if (!requestedEngine.equals("legacy") && !requestedEngine.equals("v2")) {
            sendError(session, "Unsupported voice engine.");
            state.closed = true;
            return;
        }
        if (!requestedEngine.equals("legacy") && !properties.isAivaV2Enabled()) {
            sendError(session, "AIVA V2 voice is currently unavailable.");
            state.closed = true;
            return;
        }
        if (state.startedAt != null && !state.closed && !state.engine.equals(requestedEngine)) {
            sendError(session, "Voice engine cannot change during an active session.");
            return;
        }
        state.engine = requestedEngine;
        state.v2ConversationId = requestedEngine.equals("v2")
                ? v2ConversationId(state.logicalSessionId)
                : null;
        state.v2TurnSequence = requestedEngine.equals("v2")
                ? resumedV2TurnSequence(session, state.v2ConversationId)
                : 0L;
        state.language = root.path("language").asText("auto");
        state.startedAt = Instant.now();
        state.lastActivityAt = Instant.now();
        state.lastHeartbeatAt = Instant.now();
        state.closed = false;
        state.terminalConversation = false;
        cancelTerminalClose(state);
        state.turnCount = 0;
        clearAudioBuffer(state);
        CareAiConversationSessionSnapshot recovered = tryResumeConversation(session, state, !requestedResumeSessionId.isBlank());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "session.started");
        payload.put("sessionId", state.logicalSessionId);
        payload.put("language", state.language);
        payload.put("engine", state.engine);
        payload.put("resumed", recovered != null);
        payload.put("conversationId", recovered == null ? null : recovered.conversation().getId());
        payload.put("workflowId", recovered == null || recovered.workflow() == null ? null : recovered.workflow().getId());
        payload.put("resumeMessage", recovered == null ? null : "Reconnected. Continuing your previous conversation.");
        payload.put("voiceConfig", Map.of(
                "heartbeatIntervalMs", properties.getLive().getHeartbeatIntervalMs(),
                "speechStartThreshold", properties.getVad().getSpeechStartThreshold(),
                "speechEndThreshold", properties.getVad().getSpeechEndThreshold(),
                "minSpeechMs", properties.getVad().getMinSpeechMs(),
                "silenceTimeoutMs", properties.getVad().getSilenceTimeoutMs(),
                "maxUtteranceMs", properties.getVad().getMaxUtteranceMs(),
                "autoResumeDelayMs", properties.getLive().getFrontendAutoResumeDelayMs()
        ));
        sendEvent(session, payload);
    }

    private void handleAudioChunk(WebSocketSession session, JsonNode root) throws IOException {
        SessionState state = requireState(session);
        if (enforceSessionPolicies(session, state)) {
            return;
        }
        if (state.closed) {
            sendError(session, "Session is closed. Start a new session.");
            return;
        }
        if (state.turnInProgress) {
            sendError(session, "A voice turn is already being processed.");
            return;
        }
        if (state.audioChunks.isEmpty()) {
            state.voiceUtteranceId = requestedUtteranceId(root);
            if (state.voiceUtteranceId == null) {
                state.voiceUtteranceId = UUID.randomUUID().toString();
            }
        } else if (root.hasNonNull("voiceUtteranceId")
                && !state.voiceUtteranceId.equals(requestedUtteranceId(root))) {
            sendError(session, "Audio utterance identity changed mid-stream.");
            clearAudioBuffer(state);
            return;
        }
        String audioBase64Chunk = stripDataUrlPrefix(root.path("audioBase64Chunk").asText(""));
        if (audioBase64Chunk.isBlank()) {
            sendError(session, "Audio chunk is empty.");
            return;
        }
        cancelTerminalClose(state);
        state.terminalConversation = false;
        int sequence = root.path("sequence").asInt(0);
        int totalChunks = root.path("totalChunks").asInt(0);
        if (sequence <= 0 || sequence > maxTotalChunks() || totalChunks > maxTotalChunks()) {
            sendError(session, "Audio chunk metadata is invalid.");
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
        state.contentType = root.path("contentType").asText("audio/webm");
        state.filename = root.path("filename").asText("patient-careai-voice.webm");
        Instant now = Instant.now();
        if (state.firstUploadAt == null) {
            state.firstUploadAt = now;
        }
        state.lastUploadActivityAt = now;
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
        String requestedUtteranceId = requestedUtteranceId(root);
        if (state.turnInProgress) {
            boolean sameV2TurnWithoutClientId = state.engine.equals("v2") && requestedUtteranceId == null;
            if (state.voiceUtteranceId != null
                    && (state.voiceUtteranceId.equals(requestedUtteranceId) || sameV2TurnWithoutClientId)) {
                sendEvent(session, Map.of("type", "turn.in_flight", "voiceUtteranceId", state.voiceUtteranceId));
            } else {
                sendError(session, "A voice turn is already being processed.");
            }
            return;
        }
        int declaredTotalChunks = root.path("totalChunks").asInt(state.expectedTotalChunks);
        if (declaredTotalChunks > 0 && state.expectedTotalChunks > 0 && declaredTotalChunks != state.expectedTotalChunks) {
            sendError(session, "Audio end metadata did not match the received chunks.");
            clearAudioBuffer(state);
            return;
        }
        if (state.audioChunks.isEmpty()) {
            boolean sameV2TurnWithoutClientId = state.engine.equals("v2") && requestedUtteranceId == null;
            if (state.lastCompletedTurn != null
                    && (state.lastCompletedTurn.voiceUtteranceId().equals(requestedUtteranceId)
                    || sameV2TurnWithoutClientId)) {
                replayCompletedTurn(session, state, state.lastCompletedTurn);
                return;
            }
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
        for (int index = 1; index <= totalChunks; index++) {
            if (!state.audioChunks.containsKey(index)) {
                sendError(session, "Audio upload is incomplete. Missing chunks: [" + index + "]");
                clearAudioBuffer(state);
                return;
            }
        }
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

        UUID tenantId = UUID.fromString(String.valueOf(session.getAttributes().get("tenantId")));
        UUID appUserId = UUID.fromString(String.valueOf(session.getAttributes().get("appUserId")));
        String subject = String.valueOf(session.getAttributes().getOrDefault("sub", "patient-voice-user"));
        @SuppressWarnings("unchecked")
        Set<String> roles = (Set<String>) session.getAttributes().getOrDefault("roles", Set.of("PATIENT"));
        int turnIndex = state.turnCount + 1;
        state.turnInProgress = true;
        state.turnStartedAt = Instant.now();
        long uploadDurationMs = state.firstUploadAt == null ? 0L : Duration.between(state.firstUploadAt, Instant.now()).toMillis();
        long uploadToProcessGapMs = state.lastUploadActivityAt == null ? 0L : Duration.between(state.lastUploadActivityAt, Instant.now()).toMillis();
        log.info("patient.voice.turn.audio.ready sessionId={} turnIndex={} chunks={} bytes={} uploadDurationMs={} uploadToProcessGapMs={}",
                state.sessionId, turnIndex, totalChunks, audioBytes.length, uploadDurationMs, uploadToProcessGapMs);

        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), appUserId, subject, roles, "PATIENT", state.logicalSessionId));
        try {
            sendEvent(session, Map.of(
                    "type", "turn.started",
                    "sessionId", state.sessionId,
                    "turnIndex", turnIndex
            ));
            sendEvent(session, Map.of("type", "stt.started"));
            PatientPortalVoiceTurnResponse response = state.engine.equals("v2")
                    ? voiceAssistantService.processAudioTurnV2(
                            audioBytes,
                            state.contentType == null ? "audio/webm" : state.contentType,
                            state.filename == null ? "patient-careai-voice.webm" : state.filename,
                            state.language,
                            state.v2ConversationId,
                            "voice-" + state.voiceUtteranceId,
                            state.v2TurnSequence + 1,
                            progress -> sendV2ProgressEvent(session, state, turnIndex, progress)
                    )
                    : voiceAssistantService.processAudioTurn(
                            audioBytes,
                            state.contentType == null ? "audio/webm" : state.contentType,
                            state.filename == null ? "patient-careai-voice.webm" : state.filename,
                            state.language,
                            progress -> sendProgressEvent(session, state, turnIndex, progress)
                    );
            if (response == null) {
                log.info("patient.voice.turn.ignored sessionId={} turnIndex={} reason=EMPTY_STT_TRANSCRIPT",
                        state.sessionId, turnIndex);
                if (state.terminalCloseTask == null && state.terminalTurnIndex > 0) {
                    state.terminalConversation = true;
                    scheduleTerminalClose(session, state, turnIndex);
                }
                return;
            }
            state.touch();
            state.idleReminderEmitted = false;
            state.turnCount = turnIndex;
            if (state.engine.equals("v2")) {
                state.v2TurnSequence++;
            }
            state.lastCompletedTurn = new CompletedTurn(state.voiceUtteranceId, turnIndex, response);
            sendEvent(session, Map.of(
                    "type", "transcript.final",
                    "text", response.transcript(),
                    "turnIndex", turnIndex
            ));
            sendEvent(session, Map.of(
                    "type", "stt.complete",
                    "provider", response.sttProvider(),
                    "turnIndex", turnIndex,
                    "durationMs", response.sttDurationMs()
            ));
            sendEvent(session, Map.of(
                    "type", "turn.stt.complete",
                    "sessionId", state.sessionId,
                    "turnIndex", turnIndex,
                    "provider", response.sttProvider(),
                    "durationMs", response.sttDurationMs()
            ));
            Map<String, Object> assistantTextEvent = new LinkedHashMap<>();
            assistantTextEvent.put("type", "assistant.text");
            assistantTextEvent.put("text", response.assistantText());
            assistantTextEvent.put("turnIndex", turnIndex);
            assistantTextEvent.put("requestId", response.requestId());
            assistantTextEvent.put("state", response.state());
            Map<String, Object> providerTrace = new LinkedHashMap<>();
            providerTrace.put("sttProvider", response.sttProvider());
            providerTrace.put("llmProvider", response.llmProvider());
            providerTrace.put("ttsProvider", response.ttsProvider());
            assistantTextEvent.put("providerTrace", providerTrace);
            sendEvent(session, assistantTextEvent);
            sendEvent(session, Map.of(
                    "type", "turn.careai.complete",
                    "sessionId", state.sessionId,
                    "turnIndex", turnIndex,
                    "provider", response.llmProvider(),
                    "durationMs", response.careAiDurationMs()
            ));
            if (response.audioBase64() != null && response.audioContentType() != null) {
                sendAssistantAudioChunks(session, response, turnIndex);
                sendEvent(session, Map.of(
                        "type", "turn.tts.complete",
                        "sessionId", state.sessionId,
                        "turnIndex", turnIndex,
                        "provider", response.ttsProvider(),
                        "durationMs", response.ttsDurationMs()
                ));
            }
            Map<String, Object> completedEvent = new LinkedHashMap<>();
            completedEvent.put("type", "turn.complete");
            completedEvent.put("sessionId", state.sessionId);
            completedEvent.put("turnIndex", turnIndex);
            completedEvent.put("requestId", response.requestId());
            completedEvent.put("state", response.state());
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("uploadDurationMs", uploadDurationMs);
            metrics.put("uploadToProcessGapMs", uploadToProcessGapMs);
            metrics.put("sttDurationMs", response.sttDurationMs());
            metrics.put("careAiDurationMs", response.careAiDurationMs());
            metrics.put("ttsDurationMs", response.ttsDurationMs());
            metrics.put("totalDurationMs", response.totalDurationMs());
            metrics.put("captureBytes", response.captureBytes());
            metrics.put("sttProvider", response.sttProvider() == null ? "" : response.sttProvider());
            metrics.put("llmProvider", response.llmProvider() == null ? "" : response.llmProvider());
            metrics.put("ttsProvider", response.ttsProvider() == null ? "" : response.ttsProvider());
            metrics.put("ttsFallbackReason", response.ttsFallbackReason() == null ? "" : response.ttsFallbackReason());
            completedEvent.put("metrics", metrics);
            sendEvent(session, completedEvent);
            if (response.conversationClosed()) {
                state.terminalConversation = true;
                state.terminalTurnIndex = turnIndex;
                sendEvent(session, Map.of("type", "conversation.terminal", "sessionId", state.sessionId,
                        "turnIndex", turnIndex));
                // The browser acknowledges actual final-audio playback. If no
                // audio was produced, completion of the terminal response is
                // the best available boundary (including TTS failure/fallback).
                if (response.audioBase64() == null || response.audioContentType() == null) {
                    scheduleTerminalClose(session, state, turnIndex);
                }
            }
            log.info("patient.voice.turn.completed sessionId={} turnIndex={} requestId={} uploadDurationMs={} sttDurationMs={} careAiDurationMs={} ttsDurationMs={} totalDurationMs={} sttProvider={} llmProvider={} ttsProvider={} ttsFallback={}",
                    state.sessionId,
                    turnIndex,
                    response.requestId(),
                    uploadDurationMs,
                    response.sttDurationMs(),
                    response.careAiDurationMs(),
                    response.ttsDurationMs(),
                    response.totalDurationMs(),
                    response.sttProvider(),
                    response.llmProvider(),
                    response.ttsProvider(),
                    response.ttsFallbackReason());
        } catch (Exception ex) {
            log.warn("patient.voice.turn.failed sessionId={} turnIndex={} exception={} reason={}",
                    state.sessionId,
                    turnIndex,
                    ex.getClass().getSimpleName(),
                    safeVoiceDiagnosticReason(ex));
            sendError(session, safePatientVoiceErrorMessage(ex));
        } finally {
            RequestContextHolder.clear();
            state.turnInProgress = false;
            clearAudioBuffer(state);
        }
    }

    private void handleAudioPlaybackComplete(WebSocketSession session, JsonNode root) throws IOException {
        SessionState state = requireState(session);
        if (!state.terminalConversation || state.closed) return;
        int turnIndex = root.path("turnIndex").asInt(-1);
        if (turnIndex <= 0 || turnIndex != state.terminalTurnIndex) return;
        scheduleTerminalClose(session, state, turnIndex);
    }

    private void scheduleTerminalClose(WebSocketSession session, SessionState state, int turnIndex) {
        cancelTerminalClose(state);
        state.terminalTurnIndex = turnIndex;
        long delaySeconds = Math.max(0, properties.getLive().getTerminalCloseSeconds());
        state.terminalCloseTask = TERMINAL_CLOSE_EXECUTOR.schedule(() -> {
            if (state.closed || !state.terminalConversation || state.turnInProgress || !session.isOpen()) return;
            state.closed = true;
            clearAudioBuffer(state);
            try {
                sendEvent(session, Map.of("type", "session.closed", "sessionId", state.logicalSessionId,
                        "reason", "terminal_idle"));
                markVoiceDisconnected(session, state);
                session.close(CloseStatus.NORMAL);
            } catch (IOException ex) {
                log.debug("patient.voice.websocket.terminal-close.failed sessionId={} reason={}",
                        state.sessionId, ex.getClass().getSimpleName());
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private void cancelTerminalClose(SessionState state) {
        ScheduledFuture<?> task = state.terminalCloseTask;
        if (task != null) {
            task.cancel(false);
            state.terminalCloseTask = null;
        }
    }

    private void handleHeartbeat(WebSocketSession session) throws IOException {
        SessionState state = requireState(session);
        if (enforceSessionPolicies(session, state, true)) {
            return;
        }
        sendEvent(session, Map.of(
                "type", "heartbeat",
                "sessionId", state.sessionId,
                "serverTime", Instant.now().toString()
        ));
    }

    private void handleSessionClose(WebSocketSession session) throws IOException {
        SessionState state = requireState(session);
        state.closed = true;
        cancelTerminalClose(state);
        clearAudioBuffer(state);
        markVoiceDisconnected(session, state);
        sendEvent(session, Map.of(
                "type", "session.closed",
                "sessionId", state.logicalSessionId
        ));
    }

    private SessionState requireState(WebSocketSession session) {
        SessionState state = sessionStates.get(session.getId());
        if (state == null) {
            throw new IllegalStateException("Patient voice session is not initialized.");
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
        state.firstUploadAt = null;
        state.lastUploadActivityAt = null;
        state.turnStartedAt = null;
        state.voiceUtteranceId = null;
    }

    private void replayCompletedTurn(WebSocketSession session, SessionState state, CompletedTurn completedTurn) throws IOException {
        PatientPortalVoiceTurnResponse response = completedTurn.response();
        sendEvent(session, Map.of(
                "type", "turn.duplicate_replay",
                "sessionId", state.sessionId,
                "turnIndex", completedTurn.turnIndex(),
                "voiceUtteranceId", completedTurn.voiceUtteranceId(),
                "clientTurnId", "voice-" + completedTurn.voiceUtteranceId()
        ));
        sendEvent(session, Map.of(
                "type", "transcript.final",
                "text", response.transcript(),
                "turnIndex", completedTurn.turnIndex()
        ));
        Map<String, Object> assistantTextEvent = new LinkedHashMap<>();
        assistantTextEvent.put("type", "assistant.text");
        assistantTextEvent.put("text", response.assistantText());
        assistantTextEvent.put("turnIndex", completedTurn.turnIndex());
        assistantTextEvent.put("requestId", response.requestId());
        assistantTextEvent.put("state", response.state());
        assistantTextEvent.put("providerTrace", Map.of(
                "sttProvider", response.sttProvider() == null ? "" : response.sttProvider(),
                "llmProvider", response.llmProvider() == null ? "" : response.llmProvider(),
                "ttsProvider", response.ttsProvider() == null ? "" : response.ttsProvider()
        ));
        sendEvent(session, assistantTextEvent);
        if (response.audioBase64() != null && response.audioContentType() != null) {
            sendAssistantAudioChunks(session, response, completedTurn.turnIndex());
        }
        sendEvent(session, Map.of(
                "type", "turn.complete",
                "sessionId", state.sessionId,
                "turnIndex", completedTurn.turnIndex(),
                "requestId", response.requestId(),
                "replayed", true
        ));
    }

    private void sendError(WebSocketSession session, String message) throws IOException {
        sendEvent(session, Map.of(
                "type", "error",
                "message", message
        ));
    }

    private String safePatientVoiceErrorMessage(Exception ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (message.contains("transcrib") || message.contains("speech recognition") || message.contains("stt")) {
            return "Speech recognition unavailable.";
        }
        if (message.contains("synth") || message.contains("tts") || message.contains("audio")) {
            return "Voice playback unavailable.";
        }
        if (message.contains("ai") || message.contains("llm") || message.contains("careai")) {
            return "AI service unavailable.";
        }
        if (message.contains("connect") || message.contains("socket") || message.contains("websocket")) {
            return "Connection interrupted.";
        }
        return "Voice service temporarily unavailable.";
    }

    private String safeVoiceDiagnosticReason(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "unspecified";
        }
        String sanitized = message.replaceAll("(?i)(token|sessionToken|jwt|authorization|api[_-]?key|secret)=[^&\\s]+", "$1=[redacted]");
        sanitized = sanitized.replaceAll("(?i)(wss?|https?)://[^\\s]+", "[endpoint-redacted]");
        return sanitized.length() > 160 ? sanitized.substring(0, 160) : sanitized;
    }

    private boolean enforceSessionPolicies(WebSocketSession session, SessionState state) throws IOException {
        return enforceSessionPolicies(session, state, false);
    }

    private boolean enforceSessionPolicies(WebSocketSession session, SessionState state, boolean allowReminder) throws IOException {
        Instant now = Instant.now();
        if (state.startedAt != null
                && Duration.between(state.startedAt, now).compareTo(Duration.ofSeconds(properties.getLive().getMaxSessionDurationSeconds())) > 0) {
            state.closed = true;
            clearAudioBuffer(state);
            sendEvent(session, Map.of("type", "session.timeout", "reason", "max_session_duration"));
            sendError(session, "Live voice session exceeded the supported duration.");
            return true;
        }
        long idleSeconds = state.lastActivityAt == null ? 0
                : Duration.between(state.lastActivityAt, now).getSeconds();
        if (allowReminder && !state.idleReminderEmitted && !state.turnInProgress && state.audioChunks.isEmpty()
                && idleSeconds >= properties.getLive().getIdleReminderSeconds()
                && idleSeconds < properties.getLive().getIdleCloseSeconds()) {
            sendIdleReminder(session, state);
            state.idleReminderEmitted = true;
        }
        if (state.lastActivityAt != null
                && idleSeconds >= Math.min(properties.getLive().getMaxIdleSeconds(), properties.getLive().getIdleCloseSeconds())) {
            state.closed = true;
            clearAudioBuffer(state);
            sendEvent(session, Map.of("type", "session.timeout", "reason", "idle_timeout"));
            sendEvent(session, Map.of("type", "assistant.idle.close", "message",
                    idleMessage(state, true)));
            sendError(session, "Live voice session timed out due to inactivity.");
            markVoiceDisconnected(session, state);
            session.close(CloseStatus.NORMAL);
            return true;
        }
        return false;
    }

    private void sendIdleReminder(WebSocketSession session, SessionState state) throws IOException {
        String message = idleMessage(state, false);
        sendEvent(session, Map.of("type", "assistant.idle.reminder", "message", message));
        PatientPortalVoiceProgressAudio audio = voiceAssistantService.synthesizeV2ProgressAudio(message, state.language);
        if (audio != null) {
            sendV2ProgressAudioChunks(session, state, state.turnCount, new AivaV2ProgressEvent("VOICE_IDLE_REMINDER", message), audio);
        }
        log.info("VOICE_IDLE_REMINDER_EMITTED sessionId={}", state.sessionId);
    }

    private String idleMessage(SessionState state, boolean closing) {
        boolean hindi = state.language != null && state.language.toLowerCase().startsWith("hi");
        if (closing) return hindi
                ? "काफी देर से कोई जवाब नहीं मिला, इसलिए मैं अभी यह वॉइस सेशन समाप्त कर रही हूँ। आप कभी भी फिर शुरू कर सकते हैं।"
                : "I haven't heard a response, so I'll end this voice session for now. You can start again anytime.";
        return hindi ? "मैं यहीं हूँ। जब आप तैयार हों, बताइए।" : "I'm still here. Let me know when you're ready.";
    }

    private int maxTotalChunks() {
        return 1000;
    }

    private void sendEvent(WebSocketSession session, Object payload) throws IOException {
        synchronized (session) {
            if (!session.isOpen()) {
                return;
            }
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        }
    }

    private void sendProgressEvent(
            WebSocketSession session,
            SessionState state,
            int turnIndex,
            PatientPortalCareAiProgressEvent progress
    ) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "turn.progress");
            payload.put("sessionId", state.sessionId);
            payload.put("turnIndex", turnIndex);
            payload.put("turnId", progress.turnId());
            payload.put("skillExecutionId", progress.skillExecutionId());
            payload.put("skillId", progress.skillId());
            payload.put("progressKey", progress.progressKey());
            payload.put("state", progress.state());
            payload.put("acknowledgement", progress.acknowledgement());
            sendEvent(session, payload);
            PatientPortalVoiceProgressAudio audio = voiceAssistantService.synthesizeProgressAudio(progress, state.language);
            if (audio != null) {
                sendProgressAudioChunks(session, state, turnIndex, progress, audio);
            }
        } catch (IOException ex) {
            log.debug("patient.voice.progress.delivery.failed sessionId={} turnIndex={} reason={}",
                    state.sessionId, turnIndex, safeVoiceDiagnosticReason(ex));
        }
    }

    private void sendV2ProgressEvent(WebSocketSession session, SessionState state, int turnIndex,
                                     AivaV2ProgressEvent progress) {
        if (progress == null || !session.isOpen()) return;
        try {
            sendEvent(session, Map.of(
                    "type", "turn.progress",
                    "sessionId", state.sessionId,
                    "turnIndex", turnIndex,
                    "operation", progress.operation(),
                    "acknowledgement", progress.acknowledgement()
            ));
            PatientPortalVoiceProgressAudio audio = voiceAssistantService
                    .synthesizeV2ProgressAudio(progress.acknowledgement(), state.language);
            if (audio != null) sendV2ProgressAudioChunks(session, state, turnIndex, progress, audio);
        } catch (IOException ex) {
            log.debug("patient.voice.v2.progress.delivery.failed sessionId={} turnIndex={} reason={}",
                    state.sessionId, turnIndex, safeVoiceDiagnosticReason(ex));
        }
    }

    private void sendV2ProgressAudioChunks(WebSocketSession session, SessionState state, int turnIndex,
                                           AivaV2ProgressEvent progress, PatientPortalVoiceProgressAudio audio)
            throws IOException {
        String audioBase64 = audio.audioBase64();
        int totalChunks = (int) Math.ceil((double) audioBase64.length() / RESPONSE_CHUNK_BASE64_CHARS);
        for (int sequence = 1; sequence <= totalChunks; sequence++) {
            int start = (sequence - 1) * RESPONSE_CHUNK_BASE64_CHARS;
            int end = Math.min(audioBase64.length(), start + RESPONSE_CHUNK_BASE64_CHARS);
            sendEvent(session, Map.of(
                    "type", "assistant.progress.audio.chunk", "sessionId", state.sessionId,
                    "turnIndex", turnIndex, "operation", progress.operation(), "sequence", sequence,
                    "totalChunks", totalChunks, "contentType", audio.contentType(),
                    "audioBase64Chunk", audioBase64.substring(start, end)));
        }
        sendEvent(session, Map.of(
                "type", "assistant.progress.audio.end", "sessionId", state.sessionId,
                "turnIndex", turnIndex, "operation", progress.operation(), "totalChunks", totalChunks,
                "contentType", audio.contentType(), "provider", audio.provider() == null ? "" : audio.provider()));
    }

    private void sendProgressAudioChunks(
            WebSocketSession session,
            SessionState state,
            int turnIndex,
            PatientPortalCareAiProgressEvent progress,
            PatientPortalVoiceProgressAudio audio
    ) throws IOException {
        String audioBase64 = audio.audioBase64();
        int totalChunks = (int) Math.ceil((double) audioBase64.length() / RESPONSE_CHUNK_BASE64_CHARS);
        for (int sequence = 1; sequence <= totalChunks; sequence++) {
            int start = (sequence - 1) * RESPONSE_CHUNK_BASE64_CHARS;
            int end = Math.min(audioBase64.length(), start + RESPONSE_CHUNK_BASE64_CHARS);
            sendEvent(session, Map.of(
                    "type", "assistant.progress.audio.chunk",
                    "sessionId", state.sessionId,
                    "turnIndex", turnIndex,
                    "turnId", progress.turnId(),
                    "skillExecutionId", progress.skillExecutionId(),
                    "sequence", sequence,
                    "totalChunks", totalChunks,
                    "contentType", audio.contentType(),
                    "audioBase64Chunk", audioBase64.substring(start, end)
            ));
        }
        sendEvent(session, Map.of(
                "type", "assistant.progress.audio.end",
                "sessionId", state.sessionId,
                "turnIndex", turnIndex,
                "turnId", progress.turnId(),
                "skillExecutionId", progress.skillExecutionId(),
                "totalChunks", totalChunks,
                "contentType", audio.contentType(),
                "provider", audio.provider() == null ? "" : audio.provider()
        ));
    }

    private byte[] decodeAudioChunks(SessionState state, int totalChunks) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int index = 1; index <= totalChunks; index++) {
            String chunk = state.audioChunks.get(index);
            if (chunk == null) {
                return null;
            }
            try {
                output.write(Base64.getDecoder().decode(stripDataUrlPrefix(chunk)));
            } catch (IllegalArgumentException | IOException ex) {
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

    private String requestedUtteranceId(JsonNode root) {
        String value = root.path("voiceUtteranceId").asText("").trim();
        if (value.isBlank() || value.length() > 100 || !value.matches("[A-Za-z0-9_-]+")) {
            return null;
        }
        return value;
    }

    private String v2ConversationId(String logicalSessionId) {
        String normalized = logicalSessionId == null ? "session" : logicalSessionId.replaceAll("[^A-Za-z0-9_-]", "-");
        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 80);
        }
        return "voice-v2-" + normalized;
    }

    private long resumedV2TurnSequence(WebSocketSession session, String conversationId) {
        if (aivaV2VoiceTurnConnector == null) {
            return 0L;
        }
        UUID tenantId = uuidAttribute(session, "tenantId");
        UUID patientId = uuidAttribute(session, "patientId");
        if (tenantId == null || patientId == null) {
            throw new IllegalStateException("Missing patient session context");
        }
        return aivaV2VoiceTurnConnector.lastAcceptedVoiceTurnSequence(tenantId, patientId, conversationId);
    }

    private void sendAssistantAudioChunks(WebSocketSession session, PatientPortalVoiceTurnResponse response, int turnIndex) throws IOException {
        String audioBase64 = response.audioBase64();
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
                "requestId", response.requestId(),
                "totalChunks", totalChunks,
                "durationMs", response.ttsDurationMs()
        ));
    }

    private CareAiConversationSessionSnapshot tryResumeConversation(WebSocketSession session, SessionState state, boolean explicitResumeRequest) {
        UUID tenantId = uuidAttribute(session, "tenantId");
        UUID patientId = uuidAttribute(session, "patientId");
        if (tenantId == null || patientId == null || state.logicalSessionId == null || state.logicalSessionId.isBlank()) {
            return null;
        }
        CareAiConversationSessionSnapshot snapshot = conversationPersistenceService.safeResumeSession(
                tenantId,
                CareAiChannel.PATIENT_PORTAL_VOICE,
                patientId,
                state.logicalSessionId,
                CareAiTransport.WEBSOCKET_PATIENT_PORTAL,
                INSTANCE_ID,
                8
        );
        if (snapshot != null) {
            state.recoveredConversationId = snapshot.conversation().getId();
            state.recoveredWorkflowId = snapshot.workflow() == null ? null : snapshot.workflow().getId();
            return snapshot;
        }
        if (explicitResumeRequest) {
            conversationPersistenceService.safeMarkVoiceRecoveryFailed(
                    tenantId,
                    patientId,
                    state.logicalSessionId,
                    INSTANCE_ID,
                    "active-conversation-not-found"
            );
        }
        return null;
    }

    private void markVoiceDisconnected(WebSocketSession session, SessionState state) {
        UUID tenantId = uuidAttribute(session, "tenantId");
        UUID patientId = uuidAttribute(session, "patientId");
        if (tenantId == null || patientId == null || state.logicalSessionId == null || state.logicalSessionId.isBlank()) {
            return;
        }
        conversationPersistenceService.safeMarkVoiceDisconnected(tenantId, patientId, state.logicalSessionId, INSTANCE_ID);
    }

    private UUID uuidAttribute(WebSocketSession session, String attributeName) {
        Object value = session.getAttributes().get(attributeName);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String buildInstanceId() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":" + ManagementFactory.getRuntimeMXBean().getName();
        } catch (Exception ex) {
            return "api-bff:" + ManagementFactory.getRuntimeMXBean().getName();
        }
    }

    static final class SessionState {
        private final String sessionId;
        private String logicalSessionId;
        private final Map<Integer, String> audioChunks = new LinkedHashMap<>();
        private String language = "auto";
        private String contentType;
        private String filename;
        private boolean closed;
        private boolean turnInProgress;
        private int expectedTotalChunks;
        private int chunkCount;
        private int turnCount;
        private long base64CharsReceived;
        private Instant startedAt;
        private Instant lastActivityAt;
        private Instant lastHeartbeatAt;
        private boolean idleReminderEmitted;
        private boolean terminalConversation;
        private int terminalTurnIndex;
        private ScheduledFuture<?> terminalCloseTask;
        private Instant firstUploadAt;
        private Instant lastUploadActivityAt;
        private Instant turnStartedAt;
        private UUID recoveredConversationId;
        private UUID recoveredWorkflowId;
        private String engine = "v2";
        private String v2ConversationId;
        private long v2TurnSequence;
        private String voiceUtteranceId;
        private CompletedTurn lastCompletedTurn;

        private SessionState(String sessionId) {
            this.sessionId = sessionId;
            this.logicalSessionId = sessionId;
        }

        private void touch() {
            this.lastActivityAt = Instant.now();
        }

        private void touchHeartbeat() { this.lastHeartbeatAt = Instant.now(); }
    }

    private record CompletedTurn(
            String voiceUtteranceId,
            int turnIndex,
            PatientPortalVoiceTurnResponse response
    ) {
    }
}
