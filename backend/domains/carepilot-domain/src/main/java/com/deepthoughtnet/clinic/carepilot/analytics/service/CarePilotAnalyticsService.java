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
    private static final Map<String, Integer> TIMELINE_ORDER = Map.ofEntries(
            Map.entry("EXECUTION_CREATED", 10),
            Map.entry("DISPATCH_STARTED", 20),
            Map.entry("DELIVERY_ATTEMPT", 30),
            Map.entry("DELIVERY_EVENT", 35),
            Map.entry("EXECUTED", 40),
            Map.entry("FAILED", 45),
            Map.entry("DEAD_LETTER", 46),
            Map.entry("RETRY_SCHEDULED", 50),
            Map.entry("RESCHEDULED", 60),
            Map.entry("CANCELLED", 70),
            Map.entry("SUPPRESSED", 80)
    );

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

        List<UUID> executionIds = executionRows.stream().map(CampaignExecutionEntity::getId).toList();
        Map<UUID, Integer> deliveryAttemptCounts = executionIds.isEmpty()
                ? Map.of()
                : attemptRepository.findByTenantIdAndExecutionIdInOrderByAttemptedAtDesc(tenantId, executionIds).stream()
                        .collect(Collectors.groupingBy(
                                CampaignDeliveryAttemptEntity::getExecutionId,
                                LinkedHashMap::new,
                                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                        ));

        List<CampaignExecutionRecord> executions = executionRows.stream()
                .map(execution -> toExecutionRecord(execution, deliveryAttemptCounts.getOrDefault(execution.getId(), 0)))
                .toList();

        long totalExecutions = executions.size();
        long successful = countByStatus(executions, ExecutionStatus.SUCCEEDED);
        long failed = executions.stream().filter(e -> FAILED_STATUSES.contains(e.status())).count();
        long retrying = countByStatus(executions, ExecutionStatus.RETRY_SCHEDULED);
        long pending = executions.stream().filter(e -> e.status() == ExecutionStatus.QUEUED || e.status() == ExecutionStatus.PROCESSING).count();
        long scheduled = executions.stream().filter(e -> e.status() == ExecutionStatus.QUEUED && e.scheduledAt() != null && e.scheduledAt().isAfter(OffsetDateTime.now())).count();
        long skipped = executions.stream().filter(e -> e.deliveryStatus() == MessageDeliveryStatus.SKIPPED).count();
        long queued = countByDeliveryMetric(executions, DeliveryMetricStatus.QUEUED);
        long sent = countByDeliveryMetric(executions, DeliveryMetricStatus.SENT);
        long delivered = countByDeliveryMetric(executions, DeliveryMetricStatus.DELIVERED);
        long read = countByDeliveryMetric(executions, DeliveryMetricStatus.READ);
        long undelivered = countByDeliveryMetric(executions, DeliveryMetricStatus.UNDELIVERED);

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
                            campaign == null ? entry.getKey().toString() : campaign.getCampaignReference(),
                            campaign == null ? entry.getKey().toString() : campaign.getName(),
                            rowTotal,
                            rowSuccess,
                            rowFailed,
                            percentage(rowSuccess, rowTotal)
                    );
                })
                .sorted(Comparator.comparingLong(CampaignExecutionBreakdownRecord::totalExecutions).reversed())
                .toList();

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
                queued,
                sent,
                delivered,
                read,
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
                .map(row -> toExecutionRecord(row, deliveryAttemptCount(tenantId, row.getId())))
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
        events.add(new ExecutionTimelineEventRecord("EXECUTION_CREATED", "Queued", ExecutionStatus.QUEUED.name(), "Execution queued", execution.getCreatedAt()));
        if (execution.getLastAttemptAt() != null) {
            events.add(new ExecutionTimelineEventRecord(
                    "DISPATCH_STARTED",
                    "Dispatch Started/Acquired",
                    ExecutionStatus.PROCESSING.name(),
                    execution.getFailureReason() == null ? "Execution acquired" : execution.getFailureReason(),
                    execution.getLastAttemptAt()
            ));
        }
        if (execution.getExecutedAt() != null) {
            events.add(new ExecutionTimelineEventRecord("EXECUTED", "Execution Succeeded", ExecutionStatus.SUCCEEDED.name(), "Execution completed", execution.getExecutedAt()));
        }
        if (execution.getNextAttemptAt() != null) {
            events.add(new ExecutionTimelineEventRecord("RETRY_SCHEDULED", "Retry Scheduled", ExecutionStatus.RETRY_SCHEDULED.name(), "Retry scheduled", execution.getNextAttemptAt()));
        }
        if (execution.getStatus() == ExecutionStatus.CANCELLED) {
            events.add(new ExecutionTimelineEventRecord(
                    "CANCELLED",
                    "Cancelled",
                    ExecutionStatus.CANCELLED.name(),
                    execution.getFailureReason(),
                    execution.getUpdatedAt()
            ));
        }
        if (execution.getStatus() == ExecutionStatus.SUPPRESSED) {
            events.add(new ExecutionTimelineEventRecord(
                    "SUPPRESSED",
                    "Suppressed",
                    ExecutionStatus.SUPPRESSED.name(),
                    execution.getFailureReason(),
                    execution.getUpdatedAt()
            ));
        }
        if (execution.getFailureReason() != null && execution.getFailureReason().startsWith("RESCHEDULED_")) {
            events.add(new ExecutionTimelineEventRecord(
                    "RESCHEDULED",
                    "Rescheduled",
                    ExecutionStatus.QUEUED.name(),
                    execution.getFailureReason(),
                    execution.getUpdatedAt()
            ));
        }
        attempts.stream()
                .sorted(Comparator.comparing(CampaignDeliveryAttemptRecord::attemptedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(attempt -> events.add(new ExecutionTimelineEventRecord(
                        "DELIVERY_ATTEMPT",
                        deliveryAttemptLabel(attempt.channelType(), attempt.deliveryStatus()),
                        attempt.deliveryStatus().name(),
                        attempt.errorMessage() == null || attempt.errorMessage().isBlank()
                                ? (attempt.deliveryStatus() == MessageDeliveryStatus.SENT
                                || attempt.deliveryStatus() == MessageDeliveryStatus.DELIVERED
                                || attempt.deliveryStatus() == MessageDeliveryStatus.READ
                                ? "Delivery succeeded"
                                : "Attempt recorded")
                                : attempt.errorMessage(),
                        attempt.attemptedAt()
                )));
        deliveryEvents.stream()
                .sorted(Comparator.comparing(CampaignDeliveryEventRecord::eventTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(event -> events.add(new ExecutionTimelineEventRecord(
                        "DELIVERY_EVENT",
                        humanizeLabel(event.externalStatus()),
                        event.internalStatus().name(),
                        event.externalStatus() == null || event.externalStatus().isBlank() ? "Delivery Event" : event.externalStatus(),
                        event.eventTimestamp()
                )));

        events.sort(Comparator.comparing(ExecutionTimelineEventRecord::at, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(event -> TIMELINE_ORDER.getOrDefault(event.reasonCode(), Integer.MAX_VALUE)));

        return new CarePilotExecutionTimelineRecord(
                toExecutionRecord(execution, deliveryAttemptCount(tenantId, executionId)),
                attempts,
                deliveryEvents,
                events
        );
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

    private long countByDeliveryMetric(List<CampaignExecutionRecord> rows, DeliveryMetricStatus metricStatus) {
        return rows.stream().filter(row -> deliveryMetricStatus(row) == metricStatus).count();
    }

    private DeliveryMetricStatus deliveryMetricStatus(CampaignExecutionRecord row) {
        MessageDeliveryStatus deliveryStatus = row.deliveryStatus();
        if (deliveryStatus == null) {
            return row.status() == ExecutionStatus.QUEUED
                    || row.status() == ExecutionStatus.PROCESSING
                    || row.status() == ExecutionStatus.RETRY_SCHEDULED
                    ? DeliveryMetricStatus.QUEUED
                    : null;
        }
        return switch (deliveryStatus) {
            case QUEUED -> DeliveryMetricStatus.QUEUED;
            case SENT -> DeliveryMetricStatus.SENT;
            case DELIVERED -> DeliveryMetricStatus.DELIVERED;
            case READ -> DeliveryMetricStatus.READ;
            case BOUNCED, UNDELIVERED -> DeliveryMetricStatus.UNDELIVERED;
            default -> null;
        };
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

    private String deliveryAttemptLabel(ChannelType channelType, MessageDeliveryStatus deliveryStatus) {
        if (deliveryStatus != MessageDeliveryStatus.SENT
                && deliveryStatus != MessageDeliveryStatus.DELIVERED
                && deliveryStatus != MessageDeliveryStatus.READ) {
            return "Delivery Attempt";
        }
        String channelLabel = switch (channelType) {
            case EMAIL -> "Email";
            case SMS -> "SMS";
            case WHATSAPP -> "WhatsApp";
            default -> "Delivery";
        };
        String outcome = switch (deliveryStatus) {
            case DELIVERED -> "Delivered";
            case READ -> "Read";
            default -> "Sent";
        };
        return channelLabel + " " + outcome;
    }

    private String humanizeLabel(String value) {
        if (value == null || value.isBlank()) {
            return "Delivery Event";
        }
        String normalized = value.replace('-', ' ').replace('_', ' ').trim().toLowerCase();
        StringBuilder builder = new StringBuilder(normalized.length());
        boolean capitalizeNext = true;
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isWhitespace(ch)) {
                builder.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                builder.append(Character.toTitleCase(ch));
                capitalizeNext = false;
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private CampaignExecutionRecord toExecutionRecord(CampaignExecutionEntity e, int deliveryAttemptCount) {
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
                deliveryAttemptCount,
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
                e.getAcquiredAt(),
                e.getUpdatedAt()
        );
    }

    private int deliveryAttemptCount(UUID tenantId, UUID executionId) {
        return attemptRepository.findByTenantIdAndExecutionIdOrderByAttemptNumberDesc(tenantId, executionId).size();
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

    private enum DeliveryMetricStatus {
        QUEUED,
        SENT,
        DELIVERED,
        READ,
        UNDELIVERED
    }

    private record DateWindow(LocalDate startDate, LocalDate endDate, OffsetDateTime from, OffsetDateTime to) {}
}
