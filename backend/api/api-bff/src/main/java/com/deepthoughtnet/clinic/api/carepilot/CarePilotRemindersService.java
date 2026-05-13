package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.api.carepilot.dto.AnalyticsDtos.DeliveryEventResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.AnalyticsDtos.ExecutionTimelineResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.AnalyticsDtos.TimelineEventResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.ExecutionDtos.DeliveryAttemptResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.ExecutionDtos.ExecutionResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.RemindersDtos.ReminderDetailResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.RemindersDtos.ReminderListResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.RemindersDtos.ReminderRowResponse;
import com.deepthoughtnet.clinic.carepilot.analytics.service.CarePilotAnalyticsService;
import com.deepthoughtnet.clinic.carepilot.analytics.service.model.CarePilotExecutionTimelineRecord;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryEventEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryEventRepository;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Read model service for tenant-scoped reminders management queries.
 */
@Service
public class CarePilotRemindersService {
    private final CampaignExecutionRepository executionRepository;
    private final CampaignRepository campaignRepository;
    private final PatientRepository patientRepository;
    private final CampaignDeliveryEventRepository eventRepository;
    private final CarePilotAnalyticsService analyticsService;

    public CarePilotRemindersService(
            CampaignExecutionRepository executionRepository,
            CampaignRepository campaignRepository,
            PatientRepository patientRepository,
            CampaignDeliveryEventRepository eventRepository,
            CarePilotAnalyticsService analyticsService
    ) {
        this.executionRepository = executionRepository;
        this.campaignRepository = campaignRepository;
        this.patientRepository = patientRepository;
        this.eventRepository = eventRepository;
        this.analyticsService = analyticsService;
    }

    /**
     * Lists reminders with operational filters and pagination.
     */
    @Transactional(readOnly = true)
    public ReminderListResponse list(
            UUID tenantId,
            String status,
            UUID campaignId,
            CampaignType campaignType,
            ChannelType channel,
            UUID patientId,
            String patientName,
            LocalDate fromDate,
            LocalDate toDate,
            String providerName,
            int page,
            int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(200, size));

        List<CampaignExecutionEntity> executions = executionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        Map<UUID, CampaignEntity> campaigns = campaignRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .collect(LinkedHashMap::new, (m, c) -> m.put(c.getId(), c), Map::putAll);
        Map<UUID, PatientEntity> patients = patientRepository.findByTenantIdAndIdIn(
                        tenantId,
                        executions.stream()
                                .map(CampaignExecutionEntity::getRecipientPatientId)
                                .filter(java.util.Objects::nonNull)
                                .distinct()
                                .toList()
                )
                .stream()
                .collect(LinkedHashMap::new, (m, p) -> m.put(p.getId(), p), Map::putAll);

        List<UUID> executionIds = executions.stream().map(CampaignExecutionEntity::getId).toList();
        Map<UUID, List<CampaignDeliveryEventEntity>> eventsByExecution = executionIds.isEmpty()
                ? Map.of()
                : eventRepository.findByTenantIdAndExecutionIdInOrderByEventTimestampAsc(tenantId, executionIds)
                        .stream()
                        .collect(java.util.stream.Collectors.groupingBy(CampaignDeliveryEventEntity::getExecutionId));

