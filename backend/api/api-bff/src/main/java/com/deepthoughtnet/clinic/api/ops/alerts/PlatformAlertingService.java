package com.deepthoughtnet.clinic.api.ops.alerts;

import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiInvocationLogRepository;
import com.deepthoughtnet.clinic.api.carepilot.CarePilotAiCallSchedulerMonitor;
import com.deepthoughtnet.clinic.api.carepilot.CarePilotRuntimeSchedulerMonitor;
import com.deepthoughtnet.clinic.api.ops.SchedulerLockMonitor;
import com.deepthoughtnet.clinic.api.ops.db.DeadLetterEventRepository;
import com.deepthoughtnet.clinic.api.ops.db.PlatformAlertEscalationEntity;
import com.deepthoughtnet.clinic.api.ops.db.PlatformAlertEscalationRepository;
import com.deepthoughtnet.clinic.api.ops.db.PlatformAlertRuleEntity;
import com.deepthoughtnet.clinic.api.ops.db.PlatformAlertRuleRepository;
import com.deepthoughtnet.clinic.api.ops.db.PlatformOperationalAlertEntity;
import com.deepthoughtnet.clinic.api.ops.db.PlatformOperationalAlertRepository;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryAttemptRepository;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryEventEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryEventRepository;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates cross-domain operational signals and generates deduplicated platform alerts.
 */
@Service
public class PlatformAlertingService {
    private final PlatformAlertRuleRepository alertRuleRepository;
    private final PlatformOperationalAlertRepository alertRepository;
    private final PlatformAlertEscalationRepository escalationRepository;
    private final CampaignExecutionRepository campaignExecutionRepository;
    private final CampaignDeliveryAttemptRepository deliveryAttemptRepository;
    private final CampaignDeliveryEventRepository deliveryEventRepository;
    private final DeadLetterEventRepository deadLetterRepository;
    private final AiInvocationLogRepository aiInvocationLogRepository;
    private final CarePilotAiCallSchedulerMonitor aiCallSchedulerMonitor;
    private final CarePilotRuntimeSchedulerMonitor reminderSchedulerMonitor;
    private final SchedulerLockMonitor schedulerLockMonitor;

    public PlatformAlertingService(
            PlatformAlertRuleRepository alertRuleRepository,
            PlatformOperationalAlertRepository alertRepository,
            PlatformAlertEscalationRepository escalationRepository,
            CampaignExecutionRepository campaignExecutionRepository,
            CampaignDeliveryAttemptRepository deliveryAttemptRepository,
            CampaignDeliveryEventRepository deliveryEventRepository,
            DeadLetterEventRepository deadLetterRepository,
            AiInvocationLogRepository aiInvocationLogRepository,
            CarePilotAiCallSchedulerMonitor aiCallSchedulerMonitor,
            CarePilotRuntimeSchedulerMonitor reminderSchedulerMonitor,
            SchedulerLockMonitor schedulerLockMonitor
    ) {
        this.alertRuleRepository = alertRuleRepository;
        this.alertRepository = alertRepository;
        this.escalationRepository = escalationRepository;
        this.campaignExecutionRepository = campaignExecutionRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.deliveryEventRepository = deliveryEventRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.aiInvocationLogRepository = aiInvocationLogRepository;
        this.aiCallSchedulerMonitor = aiCallSchedulerMonitor;
        this.reminderSchedulerMonitor = reminderSchedulerMonitor;
        this.schedulerLockMonitor = schedulerLockMonitor;
    }

    /**
     * Evaluates configured rules and emits/updates alerts.
     */
    @Transactional
    public void evaluate(UUID tenantId) {
        ensureDefaultRules();
        List<PlatformAlertRuleEntity> rules = new ArrayList<>(alertRuleRepository.findByTenantIdIsNullAndEnabledTrue());
        rules.addAll(alertRuleRepository.findByTenantIdAndEnabledTrue(tenantId));
        for (PlatformAlertRuleEntity rule : rules) {
            evaluateRule(tenantId, rule);
        }
    }

