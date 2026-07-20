package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.api.carepilot.dto.OpsConsoleDtos.OpsExecutionResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.OpsConsoleDtos.OpsReadinessResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderStatusResponse;
import com.deepthoughtnet.clinic.api.ops.PlatformOpsService;
import com.deepthoughtnet.clinic.api.ops.SchedulerLockMonitor;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryAttemptEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryAttemptRepository;
import com.deepthoughtnet.clinic.carepilot.execution.service.CampaignExecutionService;
import com.deepthoughtnet.clinic.carepilot.execution.service.model.CampaignExecutionRecord;
import com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Tenant-safe operational view service for the Engage ops console. */
@Service
public class CarePilotOpsConsoleService {
    private static final Collection<ExecutionStatus> QUEUE_STATUSES = List.of(
            ExecutionStatus.QUEUED,
            ExecutionStatus.PROCESSING,
            ExecutionStatus.RETRY_SCHEDULED
    );

    private final CampaignExecutionService executionService;
    private final CampaignRepository campaignRepository;
    private final PatientRepository patientRepository;
    private final CampaignDeliveryAttemptRepository attemptRepository;
    private final PlatformOpsService platformOpsService;
    private final CarePilotMessagingStatusService messagingStatusService;
    private final CarePilotRuntimeSchedulerMonitor reminderSchedulerMonitor;
    private final SchedulerLockMonitor schedulerLockMonitor;
    private final boolean manualExecutionDispatcherEnabled;
    private final int stuckThresholdMinutes;

    public CarePilotOpsConsoleService(
            CampaignExecutionService executionService,
            CampaignRepository campaignRepository,
            PatientRepository patientRepository,
            CampaignDeliveryAttemptRepository attemptRepository,
            PlatformOpsService platformOpsService,
            CarePilotMessagingStatusService messagingStatusService,
            CarePilotRuntimeSchedulerMonitor reminderSchedulerMonitor,
            SchedulerLockMonitor schedulerLockMonitor,
            @Value("${clinic.carepilot.scheduler.enabled:false}") boolean manualExecutionDispatcherEnabled,
            @Value("${carepilot.ops.stuck-threshold-minutes:30}") int stuckThresholdMinutes
    ) {
        this.executionService = executionService;
        this.campaignRepository = campaignRepository;
        this.patientRepository = patientRepository;
        this.attemptRepository = attemptRepository;
        this.platformOpsService = platformOpsService;
        this.messagingStatusService = messagingStatusService;
        this.reminderSchedulerMonitor = reminderSchedulerMonitor;
        this.schedulerLockMonitor = schedulerLockMonitor;
        this.manualExecutionDispatcherEnabled = manualExecutionDispatcherEnabled;
        this.stuckThresholdMinutes = Math.max(5, stuckThresholdMinutes);
    }

    @Transactional(readOnly = true)
    public List<OpsExecutionResponse> listExecutions(
            UUID tenantId,
            String campaignRef,
            ChannelType channel,
            ExecutionStatus status,
            String providerName,
            boolean retryableOnly,
            LocalDate startDate,
            LocalDate endDate,
            String reminderWindow
    ) {
        List<CampaignExecutionRecord> executions = filterExecutions(tenantId, campaignRef, channel, status, providerName, retryableOnly, startDate, endDate, reminderWindow);
        return toRows(tenantId, executions);
    }

