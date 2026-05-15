package com.deepthoughtnet.clinic.realtime.voice.metrics;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/** In-memory metrics foundation for realtime gateway observability. */
@Service
public class RealtimeVoiceGatewayMetrics {
    private final AtomicLong failedSessions = new AtomicLong();
    private final AtomicLong escalations = new AtomicLong();
    private final AtomicLong websocketDisconnects = new AtomicLong();
    private final AtomicLong aiLatencyMs = new AtomicLong();
    private final AtomicLong aiLatencySamples = new AtomicLong();
    private final AtomicLong sttLatencyMs = new AtomicLong();
    private final AtomicLong sttLatencySamples = new AtomicLong();
    private final AtomicLong ttsLatencyMs = new AtomicLong();
    private final AtomicLong ttsLatencySamples = new AtomicLong();
    private final AtomicLong sttFailures = new AtomicLong();
    private final AtomicLong ttsFailures = new AtomicLong();
    private final AtomicLong interruptions = new AtomicLong();
    private final AtomicLong transcriptLatencyMs = new AtomicLong();
    private final AtomicLong transcriptLatencySamples = new AtomicLong();
    private final Map<UUID, Long> activeSessionStartEpochMs = new ConcurrentHashMap<>();

    public void markSessionStarted(UUID sessionId) { activeSessionStartEpochMs.put(sessionId, System.currentTimeMillis()); }
    public void markSessionEnded(UUID sessionId) { activeSessionStartEpochMs.remove(sessionId); }
    public void markFailedSession() { failedSessions.incrementAndGet(); }
    public void markEscalation() { escalations.incrementAndGet(); }
    public void markWebsocketDisconnect() { websocketDisconnects.incrementAndGet(); }
    public void addAiLatency(long latencyMs) { aiLatencyMs.addAndGet(Math.max(0, latencyMs)); aiLatencySamples.incrementAndGet(); }
    public void addSttLatency(long latencyMs) { sttLatencyMs.addAndGet(Math.max(0, latencyMs)); sttLatencySamples.incrementAndGet(); }
    public void addTtsLatency(long latencyMs) { ttsLatencyMs.addAndGet(Math.max(0, latencyMs)); ttsLatencySamples.incrementAndGet(); }
    public void markSttFailure() { sttFailures.incrementAndGet(); }
    public void markTtsFailure() { ttsFailures.incrementAndGet(); }
    public void markInterruption() { interruptions.incrementAndGet(); }
    public void markTranscriptLatency(long latencyMs) { transcriptLatencyMs.addAndGet(Math.max(0, latencyMs)); transcriptLatencySamples.incrementAndGet(); }

    public long activeSessions() { return activeSessionStartEpochMs.size(); }
    public long failedSessions() { return failedSessions.get(); }
    public long escalationCount() { return escalations.get(); }
    public long websocketDisconnects() { return websocketDisconnects.get(); }
    public long avgAiLatencyMs() { return aiLatencySamples.get() == 0 ? 0 : aiLatencyMs.get() / aiLatencySamples.get(); }
    public long avgSttLatencyMs() { return sttLatencySamples.get() == 0 ? 0 : sttLatencyMs.get() / sttLatencySamples.get(); }
    public long avgTtsLatencyMs() { return ttsLatencySamples.get() == 0 ? 0 : ttsLatencyMs.get() / ttsLatencySamples.get(); }
    public long sttFailures() { return sttFailures.get(); }
    public long ttsFailures() { return ttsFailures.get(); }
    public long interruptionCount() { return interruptions.get(); }
    public long avgTranscriptLatencyMs() { return transcriptLatencySamples.get() == 0 ? 0 : transcriptLatencyMs.get() / transcriptLatencySamples.get(); }
}