        Predicate<CampaignExecutionEntity> predicate = row -> true;
        if (campaignId != null) {
            predicate = predicate.and(row -> campaignId.equals(row.getCampaignId()));
        }
        if (campaignType != null) {
            predicate = predicate.and(row -> {
                CampaignEntity campaign = campaigns.get(row.getCampaignId());
                return campaign != null && campaign.getCampaignType() == campaignType;
            });
        }
        if (channel != null) {
            predicate = predicate.and(row -> row.getChannelType() == channel);
        }
        if (patientId != null) {
            predicate = predicate.and(row -> patientId.equals(row.getRecipientPatientId()));
        }
        if (StringUtils.hasText(patientName)) {
            String normalized = patientName.trim().toLowerCase();
            predicate = predicate.and(row -> {
                PatientEntity patient = patients.get(row.getRecipientPatientId());
                if (patient == null) {
                    return false;
                }
                String full = (safe(patient.getFirstName()) + " " + safe(patient.getLastName())).trim().toLowerCase();
                return full.contains(normalized)
                        || safe(patient.getFirstName()).toLowerCase().contains(normalized)
                        || safe(patient.getLastName()).toLowerCase().contains(normalized);
            });
        }
        if (StringUtils.hasText(providerName)) {
            String normalized = providerName.trim().toLowerCase();
            predicate = predicate.and(row -> row.getProviderName() != null && row.getProviderName().toLowerCase().contains(normalized));
        }
        if (fromDate != null) {
            OffsetDateTime from = fromDate.atStartOfDay().atOffset(ZoneOffset.UTC);
            predicate = predicate.and(row -> row.getScheduledAt() != null && !row.getScheduledAt().isBefore(from));
        }
        if (toDate != null) {
            OffsetDateTime to = toDate.plusDays(1).atStartOfDay().minusNanos(1).atOffset(ZoneOffset.UTC);
            predicate = predicate.and(row -> row.getScheduledAt() != null && !row.getScheduledAt().isAfter(to));
        }
        if (StringUtils.hasText(status)) {
            String normalized = status.trim().toUpperCase();
            predicate = predicate.and(row -> matchesStatusFilter(normalized, row));
        }

