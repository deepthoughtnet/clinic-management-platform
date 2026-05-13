package com.deepthoughtnet.clinic.carepilot.analytics.service;

import com.deepthoughtnet.clinic.carepilot.analytics.service.model.CampaignExecutionBreakdownRecord;
import com.deepthoughtnet.clinic.carepilot.analytics.service.model.CarePilotAnalyticsSummaryRecord;
import com.deepthoughtnet.clinic.carepilot.analytics.service.model.CarePilotExecutionTimelineRecord;
import com.deepthoughtnet.clinic.carepilot.analytics.service.model.ExecutionTimelineEventRecord;
import com.deepthoughtnet.clinic.carepilot.analytics.service.model.ProviderFailureSummaryRecord;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignStatus;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryAttemptEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryAttemptRepository;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryEventEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryEventRepository;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus;
import com.deepthoughtnet.clinic.carepilot.execution.service.model.CampaignDeliveryAttemptRecord;
import com.deepthoughtnet.clinic.carepilot.analytics.service.model.CampaignDeliveryEventRecord;
import com.deepthoughtnet.clinic.carepilot.execution.service.model.CampaignExecutionRecord;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.carepilot.shared.util.CarePilotValidators;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides tenant-scoped read-only analytics and operational insights for CarePilot.
 */
@Service
public class CarePilotAnalyticsService {
    private static final int RECENT_LIMIT = 10;
    private static final Collection<ExecutionStatus> FAILED_STATUSES = List.of(ExecutionStatus.FAILED, ExecutionStatus.DEAD_LETTER);

    private final CampaignRepository campaignRepository;
    private final CampaignExecutionRepository executionRepository;
    private final CampaignDeliveryAttemptRepository attemptRepository;
    private final CampaignDeliveryEventRepository eventRepository;

