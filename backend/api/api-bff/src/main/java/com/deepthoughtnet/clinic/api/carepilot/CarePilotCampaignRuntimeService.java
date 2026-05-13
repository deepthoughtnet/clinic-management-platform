package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.appointment.db.AppointmentEntity;
import com.deepthoughtnet.clinic.appointment.db.AppointmentRepository;
import com.deepthoughtnet.clinic.billing.db.BillRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus;
import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionRepository;
import com.deepthoughtnet.clinic.vaccination.db.PatientVaccinationRepository;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only campaign runtime aggregation used by campaign details activity views.
 */
@Service
public class CarePilotCampaignRuntimeService {
    private static final Set<ExecutionStatus> FAILED_STATUSES = Set.of(ExecutionStatus.FAILED, ExecutionStatus.DEAD_LETTER);
    private static final Collection<ExecutionStatus> FUTURE_STATUSES = List.of(ExecutionStatus.QUEUED, ExecutionStatus.RETRY_SCHEDULED);

    private final CampaignRepository campaignRepository;
    private final CampaignExecutionRepository executionRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final BillRepository billRepository;
    private final PatientVaccinationRepository patientVaccinationRepository;
    private final AppUserRepository appUserRepository;
    private final CarePilotRuntimeSchedulerMonitor schedulerMonitor;

    public CarePilotCampaignRuntimeService(
            CampaignRepository campaignRepository,
            CampaignExecutionRepository executionRepository,
            PatientRepository patientRepository,
            AppointmentRepository appointmentRepository,
            PrescriptionRepository prescriptionRepository,
            BillRepository billRepository,
            PatientVaccinationRepository patientVaccinationRepository,
            AppUserRepository appUserRepository,
            CarePilotRuntimeSchedulerMonitor schedulerMonitor
    ) {
        this.campaignRepository = campaignRepository;
        this.executionRepository = executionRepository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.billRepository = billRepository;
        this.patientVaccinationRepository = patientVaccinationRepository;
        this.appUserRepository = appUserRepository;
        this.schedulerMonitor = schedulerMonitor;
    }

