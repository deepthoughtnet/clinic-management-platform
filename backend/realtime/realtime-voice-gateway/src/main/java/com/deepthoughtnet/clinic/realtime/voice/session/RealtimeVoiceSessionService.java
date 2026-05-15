package com.deepthoughtnet.clinic.realtime.voice.session;

import com.deepthoughtnet.clinic.realtime.voice.config.VoiceGatewayProperties;
import com.deepthoughtnet.clinic.realtime.voice.db.VoiceSessionEntity;
import com.deepthoughtnet.clinic.realtime.voice.db.VoiceSessionEventEntity;
import com.deepthoughtnet.clinic.realtime.voice.db.VoiceSessionEventRepository;
import com.deepthoughtnet.clinic.realtime.voice.db.VoiceSessionRepository;
import com.deepthoughtnet.clinic.realtime.voice.db.VoiceTranscriptEntity;
import com.deepthoughtnet.clinic.realtime.voice.db.VoiceTranscriptRepository;
import com.deepthoughtnet.clinic.realtime.voice.escalation.VoiceEscalationDecider;
import com.deepthoughtnet.clinic.realtime.voice.events.VoiceRealtimeEvent;
import com.deepthoughtnet.clinic.realtime.voice.events.VoiceSessionEventBus;
import com.deepthoughtnet.clinic.realtime.voice.events.VoiceSessionEventType;
import com.deepthoughtnet.clinic.realtime.voice.metrics.RealtimeVoiceGatewayMetrics;
import com.deepthoughtnet.clinic.realtime.voice.orchestration.RealtimeConversationOrchestrator;
import com.deepthoughtnet.clinic.realtime.voice.orchestration.RollingConversationMemory;
import com.deepthoughtnet.clinic.realtime.voice.receptionist.AiReceptionistWorkflowService;
import com.deepthoughtnet.clinic.realtime.voice.transcript.SpeakerType;
import com.deepthoughtnet.clinic.realtime.voice.transcript.VoiceTranscriptRecord;
import com.deepthoughtnet.clinic.stt.spi.SpeechRecognitionResult;
import com.deepthoughtnet.clinic.stt.spi.SpeechToTextProvider;
import com.deepthoughtnet.clinic.stt.spi.StreamingSpeechSession;
import com.deepthoughtnet.clinic.stt.spi.TranscriptChunk;
import com.deepthoughtnet.clinic.tts.spi.SpeechSynthesisRequest;
import com.deepthoughtnet.clinic.tts.spi.SpeechSynthesisResult;
import com.deepthoughtnet.clinic.tts.spi.TextToSpeechProvider;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core lifecycle service for realtime voice sessions and transcript/event timelines.
 */
@Service
public class RealtimeVoiceSessionService {
    private final VoiceSessionRepository sessionRepository;
    private final VoiceSessionEventRepository eventRepository;
    private final VoiceTranscriptRepository transcriptRepository;
    private final SpeechToTextProvider sttProvider;
    private final TextToSpeechProvider ttsProvider;
    private final RealtimeConversationOrchestrator conversationOrchestrator;
    private final RollingConversationMemory rollingConversationMemory;
    private final VoiceEscalationDecider escalationDecider;
    private final RealtimeVoiceGatewayMetrics metrics;
    private final VoiceSessionEventBus eventBus;
    private final VoiceGatewayProperties properties;
    private final AiReceptionistWorkflowService receptionistWorkflowService;
    private final Map<UUID, SessionAudioRuntime> audioRuntime = new ConcurrentHashMap<>();