    public CarePilotAnalyticsService(
            CampaignRepository campaignRepository,
            CampaignExecutionRepository executionRepository,
            CampaignDeliveryAttemptRepository attemptRepository,
            CampaignDeliveryEventRepository eventRepository
    ) {
        this.campaignRepository = campaignRepository;
        this.executionRepository = executionRepository;
        this.attemptRepository = attemptRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * Returns execution and delivery analytics for the provided tenant and date window.
     */
    @Transactional(readOnly = true)
    public CarePilotAnalyticsSummaryRecord summary(UUID tenantId, LocalDate startDate, LocalDate endDate, UUID campaignId) {
        CarePilotValidators.requireTenant(tenantId);
        DateWindow window = resolveWindow(startDate, endDate);

        List<CampaignEntity> campaigns = campaignRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        Map<UUID, CampaignEntity> campaignById = campaigns.stream().collect(Collectors.toMap(CampaignEntity::getId, Function.identity()));

        List<CampaignExecutionEntity> executionRows = campaignId == null
                ? executionRepository.findByTenantIdAndScheduledAtBetweenOrderByScheduledAtDesc(tenantId, window.from(), window.to())
                : executionRepository.findByTenantIdAndCampaignIdAndScheduledAtBetweenOrderByScheduledAtDesc(tenantId, campaignId, window.from(), window.to());

        List<CampaignExecutionRecord> executions = executionRows.stream().map(this::toExecutionRecord).toList();

        long totalExecutions = executions.size();
        long successful = countByStatus(executions, ExecutionStatus.SUCCEEDED);
        long failed = executions.stream().filter(e -> FAILED_STATUSES.contains(e.status())).count();
        long retrying = countByStatus(executions, ExecutionStatus.RETRY_SCHEDULED);
        long pending = executions.stream().filter(e -> e.status() == ExecutionStatus.QUEUED || e.status() == ExecutionStatus.PROCESSING).count();
        long scheduled = executions.stream().filter(e -> e.status() == ExecutionStatus.QUEUED && e.scheduledAt() != null && e.scheduledAt().isAfter(OffsetDateTime.now())).count();
        long skipped = executions.stream().filter(e -> e.deliveryStatus() == MessageDeliveryStatus.SKIPPED).count();
        long delivered = executions.stream().filter(e -> e.deliveryStatus() == MessageDeliveryStatus.DELIVERED).count();
        long read = executions.stream().filter(e -> e.deliveryStatus() == MessageDeliveryStatus.READ).count();
        long bounced = executions.stream().filter(e -> e.deliveryStatus() == MessageDeliveryStatus.BOUNCED).count();
        long undelivered = executions.stream().filter(e -> e.deliveryStatus() == MessageDeliveryStatus.UNDELIVERED).count();

        Map<String, Long> byStatus = executions.stream().collect(Collectors.groupingBy(e -> e.status().name(), LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> byChannel = executions.stream().collect(Collectors.groupingBy(e -> e.channelType().name(), LinkedHashMap::new, Collectors.counting()));

        List<CampaignExecutionBreakdownRecord> byCampaign = executions.stream()
                .collect(Collectors.groupingBy(CampaignExecutionRecord::campaignId, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    List<CampaignExecutionRecord> rows = entry.getValue();
                    long rowTotal = rows.size();
                    long rowSuccess = rows.stream().filter(r -> r.status() == ExecutionStatus.SUCCEEDED).count();
                    long rowFailed = rows.stream().filter(r -> FAILED_STATUSES.contains(r.status())).count();
                    CampaignEntity campaign = campaignById.get(entry.getKey());
                    return new CampaignExecutionBreakdownRecord(
                            entry.getKey(),
                            campaign == null ? entry.getKey().toString() : campaign.getName(),
                            rowTotal,
                            rowSuccess,
                            rowFailed,
                            percentage(rowSuccess, rowTotal)
                    );
                })
                .sorted(Comparator.comparingLong(CampaignExecutionBreakdownRecord::totalExecutions).reversed())
                .toList();

        List<UUID> executionIds = executionRows.stream().map(CampaignExecutionEntity::getId).toList();
        List<ProviderFailureSummaryRecord> providerFailures = executionIds.isEmpty()
                ? List.of()
                : attemptRepository.findByTenantIdAndExecutionIdInOrderByAttemptedAtDesc(tenantId, executionIds).stream()
                        .filter(a -> a.getDeliveryStatus() == MessageDeliveryStatus.FAILED
                                || a.getDeliveryStatus() == MessageDeliveryStatus.UNDELIVERED
                                || a.getDeliveryStatus() == MessageDeliveryStatus.BOUNCED
                                || a.getDeliveryStatus() == MessageDeliveryStatus.PROVIDER_NOT_AVAILABLE)
                        .collect(Collectors.groupingBy(a -> normalizeProvider(a.getProviderName()), Collectors.counting()))
                        .entrySet().stream()
                        .map(entry -> new ProviderFailureSummaryRecord(entry.getKey(), entry.getValue()))
                        .sorted(Comparator.comparingLong(ProviderFailureSummaryRecord::failureCount).reversed())
                        .toList();

        List<CampaignExecutionRecord> recentFailures = executions.stream()
                .filter(e -> FAILED_STATUSES.contains(e.status()) || e.status() == ExecutionStatus.RETRY_SCHEDULED)
                .sorted(Comparator.comparing(CampaignExecutionRecord::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(RECENT_LIMIT)
                .toList();

        List<CampaignExecutionRecord> recentSuccesses = executions.stream()
                .filter(e -> e.status() == ExecutionStatus.SUCCEEDED)
                .sorted(Comparator.comparing(CampaignExecutionRecord::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(RECENT_LIMIT)
                .toList();

        return new CarePilotAnalyticsSummaryRecord(
                window.startDate(),
                window.endDate(),
                campaigns.size(),
                campaigns.stream().filter(c -> c.getStatus() == CampaignStatus.ACTIVE || c.isActive()).count(),
                totalExecutions,
                pending,
                scheduled,
                successful,
                failed,
                retrying,
                skipped,
                delivered,
                read,
                bounced,
                undelivered,
                percentage(successful, totalExecutions),
                percentage(failed, totalExecutions),
                percentage(retrying, totalExecutions),
                byStatus,
                byChannel,
                byCampaign,
                providerFailures,
                recentFailures,
                recentSuccesses
        );
    }

    /**
     * Lists failed or dead-letter executions with optional operational filters.
     */
    @Transactional(readOnly = true)
    public List<CampaignExecutionRecord> listFailedExecutions(
            UUID tenantId,
            LocalDate startDate,
            LocalDate endDate,
            UUID campaignId,
            ChannelType channel,
            ExecutionStatus status,
            String providerName,
            boolean retryableOnly
    ) {
        CarePilotValidators.requireTenant(tenantId);
        DateWindow window = resolveWindow(startDate, endDate);
        Collection<ExecutionStatus> statuses = status == null ? FAILED_STATUSES : List.of(status);

        List<CampaignExecutionEntity> rows = campaignId == null
                ? executionRepository.findByTenantIdAndStatusInAndScheduledAtBetweenOrderByUpdatedAtDesc(tenantId, statuses, window.from(), window.to())
                : executionRepository.findByTenantIdAndCampaignIdAndStatusInAndScheduledAtBetweenOrderByUpdatedAtDesc(tenantId, campaignId, statuses, window.from(), window.to());

        return rows.stream()
                .map(this::toExecutionRecord)
                .filter(e -> channel == null || e.channelType() == channel)
                .filter(e -> providerName == null || providerName.isBlank() || Objects.equals(normalizeProvider(e.providerName()), normalizeProvider(providerName)))
                .filter(e -> !retryableOnly || e.status() == ExecutionStatus.FAILED)
                .toList();
    }

    /**
     * Returns a timeline view for one execution including delivery-attempt history.
     */
    @Transactional(readOnly = true)
    public CarePilotExecutionTimelineRecord timeline(UUID tenantId, UUID executionId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(executionId, "executionId");

        CampaignExecutionEntity execution = executionRepository.findByTenantIdAndId(tenantId, executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found"));

        List<CampaignDeliveryAttemptRecord> attempts = attemptRepository
                .findByTenantIdAndExecutionIdOrderByAttemptNumberDesc(tenantId, executionId)
                .stream()
                .map(this::toAttemptRecord)
                .toList();
        List<CampaignDeliveryEventRecord> deliveryEvents = eventRepository
                .findByTenantIdAndExecutionIdOrderByEventTimestampAsc(tenantId, executionId)
                .stream()
                .map(this::toEventRecord)
                .toList();

        List<ExecutionTimelineEventRecord> events = new ArrayList<>();
        events.add(new ExecutionTimelineEventRecord("EXECUTION_CREATED", ExecutionStatus.QUEUED.name(), "Execution queued", execution.getCreatedAt()));
        if (execution.getLastAttemptAt() != null) {
            events.add(new ExecutionTimelineEventRecord("LAST_ATTEMPT", execution.getStatus().name(), execution.getFailureReason(), execution.getLastAttemptAt()));
        }
        if (execution.getExecutedAt() != null) {
            events.add(new ExecutionTimelineEventRecord("EXECUTED", ExecutionStatus.SUCCEEDED.name(), "Execution completed", execution.getExecutedAt()));
        }
        if (execution.getNextAttemptAt() != null) {
            events.add(new ExecutionTimelineEventRecord("RETRY_SCHEDULED", ExecutionStatus.RETRY_SCHEDULED.name(), "Retry scheduled", execution.getNextAttemptAt()));
        }
        if (execution.getStatus() == ExecutionStatus.CANCELLED) {
            events.add(new ExecutionTimelineEventRecord(
                    "CANCELLED",
                    ExecutionStatus.CANCELLED.name(),
                    execution.getFailureReason(),
                    execution.getUpdatedAt()
            ));
        }
        if (execution.getStatus() == ExecutionStatus.SUPPRESSED) {
            events.add(new ExecutionTimelineEventRecord(
                    "SUPPRESSED",
                    ExecutionStatus.SUPPRESSED.name(),
                    execution.getFailureReason(),
                    execution.getUpdatedAt()
            ));
        }
        if (execution.getFailureReason() != null && execution.getFailureReason().startsWith("RESCHEDULED_")) {
            events.add(new ExecutionTimelineEventRecord(
                    "RESCHEDULED",
                    ExecutionStatus.QUEUED.name(),
                    execution.getFailureReason(),
                    execution.getUpdatedAt()
            ));
        }
        attempts.stream()
                .sorted(Comparator.comparing(CampaignDeliveryAttemptRecord::attemptedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(attempt -> events.add(new ExecutionTimelineEventRecord(
                        "DELIVERY_ATTEMPT",
                        attempt.deliveryStatus().name(),
                        attempt.errorMessage(),
                        attempt.attemptedAt()
                )));
        deliveryEvents.stream()
                .sorted(Comparator.comparing(CampaignDeliveryEventRecord::eventTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(event -> events.add(new ExecutionTimelineEventRecord(
                        "DELIVERY_EVENT",
                        event.internalStatus().name(),
                        event.externalStatus(),
                        event.eventTimestamp()
                )));

        events.sort(Comparator.comparing(ExecutionTimelineEventRecord::at, Comparator.nullsLast(Comparator.naturalOrder())));

        return new CarePilotExecutionTimelineRecord(toExecutionRecord(execution), attempts, deliveryEvents, events);
    }

    private DateWindow resolveWindow(LocalDate startDate, LocalDate endDate) {
        LocalDate effectiveEnd = endDate == null ? LocalDate.now(ZoneOffset.UTC) : endDate;
        LocalDate effectiveStart = startDate == null ? effectiveEnd.minusDays(6) : startDate;
        if (effectiveStart.isAfter(effectiveEnd)) {
            throw new IllegalArgumentException("startDate cannot be after endDate");
        }
        return new DateWindow(
                effectiveStart,
                effectiveEnd,
                effectiveStart.atStartOfDay().atOffset(ZoneOffset.UTC),
                effectiveEnd.plusDays(1).atStartOfDay().minusNanos(1).atOffset(ZoneOffset.UTC)
        );
    }

    private long countByStatus(List<CampaignExecutionRecord> rows, ExecutionStatus status) {
        return rows.stream().filter(r -> r.status() == status).count();
    }

    private double percentage(long part, long total) {
        return total == 0 ? 0D : (part * 100.0D) / total;
    }

    private String normalizeProvider(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return "unknown";
        }
        return providerName.trim().toLowerCase();
    }

    private CampaignExecutionRecord toExecutionRecord(CampaignExecutionEntity e) {
        return new CampaignExecutionRecord(
                e.getId(),
                e.getTenantId(),
                e.getCampaignId(),
                e.getTemplateId(),
                e.getChannelType(),
                e.getRecipientPatientId(),
                e.getScheduledAt(),
                e.getStatus(),
                e.getAttemptCount(),
                e.getLastError(),
                e.getExecutedAt(),
                e.getNextAttemptAt(),
                e.getDeliveryStatus(),
                e.getProviderName(),
                e.getProviderMessageId(),
                e.getSourceType(),
                e.getSourceReferenceId(),
                e.getReminderWindow(),
                e.getReferenceDateTime(),
                e.getLastAttemptAt(),
                e.getFailureReason(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private CampaignDeliveryAttemptRecord toAttemptRecord(CampaignDeliveryAttemptEntity a) {
        return new CampaignDeliveryAttemptRecord(
                a.getId(),
                a.getTenantId(),
                a.getExecutionId(),
                a.getAttemptNumber(),
                a.getProviderName(),
                a.getChannelType(),
                a.getDeliveryStatus(),
                a.getErrorCode(),
                a.getErrorMessage(),
                a.getAttemptedAt()
        );
    }

    private CampaignDeliveryEventRecord toEventRecord(CampaignDeliveryEventEntity e) {
        return new CampaignDeliveryEventRecord(
                e.getId(),
                e.getTenantId(),
                e.getExecutionId(),
                e.getDeliveryAttemptId(),
                e.getProviderName(),
                e.getProviderMessageId(),
                e.getChannelType(),
                e.getExternalStatus(),
                e.getInternalStatus(),
                e.getEventType(),
                e.getEventTimestamp(),
                e.getReceivedAt()
        );
    }

    private record DateWindow(LocalDate startDate, LocalDate endDate, OffsetDateTime from, OffsetDateTime to) {}
}
