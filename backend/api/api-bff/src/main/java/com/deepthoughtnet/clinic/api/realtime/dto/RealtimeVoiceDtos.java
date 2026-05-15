package com.deepthoughtnet.clinic.api.realtime.dto;

import com.deepthoughtnet.clinic.realtime.voice.events.VoiceSessionEventType;
import com.deepthoughtnet.clinic.realtime.voice.session.VoiceSessionRecord;
import com.deepthoughtnet.clinic.realtime.voice.session.VoiceSessionType;
import com.deepthoughtnet.clinic.realtime.voice.transcript.VoiceTranscriptRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs for realtime AI voice gateway administration APIs. */
public final class RealtimeVoiceDtos {
    private RealtimeVoiceDtos() {
    }

    public record CreateVoiceSessionRequest(VoiceSessionType sessionType, UUID patientId, UUID leadId, String metadataJson) {}

    public record VoiceTurnRequest(String text, String promptKey, String patientContextJson) {}

    public record VoiceSessionResponse(VoiceSessionRecord session) {}

    public record VoiceSessionsResponse(List<VoiceSessionRecord> sessions) {}

    public record VoiceTurnResponse(VoiceTranscriptRecord userTranscript, VoiceTranscriptRecord aiTranscript,
                                    String escalationReason, String aiProvider, long aiLatencyMs) {}

    public record VoiceSessionEventResponse(UUID id, UUID sessionId, VoiceSessionEventType eventType,
                                            OffsetDateTime eventTimestamp, long sequenceNumber,
                                            String payloadSummary, String correlationId) {}

    public record VoiceSessionEventsResponse(List<VoiceSessionEventResponse> events) {}

    public record VoiceTranscriptsResponse(List<VoiceTranscriptRecord> transcripts) {}

    public record RealtimeVoiceSummaryResponse(long activeSessions, long escalationCount, long failedSessions,
                                               long avgAiLatencyMs, long avgSttLatencyMs, long avgTtsLatencyMs,
                                               long avgTranscriptLatencyMs, long websocketDisconnects, long sttFailures,
                                               long ttsFailures, long interruptionCount,
                                               List<ProviderStatus> sttProviders,
                                               List<ProviderStatus> ttsProviders,
                                               RuntimeStatus runtimeStatus) {}

    public record ProviderStatus(String providerName, boolean ready) {}
    public record RuntimeStatus(String status, boolean sttReady, boolean ttsReady, boolean modelReady,
                                long activeSessions, long uptimeSeconds, String error) {}
}
