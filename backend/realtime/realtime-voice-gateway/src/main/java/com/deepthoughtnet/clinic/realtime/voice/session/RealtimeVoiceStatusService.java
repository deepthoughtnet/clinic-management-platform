package com.deepthoughtnet.clinic.realtime.voice.session;

import com.deepthoughtnet.clinic.realtime.voice.config.VoiceGatewayProperties;
import com.deepthoughtnet.clinic.realtime.voice.db.VoiceSessionRepository;
import com.deepthoughtnet.clinic.realtime.voice.metrics.RealtimeVoiceGatewayMetrics;
import com.deepthoughtnet.clinic.stt.spi.SpeechToTextProvider;
import com.deepthoughtnet.clinic.tts.spi.TextToSpeechProvider;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/** Read-only operational summary for realtime AI admin console. */
@Service
public class RealtimeVoiceStatusService {
    private final VoiceSessionRepository sessionRepository;
    private final RealtimeVoiceGatewayMetrics metrics;
    private final List<SpeechToTextProvider> sttProviders;
    private final List<TextToSpeechProvider> ttsProviders;
    private final VoiceGatewayProperties properties;
    private final RestTemplate restTemplate;

    public RealtimeVoiceStatusService(
            VoiceSessionRepository sessionRepository,
            RealtimeVoiceGatewayMetrics metrics,
            List<SpeechToTextProvider> sttProviders,
            List<TextToSpeechProvider> ttsProviders,
            VoiceGatewayProperties properties,
            RestTemplateBuilder restTemplateBuilder
    ) {
        this.sessionRepository = sessionRepository;
        this.metrics = metrics;
        this.sttProviders = sttProviders;
        this.ttsProviders = ttsProviders;
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(1500))
                .setReadTimeout(Duration.ofMillis(1500))
                .build();
    }

    public RealtimeVoiceSummary summary(UUID tenantId) {
        long active = metrics.activeSessions();
        long escalated = metrics.escalationCount();
        long failed = metrics.failedSessions();
        RuntimeStatus runtimeStatus = fetchRuntimeStatus();
        return new RealtimeVoiceSummary(
                active,
                escalated,
                failed,
                metrics.avgAiLatencyMs(),
                metrics.avgSttLatencyMs(),
                metrics.avgTtsLatencyMs(),
                metrics.avgTranscriptLatencyMs(),
                metrics.websocketDisconnects(),
                metrics.sttFailures(),
                metrics.ttsFailures(),
                metrics.interruptionCount(),
                sttProviders.stream().map(p -> new ProviderStatus(p.providerName(), p.isReady())).toList(),
                ttsProviders.stream().map(p -> new ProviderStatus(p.providerName(), p.isReady())).toList(),
                runtimeStatus
        );
    }

    private RuntimeStatus fetchRuntimeStatus() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(properties.getRuntime().getSummaryEndpoint(), Map.class);
            if (response == null) {
                return new RuntimeStatus("UNKNOWN", false, false, false, 0, 0, "No response");
            }
            return new RuntimeStatus(
                    String.valueOf(response.getOrDefault("status", "UNKNOWN")),
                    Boolean.parseBoolean(String.valueOf(response.getOrDefault("sttReady", false))),
                    Boolean.parseBoolean(String.valueOf(response.getOrDefault("ttsReady", false))),
                    Boolean.parseBoolean(String.valueOf(response.getOrDefault("modelReady", false))),
                    longValue(response.get("activeSessions")),
                    longValue(response.get("uptimeSeconds")),
                    null
            );
        } catch (Exception ex) {
            return new RuntimeStatus("UNREACHABLE", false, false, false, 0, 0, ex.getMessage());
        }
    }

    private long longValue(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public record ProviderStatus(String providerName, boolean ready) {}

    public record RuntimeStatus(String status, boolean sttReady, boolean ttsReady, boolean modelReady,
                                long activeSessions, long uptimeSeconds, String error) {}

    public record RealtimeVoiceSummary(long activeSessions, long escalationCount, long failedSessions,
                                       long avgAiLatencyMs, long avgSttLatencyMs, long avgTtsLatencyMs,
                                       long avgTranscriptLatencyMs, long websocketDisconnects, long sttFailures,
                                       long ttsFailures, long interruptionCount,
                                       List<ProviderStatus> sttProviders, List<ProviderStatus> ttsProviders,
                                       RuntimeStatus runtimeStatus) {}
}