    public RealtimeVoiceSessionService(
            VoiceSessionRepository sessionRepository,
            VoiceSessionEventRepository eventRepository,
            VoiceTranscriptRepository transcriptRepository,
            List<SpeechToTextProvider> sttProviders,
            List<TextToSpeechProvider> ttsProviders,
            RealtimeConversationOrchestrator conversationOrchestrator,
            RollingConversationMemory rollingConversationMemory,
            VoiceEscalationDecider escalationDecider,
            RealtimeVoiceGatewayMetrics metrics,
            VoiceSessionEventBus eventBus,
            VoiceGatewayProperties properties,
            AiReceptionistWorkflowService receptionistWorkflowService
    ) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.transcriptRepository = transcriptRepository;
        this.sttProvider = selectSttProvider(sttProviders, properties.getStt().getProvider());
        this.ttsProvider = selectTtsProvider(ttsProviders, properties.getTts().getProvider());
        this.conversationOrchestrator = conversationOrchestrator;
        this.rollingConversationMemory = rollingConversationMemory;
        this.escalationDecider = escalationDecider;
        this.metrics = metrics;
        this.eventBus = eventBus;
        this.properties = properties;
        this.receptionistWorkflowService = receptionistWorkflowService;
    }

    @Transactional
    public VoiceSessionRecord createSession(UUID tenantId, VoiceSessionType sessionType, UUID patientId, UUID leadId,
                                            String metadataJson, String correlationId) {
        VoiceSessionEntity row = VoiceSessionEntity.create(
                tenantId,
                sessionType,
                patientId,
                leadId,
                "ai-orchestration",
                sttProvider.providerName(),
                ttsProvider.providerName(),
                metadataJson
        );
        row.markActive();
        sessionRepository.save(row);
        metrics.markSessionStarted(row.getId());
        addEvent(row.getId(), VoiceSessionEventType.SESSION_STARTED, "Session activated", correlationId);
        return toRecord(row);
    }

    @Transactional(readOnly = true)
    public List<VoiceSessionRecord> listSessions(UUID tenantId) {
        return sessionRepository.findTop200ByTenantIdOrderByStartedAtDesc(tenantId).stream().map(this::toRecord).toList();
    }

    @Transactional(readOnly = true)
    public VoiceSessionRecord getSession(UUID tenantId, UUID sessionId) {
        return toRecord(requireSession(tenantId, sessionId));
    }

    @Transactional(readOnly = true)
    public List<VoiceSessionEventEntity> sessionEvents(UUID tenantId, UUID sessionId) {
        requireSession(tenantId, sessionId);
        return eventRepository.findBySessionIdOrderBySequenceNumberAsc(sessionId);
    }

    @Transactional(readOnly = true)
    public List<VoiceTranscriptRecord> transcripts(UUID tenantId, UUID sessionId) {
        requireSession(tenantId, sessionId);
        return transcriptRepository.findBySessionIdOrderByTranscriptTimestampAsc(sessionId).stream()
                .map(t -> new VoiceTranscriptRecord(t.getId(), t.getSessionId(), t.getSpeakerType(), t.getTranscriptText(), t.getTranscriptTimestamp(), t.getConfidence()))
                .toList();
    }

    /**
     * Audio chunk ingestion entrypoint for websocket transport.
     */
    @Transactional
    public AudioTurnResult processAudioChunk(UUID tenantId, UUID sessionId, UUID actorUserId, byte[] audioBytes,
                                             String promptKey, String patientContextJson, String locale,
                                             boolean finalize, String correlationId) {
        requireSession(tenantId, sessionId);
        if (audioBytes == null || audioBytes.length < properties.getAudio().getMinChunkBytes()) {
            return AudioTurnResult.empty();
        }

        SessionAudioRuntime runtime = audioRuntime.computeIfAbsent(sessionId, ignored -> new SessionAudioRuntime(sttProvider.openSession(tenantId, locale)));
        runtime.lastAudioEpochMs = System.currentTimeMillis();

        if (runtime.aiSpeaking) {
            runtime.pendingAudioResponses.clear();
            runtime.aiSpeaking = false;
            metrics.markInterruption();
            addEvent(sessionId, VoiceSessionEventType.INTERRUPTION, "Barge-in detected", correlationId);
        }

        addEvent(sessionId, VoiceSessionEventType.AUDIO_RECEIVED, "audio-bytes=" + audioBytes.length, correlationId);
        long sttStarted = System.currentTimeMillis();
        TranscriptChunk chunk = runtime.sttSession.pushAudio(audioBytes);
        metrics.addSttLatency(System.currentTimeMillis() - sttStarted);
        if (chunk.text() != null && !chunk.text().isBlank()) {
            metrics.markTranscriptLatency(0);
            appendTranscript(sessionId, SpeakerType.USER, sanitize(chunk.text()), chunk.confidence());
            addEvent(sessionId, VoiceSessionEventType.STT_TRANSCRIPT, trimPayload(chunk.text()), correlationId);
            publishRealtimeMessage(sessionId, VoiceSessionEventType.STT_TRANSCRIPT, "INTERIM:" + trimPayload(chunk.text()));
        }

        boolean shouldFinalize = finalize || chunk.finalChunk() || isSilenceTimeout(runtime.lastAudioEpochMs);
        if (!shouldFinalize) {
            return AudioTurnResult.interim(chunk.text());
        }
        SpeechRecognitionResult finalResult = runtime.sttSession.finish();
        if (finalResult.fullText() == null || finalResult.fullText().isBlank()) {
            return AudioTurnResult.interim("");
        }
        runtime.sttSession.close();
        runtime.sttSession = sttProvider.openSession(tenantId, locale);
        VoiceTurnResult turn = processUserText(tenantId, sessionId, actorUserId, finalResult.fullText(), promptKey, patientContextJson, correlationId);

        long ttsStarted = System.currentTimeMillis();
        SpeechSynthesisResult tts = ttsProvider.synthesize(new SpeechSynthesisRequest(tenantId, turn.aiTranscript().text(), locale, properties.getTts().getVoice()));
        metrics.addTtsLatency(System.currentTimeMillis() - ttsStarted);
        if (tts.audioBytes() == null || tts.audioBytes().length == 0) {
            metrics.markTtsFailure();
        } else {
            runtime.aiSpeaking = true;
            runtime.pendingAudioResponses.addLast(tts.audioBytes());
            addEvent(sessionId, VoiceSessionEventType.TTS_GENERATED, "audio-bytes=" + tts.audioBytes().length, correlationId);
            publishRealtimeMessage(sessionId, VoiceSessionEventType.TTS_GENERATED,
                    "AUDIO_BASE64:" + Base64.getEncoder().encodeToString(tts.audioBytes()));
        }
        return AudioTurnResult.finalTurn(turn, tts.audioBytes() == null ? 0 : tts.audioBytes().length);
    }

    /**
     * Receives a user utterance, calls shared AI orchestration, persists transcript/events,
     * and returns AI reply with escalation signals.
     */
    @Transactional
    public VoiceTurnResult processUserText(UUID tenantId, UUID sessionId, UUID actorUserId, String userText,
                                           String promptKey, String patientContextJson, String correlationId) {
        VoiceSessionEntity session = requireSession(tenantId, sessionId);
        var workflow = receptionistWorkflowService.evaluate(session, actorUserId, userText, correlationId);
        sessionRepository.save(session);
        VoiceTranscriptRecord userLine = appendTranscript(sessionId, SpeakerType.USER, sanitize(userText), null);
        addEvent(sessionId, VoiceSessionEventType.STT_TRANSCRIPT, trimPayload(userText), correlationId);
        addEvent(sessionId, VoiceSessionEventType.RECEPTIONIST_EXTRACTION, trimPayload(workflow.extractionSummary()), correlationId);

        String context = rollingConversationMemory.buildPromptContext(sessionId);
        long aiLatency = 0L;
        RealtimeConversationOrchestrator.OrchestratorReply aiReply;
        if (workflow.deterministicReply() != null && !workflow.deterministicReply().isBlank()) {
            aiReply = new RealtimeConversationOrchestrator.OrchestratorReply(workflow.deterministicReply(), 1.0d, "DETERMINISTIC_RULES", 0L);
        } else {
            long aiStarted = System.currentTimeMillis();
            aiReply = conversationOrchestrator.respond(
                    session,
                    actorUserId,
                    workflow.promptKey() == null || workflow.promptKey().isBlank() ? promptKey : workflow.promptKey(),
                    workflow.userTextForLlm() == null || workflow.userTextForLlm().isBlank() ? userText : workflow.userTextForLlm(),
                    context,
                    patientContextJson,
                    correlationId
            );
            aiLatency = System.currentTimeMillis() - aiStarted;
            metrics.addAiLatency(aiLatency);
        }

        VoiceTranscriptRecord aiLine = appendTranscript(sessionId, SpeakerType.AI, aiReply.aiText(), aiReply.confidence());
        addEvent(sessionId, VoiceSessionEventType.AI_RESPONSE, trimPayload(aiReply.aiText()), correlationId);

        int misunderstandings = rollingConversationMemory.recentMisunderstandingCount(sessionId);
        String escalationReason = escalationDecider.escalationReason(userText, aiReply.aiText(), misunderstandings, aiReply.confidence());
        if (workflow.escalate() && escalationReason == null) {
            escalationReason = workflow.escalationReason() == null ? "Workflow-triggered escalation" : workflow.escalationReason();
        }
        if (escalationReason != null) {
            session.markEscalated(escalationReason);
            sessionRepository.save(session);
            metrics.markEscalation();
            String escalationPayload = workflow.escalationCategory() == null
                    ? escalationReason
                    : workflow.escalationCategory() + " | " + workflow.escalationPriority() + " | " + escalationReason;
            addEvent(sessionId, VoiceSessionEventType.ESCALATION, escalationPayload, correlationId);
        }

        return new VoiceTurnResult(userLine, aiLine, escalationReason, aiReply.provider(), aiLatency);
    }

    @Transactional
    public VoiceSessionRecord completeSession(UUID tenantId, UUID sessionId, String correlationId) {
        VoiceSessionEntity session = requireSession(tenantId, sessionId);
        String summaryPromptKey = "AI_RECEPTIONIST_SUMMARY";
        String summaryText = null;
        if (session.getSessionType() == VoiceSessionType.AI_RECEPTIONIST) {
            StringBuilder transcriptDigest = new StringBuilder();
            var lines = transcriptRepository.findBySessionIdOrderByTranscriptTimestampAsc(sessionId);
            for (var line : lines) {
                transcriptDigest
                        .append(line.getSpeakerType().name())
                        .append(": ")
                        .append(line.getTranscriptText())
                        .append("\n");
            }
            try {
                var summaryReply = conversationOrchestrator.respond(
                        session,
                        session.getAssignedHumanUserId(),
                        summaryPromptKey,
                        transcriptDigest.toString(),
                        "",
                        "{}",
                        correlationId
                );
                summaryText = summaryReply.aiText();
            } catch (RuntimeException ignored) {
                summaryText = "Session completed. Summary unavailable due to AI provider failure.";
            }
            receptionistWorkflowService.applySummary(session, summaryText);
            addEvent(sessionId, VoiceSessionEventType.AI_RESPONSE, trimPayload(summaryText), correlationId);
            receptionistWorkflowService.markCompleted(session);
            sessionRepository.save(session);
        }
        session.markCompleted();
        sessionRepository.save(session);
        rollingConversationMemory.clear(sessionId);
        SessionAudioRuntime runtime = audioRuntime.remove(sessionId);
        if (runtime != null) {
            runtime.sttSession.close();
        }
        metrics.markSessionEnded(sessionId);
        addEvent(sessionId, VoiceSessionEventType.SESSION_COMPLETED, "Session completed", correlationId);
        return toRecord(session);
    }

    @Transactional
    public VoiceSessionRecord markFailed(UUID tenantId, UUID sessionId, String reason, String correlationId) {
        VoiceSessionEntity session = requireSession(tenantId, sessionId);
        session.markFailed(reason);
        sessionRepository.save(session);
        rollingConversationMemory.clear(sessionId);
        SessionAudioRuntime runtime = audioRuntime.remove(sessionId);
        if (runtime != null) {
            runtime.sttSession.close();
        }
        metrics.markSessionEnded(sessionId);
        metrics.markFailedSession();
        addEvent(sessionId, VoiceSessionEventType.SESSION_FAILED, reason, correlationId);
        return toRecord(session);
    }

    private VoiceTranscriptRecord appendTranscript(UUID sessionId, SpeakerType speakerType, String text, Double confidence) {
        VoiceTranscriptEntity row = transcriptRepository.save(VoiceTranscriptEntity.create(sessionId, speakerType, text, confidence));
        VoiceTranscriptRecord record = new VoiceTranscriptRecord(row.getId(), row.getSessionId(), row.getSpeakerType(), row.getTranscriptText(), row.getTranscriptTimestamp(), row.getConfidence());
        rollingConversationMemory.append(sessionId, record);
        return record;
    }

    private void addEvent(UUID sessionId, VoiceSessionEventType type, String payload, String correlationId) {
        long seq = eventRepository.countBySessionId(sessionId) + 1;
        VoiceSessionEventEntity row = eventRepository.save(VoiceSessionEventEntity.create(sessionId, type, seq, payload, correlationId));
        eventBus.publish(new VoiceRealtimeEvent(sessionId, type, payload, row.getSequenceNumber(), OffsetDateTime.now()));
    }

    private void publishRealtimeMessage(UUID sessionId, VoiceSessionEventType type, String message) {
        long seq = eventRepository.countBySessionId(sessionId) + 1;
        eventBus.publish(new VoiceRealtimeEvent(sessionId, type, message, seq, OffsetDateTime.now()));
    }

    private VoiceSessionEntity requireSession(UUID tenantId, UUID sessionId) {
        return sessionRepository.findByTenantIdAndId(tenantId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Voice session not found"));
    }

    private VoiceSessionRecord toRecord(VoiceSessionEntity row) {
        return new VoiceSessionRecord(
                row.getId(), row.getTenantId(), row.getSessionType(), row.getSessionStatus(), row.getPatientId(), row.getLeadId(),
                row.getStartedAt(), row.getEndedAt(), row.isEscalationRequired(), row.getEscalationReason(), row.getAssignedHumanUserId(),
                row.getAiProvider(), row.getSttProvider(), row.getTtsProvider(), row.getMetadataJson()
        );
    }

    private String sanitize(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private String trimPayload(String value) {
        String sanitized = sanitize(value);
        return sanitized.length() <= 300 ? sanitized : sanitized.substring(0, 300);
    }

    private boolean isSilenceTimeout(long lastAudioEpochMs) {
        return (System.currentTimeMillis() - lastAudioEpochMs) >= properties.getAudio().getVadSilenceMs();
    }

    private SpeechToTextProvider selectSttProvider(List<SpeechToTextProvider> providers, String configured) {
        return providers.stream()
                .filter(p -> p.providerName().equalsIgnoreCase(configured))
                .findFirst()
                .orElseGet(() -> providers.stream().filter(SpeechToTextProvider::isReady).findFirst().orElse(providers.getFirst()));
    }

    private TextToSpeechProvider selectTtsProvider(List<TextToSpeechProvider> providers, String configured) {
        return providers.stream()
                .filter(p -> p.providerName().equalsIgnoreCase(configured))
                .findFirst()
                .orElseGet(() -> providers.stream().filter(TextToSpeechProvider::isReady).findFirst().orElse(providers.getFirst()));
    }

    public record VoiceTurnResult(VoiceTranscriptRecord userTranscript, VoiceTranscriptRecord aiTranscript,
                                  String escalationReason, String aiProvider, long aiLatencyMs) {
    }

    public record AudioTurnResult(boolean finalized, String interimTranscript, VoiceTurnResult turn, int audioBytes) {
        static AudioTurnResult interim(String text) { return new AudioTurnResult(false, text, null, 0); }
        static AudioTurnResult finalTurn(VoiceTurnResult turn, int audioBytes) { return new AudioTurnResult(true, null, turn, audioBytes); }
        static AudioTurnResult empty() { return new AudioTurnResult(false, "", null, 0); }
    }

    private static final class SessionAudioRuntime {
        private StreamingSpeechSession sttSession;
        private final Deque<byte[]> pendingAudioResponses = new ArrayDeque<>();
        private volatile boolean aiSpeaking;
        private volatile long lastAudioEpochMs;

        private SessionAudioRuntime(StreamingSpeechSession sttSession) {
            this.sttSession = sttSession;
            this.lastAudioEpochMs = System.currentTimeMillis();
        }
    }
}
