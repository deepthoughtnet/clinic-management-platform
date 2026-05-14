package com.deepthoughtnet.clinic.api.ops;

import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiInvocationLogRepository;
import com.deepthoughtnet.clinic.ai.orchestration.platform.service.AiUsageSummaryService;
import com.deepthoughtnet.clinic.api.admin.AdminIntegrationsStatusService;
import com.deepthoughtnet.clinic.api.admin.dto.AdminIntegrationsDtos.IntegrationStatus;
import com.deepthoughtnet.clinic.api.carepilot.CarePilotAiCallSchedulerMonitor;
import com.deepthoughtnet.clinic.api.carepilot.CarePilotRuntimeSchedulerMonitor;
import com.deepthoughtnet.clinic.api.ops.alerts.PlatformAlertingService;
import com.deepthoughtnet.clinic.api.ops.db.DeadLetterEventRepository;
import com.deepthoughtnet.clinic.api.ops.db.PlatformAlertRuleRepository;
import com.deepthoughtnet.clinic.api.ops.db.PlatformOperationalAlertEntity;
import com.deepthoughtnet.clinic.api.ops.db.PlatformOperationalAlertRepository;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.AiMetricsResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.AlertRuleResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.AlertSeverity;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.AlertRulesResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.HealthStatus;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.OperationalAlertResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformHealthResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.ProviderMetricItem;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.ProviderMetricsResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.ProviderSloItem;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.ProviderSlosResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.QueueMetricsItem;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.QueueMetricsResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.RuntimeSummaryResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.SchedulerStatusItem;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.SchedulerStatusResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.WebhookMetricsResponse;
import com.deepthoughtnet.clinic.carepilot.ai_call.db.AiCallExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallExecutionStatus;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryAttemptEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryAttemptRepository;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryEventRepository;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aggregates tenant-scoped operational observability views for platform ops endpoints. */
@Service
public class PlatformOpsService {
    private final CarePilotAiCallSchedulerMonitor aiCallSchedulerMonitor;
    private final CarePilotRuntimeSchedulerMonitor reminderSchedulerMonitor;
    private final AdminIntegrationsStatusService integrationsStatusService;
    private final AiUsageSummaryService aiUsageSummaryService;
    private final CampaignExecutionRepository campaignExecutionRepository;
    private final AiCallExecutionRepository aiCallExecutionRepository;
    private final CampaignDeliveryAttemptRepository deliveryAttemptRepository;
    private final CampaignDeliveryEventRepository deliveryEventRepository;
    private final PlatformOperationalAlertRepository alertRepository;
    private final SchedulerLockMonitor schedulerLockMonitor;
    private final DeadLetterEventRepository deadLetterRepository;
    private final PlatformAlertingService alertingService;
    private final PlatformAlertRuleRepository alertRuleRepository;
    private final AiInvocationLogRepository aiInvocationLogRepository;

    public PlatformOpsService(
            CarePilotAiCallSchedulerMonitor aiCallSchedulerMonitor,
            CarePilotRuntimeSchedulerMonitor reminderSchedulerMonitor,
            AdminIntegrationsStatusService integrationsStatusService,
            AiUsageSummaryService aiUsageSummaryService,
            CampaignExecutionRepository campaignExecutionRepository,
            AiCallExecutionRepository aiCallExecutionRepository,
            CampaignDeliveryAttemptRepository deliveryAttemptRepository,
            CampaignDeliveryEventRepository deliveryEventRepository,
            PlatformOperationalAlertRepository alertRepository,
            SchedulerLockMonitor schedulerLockMonitor,
            DeadLetterEventRepository deadLetterRepository,
            PlatformAlertingService alertingService,
            PlatformAlertRuleRepository alertRuleRepository,
            AiInvocationLogRepository aiInvocationLogRepository
    ) {
        this.aiCallSchedulerMonitor = aiCallSchedulerMonitor;
        this.reminderSchedulerMonitor = reminderSchedulerMonitor;
        this.integrationsStatusService = integrationsStatusService;
        this.aiUsageSummaryService = aiUsageSummaryService;
        this.campaignExecutionRepository = campaignExecutionRepository;
        this.aiCallExecutionRepository = aiCallExecutionRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.deliveryEventRepository = deliveryEventRepository;
        this.alertRepository = alertRepository;
        this.schedulerLockMonitor = schedulerLockMonitor;
        this.deadLetterRepository = deadLetterRepository;
        this.alertingService = alertingService;
        this.alertRuleRepository = alertRuleRepository;
        this.aiInvocationLogRepository = aiInvocationLogRepository;
    }