    /**
     * Builds tenant-scoped runtime details for one campaign.
     */
    @Transactional(readOnly = true)
    public CampaignRuntimeView runtime(UUID tenantId, UUID campaignId) {
        CampaignEntity campaign = campaignRepository.findByTenantIdAndId(tenantId, campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found"));

        List<CampaignExecutionEntity> all = executionRepository.findByTenantIdAndCampaignIdOrderByUpdatedAtDesc(tenantId, campaignId);
        List<CampaignExecutionEntity> recent = executionRepository.findTop50ByTenantIdAndCampaignIdOrderByUpdatedAtDesc(tenantId, campaignId);

        Map<UUID, PatientEntity> patientById = patientRepository.findByTenantIdAndIdIn(
                tenantId,
                recent.stream().map(CampaignExecutionEntity::getRecipientPatientId).filter(Objects::nonNull).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(PatientEntity::getId, Function.identity()));

        Map<UUID, AppointmentEntity> appointmentById = recent.stream()
                .filter(e -> "APPOINTMENT".equalsIgnoreCase(e.getSourceType()) && e.getSourceReferenceId() != null)
                .map(CampaignExecutionEntity::getSourceReferenceId)
                .distinct()
                .map(id -> appointmentRepository.findByTenantIdAndId(tenantId, id).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(AppointmentEntity::getId, Function.identity()));

        List<UUID> doctorUserIds = appointmentById.values().stream().map(AppointmentEntity::getDoctorUserId).distinct().toList();
        Map<UUID, AppUserEntity> doctorByUserId = doctorUserIds.isEmpty()
                ? Map.of()
                : appUserRepository.findByTenantIdAndIdIn(tenantId, doctorUserIds)
                        .stream()
                        .collect(Collectors.toMap(AppUserEntity::getId, Function.identity()));

        OffsetDateTime nextExpected = executionRepository
                .findFirstByTenantIdAndCampaignIdAndStatusInOrderByScheduledAtAsc(tenantId, campaignId, FUTURE_STATUSES)
                .map(CampaignExecutionEntity::getScheduledAt)
                .orElse(null);

        return new CampaignRuntimeView(
                campaign.getId(),
                campaign.getName(),
                campaign.isActive(),
                campaign.getTriggerType().name(),
                campaign.getCampaignType().name(),
                nextExpected,
                schedulerMonitor.reminderSchedulerStatus(),
                schedulerMonitor.lastReminderScanAt(tenantId),
                summary(all),
                recent.stream()
                        .map(e -> toExecutionView(tenantId, e, patientById.get(e.getRecipientPatientId()), appointmentById.get(e.getSourceReferenceId()), doctorByUserId))
                        .toList()
        );
    }

    private CampaignRuntimeSummaryView summary(List<CampaignExecutionEntity> rows) {
        OffsetDateTime lastSentAt = rows.stream()
                .filter(e -> e.getStatus() == ExecutionStatus.SUCCEEDED)
                .map(CampaignExecutionEntity::getExecutedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        OffsetDateTime lastFailedAt = rows.stream()
                .filter(e -> FAILED_STATUSES.contains(e.getStatus()))
                .map(e -> e.getLastAttemptAt() == null ? e.getUpdatedAt() : e.getLastAttemptAt())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new CampaignRuntimeSummaryView(
                rows.size(),
                rows.stream().filter(e -> e.getStatus() == ExecutionStatus.QUEUED).count(),
                rows.stream().filter(e -> e.getStatus() == ExecutionStatus.SUCCEEDED).count(),
                rows.stream().filter(e -> FAILED_STATUSES.contains(e.getStatus())).count(),
                rows.stream().filter(e -> e.getStatus() == ExecutionStatus.RETRY_SCHEDULED).count(),
                rows.stream().filter(e -> "SKIPPED".equalsIgnoreCase(String.valueOf(e.getDeliveryStatus()))).count(),
                lastSentAt,
                lastFailedAt
        );
    }

    private CampaignRuntimeExecutionView toExecutionView(
            UUID tenantId,
            CampaignExecutionEntity e,
            PatientEntity patient,
            AppointmentEntity appointment,
            Map<UUID, AppUserEntity> doctorByUserId
    ) {
        String entityType = normalizeEntityType(e.getSourceType(), e.getCampaignId());
        UUID entityId = e.getSourceReferenceId();
        String entityLabel = relatedEntityLabel(tenantId, entityType, entityId, appointment);
        String doctorName = appointment == null ? null : displayName(doctorByUserId.get(appointment.getDoctorUserId()));
        OffsetDateTime failedAt = FAILED_STATUSES.contains(e.getStatus()) ? (e.getLastAttemptAt() == null ? e.getUpdatedAt() : e.getLastAttemptAt()) : null;

        return new CampaignRuntimeExecutionView(
                e.getId(),
                e.getRecipientPatientId(),
                patient == null ? null : (patient.getFirstName() + " " + patient.getLastName()).trim(),
                patient == null ? null : patient.getEmail(),
                patient == null ? null : patient.getMobile(),
                entityType,
                entityId,
                entityLabel,
                doctorName,
                e.getReminderWindow(),
                e.getCreatedAt(),
                e.getScheduledAt(),
                e.getLastAttemptAt(),
                e.getExecutedAt(),
                failedAt,
                e.getNextAttemptAt(),
                e.getChannelType() == null ? null : e.getChannelType().name(),
                e.getProviderName(),
                e.getProviderMessageId(),
                e.getDeliveryStatus() != null ? e.getDeliveryStatus().name() : (e.getStatus() == null ? null : e.getStatus().name()),
                e.getFailureReason() == null ? e.getLastError() : e.getFailureReason(),
                e.getAttemptCount()
        );
    }

    private String relatedEntityLabel(UUID tenantId, String entityType, UUID entityId, AppointmentEntity appointment) {
        if (entityType == null || entityId == null) {
            return null;
        }
        return switch (entityType.toUpperCase(Locale.ROOT)) {
            case "APPOINTMENT" -> {
                AppointmentEntity resolved = appointment != null ? appointment : appointmentRepository.findByTenantIdAndId(tenantId, entityId).orElse(null);
                if (resolved == null) {
                    yield "Appointment " + entityId.toString().substring(0, 8);
                }
                String at = resolved.getAppointmentDate() + (resolved.getAppointmentTime() == null ? "" : " " + resolved.getAppointmentTime());
                yield "Appointment " + at;
            }
            case "PRESCRIPTION", "FOLLOW_UP" -> prescriptionRepository.findByTenantIdAndId(tenantId, entityId)
                    .map(p -> "Prescription " + p.getPrescriptionNumber())
                    .orElse("Prescription " + entityId.toString().substring(0, 8));
            case "BILL", "BILLING" -> billRepository.findByTenantIdAndId(tenantId, entityId)
                    .map(b -> "Bill " + b.getBillNumber())
                    .orElse("Bill " + entityId.toString().substring(0, 8));
            case "VACCINATION" -> patientVaccinationRepository.findByTenantIdAndId(tenantId, entityId)
                    .map(v -> "Vaccination " + (v.getVaccineNameSnapshot() == null ? "" : v.getVaccineNameSnapshot())
                            + (v.getNextDueDate() == null ? "" : " due " + v.getNextDueDate()))
                    .orElse("Vaccination " + entityId.toString().substring(0, 8));
            case "PATIENT_BIRTHDAY" -> patientRepository.findByTenantIdAndId(tenantId, entityId)
                    .map(p -> "Birthday " + p.getFirstName() + " " + p.getLastName()
                            + (p.getDateOfBirth() == null ? "" : " (" + p.getDateOfBirth() + ")"))
                    .orElse("Birthday " + entityId.toString().substring(0, 8));
            default -> entityType + " " + entityId.toString().substring(0, 8);
        };
    }

    private String normalizeEntityType(String sourceType, UUID ignoredCampaignId) {
        if (sourceType != null && !sourceType.isBlank()) {
            return sourceType.trim().toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private String displayName(AppUserEntity doctor) {
        if (doctor == null) {
            return null;
        }
        if (doctor.getDisplayName() != null && !doctor.getDisplayName().isBlank()) {
            return doctor.getDisplayName();
        }
        return doctor.getEmail();
    }

    /** Runtime response model used by API mapping. */
    public record CampaignRuntimeView(
            UUID campaignId,
            String campaignName,
            boolean active,
            String triggerType,
            String campaignType,
            OffsetDateTime nextExpectedExecutionAt,
            String schedulerStatus,
            OffsetDateTime lastSchedulerScanAt,
            CampaignRuntimeSummaryView summary,
            List<CampaignRuntimeExecutionView> recentExecutions
    ) {}

    /** Runtime counters model. */
    public record CampaignRuntimeSummaryView(
            long totalExecutions,
            long scheduled,
            long sent,
            long failed,
            long retrying,
            long skipped,
            OffsetDateTime lastSentAt,
            OffsetDateTime lastFailedAt
    ) {}

    /** One runtime execution row model. */
    public record CampaignRuntimeExecutionView(
            UUID executionId,
            UUID recipientPatientId,
            String recipientPatientName,
            String recipientEmail,
            String recipientPhone,
            String relatedEntityType,
            UUID relatedEntityId,
            String relatedEntityLabel,
            String doctorName,
            String reminderWindow,
            OffsetDateTime createdAt,
            OffsetDateTime scheduledAt,
            OffsetDateTime attemptedAt,
            OffsetDateTime sentAt,
            OffsetDateTime failedAt,
            OffsetDateTime nextRetryAt,
            String channel,
            String providerName,
            String providerMessageId,
            String status,
            String failureReason,
            int retryCount
    ) {}
}
