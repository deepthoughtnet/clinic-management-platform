package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentSearchCriteria;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.execution.service.CampaignExecutionService;
import com.deepthoughtnet.clinic.carepilot.execution.service.model.CampaignExecutionCreateCommand;
import com.deepthoughtnet.clinic.carepilot.featureflag.service.FeatureFlagService;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateEntity;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateRepository;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionStatus;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import com.deepthoughtnet.clinic.vaccination.service.model.PatientVaccinationRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Creates operational reminder executions for active CarePilot campaigns.
 *
 * <p>This orchestration runs outside core clinic workflow services so engagement automation
 * remains additive and extractable.</p>
 */
@Service
public class CarePilotReminderTriggerService {
    private final PlatformTenantManagementService tenantManagementService;
    private final FeatureFlagService featureFlagService;
    private final CampaignRepository campaignRepository;
    private final CampaignTemplateRepository templateRepository;
    private final CampaignExecutionRepository executionRepository;
    private final CampaignExecutionService executionService;
    private final AppointmentService appointmentService;
    private final PrescriptionService prescriptionService;
    private final VaccinationService vaccinationService;
    private final BillingService billingService;
    private final PatientRepository patientRepository;
    private final int refillEstimatedDays;
    private final int billingOverdueDays;
    private final int batchSize;

    public CarePilotReminderTriggerService(
            PlatformTenantManagementService tenantManagementService,
            FeatureFlagService featureFlagService,
            CampaignRepository campaignRepository,
            CampaignTemplateRepository templateRepository,
            CampaignExecutionRepository executionRepository,
            CampaignExecutionService executionService,
            AppointmentService appointmentService,
            PrescriptionService prescriptionService,
            VaccinationService vaccinationService,
            BillingService billingService,
            PatientRepository patientRepository,
            @Value("${carepilot.reminders.refill-estimated-days:30}") int refillEstimatedDays,
            @Value("${carepilot.reminders.billing-overdue-days:3}") int billingOverdueDays,
            @Value("${carepilot.reminders.batch-size:100}") int batchSize
    ) {
        this.tenantManagementService = tenantManagementService;
        this.featureFlagService = featureFlagService;
        this.campaignRepository = campaignRepository;
        this.templateRepository = templateRepository;
        this.executionRepository = executionRepository;
        this.executionService = executionService;
        this.appointmentService = appointmentService;
        this.prescriptionService = prescriptionService;
        this.vaccinationService = vaccinationService;
        this.billingService = billingService;
        this.patientRepository = patientRepository;
        this.refillEstimatedDays = Math.max(1, refillEstimatedDays);
        this.billingOverdueDays = Math.max(0, billingOverdueDays);
        this.batchSize = Math.max(1, batchSize);
    }

    /**
     * Runs reminder generation across CarePilot-enabled tenants with bounded workload.
     */
    public int queueDueReminders() {
        int queued = 0;
        for (var tenant : tenantManagementService.list()) {
            UUID tenantId = tenant.id();
            if (!featureFlagService.carePilotForTenant(tenantId).carePilotEnabled()) {
                continue;
            }
            queued += queueAppointmentReminders(tenantId);
            queued += queueFollowUpReminders(tenantId);
            queued += queueRefillReminders(tenantId);
            queued += queueVaccinationReminders(tenantId);
            queued += queueBillingReminders(tenantId);
            if (queued >= batchSize) {
                break;
            }
        }
        return queued;
    }