    public PlatformHealthResponse platformHealth(UUID tenantId) {
        SchedulerStatusResponse schedulers = schedulers(tenantId);
        QueueMetricsResponse queues = queues(tenantId);
        ProviderMetricsResponse providers = providers(tenantId);
        AiMetricsResponse aiMetrics = aiMetrics(tenantId);
        WebhookMetricsResponse webhookMetrics = webhooks(tenantId);

        List<String> degraded = new ArrayList<>();
        if (providers.providers().stream().anyMatch(p -> p.status() == IntegrationStatus.ERROR)) degraded.add("provider-errors");
        if (queues.queues().stream().anyMatch(q -> q.failed() > 0 || q.stale() > 0)) degraded.add("queue-failures");
        if (webhookMetrics.failedWebhookProcessingCount() > 0 || webhookMetrics.invalidSignatureCount() > 0) degraded.add("webhook-processing");
        if (schedulers.schedulers().stream().anyMatch(s -> s.failureCount() > 0)) degraded.add("scheduler-failures");
        HealthStatus status = degraded.isEmpty() ? HealthStatus.HEALTHY : HealthStatus.DEGRADED;

        return new PlatformHealthResponse(
                status,
                List.copyOf(degraded),
                schedulers.schedulers().stream().filter(s -> "carepilot-reminder-scheduler".equals(s.schedulerName())).findFirst().orElse(null),
                schedulers.schedulers().stream().filter(s -> "ai-call-dispatch-scheduler".equals(s.schedulerName())).findFirst().orElse(null),
                queues,
                providers,
                aiMetrics,
                webhookMetrics,
                true,
                providers.providers().stream().noneMatch(p -> p.status() == IntegrationStatus.ERROR)
        );
    }

    public SchedulerStatusResponse schedulers(UUID tenantId) {
        return new SchedulerStatusResponse(List.of(
                new SchedulerStatusItem("carepilot-reminder-scheduler", "ENABLED".equalsIgnoreCase(reminderSchedulerMonitor.reminderSchedulerStatus()),
                        reminderSchedulerMonitor.lastReminderScanAt(tenantId), null,
                        reminderSchedulerMonitor.lastReminderScanAt(tenantId) == null ? 0 : 1, 0, 0, 0, null,
                        lockState("carepilot-reminder-scheduler").lastAcquiredAt(),
                        lockState("carepilot-reminder-scheduler").lastSkippedAt(),
                        lockState("carepilot-reminder-scheduler").acquireCount(),
                        lockState("carepilot-reminder-scheduler").skipCount()),
                new SchedulerStatusItem("ai-call-dispatch-scheduler", aiCallSchedulerMonitor.enabled(),
                        aiCallSchedulerMonitor.lastRunAt(), aiCallSchedulerMonitor.nextEstimatedRunAt(),
                        aiCallSchedulerMonitor.lastProcessedCount(), aiCallSchedulerMonitor.lastFailedCount(),
                        aiCallSchedulerMonitor.lastSkippedCount(), aiCallSchedulerMonitor.lastDurationMs(),
                        aiCallSchedulerMonitor.lastFailedCount() > 0 ? "Last run had failed dispatches." : null,
                        lockState("ai-call-dispatch-scheduler").lastAcquiredAt(),
                        lockState("ai-call-dispatch-scheduler").lastSkippedAt(),
                        lockState("ai-call-dispatch-scheduler").acquireCount(),
                        lockState("ai-call-dispatch-scheduler").skipCount()),
                new SchedulerStatusItem("carepilot-campaign-execution-scheduler", true, null, null, 0, 0, 0, 0, null,
                        lockState("carepilot-campaign-execution-scheduler").lastAcquiredAt(),
                        lockState("carepilot-campaign-execution-scheduler").lastSkippedAt(),
                        lockState("carepilot-campaign-execution-scheduler").acquireCount(),
                        lockState("carepilot-campaign-execution-scheduler").skipCount()),
                new SchedulerStatusItem("ai-call-reconciliation-scheduler", true, null, null, 0, 0, 0, 0, null,
                        lockState("ai-call-reconciliation-scheduler").lastAcquiredAt(),
                        lockState("ai-call-reconciliation-scheduler").lastSkippedAt(),
                        lockState("ai-call-reconciliation-scheduler").acquireCount(),
                        lockState("ai-call-reconciliation-scheduler").skipCount())
        ));
    }