    @Transactional
    public PlatformOperationalAlertEntity acknowledge(UUID tenantId, UUID id, UUID userId) {
        PlatformOperationalAlertEntity alert = alertRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));
        alert.acknowledge(userId);
        return alertRepository.save(alert);
    }

    @Transactional
    public PlatformOperationalAlertEntity resolve(UUID tenantId, UUID id, UUID userId, String notes) {
        PlatformOperationalAlertEntity alert = alertRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));
        alert.resolve(userId, notes);
        return alertRepository.save(alert);
    }

    @Transactional
    public PlatformOperationalAlertEntity suppress(UUID tenantId, UUID id) {
        PlatformOperationalAlertEntity alert = alertRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));
        alert.suppress();
        return alertRepository.save(alert);
    }

    private void evaluateRule(UUID tenantId, PlatformAlertRuleEntity rule) {
        RuleSignal signal = signalForRule(tenantId, rule);
        String sourceEntityId = signal.sourceEntityId() == null ? rule.getSourceType() : signal.sourceEntityId();
        Optional<PlatformOperationalAlertEntity> existing = alertRepository
                .findFirstByTenantIdAndRuleKeyAndSourceEntityIdAndStatusInOrderByLastSeenAtDesc(
                        tenantId,
                        rule.getRuleKey(),
                        sourceEntityId,
                        List.of(PlatformOperationalAlertEntity.Status.OPEN,
                                PlatformOperationalAlertEntity.Status.ACKNOWLEDGED,
                                PlatformOperationalAlertEntity.Status.SUPPRESSED)
                );

        if (signal.triggered()) {
            if (existing.isPresent()) {
                PlatformOperationalAlertEntity row = existing.get();
                OffsetDateTime cooldownBoundary = row.getLastSeenAt().plusMinutes(rule.getCooldownMinutes());
                if (OffsetDateTime.now().isBefore(cooldownBoundary)) {
                    row.suppress();
                } else {
                    row.reopen(signal.message(), rule.getSeverity());
                }
                row.markRepeated(signal.message(), rule.getSeverity());
                PlatformOperationalAlertEntity saved = alertRepository.save(row);
                if (saved.getOccurrenceCount() >= 3) {
                    escalationRepository.save(PlatformAlertEscalationEntity.raised(saved.getId(), 1, "platform-ops"));
                }
            } else {
                alertRepository.save(PlatformOperationalAlertEntity.open(
                        tenantId,
                        rule.getRuleKey(),
                        rule.getRuleKey(),
                        rule.getSeverity(),
                        rule.getSourceType(),
                        sourceEntityId,
                        signal.correlationId(),
                        signal.message()
                ));
            }
            return;
        }

        if (existing.isPresent() && rule.isAutoResolveEnabled()) {
            PlatformOperationalAlertEntity row = existing.get();
            row.resolve(null, "Auto-resolved by alert engine after recovery.");
            alertRepository.save(row);
        }
    }

    private RuleSignal signalForRule(UUID tenantId, PlatformAlertRuleEntity rule) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime from = now.minusMinutes(30);
        return switch (rule.getRuleKey()) {
            case "QUEUE_BACKLOG_HIGH" -> {
                List<CampaignExecutionEntity> rows = campaignExecutionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
                long backlog = rows.stream().filter(e -> e.getStatus() == ExecutionStatus.QUEUED || e.getStatus() == ExecutionStatus.RETRY_SCHEDULED).count();
                yield new RuleSignal(backlog >= rule.getThresholdValue().longValue(), "campaign-queue", null,
                        "Queue backlog high: " + backlog + " pending/retrying executions");
            }
            case "DLQ_SPIKE" -> {
                long count = deadLetterRepository.findTop200ByTenantIdOrderByDeadLetteredAtDesc(tenantId).stream().filter(d -> d.getDeadLetteredAt().isAfter(from)).count();
                yield new RuleSignal(count >= rule.getThresholdValue().longValue(), "dead-letter", null,
                        "DLQ spike detected: " + count + " dead-letter events in 30m");
            }
            case "RETRY_STORM" -> {
                long retries = campaignExecutionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().filter(e -> e.getAttemptCount() >= 3).count();
                yield new RuleSignal(retries >= rule.getThresholdValue().longValue(), "campaign-retries", null,
                        "Retry storm detected: " + retries + " executions with >=3 attempts");
            }
            case "SCHEDULER_STALLED" -> {
                OffsetDateTime reminderLast = reminderSchedulerMonitor.lastReminderScanAt(tenantId);
                boolean stalled = reminderLast == null || reminderLast.isBefore(now.minusMinutes(rule.getThresholdValue().longValue()));
                yield new RuleSignal(stalled, "carepilot-reminder-scheduler", null,
                        "Reminder scheduler heartbeat stale");
            }
            case "LOCK_CONTENTION" -> {
                Map<String, SchedulerLockMonitor.LockState> locks = schedulerLockMonitor.snapshot();
                long skips = locks.values().stream().mapToLong(SchedulerLockMonitor.LockState::skipCount).sum();
                yield new RuleSignal(skips >= rule.getThresholdValue().longValue(), "scheduler-locks", null,
                        "Lock contention elevated. skipped locks=" + skips);
            }
            case "AI_PROVIDER_DEGRADED" -> {
                var logs = aiInvocationLogRepository.findByTenantIdAndCreatedAtBetween(tenantId, from, now);
                long total = logs.size();
                long failed = logs.stream().filter(l -> !"SUCCESS".equalsIgnoreCase(l.getStatus()) && !"COMPLETED".equalsIgnoreCase(l.getStatus())).count();
                double failPct = total == 0 ? 0 : (failed * 100.0 / total);
                yield new RuleSignal(failPct >= rule.getThresholdValue().doubleValue(), "ai-provider", null,
                        "AI failure rate elevated: " + String.format(java.util.Locale.ROOT, "%.2f", failPct) + "%");
            }
            case "AI_COST_SPIKE" -> {
                var logs = aiInvocationLogRepository.findByTenantIdAndCreatedAtBetween(tenantId, from, now);
                BigDecimal cost = logs.stream().map(l -> l.getEstimatedCost() == null ? BigDecimal.ZERO : l.getEstimatedCost())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                yield new RuleSignal(cost.compareTo(rule.getThresholdValue()) >= 0, "ai-cost", null,
                        "AI cost spike detected. 30m cost=" + cost);
            }
            case "WEBHOOK_FAILURE_STORM" -> {
                List<CampaignDeliveryEventEntity> events = deliveryEventRepository.findByTenantIdAndReceivedAtBetween(tenantId, from, now);
                long failed = events.stream().filter(e -> "WHATSAPP_STATUS_ERROR".equalsIgnoreCase(e.getEventType()) || "SMS_STATUS_ERROR".equalsIgnoreCase(e.getEventType())).count();
                yield new RuleSignal(failed >= rule.getThresholdValue().longValue(), "webhook-delivery", null,
                        "Webhook failure storm: " + failed + " callback failures in 30m");
            }
            default -> new RuleSignal(false, rule.getSourceType(), null, "");
        };
    }

    private void ensureDefaultRules() {
        if (!alertRuleRepository.findByTenantIdIsNullAndEnabledTrue().isEmpty()) {
            return;
        }
        List<PlatformAlertRuleEntity> defaults = List.of(
                PlatformAlertRuleEntity.defaults("QUEUE_BACKLOG_HIGH", "queues", PlatformOperationalAlertEntity.Severity.WARNING,
                        PlatformAlertRuleEntity.ThresholdType.COUNT, BigDecimal.valueOf(50), 10),
                PlatformAlertRuleEntity.defaults("DLQ_SPIKE", "dlq", PlatformOperationalAlertEntity.Severity.CRITICAL,
                        PlatformAlertRuleEntity.ThresholdType.COUNT, BigDecimal.valueOf(10), 10),
                PlatformAlertRuleEntity.defaults("RETRY_STORM", "retries", PlatformOperationalAlertEntity.Severity.CRITICAL,
                        PlatformAlertRuleEntity.ThresholdType.COUNT, BigDecimal.valueOf(20), 10),
                PlatformAlertRuleEntity.defaults("SCHEDULER_STALLED", "scheduler", PlatformOperationalAlertEntity.Severity.CRITICAL,
                        PlatformAlertRuleEntity.ThresholdType.STALENESS, BigDecimal.valueOf(30), 5),
                PlatformAlertRuleEntity.defaults("LOCK_CONTENTION", "locks", PlatformOperationalAlertEntity.Severity.WARNING,
                        PlatformAlertRuleEntity.ThresholdType.COUNT, BigDecimal.valueOf(20), 10),
                PlatformAlertRuleEntity.defaults("AI_PROVIDER_DEGRADED", "ai", PlatformOperationalAlertEntity.Severity.CRITICAL,
                        PlatformAlertRuleEntity.ThresholdType.PERCENTAGE, BigDecimal.valueOf(30), 10),
                PlatformAlertRuleEntity.defaults("AI_COST_SPIKE", "ai", PlatformOperationalAlertEntity.Severity.WARNING,
                        PlatformAlertRuleEntity.ThresholdType.COUNT, BigDecimal.valueOf(5), 10),
                PlatformAlertRuleEntity.defaults("WEBHOOK_FAILURE_STORM", "webhooks", PlatformOperationalAlertEntity.Severity.WARNING,
                        PlatformAlertRuleEntity.ThresholdType.COUNT, BigDecimal.valueOf(10), 10)
        );
        alertRuleRepository.saveAll(defaults);
    }

    private record RuleSignal(boolean triggered, String sourceEntityId, String correlationId, String message) {}
}