        List<ReminderRowResponse> filtered = executions.stream()
                .filter(predicate)
                .sorted(Comparator.comparing(CampaignExecutionEntity::getScheduledAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(row -> toRow(row, campaigns.get(row.getCampaignId()), patients.get(row.getRecipientPatientId()), eventsByExecution.getOrDefault(row.getId(), List.of())))
                .toList();

        int start = Math.min(filtered.size(), safePage * safeSize);
        int end = Math.min(filtered.size(), start + safeSize);
        return new ReminderListResponse(safePage, safeSize, filtered.size(), filtered.subList(start, end));
    }

    /**
     * Returns one reminder row plus full timeline details.
     */
    @Transactional(readOnly = true)
    public ReminderDetailResponse detail(UUID tenantId, UUID executionId) {
        CampaignExecutionEntity execution = executionRepository.findByTenantIdAndId(tenantId, executionId)
                .orElseThrow(() -> new IllegalArgumentException("Reminder execution not found"));

        Map<UUID, CampaignEntity> campaigns = campaignRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .collect(LinkedHashMap::new, (m, c) -> m.put(c.getId(), c), Map::putAll);
        PatientEntity patient = execution.getRecipientPatientId() == null
                ? null
                : patientRepository.findByTenantIdAndId(tenantId, execution.getRecipientPatientId()).orElse(null);
        List<CampaignDeliveryEventEntity> events = eventRepository.findByTenantIdAndExecutionIdOrderByEventTimestampAsc(tenantId, executionId);
        ReminderRowResponse row = toRow(execution, campaigns.get(execution.getCampaignId()), patient, events);

        CarePilotExecutionTimelineRecord timelineRecord = analyticsService.timeline(tenantId, executionId);
        ExecutionTimelineResponse timeline = new ExecutionTimelineResponse(
                toExecutionResponse(timelineRecord.execution()),
                timelineRecord.deliveryAttempts().stream().map(attempt -> new DeliveryAttemptResponse(
                        attempt.id(), attempt.tenantId(), attempt.executionId(), attempt.attemptNumber(), attempt.providerName(),
                        attempt.channelType(), attempt.deliveryStatus(), attempt.errorCode(), attempt.errorMessage(), attempt.attemptedAt()
                )).toList(),
                timelineRecord.deliveryEvents().stream().map(event -> new DeliveryEventResponse(
                        event.id() == null ? null : event.id().toString(),
                        event.executionId() == null ? null : event.executionId().toString(),
                        event.providerName(), event.providerMessageId(), event.channelType(), event.externalStatus(), event.internalStatus(),
                        event.eventType(), event.eventTimestamp(), event.receivedAt()
                )).toList(),
                timelineRecord.statusEvents().stream().map(event -> new TimelineEventResponse(event.type(), event.status(), event.detail(), event.at())).toList()
        );

        return new ReminderDetailResponse(row, timeline);
    }

    private boolean matchesStatusFilter(String status, CampaignExecutionEntity row) {
        return status.equals(row.getStatus().name()) || (row.getDeliveryStatus() != null && status.equals(row.getDeliveryStatus().name()));
    }

    private ReminderRowResponse toRow(
            CampaignExecutionEntity row,
            CampaignEntity campaign,
            PatientEntity patient,
            List<CampaignDeliveryEventEntity> events
    ) {
        OffsetDateTime deliveredAt = latestEvent(events, MessageDeliveryStatus.DELIVERED);
        OffsetDateTime readAt = latestEvent(events, MessageDeliveryStatus.READ);
        OffsetDateTime failedAt = latestFailureEvent(events);

        String patientName = patient == null ? null : (safe(patient.getFirstName()) + " " + safe(patient.getLastName())).trim();
        String relatedType = row.getSourceType();
        String reminderReason = buildReminderReason(row);

        return new ReminderRowResponse(
                row.getId().toString(),
                row.getCampaignId().toString(),
                campaign == null ? row.getCampaignId().toString() : campaign.getName(),
                campaign == null ? null : campaign.getCampaignType(),
                campaign == null ? null : campaign.getTriggerType(),
                row.getRecipientPatientId() == null ? null : row.getRecipientPatientId().toString(),
                StringUtils.hasText(patientName) ? patientName : null,
                patient == null ? null : patient.getEmail(),
                patient == null ? null : patient.getMobile(),
                row.getChannelType(),
                row.getProviderName(),
                row.getProviderMessageId(),
                row.getStatus(),
                row.getDeliveryStatus(),
                row.getScheduledAt(),
                row.getLastAttemptAt(),
                row.getExecutedAt(),
                deliveredAt,
                readAt,
                failedAt,
                row.getNextAttemptAt(),
                row.getAttemptCount(),
                StringUtils.hasText(row.getFailureReason()) ? row.getFailureReason() : row.getLastError(),
                relatedType,
                row.getSourceReferenceId() == null ? null : row.getSourceReferenceId().toString(),
                buildRelatedLabel(row),
                reminderReason,
                row.getCreatedAt()
        );
    }

    private OffsetDateTime latestEvent(List<CampaignDeliveryEventEntity> events, MessageDeliveryStatus status) {
        return events.stream()
                .filter(event -> event.getInternalStatus() == status)
                .map(CampaignDeliveryEventEntity::getEventTimestamp)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private OffsetDateTime latestFailureEvent(List<CampaignDeliveryEventEntity> events) {
        return events.stream()
                .filter(event -> event.getInternalStatus() == MessageDeliveryStatus.FAILED
                        || event.getInternalStatus() == MessageDeliveryStatus.BOUNCED
                        || event.getInternalStatus() == MessageDeliveryStatus.UNDELIVERED)
                .map(CampaignDeliveryEventEntity::getEventTimestamp)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private String buildReminderReason(CampaignExecutionEntity row) {
        if (StringUtils.hasText(row.getReminderWindow())) {
            return row.getReminderWindow();
        }
        if (StringUtils.hasText(row.getSourceType())) {
            return row.getSourceType();
        }
        return "Campaign reminder";
    }

    private String buildRelatedLabel(CampaignExecutionEntity row) {
        if (row.getSourceReferenceId() == null) {
            return null;
        }
        if (StringUtils.hasText(row.getSourceType())) {
            return row.getSourceType() + " " + row.getSourceReferenceId();
        }
        return row.getSourceReferenceId().toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private ExecutionResponse toExecutionResponse(com.deepthoughtnet.clinic.carepilot.execution.service.model.CampaignExecutionRecord record) {
        return new ExecutionResponse(
                record.id(), record.tenantId(), record.campaignId(), record.templateId(), record.channelType(),
                record.recipientPatientId(), record.scheduledAt(), record.status(), record.attemptCount(), record.lastError(),
                record.executedAt(), record.nextAttemptAt(), record.deliveryStatus(), record.providerName(),
                record.providerMessageId(), record.sourceType(), record.sourceReferenceId(), record.reminderWindow(),
                record.referenceDateTime(), record.lastAttemptAt(), record.failureReason(), record.createdAt(), record.updatedAt()
        );
    }
}