    public QueueMetricsResponse queues(UUID tenantId) {
        List<CampaignExecutionEntity> rows = campaignExecutionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        QueueMetricsItem campaignQueue = new QueueMetricsItem(
                "campaign-executions",
                rows.size(),
                count(rows, ExecutionStatus.QUEUED),
                count(rows, ExecutionStatus.RETRY_SCHEDULED),
                count(rows, ExecutionStatus.FAILED, ExecutionStatus.DEAD_LETTER),
                count(rows, ExecutionStatus.PROCESSING),
                rows.stream().filter(r -> r.getStatus() == ExecutionStatus.PROCESSING && r.getUpdatedAt() != null
                        && r.getUpdatedAt().isBefore(OffsetDateTime.now().minusMinutes(30))).count(),
                rows.stream().filter(r -> r.getStatus() == ExecutionStatus.RETRY_SCHEDULED && r.getAttemptCount() >= 2).count(),
                count(rows, ExecutionStatus.SUPPRESSED)
        );
        QueueMetricsItem aiQueue = new QueueMetricsItem(
                "ai-call-executions",
                aiCallExecutionRepository.countByTenantId(tenantId),
                aiCallExecutionRepository.countByTenantIdAndStatuses(tenantId, List.of(AiCallExecutionStatus.PENDING, AiCallExecutionStatus.QUEUED)),
                0,
                aiCallExecutionRepository.countByTenantIdAndStatuses(tenantId, List.of(AiCallExecutionStatus.FAILED)),
                aiCallExecutionRepository.countByTenantIdAndStatuses(tenantId, List.of(AiCallExecutionStatus.DIALING, AiCallExecutionStatus.IN_PROGRESS)),
                aiCallExecutionRepository.countByTenantIdAndStatuses(tenantId, List.of(AiCallExecutionStatus.NO_ANSWER, AiCallExecutionStatus.BUSY)),
                aiCallExecutionRepository.countByTenantIdAndStatuses(tenantId, List.of(AiCallExecutionStatus.SKIPPED)),
                aiCallExecutionRepository.countByTenantIdAndStatuses(tenantId, List.of(AiCallExecutionStatus.SUPPRESSED))
        );
        return new QueueMetricsResponse(List.of(campaignQueue, aiQueue));
    }

    public ProviderMetricsResponse providers(UUID tenantId) {
        var integrationRows = integrationsStatusService.status(tenantId);
        OffsetDateTime from = OffsetDateTime.now().minusDays(1);
        List<CampaignDeliveryAttemptEntity> attempts = deliveryAttemptRepository.findByTenantIdAndAttemptedAtBetween(tenantId, from, OffsetDateTime.now());
        long success = attempts.stream().filter(a -> a.getDeliveryStatus() == MessageDeliveryStatus.SENT || a.getDeliveryStatus() == MessageDeliveryStatus.DELIVERED).count();
        long failed = attempts.stream().filter(a -> a.getDeliveryStatus() == MessageDeliveryStatus.FAILED).count();

        List<ProviderMetricItem> rows = integrationRows.stream().map(row -> new ProviderMetricItem(
                row.key(), row.name(), row.category(), row.status(), row.enabled(), row.configured(), row.providerName(),
                "MESSAGING".equals(row.category()) ? success : 0,
                "MESSAGING".equals(row.category()) ? failed : 0,
                attempts.stream().filter(a -> "TIMEOUT".equalsIgnoreCase(a.getErrorCode())).count(),
                0,
                failed > 0 && "MESSAGING".equals(row.category()) ? "Delivery failures detected in attempt logs." : null
        )).toList();
        return new ProviderMetricsResponse(rows);
    }