    @Transactional(readOnly = true)
    public List<OpsExecutionResponse> listQueuedExecutions(
            UUID tenantId,
            String campaignRef,
            ChannelType channel,
            String providerName,
            boolean retryableOnly,
            LocalDate startDate,
            LocalDate endDate,
            String reminderWindow
    ) {
        List<OpsExecutionResponse> rows = listExecutions(tenantId, campaignRef, channel, null, providerName, retryableOnly, startDate, endDate, reminderWindow);
        return rows.stream()
                .filter(row -> QUEUE_STATUSES.contains(row.status()))
                .sorted(Comparator.comparing(OpsExecutionResponse::queuedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public OpsReadinessResponse readiness(UUID tenantId) {
        var queues = platformOpsService.queues(tenantId).queues().stream()
                .filter(q -> "campaign-executions".equals(q.queueName()))
                .findFirst()
                .orElse(null);
        var lockState = schedulerLockMonitor.snapshot().get("carepilot-campaign-execution-scheduler");
        List<CampaignExecutionRecord> all = executionService.list(tenantId);
        OffsetDateTime lastSuccessfulDispatchAt = all.stream()
                .filter(row -> row.status() == ExecutionStatus.SUCCEEDED)
                .map(CampaignExecutionRecord::executedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        List<CampaignExecutionRecord> queueRows = all.stream().filter(row -> QUEUE_STATUSES.contains(row.status())).toList();
        long oldestQueuedAgeMinutes = queueRows.stream()
                .map(CampaignExecutionRecord::createdAt)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .map(createdAt -> Duration.between(createdAt, OffsetDateTime.now()).toMinutes())
                .orElse(0L);
        long queuedCount = queueRows.stream().filter(row -> row.status() == ExecutionStatus.QUEUED).count();
        long processingCount = queueRows.stream().filter(row -> row.status() == ExecutionStatus.PROCESSING).count();
        long retryingCount = queueRows.stream().filter(row -> row.status() == ExecutionStatus.RETRY_SCHEDULED).count();
        long queueDepth = queueRows.size();
        List<ProviderStatusResponse> providers = messagingStatusService.providerStatuses();

        return new OpsReadinessResponse(
                manualExecutionDispatcherEnabled,
                lockState == null ? null : lockState.lastAcquiredAt(),
                lockState == null ? null : lockState.lastSkippedAt(),
                reminderSchedulerMonitor.reminderSchedulerStatus().equalsIgnoreCase("ENABLED"),
                reminderSchedulerMonitor.lastReminderScanAt(tenantId),
                queueDepth,
                queuedCount,
                processingCount,
                retryingCount,
                oldestQueuedAgeMinutes,
                lastSuccessfulDispatchAt,
                providers
        );
    }

    private List<CampaignExecutionRecord> filterExecutions(
            UUID tenantId,
            String campaignRef,
            ChannelType channel,
            ExecutionStatus status,
            String providerName,
            boolean retryableOnly,
            LocalDate startDate,
            LocalDate endDate,
            String reminderWindow
    ) {
        Map<UUID, CampaignEntity> campaignById = campaignRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .collect(Collectors.toMap(CampaignEntity::getId, java.util.function.Function.identity(), (left, right) -> left, LinkedHashMap::new));
        List<CampaignExecutionRecord> all = executionService.list(tenantId);
        OffsetDateTime from = startDate == null ? null : startDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = endDate == null ? null : endDate.plusDays(1).atStartOfDay().minusNanos(1).atOffset(ZoneOffset.UTC);
        String campaignRefFilter = StringUtils.hasText(campaignRef) ? campaignRef.trim().toLowerCase(Locale.ROOT) : null;
        String providerFilter = StringUtils.hasText(providerName) ? providerName.trim().toLowerCase(Locale.ROOT) : null;
        String reminderFilter = StringUtils.hasText(reminderWindow) ? reminderWindow.trim().toUpperCase(Locale.ROOT) : null;

        return all.stream()
                .filter(row -> {
                    if (campaignRefFilter == null) {
                        return true;
                    }
                    CampaignEntity campaign = campaignById.get(row.campaignId());
                    return campaign != null && campaign.getCampaignReference() != null && campaign.getCampaignReference().toLowerCase(Locale.ROOT).contains(campaignRefFilter);
                })
                .filter(row -> channel == null || row.channelType() == channel)
                .filter(row -> status == null || row.status() == status)
                .filter(row -> providerFilter == null || (row.providerName() != null && row.providerName().toLowerCase(Locale.ROOT).contains(providerFilter)))
                .filter(row -> !retryableOnly || row.status() == ExecutionStatus.FAILED)
                .filter(row -> reminderFilter == null || reminderFilter.equalsIgnoreCase(row.reminderWindow()))
                .filter(row -> from == null || row.scheduledAt() == null || !row.scheduledAt().isBefore(from))
                .filter(row -> to == null || row.scheduledAt() == null || !row.scheduledAt().isAfter(to))
                .sorted(Comparator.comparing(CampaignExecutionRecord::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private List<OpsExecutionResponse> toRows(UUID tenantId, List<CampaignExecutionRecord> executions) {
        Map<UUID, CampaignEntity> campaignById = campaignRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .collect(Collectors.toMap(CampaignEntity::getId, java.util.function.Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<UUID, PatientEntity> patientById = patientRepository.findByTenantIdAndIdIn(
                        tenantId,
                        executions.stream().map(CampaignExecutionRecord::recipientPatientId).filter(java.util.Objects::nonNull).collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(PatientEntity::getId, java.util.function.Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<UUID, Integer> deliveryAttemptCounts = attemptRepository.findByTenantIdAndExecutionIdInOrderByAttemptedAtDesc(
                        tenantId,
                        executions.stream().map(CampaignExecutionRecord::id).toList()
                ).stream()
                .collect(Collectors.groupingBy(
                        CampaignDeliveryAttemptEntity::getExecutionId,
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));
        OffsetDateTime now = OffsetDateTime.now();
        return executions.stream().map(row -> {
            CampaignEntity campaign = campaignById.get(row.campaignId());
            PatientEntity patient = patientById.get(row.recipientPatientId());
            String campaignReference = campaign == null ? "Unknown campaign" : campaign.getCampaignReference();
            String executionReference = buildExecutionReference(campaignReference, row.createdAt());
            long queueAgeMinutes = queueAgeMinutes(row, now);
            String blockingReason = blockingReason(row, queueAgeMinutes);
            boolean stuck = isStuck(row, queueAgeMinutes);
            return new OpsExecutionResponse(
                    row.id().toString(),
                    executionReference,
                    campaignReference,
                    campaign == null ? "Unknown campaign" : campaign.getName(),
                    campaign == null ? null : campaign.getCampaignType().name(),
                    patient == null ? null : patient.getPatientNumber(),
                    patient == null ? null : fullName(patient),
                    row.sourceType(),
                    relatedEntityLabel(row, campaign),
                    row.channelType(),
                    row.status(),
                    row.deliveryStatus() == null ? null : row.deliveryStatus().name(),
                    row.providerName(),
                    row.scheduledAt(),
                    row.createdAt(),
                    queueAgeMinutes,
                    row.attemptCount(),
                    deliveryAttemptCounts.getOrDefault(row.id(), 0),
                    row.attemptCount(),
                    blockingReason,
                    stuck,
                    row.reminderWindow(),
                    row.nextAttemptAt(),
                    row.lastAttemptAt(),
                    row.failureReason(),
                    row.executedAt(),
                    row.createdAt(),
                    row.updatedAt()
            );
        }).toList();
    }

    private long queueAgeMinutes(CampaignExecutionRecord row, OffsetDateTime now) {
        if (!QUEUE_STATUSES.contains(row.status()) || row.createdAt() == null) {
            return -1L;
        }
        return Duration.between(row.createdAt(), now).toMinutes();
    }

    private boolean isStuck(CampaignExecutionRecord row, long queueAgeMinutes) {
        return queueAgeMinutes >= stuckThresholdMinutes && QUEUE_STATUSES.contains(row.status());
    }

    private String blockingReason(CampaignExecutionRecord row, long queueAgeMinutes) {
        if (row.status() == ExecutionStatus.RETRY_SCHEDULED) {
            return row.nextAttemptAt() != null ? "Retry backoff until " + row.nextAttemptAt() : "Retry backoff";
        }
        if (row.status() == ExecutionStatus.PROCESSING && queueAgeMinutes >= stuckThresholdMinutes) {
            return "Worker heartbeat stale";
        }
        if (row.status() == ExecutionStatus.QUEUED) {
            if (row.scheduledAt() != null && row.scheduledAt().isAfter(OffsetDateTime.now())) {
                return "Scheduled for later";
            }
            if (manualExecutionDispatcherEnabled) {
                return queueAgeMinutes >= stuckThresholdMinutes ? "Dispatcher backlog" : "Awaiting dispatcher";
            }
            return "Dispatcher disabled";
        }
        if (row.status() == ExecutionStatus.FAILED || row.status() == ExecutionStatus.DEAD_LETTER) {
            return "Failed execution";
        }
        if (row.status() == ExecutionStatus.SUPPRESSED) {
            return "Suppressed";
        }
        if (row.status() == ExecutionStatus.CANCELLED) {
            return "Cancelled";
        }
        return row.failureReason();
    }

    private String buildExecutionReference(String campaignReference, OffsetDateTime at) {
        String base = campaignReference == null || campaignReference.isBlank() ? "EXE-UNKNOWN" : "EXE-" + campaignReference.trim();
        OffsetDateTime effective = at == null ? OffsetDateTime.now() : at;
        return base + "-" + DateTimeFormatter.ofPattern("yyyyMMddHHmm").format(effective);
    }

    private String fullName(PatientEntity patient) {
        String name = ((patient.getFirstName() == null ? "" : patient.getFirstName()) + " " + (patient.getLastName() == null ? "" : patient.getLastName())).trim();
        return name.isBlank() ? "Unknown patient" : name;
    }

    private String relatedEntityLabel(CampaignExecutionRecord row, CampaignEntity campaign) {
        if (row.sourceType() == null || row.sourceType().isBlank()) {
            return campaign == null ? "Campaign execution" : campaign.getName();
        }
        return switch (row.sourceType().toUpperCase(Locale.ROOT)) {
            case "APPOINTMENT" -> "Appointment";
            case "PRESCRIPTION", "FOLLOW_UP" -> "Prescription";
            case "BILL", "BILLING" -> "Bill";
            case "VACCINATION" -> "Vaccination";
            case "PATIENT_BIRTHDAY" -> "Birthday";
            default -> row.sourceType();
        };
    }
}
