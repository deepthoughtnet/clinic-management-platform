package com.deepthoughtnet.clinic.api.ops.dto;

import com.deepthoughtnet.clinic.api.admin.dto.AdminIntegrationsDtos.IntegrationStatus;
import com.deepthoughtnet.clinic.api.ops.db.PlatformAlertRuleEntity.ThresholdType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTOs for platform observability and monitoring endpoints. */
public final class PlatformOpsDtos {
    private PlatformOpsDtos() {
    }

    public enum HealthStatus { HEALTHY, DEGRADED, WARNING, CRITICAL }
    public enum AlertSeverity { WARNING, CRITICAL }

    public record PlatformHealthResponse(HealthStatus overallStatus, List<String> degradedServices,
                                         SchedulerStatusItem reminderScheduler, SchedulerStatusItem aiCallScheduler,
                                         QueueMetricsResponse queueMetrics, ProviderMetricsResponse providerMetrics,
                                         AiMetricsResponse aiMetrics, WebhookMetricsResponse webhookMetrics,
                                         boolean databaseHealthy, boolean integrationsReady) {}

    public record SchedulerStatusItem(String schedulerName, boolean enabled, OffsetDateTime lastRunAt,
                                      OffsetDateTime nextRunEstimate, long executionCount, long failureCount,
                                      long skippedCount, long avgExecutionTimeMs, String lastFailureMessage,
                                      OffsetDateTime lockLastAcquiredAt, OffsetDateTime lockLastSkippedAt,
                                      long lockAcquireCount, long lockSkipCount) {}

    public record SchedulerStatusResponse(List<SchedulerStatusItem> schedulers) {}

    public record QueueMetricsItem(String queueName, long queueSize, long pending, long retrying, long failed,
                                   long processing, long stale, long throttled, long suppressed) {}

    public record QueueMetricsResponse(List<QueueMetricsItem> queues) {}

    public record ProviderMetricItem(String key, String name, String category, IntegrationStatus status,
                                     boolean enabled, boolean configured, String providerName,
                                     long successCount, long failureCount, long timeoutCount,
                                     long avgLatencyMs, String lastFailure) {}

    public record ProviderSloItem(String provider, String providerType, long attempts, long webhookCallbacks,
                                  long retries, long failures, long timeouts, long avgLatencyMs,
                                  double successRatePct, double timeoutRatePct, double retryRatePct,
                                  long failoverUsageCount, boolean deliverySlaBreached, boolean providerDegraded) {}

    public record ProviderMetricsResponse(List<ProviderMetricItem> providers) {}
    public record ProviderSlosResponse(List<ProviderSloItem> providers) {}

    public record AiMetricsResponse(long totalCalls, long successfulCalls, long failedCalls,
                                    long inputTokens, long outputTokens, BigDecimal estimatedCost,
                                    long avgLatencyMs, Map<String, Long> callsByProvider,
                                    Map<String, Long> callsByUseCase, Map<String, Long> callsByStatus) {}

    public record WebhookMetricsResponse(long incomingWebhookCount, long failedWebhookProcessingCount,
                                         long invalidSignatureCount, long retryProcessingCount,
                                         long staleWebhookCount, long providerCallbackFailureCount,
                                         long replayAttemptCount, long unknownProviderPayloadCount,
                                         long dlqWebhookFailures, long avgProcessingLatencyMs) {}

    public record OperationalAlertResponse(UUID id, UUID tenantId, String ruleKey, String correlationId,
                                           String sourceEntityId, String alertType, AlertSeverity severity,
                                           String source, String message, String status, int occurrenceCount,
                                           OffsetDateTime firstSeenAt, OffsetDateTime lastSeenAt,
                                           OffsetDateTime createdAt, UUID acknowledgedBy,
                                           OffsetDateTime acknowledgedAt, UUID resolvedBy,
                                           String resolutionNotes, OffsetDateTime resolvedAt) {}

    public record OperationalAlertsResponse(List<OperationalAlertResponse> alerts) {}

    public record AlertRuleResponse(UUID id, UUID tenantId, String ruleKey, String sourceType, boolean enabled,
                                    AlertSeverity severity, ThresholdType thresholdType,
                                    BigDecimal thresholdValue, int cooldownMinutes,
                                    boolean autoResolveEnabled) {}

    public record AlertRulesResponse(List<AlertRuleResponse> rules) {}

    public record RuntimeSummaryResponse(long recentFailures, long retryStormSignals,
                                         long repeatedProviderFailures, long staleExecutions,
                                         List<String> notes) {}

    public record DeadLetterRow(UUID id, UUID tenantId, String sourceType, UUID sourceExecutionId,
                                String failureReason, String payloadSummary, int retryCount,
                                OffsetDateTime deadLetteredAt, String recoveryStatus,
                                String lastRecoveryError) {}

    public record DeadLetterResponse(List<DeadLetterRow> items) {}

    public record AlertActionRequest(String notes) {}
}