    public ProviderSlosResponse providerSlos(UUID tenantId) {
        OffsetDateTime from = OffsetDateTime.now().minusDays(1);
        List<CampaignDeliveryAttemptEntity> attempts = deliveryAttemptRepository.findByTenantIdAndAttemptedAtBetween(tenantId, from, OffsetDateTime.now());
        var grouped = attempts.stream().collect(java.util.stream.Collectors.groupingBy(a -> a.getProviderName() == null ? "UNKNOWN" : a.getProviderName()));
        List<ProviderSloItem> rows = new ArrayList<>();
        grouped.forEach((provider, list) -> {
            long total = list.size();
            long success = list.stream().filter(a -> a.getDeliveryStatus() == MessageDeliveryStatus.SENT || a.getDeliveryStatus() == MessageDeliveryStatus.DELIVERED).count();
            long failed = list.stream().filter(a -> a.getDeliveryStatus() == MessageDeliveryStatus.FAILED).count();
            long retries = list.stream().filter(a -> a.getAttemptNumber() > 1).count();
            long timeouts = list.stream().filter(a -> "TIMEOUT".equalsIgnoreCase(a.getErrorCode())).count();
            long callbackCount = deliveryEventRepository.findByTenantIdAndReceivedAtBetween(tenantId, from, OffsetDateTime.now()).stream().filter(e -> provider.equalsIgnoreCase(e.getProviderName())).count();
            double successRate = total == 0 ? 0.0 : success * 100.0 / total;
            double timeoutRate = total == 0 ? 0.0 : timeouts * 100.0 / total;
            double retryRate = total == 0 ? 0.0 : retries * 100.0 / total;
            rows.add(new ProviderSloItem(provider, "MESSAGING", total, callbackCount, retries, failed, timeouts, 0,
                    successRate, timeoutRate, retryRate, 0, successRate < 95.0, failed > Math.max(5, total / 5)));
        });

        var aiLogs = aiInvocationLogRepository.findByTenantIdAndCreatedAtBetween(tenantId, from, OffsetDateTime.now());
        if (!aiLogs.isEmpty()) {
            long total = aiLogs.size();
            long failed = aiLogs.stream().filter(l -> !"SUCCESS".equalsIgnoreCase(l.getStatus()) && !"COMPLETED".equalsIgnoreCase(l.getStatus())).count();
            long latency = aiLogs.stream().mapToLong(l -> l.getLatencyMs() == null ? 0L : l.getLatencyMs()).sum();
            rows.add(new ProviderSloItem("AI", "AI_PROVIDER", total, 0, 0, failed, 0, total == 0 ? 0 : latency / total,
                    total == 0 ? 0 : (total - failed) * 100.0 / total, 0.0, 0.0, 0, false, failed > Math.max(3, total / 4)));
        }
        return new ProviderSlosResponse(rows.stream().sorted(java.util.Comparator.comparing(ProviderSloItem::provider)).toList());
    }

    public AiMetricsResponse aiMetrics(UUID tenantId) {
        OffsetDateTime now = OffsetDateTime.now();
        var usage = aiUsageSummaryService.summarize(tenantId, now.minusDays(30), now, null, null);
        return new AiMetricsResponse(usage.totalCalls(), usage.successfulCalls(), usage.failedCalls(),
                usage.inputTokens(), usage.outputTokens(), usage.estimatedCost(), usage.avgLatencyMs(),
                usage.callsByProvider(), usage.callsByUseCase(), usage.callsByStatus());
    }

    public WebhookMetricsResponse webhooks(UUID tenantId) {
        OffsetDateTime from = OffsetDateTime.now().minusDays(1);
        var events = deliveryEventRepository.findByTenantIdAndReceivedAtBetween(tenantId, from, OffsetDateTime.now());
        long incoming = events.size();
        long failed = events.stream().filter(e -> "WHATSAPP_STATUS_ERROR".equalsIgnoreCase(e.getEventType()) || "SMS_STATUS_ERROR".equalsIgnoreCase(e.getEventType())).count();
        long retries = events.stream().filter(e -> e.getEventType().toUpperCase().contains("RETRY")).count();
        long stale = events.stream().filter(e -> e.getEventTimestamp() != null && e.getReceivedAt() != null && e.getReceivedAt().minusMinutes(30).isAfter(e.getEventTimestamp())).count();
        long callbackFailures = failed;
        long unknownProvider = events.stream().filter(e -> e.getProviderName() == null || e.getProviderName().isBlank()).count();
        long replayAttempts = events.stream().collect(java.util.stream.Collectors.groupingBy(e -> (e.getProviderName() + ":" + e.getProviderMessageId()))).values().stream().filter(l -> l.size() > 2).count();
        long dlqWebhookFailures = deadLetterRepository.findTop200ByTenantIdOrderByDeadLetteredAtDesc(tenantId).stream().filter(d -> d.getSourceType().name().contains("CAMPAIGN")).count();
        long avgLatencyMs = events.stream().filter(e -> e.getEventTimestamp() != null && e.getReceivedAt() != null)
                .mapToLong(e -> java.time.Duration.between(e.getEventTimestamp(), e.getReceivedAt()).toMillis()).average().orElse(0);
        return new WebhookMetricsResponse(incoming, failed, 0, retries, stale, callbackFailures, replayAttempts, unknownProvider, dlqWebhookFailures, avgLatencyMs);
    }

    @Transactional
    public List<OperationalAlertResponse> alerts(UUID tenantId) {
        alertingService.evaluate(tenantId);
        return alertRepository.findTop100ByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(this::toAlert).toList();
    }