    private int queueAppointmentReminders(UUID tenantId) {
        Optional<CampaignTemplateBinding> binding = activeEmailCampaignBinding(tenantId, CampaignType.APPOINTMENT_REMINDER);
        if (binding.isEmpty()) {
            return 0;
        }
        int queued = 0;
        LocalDate today = LocalDate.now();
        for (LocalDate date : List.of(today, today.plusDays(1))) {
            List<AppointmentRecord> appointments = appointmentService.search(tenantId, new AppointmentSearchCriteria(null, null, date, null, null));
            for (AppointmentRecord appointment : appointments) {
                if (queued >= batchSize) {
                    return queued;
                }
                if (appointment.appointmentTime() == null) {
                    continue;
                }
                if (appointment.status() == AppointmentStatus.CANCELLED
                        || appointment.status() == AppointmentStatus.NO_SHOW
                        || appointment.status() == AppointmentStatus.COMPLETED) {
                    continue;
                }
                PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, appointment.patientId()).orElse(null);
                if (!hasEmail(patient)) {
                    continue;
                }

                OffsetDateTime appointmentAt = toOffsetDateTime(appointment.appointmentDate(), appointment.appointmentTime());
                for (long hours : List.of(24L, 2L)) {
                    OffsetDateTime reminderAt = appointmentAt.minusHours(hours);
                    if (reminderAt.isBefore(OffsetDateTime.now().minusMinutes(30))) {
                        continue;
                    }
                    if (existsDuplicate(tenantId, binding.get().campaign().getId(), appointment.patientId(), binding.get().template().getChannelType(), reminderAt)) {
                        continue;
                    }
                    executionService.create(tenantId, new CampaignExecutionCreateCommand(
                            binding.get().campaign().getId(),
                            binding.get().template().getId(),
                            binding.get().template().getChannelType(),
                            appointment.patientId(),
                            reminderAt
                    ));
                    queued += 1;
                }
            }
        }
        return queued;
    }

    private int queueFollowUpReminders(UUID tenantId) {
        Optional<CampaignTemplateBinding> binding = activeEmailCampaignBinding(tenantId, CampaignType.FOLLOW_UP_REMINDER);
        if (binding.isEmpty()) {
            return 0;
        }
        int queued = 0;
        LocalDate today = LocalDate.now();
        for (PrescriptionRecord prescription : prescriptionService.list(tenantId)) {
            if (queued >= batchSize) {
                break;
            }
            if (prescription.followUpDate() == null || prescription.followUpDate().isAfter(today.plusDays(1))) {
                continue;
            }
            if (prescription.status() == PrescriptionStatus.CANCELLED) {
                continue;
            }
            PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, prescription.patientId()).orElse(null);
            if (!hasEmail(patient)) {
                continue;
            }
            OffsetDateTime reminderAt = prescription.followUpDate().atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
            if (existsDuplicate(tenantId, binding.get().campaign().getId(), prescription.patientId(), binding.get().template().getChannelType(), reminderAt)) {
                continue;
            }
            executionService.create(tenantId, new CampaignExecutionCreateCommand(
                    binding.get().campaign().getId(),
                    binding.get().template().getId(),
                    binding.get().template().getChannelType(),
                    prescription.patientId(),
                    reminderAt
            ));
            queued += 1;
        }
        return queued;
    }

    private int queueRefillReminders(UUID tenantId) {
        Optional<CampaignTemplateBinding> binding = activeEmailCampaignBinding(tenantId, CampaignType.REFILL_REMINDER);
        if (binding.isEmpty()) {
            return 0;
        }
        int queued = 0;
        LocalDate today = LocalDate.now();
        for (PrescriptionRecord prescription : prescriptionService.list(tenantId)) {
            if (queued >= batchSize) {
                break;
            }
            if (prescription.finalizedAt() == null || prescription.status() == PrescriptionStatus.CANCELLED) {
                continue;
            }
            LocalDate refillDue = prescription.finalizedAt().toLocalDate().plusDays(refillEstimatedDays);
            if (refillDue.isAfter(today.plusDays(1))) {
                continue;
            }
            PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, prescription.patientId()).orElse(null);
            if (!hasEmail(patient)) {
                continue;
            }
            OffsetDateTime reminderAt = refillDue.atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
            if (existsDuplicate(tenantId, binding.get().campaign().getId(), prescription.patientId(), binding.get().template().getChannelType(), reminderAt)) {
                continue;
            }
            executionService.create(tenantId, new CampaignExecutionCreateCommand(
                    binding.get().campaign().getId(),
                    binding.get().template().getId(),
                    binding.get().template().getChannelType(),
                    prescription.patientId(),
                    reminderAt
            ));
            queued += 1;
        }
        return queued;
    }

    private int queueVaccinationReminders(UUID tenantId) {
        Optional<CampaignTemplateBinding> binding = activeEmailCampaignBinding(tenantId, CampaignType.VACCINATION_REMINDER);
        if (binding.isEmpty()) {
            return 0;
        }
        int queued = 0;
        for (PatientVaccinationRecord vaccination : vaccinationService.listDue(tenantId)) {
            if (queued >= batchSize) {
                break;
            }
            PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, vaccination.patientId()).orElse(null);
            if (!hasEmail(patient)) {
                continue;
            }
            OffsetDateTime reminderAt = vaccination.nextDueDate() == null
                    ? OffsetDateTime.now()
                    : vaccination.nextDueDate().atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
            if (existsDuplicate(tenantId, binding.get().campaign().getId(), vaccination.patientId(), binding.get().template().getChannelType(), reminderAt)) {
                continue;
            }
            executionService.create(tenantId, new CampaignExecutionCreateCommand(
                    binding.get().campaign().getId(),
                    binding.get().template().getId(),
                    binding.get().template().getChannelType(),
                    vaccination.patientId(),
                    reminderAt
            ));
            queued += 1;
        }
        return queued;
    }

    private int queueBillingReminders(UUID tenantId) {
        Optional<CampaignTemplateBinding> binding = activeEmailCampaignBinding(tenantId, CampaignType.BILLING_REMINDER);
        if (binding.isEmpty()) {
            return 0;
        }
        int queued = 0;
        LocalDate thresholdDate = LocalDate.now().minusDays(billingOverdueDays);
        for (BillRecord bill : billingService.list(tenantId, new BillingSearchCriteria(null, null, null, null, null))) {
            if (queued >= batchSize) {
                break;
            }
            if (bill.status() == BillStatus.PAID || bill.status() == BillStatus.REFUNDED || bill.status() == BillStatus.CANCELLED) {
                continue;
            }
            if (bill.dueAmount() == null || bill.dueAmount().signum() <= 0) {
                continue;
            }
            if (bill.billDate() != null && bill.billDate().isAfter(thresholdDate)) {
                continue;
            }
            PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, bill.patientId()).orElse(null);
            if (!hasEmail(patient)) {
                continue;
            }
            OffsetDateTime reminderAt = (bill.billDate() == null ? LocalDate.now() : bill.billDate())
                    .atStartOfDay()
                    .atOffset(OffsetDateTime.now().getOffset());
            if (existsDuplicate(tenantId, binding.get().campaign().getId(), bill.patientId(), binding.get().template().getChannelType(), reminderAt)) {
                continue;
            }
            executionService.create(tenantId, new CampaignExecutionCreateCommand(
                    binding.get().campaign().getId(),
                    binding.get().template().getId(),
                    binding.get().template().getChannelType(),
                    bill.patientId(),
                    reminderAt
            ));
            queued += 1;
        }
        return queued;
    }

    private Optional<CampaignTemplateBinding> activeEmailCampaignBinding(UUID tenantId, CampaignType campaignType) {
        Optional<CampaignEntity> campaign = campaignRepository.findFirstByTenantIdAndCampaignTypeAndActiveTrueOrderByUpdatedAtDesc(tenantId, campaignType);
        if (campaign.isEmpty() || campaign.get().getTemplateId() == null) {
            return Optional.empty();
        }
        Optional<CampaignTemplateEntity> template = templateRepository.findByTenantIdAndId(tenantId, campaign.get().getTemplateId());
        if (template.isEmpty() || !template.get().isActive() || template.get().getChannelType() != ChannelType.EMAIL) {
            return Optional.empty();
        }
        return Optional.of(new CampaignTemplateBinding(campaign.get(), template.get()));
    }

    private boolean existsDuplicate(UUID tenantId, UUID campaignId, UUID patientId, ChannelType channelType, OffsetDateTime scheduledAt) {
        return executionRepository.existsByTenantIdAndCampaignIdAndRecipientPatientIdAndChannelTypeAndScheduledAtBetween(
                tenantId,
                campaignId,
                patientId,
                channelType,
                scheduledAt.minusMinutes(5),
                scheduledAt.plusMinutes(5)
        );
    }

    private boolean hasEmail(PatientEntity patient) {
        return patient != null && StringUtils.hasText(patient.getEmail());
    }

    private OffsetDateTime toOffsetDateTime(LocalDate date, LocalTime time) {
        LocalDateTime localDateTime = LocalDateTime.of(date, time);
        return localDateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private record CampaignTemplateBinding(CampaignEntity campaign, CampaignTemplateEntity template) {
    }
}