    public AlertRulesResponse alertRules(UUID tenantId) {
        List<AlertRuleResponse> rules = new ArrayList<>();
        alertRuleRepository.findByTenantIdIsNullAndEnabledTrue().forEach(r -> rules.add(new AlertRuleResponse(r.getId(), r.getTenantId(), r.getRuleKey(), r.getSourceType(), r.isEnabled(),
                r.getSeverity() == PlatformOperationalAlertEntity.Severity.CRITICAL ? AlertSeverity.CRITICAL : AlertSeverity.WARNING,
                r.getThresholdType(), r.getThresholdValue(), r.getCooldownMinutes(), r.isAutoResolveEnabled())));
        alertRuleRepository.findByTenantIdAndEnabledTrue(tenantId).forEach(r -> rules.add(new AlertRuleResponse(r.getId(), r.getTenantId(), r.getRuleKey(), r.getSourceType(), r.isEnabled(),
                r.getSeverity() == PlatformOperationalAlertEntity.Severity.CRITICAL ? AlertSeverity.CRITICAL : AlertSeverity.WARNING,
                r.getThresholdType(), r.getThresholdValue(), r.getCooldownMinutes(), r.isAutoResolveEnabled())));
        return new AlertRulesResponse(rules);
    }

    public RuntimeSummaryResponse runtimeSummary(UUID tenantId) {
        QueueMetricsResponse queues = queues(tenantId);
        WebhookMetricsResponse webhookMetrics = webhooks(tenantId);
        long failedCount = queues.queues().stream().mapToLong(QueueMetricsItem::failed).sum() + webhookMetrics.failedWebhookProcessingCount();
        long retryStorm = webhookMetrics.retryProcessingCount() + queues.queues().stream().mapToLong(QueueMetricsItem::retrying).sum();
        long repeatedProviderFailures = providers(tenantId).providers().stream().mapToLong(ProviderMetricItem::failureCount).sum();
        long staleExecutions = queues.queues().stream().mapToLong(QueueMetricsItem::stale).sum();
        List<String> notes = new ArrayList<>();
        if (failedCount > 0) notes.add("Recent failures detected in dispatch/delivery pipeline.");
        if (retryStorm > 0) notes.add("Retry pressure is elevated.");
        return new RuntimeSummaryResponse(failedCount, retryStorm, repeatedProviderFailures, staleExecutions, List.copyOf(notes));
    }

    public RuntimeSummaryResponse runtimeErrors(UUID tenantId) { return runtimeSummary(tenantId); }
    public RuntimeSummaryResponse runtimeFailures(UUID tenantId) { return runtimeSummary(tenantId); }

    private long count(List<CampaignExecutionEntity> rows, ExecutionStatus... statuses) {
        List<ExecutionStatus> target = List.of(statuses);
        return rows.stream().filter(s -> target.contains(s.getStatus())).count();
    }

    private SchedulerLockMonitor.LockState lockState(String schedulerName) {
        return schedulerLockMonitor.snapshot().getOrDefault(schedulerName, new SchedulerLockMonitor.LockState(schedulerName, null, null, 0, 0));
    }

    private OperationalAlertResponse toAlert(PlatformOperationalAlertEntity a) {
        return new OperationalAlertResponse(
                a.getId(), a.getTenantId(), a.getRuleKey(), a.getCorrelationId(), a.getSourceEntityId(),
                a.getAlertType(), a.getSeverity() == PlatformOperationalAlertEntity.Severity.CRITICAL ? AlertSeverity.CRITICAL : AlertSeverity.WARNING,
                a.getSource(), a.getMessage(), a.getStatus().name(), a.getOccurrenceCount(), a.getFirstSeenAt(), a.getLastSeenAt(), a.getCreatedAt(),
                a.getAcknowledgedBy(), a.getAcknowledgedAt(), a.getResolvedBy(), a.getResolutionNotes(), a.getResolvedAt());
    }

    @Transactional
    public OperationalAlertResponse acknowledgeAlert(UUID tenantId, UUID id, UUID actor) { return toAlert(alertingService.acknowledge(tenantId, id, actor)); }
    @Transactional
    public OperationalAlertResponse resolveAlert(UUID tenantId, UUID id, UUID actor, String notes) { return toAlert(alertingService.resolve(tenantId, id, actor, notes)); }
    @Transactional
    public OperationalAlertResponse suppressAlert(UUID tenantId, UUID id) { return toAlert(alertingService.suppress(tenantId, id)); }
}
