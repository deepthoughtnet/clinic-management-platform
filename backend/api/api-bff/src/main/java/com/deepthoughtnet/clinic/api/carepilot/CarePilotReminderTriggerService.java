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
import com.deepthoughtnet.clinic.carepilot.lead.activity.model.LeadActivityType;
import com.deepthoughtnet.clinic.carepilot.lead.activity.service.LeadActivityService;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadRepository;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateEntity;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateRepository;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarRegistrationRepository;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarRepository;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRegistrationStatus;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarStatus;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionStatus;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import com.deepthoughtnet.clinic.vaccination.service.model.PatientVaccinationRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(CarePilotReminderTriggerService.class);
    private static final String TRIGGER_CONFIG_MARKER = "[Trigger Config]";
    private static final Duration DEFAULT_MISSED_APPOINTMENT_DELAY = Duration.ofHours(2);
    private static final Duration DEFAULT_FOLLOW_UP_OFFSET = Duration.ofHours(24);
    private static final int DEFAULT_REFILL_REMINDER_OFFSET_DAYS = 0;
    private static final int DEFAULT_BILL_REMINDER_FREQUENCY_DAYS = 0;
    private static final int DEFAULT_VACCINATION_REMINDER_OFFSET_DAYS = 1;
    private static final boolean DEFAULT_VACCINATION_INCLUDE_OVERDUE = true;
    private static final int DEFAULT_VACCINATION_OVERDUE_GRACE_DAYS = 0;
    private static final int DEFAULT_BIRTHDAY_DAYS_BEFORE = 0;
    private static final LocalTime DEFAULT_BIRTHDAY_SEND_TIME_LOCAL = LocalTime.of(9, 0);
    private static final Pattern DURATION_TOKEN_PATTERN = Pattern.compile("(?i)\\b(\\d+)\\s*(day|days|week|weeks|month|months|d|w|m)\\b");

    private final PlatformTenantManagementService tenantManagementService;
    private final CarePilotRuntimeSchedulerMonitor runtimeSchedulerMonitor;
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
    private final LeadRepository leadRepository;
    private final LeadActivityService leadActivityService;
    private final WebinarRepository webinarRepository;
    private final WebinarRegistrationRepository webinarRegistrationRepository;
    private final ObjectMapper objectMapper;
    private final int refillEstimatedDays;
    private final int billingOverdueDays;
    private final int batchSize;

    public CarePilotReminderTriggerService(
            PlatformTenantManagementService tenantManagementService,
            CarePilotRuntimeSchedulerMonitor runtimeSchedulerMonitor,
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
            LeadRepository leadRepository,
            LeadActivityService leadActivityService,
            WebinarRepository webinarRepository,
            WebinarRegistrationRepository webinarRegistrationRepository,
            ObjectMapper objectMapper,
            @Value("${carepilot.reminders.refill-estimated-days:30}") int refillEstimatedDays,
            @Value("${carepilot.reminders.billing-overdue-days:3}") int billingOverdueDays,
            @Value("${carepilot.reminders.batch-size:100}") int batchSize
    ) {
        this.tenantManagementService = tenantManagementService;
        this.runtimeSchedulerMonitor = runtimeSchedulerMonitor;
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
        this.leadRepository = leadRepository;
        this.leadActivityService = leadActivityService;
        this.webinarRepository = webinarRepository;
        this.webinarRegistrationRepository = webinarRegistrationRepository;
        this.objectMapper = objectMapper;
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
            runtimeSchedulerMonitor.markReminderScan(tenantId, OffsetDateTime.now());
            if (!featureFlagService.carePilotForTenant(tenantId).carePilotEnabled()) {
                continue;
            }

            int tenantQueued = 0;
            tenantQueued += queueAppointmentReminders(tenantId);
            tenantQueued += queueMissedAppointmentFollowUps(tenantId);
            tenantQueued += queueFollowUpReminders(tenantId);
            tenantQueued += queueRefillReminders(tenantId);
            tenantQueued += queueVaccinationReminders(tenantId);
            tenantQueued += queueBillingReminders(tenantId);
            tenantQueued += queueBirthdayWellnessMessages(tenantId);
            tenantQueued += queueLeadFollowUpOperationalReminders(tenantId);
            tenantQueued += queueWebinarReminders(tenantId);
            tenantQueued += queueWebinarFollowUps(tenantId);

            queued += tenantQueued;
            log.info("CarePilot scheduler tenantId={} queued={}", tenantId, tenantQueued);
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
                    String reminderWindow = hours == 24L ? "H24" : "H2";
                    if (reminderAt.isBefore(OffsetDateTime.now().minusMinutes(30))) {
                        continue;
                    }
                    if (existsAppointmentWindowDuplicate(
                            tenantId,
                            binding.get().campaign().getId(),
                            appointment.id(),
                            reminderWindow,
                            binding.get().template().getChannelType()
                    )) {
                        continue;
                    }
                    if (existsDuplicate(tenantId, binding.get().campaign().getId(), appointment.patientId(), binding.get().template().getChannelType(), reminderAt)) {
                        continue;
                    }
                    if (createExecutionSafely(tenantId, new CampaignExecutionCreateCommand(
                            binding.get().campaign().getId(),
                            binding.get().template().getId(),
                            binding.get().template().getChannelType(),
                            appointment.patientId(),
                            reminderAt,
                            "APPOINTMENT",
                            appointment.id(),
                            reminderWindow,
                            appointmentAt
                    ))) {
                        queued += 1;
                    }
                }
            }
        }
        return queued;
    }

    /**
     * Operational follow-up reminder foundation for leads.
     * This records tenant-scoped timeline reminder entries without forcing patient-facing send when recipient model is patient-only.
     */
    private int queueLeadFollowUpOperationalReminders(UUID tenantId) {
        int queued = 0;
        var dueRows = leadRepository.findByTenantIdAndNextFollowUpAtLessThanEqualAndStatusNotIn(
                tenantId,
                OffsetDateTime.now(),
                List.of(LeadStatus.CONVERTED, LeadStatus.LOST, LeadStatus.SPAM)
        );
        for (var lead : dueRows) {
            if (queued >= batchSize) {
                break;
            }
            if (lead.getNextFollowUpAt() == null) {
                continue;
            }
            UUID marker = UUID.nameUUIDFromBytes(
                    ("LEAD_FOLLOW_UP_REMINDER|" + lead.getId() + "|" + lead.getNextFollowUpAt().toLocalDate())
                            .getBytes(StandardCharsets.UTF_8)
            );
            if (leadActivityService.existsScheduleMarker(tenantId, lead.getId(), marker)) {
                continue;
            }
            leadActivityService.record(
                    tenantId,
                    lead.getId(),
                    LeadActivityType.FOLLOW_UP_SCHEDULED,
                    "Lead follow-up reminder due",
                    "Operational reminder for scheduled lead follow-up",
                    null,
                    null,
                    "LEAD_FOLLOW_UP",
                    marker,
                    null
            );
            queued += 1;
        }
        return queued;
    }

    private int queueMissedAppointmentFollowUps(UUID tenantId) {
        Optional<CampaignTemplateBinding> binding = activeEmailCampaignBinding(tenantId, CampaignType.MISSED_APPOINTMENT_FOLLOW_UP);
        if (binding.isEmpty()) {
            return 0;
        }

        int queued = 0;
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> config = parseTriggerConfig(binding.get().campaign());
        Duration delay = parseDurationConfig(config, "delayAfterMissed", DEFAULT_MISSED_APPOINTMENT_DELAY);
        String reminderWindow = durationWindowToken("MISSED", delay);

        for (LocalDate date : List.of(LocalDate.now().minusDays(1), LocalDate.now(), LocalDate.now().plusDays(1))) {
            List<AppointmentRecord> appointments = appointmentService.search(tenantId, new AppointmentSearchCriteria(null, null, date, AppointmentStatus.NO_SHOW, null));
            for (AppointmentRecord appointment : appointments) {
                if (queued >= batchSize) {
                    return queued;
                }
                if (appointment.status() != AppointmentStatus.NO_SHOW) {
                    continue;
                }
                if (appointment.appointmentTime() == null) {
                    continue;
                }
                PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, appointment.patientId()).orElse(null);
                if (!hasEmail(patient)) {
                    continue;
                }

                OffsetDateTime appointmentAt = toOffsetDateTime(appointment.appointmentDate(), appointment.appointmentTime());
                OffsetDateTime reminderAt = appointmentAt.plus(delay);
                if (reminderAt.isBefore(now.minusMinutes(30)) || reminderAt.isAfter(now.plusDays(3))) {
                    continue;
                }

                if (existsAppointmentWindowDuplicate(
                        tenantId,
                        binding.get().campaign().getId(),
                        appointment.id(),
                        reminderWindow,
                        binding.get().template().getChannelType()
                )) {
                    continue;
                }
                if (existsDuplicate(tenantId, binding.get().campaign().getId(), appointment.patientId(), binding.get().template().getChannelType(), reminderAt)) {
                    continue;
                }

                if (createExecutionSafely(tenantId, new CampaignExecutionCreateCommand(
                        binding.get().campaign().getId(),
                        binding.get().template().getId(),
                        binding.get().template().getChannelType(),
                        appointment.patientId(),
                        reminderAt,
                        "APPOINTMENT",
                        appointment.id(),
                        reminderWindow,
                        appointmentAt
                ))) {
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
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> config = parseTriggerConfig(binding.get().campaign());
        Duration offset = parseDurationConfig(config, "reminderOffset", DEFAULT_FOLLOW_UP_OFFSET);
        String reminderWindow = durationWindowToken("FUP", offset);

        for (PrescriptionRecord prescription : prescriptionService.list(tenantId)) {
            if (queued >= batchSize) {
                break;
            }
            if (prescription.followUpDate() == null) {
                continue;
            }
            if (prescription.status() == PrescriptionStatus.CANCELLED) {
                continue;
            }

            PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, prescription.patientId()).orElse(null);
            if (!hasEmail(patient)) {
                continue;
            }

            OffsetDateTime followUpAt = prescription.followUpDate().atStartOfDay().atOffset(ZoneOffset.UTC);
            OffsetDateTime reminderAt = followUpAt.minus(offset);
            if (reminderAt.isBefore(now.minusMinutes(30)) || reminderAt.isAfter(now.plusDays(7))) {
                continue;
            }

            if (executionRepository.existsByTenantIdAndCampaignIdAndSourceReferenceIdAndReminderWindowAndChannelType(
                    tenantId,
                    binding.get().campaign().getId(),
                    prescription.id(),
                    reminderWindow,
                    binding.get().template().getChannelType()
            )) {
                continue;
            }
            if (existsDuplicate(tenantId, binding.get().campaign().getId(), prescription.patientId(), binding.get().template().getChannelType(), reminderAt)) {
                continue;
            }

            if (createExecutionSafely(tenantId, new CampaignExecutionCreateCommand(
                    binding.get().campaign().getId(),
                    binding.get().template().getId(),
                    binding.get().template().getChannelType(),
                    prescription.patientId(),
                    reminderAt,
                    "FOLLOW_UP",
                    prescription.id(),
                    reminderWindow,
                    followUpAt
            ))) {
                queued += 1;
            }
        }

        return queued;
    }

    private int queueRefillReminders(UUID tenantId) {
        Optional<CampaignTemplateBinding> binding = activeEmailCampaignBinding(tenantId, CampaignType.REFILL_REMINDER);
        if (binding.isEmpty()) {
            return 0;
        }
        Map<String, Object> config = parseTriggerConfig(binding.get().campaign());
        int configuredEstimatedRefillDays = parseIntConfig(config, "estimatedRefillDays", refillEstimatedDays, 1, 365);
        int reminderOffsetDays = parseIntConfig(config, "reminderOffset", DEFAULT_REFILL_REMINDER_OFFSET_DAYS, 0, 60);
        int queued = 0;
        int scanned = 0;
        int eligible = 0;
        int skipped = 0;
        int failed = 0;
        LocalDate today = LocalDate.now();
        for (PrescriptionRecord prescription : prescriptionService.list(tenantId)) {
            if (queued >= batchSize) {
                break;
            }
            scanned += 1;
            if (prescription.finalizedAt() == null || prescription.status() == PrescriptionStatus.CANCELLED) {
                skipped += 1;
                continue;
            }
            LocalDate finalizedDate = prescription.finalizedAt().toLocalDate();
            long refillDays = resolveRefillDays(prescription, configuredEstimatedRefillDays);
            LocalDate refillDueDate = finalizedDate.plusDays(refillDays);
            LocalDate reminderDate = refillDueDate.minusDays(reminderOffsetDays);
            if (reminderDate.isAfter(today.plusDays(1))) {
                skipped += 1;
                continue;
            }
            PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, prescription.patientId()).orElse(null);
            if (!hasEmail(patient)) {
                skipped += 1;
                continue;
            }
            String reminderWindow = "REFILL_DUE_" + refillDueDate + "_OFF_" + reminderOffsetDays;
            if (executionRepository.existsByTenantIdAndCampaignIdAndSourceReferenceIdAndReminderWindowAndChannelType(
                    tenantId,
                    binding.get().campaign().getId(),
                    prescription.id(),
                    reminderWindow,
                    binding.get().template().getChannelType()
            )) {
                skipped += 1;
                continue;
            }
            OffsetDateTime reminderAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MINUTES);
            eligible += 1;
            if (createExecutionSafely(tenantId, new CampaignExecutionCreateCommand(
                    binding.get().campaign().getId(),
                    binding.get().template().getId(),
                    binding.get().template().getChannelType(),
                    prescription.patientId(),
                    reminderAt,
                    "PRESCRIPTION",
                    prescription.id(),
                    reminderWindow,
                    refillDueDate.atStartOfDay().atOffset(ZoneOffset.UTC)
            ))) {
                queued += 1;
            } else {
                failed += 1;
            }
        }
        log.info(
                "CarePilot refill scan tenantId={} scanned={} eligible={} created={} skipped={} failed={}",
                tenantId,
                scanned,
                eligible,
                queued,
                skipped,
                failed
        );
        return queued;
    }

    private int queueVaccinationReminders(UUID tenantId) {
        Optional<CampaignTemplateBinding> binding = activeEmailCampaignBinding(tenantId, CampaignType.VACCINATION_REMINDER);
        if (binding.isEmpty()) {
            return 0;
        }
        Map<String, Object> config = parseTriggerConfig(binding.get().campaign());
        int reminderOffsetDays = parseIntConfig(config, "reminderOffset", DEFAULT_VACCINATION_REMINDER_OFFSET_DAYS, 0, 90);
        boolean includeOverdue = parseBooleanConfig(config, "includeOverdue", DEFAULT_VACCINATION_INCLUDE_OVERDUE);
        int overdueGraceDays = parseIntConfig(config, "overdueGraceDays", DEFAULT_VACCINATION_OVERDUE_GRACE_DAYS, 0, 365);

        int queued = 0;
        int scanned = 0;
        int eligible = 0;
        int skipped = 0;
        int failed = 0;
        LocalDate today = LocalDate.now();
        List<PatientVaccinationRecord> candidates = includeOverdue
                ? mergeVaccinationCandidates(vaccinationService.listDue(tenantId), vaccinationService.listOverdue(tenantId))
                : vaccinationService.listDue(tenantId);

        for (PatientVaccinationRecord vaccination : candidates) {
            if (queued >= batchSize) {
                break;
            }
            scanned += 1;
            if (vaccination.nextDueDate() == null) {
                skipped += 1;
                continue;
            }
            LocalDate reminderDate = vaccination.nextDueDate().minusDays(reminderOffsetDays);
            if (reminderDate.isAfter(today)) {
                skipped += 1;
                continue;
            }
            if (!includeOverdue && reminderDate.isBefore(today)) {
                skipped += 1;
                continue;
            }
            if (includeOverdue && overdueGraceDays > 0 && vaccination.nextDueDate().isBefore(today.minusDays(overdueGraceDays))) {
                skipped += 1;
                continue;
            }
            PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, vaccination.patientId()).orElse(null);
            if (!hasEmail(patient)) {
                skipped += 1;
                continue;
            }
            String statusToken = vaccination.nextDueDate().isBefore(today) ? "OVERDUE" : "DUE";
            String reminderWindow = "VAX_" + statusToken + "_" + vaccination.nextDueDate() + "_OFF_" + reminderOffsetDays;
            if (executionRepository.existsByTenantIdAndCampaignIdAndSourceReferenceIdAndReminderWindowAndChannelType(
                    tenantId,
                    binding.get().campaign().getId(),
                    vaccination.id(),
                    reminderWindow,
                    binding.get().template().getChannelType()
            )) {
                skipped += 1;
                continue;
            }
            OffsetDateTime reminderAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MINUTES);
            eligible += 1;
            if (createExecutionSafely(tenantId, new CampaignExecutionCreateCommand(
                    binding.get().campaign().getId(),
                    binding.get().template().getId(),
                    binding.get().template().getChannelType(),
                    vaccination.patientId(),
                    reminderAt,
                    "VACCINATION",
                    vaccination.id(),
                    reminderWindow,
                    vaccination.nextDueDate().atStartOfDay().atOffset(ZoneOffset.UTC)
            ))) {
                queued += 1;
            } else {
                failed += 1;
            }
        }
        log.info(
                "CarePilot vaccination scan tenantId={} scanned={} eligible={} created={} skipped={} failed={} reminderOffsetDays={} includeOverdue={} overdueGraceDays={}",
                tenantId,
                scanned,
                eligible,
                queued,
                skipped,
                failed,
                reminderOffsetDays,
                includeOverdue,
                overdueGraceDays
        );
        return queued;
    }

    private int queueBirthdayWellnessMessages(UUID tenantId) {
        Optional<CampaignTemplateBinding> binding = activeEmailCampaignBinding(tenantId, CampaignType.WELLNESS_MESSAGE);
        if (binding.isEmpty()) {
            return 0;
        }
        Map<String, Object> config = parseTriggerConfig(binding.get().campaign());
        int daysBeforeBirthday = parseIntConfig(config, "daysBeforeBirthday", DEFAULT_BIRTHDAY_DAYS_BEFORE, 0, 30);
        LocalTime sendTimeLocal = parseLocalTimeConfig(config.get("sendTimeLocal"), DEFAULT_BIRTHDAY_SEND_TIME_LOCAL);
        LocalDate targetBirthday = LocalDate.now().plusDays(daysBeforeBirthday);

        int queued = 0;
        int scanned = 0;
        int eligible = 0;
        int skipped = 0;
        int failed = 0;
        OffsetDateTime reminderAt = targetBirthday.atTime(sendTimeLocal).atZone(ZoneId.systemDefault()).toOffsetDateTime();

        for (PatientEntity patient : patientRepository.findByTenantIdAndActiveTrue(tenantId)) {
            if (queued >= batchSize) {
                break;
            }
            scanned += 1;
            if (patient.getDateOfBirth() == null) {
                skipped += 1;
                continue;
            }
            if (!hasEmail(patient)) {
                skipped += 1;
                continue;
            }
            if (patient.getDateOfBirth().getMonthValue() != targetBirthday.getMonthValue()
                    || patient.getDateOfBirth().getDayOfMonth() != targetBirthday.getDayOfMonth()) {
                skipped += 1;
                continue;
            }
            String reminderWindow = "BDAY_" + targetBirthday.getYear();
            if (executionRepository.existsByTenantIdAndCampaignIdAndSourceReferenceIdAndReminderWindowAndChannelType(
                    tenantId,
                    binding.get().campaign().getId(),
                    patient.getId(),
                    reminderWindow,
                    binding.get().template().getChannelType()
            )) {
                skipped += 1;
                continue;
            }
            eligible += 1;
            if (createExecutionSafely(tenantId, new CampaignExecutionCreateCommand(
                    binding.get().campaign().getId(),
                    binding.get().template().getId(),
                    binding.get().template().getChannelType(),
                    patient.getId(),
                    reminderAt,
                    "PATIENT_BIRTHDAY",
                    patient.getId(),
                    reminderWindow,
                    targetBirthday.atStartOfDay().atOffset(ZoneOffset.UTC)
            ))) {
                queued += 1;
            } else {
                failed += 1;
            }
        }
        log.info(
                "CarePilot birthday wellness scan tenantId={} scanned={} eligible={} created={} skipped={} failed={} daysBeforeBirthday={} sendTimeLocal={}",
                tenantId,
                scanned,
                eligible,
                queued,
                skipped,
                failed,
                daysBeforeBirthday,
                sendTimeLocal
        );
        return queued;
    }

    private int queueBillingReminders(UUID tenantId) {
        Optional<CampaignTemplateBinding> binding = activeEmailCampaignBinding(tenantId, CampaignType.BILLING_REMINDER);
        if (binding.isEmpty()) {
            return 0;
        }
        Map<String, Object> config = parseTriggerConfig(binding.get().campaign());
        int overdueDays = parseIntConfig(config, "overdueDays", billingOverdueDays, 0, 365);
        int reminderFrequencyDays = parseIntConfig(config, "reminderFrequencyDays", DEFAULT_BILL_REMINDER_FREQUENCY_DAYS, 0, 365);
        Collection<BillStatus> targetStatuses = parseBillStatusConfig(config.get("targetStatuses"));
        int queued = 0;
        int scanned = 0;
        int eligible = 0;
        int skipped = 0;
        int failed = 0;
        LocalDate today = LocalDate.now();
        for (BillRecord bill : billingService.list(tenantId, new BillingSearchCriteria(null, null, null, null, null))) {
            if (queued >= batchSize) {
                break;
            }
            scanned += 1;
            if (bill.status() == null || !targetStatuses.contains(bill.status())) {
                skipped += 1;
                continue;
            }
            if (bill.dueAmount() == null || bill.dueAmount().signum() <= 0) {
                skipped += 1;
                continue;
            }
            if (bill.billDate() == null) {
                skipped += 1;
                continue;
            }
            long daysOverdue = ChronoUnit.DAYS.between(bill.billDate(), today);
            if (daysOverdue < overdueDays) {
                skipped += 1;
                continue;
            }
            long overdueBeyondThreshold = daysOverdue - overdueDays;
            if (reminderFrequencyDays > 0 && overdueBeyondThreshold % reminderFrequencyDays != 0) {
                skipped += 1;
                continue;
            }
            PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, bill.patientId()).orElse(null);
            if (!hasEmail(patient)) {
                skipped += 1;
                continue;
            }
            String reminderWindow = "OD" + overdueDays + "_DUE_" + bill.billDate()
                    + (reminderFrequencyDays > 0 ? "_FREQ_" + reminderFrequencyDays + "_STEP_" + (overdueBeyondThreshold / reminderFrequencyDays) : "");
            if (executionRepository.existsByTenantIdAndCampaignIdAndSourceReferenceIdAndReminderWindowAndChannelType(
                    tenantId,
                    binding.get().campaign().getId(),
                    bill.id(),
                    reminderWindow,
                    binding.get().template().getChannelType()
            )) {
                skipped += 1;
                continue;
            }
            OffsetDateTime reminderAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MINUTES);
            eligible += 1;
            if (createExecutionSafely(tenantId, new CampaignExecutionCreateCommand(
                    binding.get().campaign().getId(),
                    binding.get().template().getId(),
                    binding.get().template().getChannelType(),
                    bill.patientId(),
                    reminderAt,
                    "BILL",
                    bill.id(),
                    reminderWindow,
                    bill.billDate().atStartOfDay().atOffset(ZoneOffset.UTC)
            ))) {
                queued += 1;
            } else {
                failed += 1;
            }
        }
        log.info(
                "CarePilot billing scan tenantId={} scanned={} eligible={} created={} skipped={} failed={} overdueDays={} frequencyDays={}",
                tenantId,
                scanned,
                eligible,
                queued,
                skipped,
                failed,
                overdueDays,
                reminderFrequencyDays
        );
        return queued;
    }

    private Optional<CampaignTemplateBinding> activeEmailCampaignBinding(UUID tenantId, CampaignType campaignType) {
        Optional<CampaignEntity> campaign = campaignRepository.findFirstByTenantIdAndCampaignTypeAndActiveTrueOrderByUpdatedAtDesc(tenantId, campaignType);
        if (campaign.isEmpty() || campaign.get().getTemplateId() == null) {
            return Optional.empty();
        }
        Optional<CampaignTemplateEntity> template = templateRepository.findByTenantIdAndId(tenantId, campaign.get().getTemplateId());
        if (template.isEmpty() || !template.get().isActive()) {
            return Optional.empty();
        }
        return Optional.of(new CampaignTemplateBinding(campaign.get(), template.get()));
    }

    private int queueWebinarReminders(UUID tenantId) {
        Optional<CampaignTemplateBinding> reminderBinding = activeEmailCampaignBinding(tenantId, CampaignType.WEBINAR_REMINDER);
        Optional<CampaignTemplateBinding> confirmationBinding = activeEmailCampaignBinding(tenantId, CampaignType.WEBINAR_CONFIRMATION);
        if (reminderBinding.isEmpty() && confirmationBinding.isEmpty()) {
            return 0;
        }
        int queued = 0;
        var webinars = webinarRepository.findByTenantIdAndStatusInAndScheduledStartAtBetween(
                tenantId,
                List.of(WebinarStatus.SCHEDULED, WebinarStatus.LIVE),
                OffsetDateTime.now().minusDays(2),
                OffsetDateTime.now().plusDays(7)
        );
        for (var webinar : webinars) {
            if (!webinar.isReminderEnabled() || webinar.getStatus() == WebinarStatus.CANCELLED) {
                continue;
            }
            var registrations = webinarRegistrationRepository.findByTenantIdAndWebinarIdAndRegistrationStatusNot(
                    tenantId,
                    webinar.getId(),
                    WebinarRegistrationStatus.CANCELLED
            );
            for (var registration : registrations) {
                if (queued >= batchSize) {
                    return queued;
                }
                if (registration.getPatientId() == null) {
                    continue;
                }
                if (confirmationBinding.isPresent() && registration.getCreatedAt().isAfter(OffsetDateTime.now().minusHours(24))) {
                    queued += createWebinarExecutionIfMissing(
                            tenantId,
                            confirmationBinding.get(),
                            registration.getPatientId(),
                            registration.getId(),
                            "WEBINAR_CONFIRMATION",
                            registration.getCreatedAt()
                    ) ? 1 : 0;
                }
                if (reminderBinding.isPresent()) {
                    OffsetDateTime at24 = webinar.getScheduledStartAt().minusHours(24);
                    OffsetDateTime at1 = webinar.getScheduledStartAt().minusHours(1);
                    queued += createWebinarExecutionIfMissing(
                            tenantId,
                            reminderBinding.get(),
                            registration.getPatientId(),
                            registration.getId(),
                            "WEBINAR_H24",
                            at24
                    ) ? 1 : 0;
                    queued += createWebinarExecutionIfMissing(
                            tenantId,
                            reminderBinding.get(),
                            registration.getPatientId(),
                            registration.getId(),
                            "WEBINAR_H1",
                            at1
                    ) ? 1 : 0;
                }
            }
        }
        return queued;
    }

    private int queueWebinarFollowUps(UUID tenantId) {
        Optional<CampaignTemplateBinding> followupBinding = activeEmailCampaignBinding(tenantId, CampaignType.WEBINAR_FOLLOW_UP);
        if (followupBinding.isEmpty()) {
            return 0;
        }
        int queued = 0;
        var webinars = webinarRepository.findByTenantIdAndStatusInAndScheduledStartAtBetween(
                tenantId,
                List.of(WebinarStatus.COMPLETED),
                OffsetDateTime.now().minusDays(7),
                OffsetDateTime.now()
        );
        for (var webinar : webinars) {
            if (!webinar.isFollowupEnabled()) {
                continue;
            }
            var registrations = webinarRegistrationRepository.findByTenantIdAndWebinarIdAndRegistrationStatusNot(
                    tenantId,
                    webinar.getId(),
                    WebinarRegistrationStatus.CANCELLED
            );
            for (var registration : registrations) {
                if (queued >= batchSize || registration.getPatientId() == null) {
                    continue;
                }
                String window = registration.isAttended() ? "WEBINAR_ATTENDED_FOLLOWUP" : "WEBINAR_MISSED_FOLLOWUP";
                OffsetDateTime sendAt = webinar.getScheduledEndAt().plusHours(2);
                queued += createWebinarExecutionIfMissing(
                        tenantId,
                        followupBinding.get(),
                        registration.getPatientId(),
                        registration.getId(),
                        window,
                        sendAt
                ) ? 1 : 0;
            }
        }
        return queued;
    }

    private boolean createWebinarExecutionIfMissing(
            UUID tenantId,
            CampaignTemplateBinding binding,
            UUID patientId,
            UUID registrationId,
            String window,
            OffsetDateTime scheduledAt
    ) {
        if (scheduledAt.isBefore(OffsetDateTime.now().minusHours(2))) {
            return false;
        }
        if (executionRepository.existsByTenantIdAndCampaignIdAndSourceReferenceIdAndReminderWindowAndChannelType(
                tenantId,
                binding.campaign().getId(),
                registrationId,
                window,
                binding.template().getChannelType()
        )) {
            return false;
        }
        if (existsDuplicate(tenantId, binding.campaign().getId(), patientId, binding.template().getChannelType(), scheduledAt)) {
            return false;
        }
        return createExecutionSafely(tenantId, new CampaignExecutionCreateCommand(
                binding.campaign().getId(),
                binding.template().getId(),
                binding.template().getChannelType(),
                patientId,
                scheduledAt,
                "WEBINAR_REGISTRATION",
                registrationId,
                window,
                scheduledAt
        ));
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

    /**
     * Detects reminder duplicates using appointment or follow-up reference + reminder window for scheduler idempotency.
     */
    private boolean existsAppointmentWindowDuplicate(
            UUID tenantId,
            UUID campaignId,
            UUID appointmentId,
            String reminderWindow,
            ChannelType channelType
    ) {
        return executionRepository.existsByTenantIdAndCampaignIdAndSourceReferenceIdAndReminderWindowAndChannelType(
                tenantId,
                campaignId,
                appointmentId,
                reminderWindow,
                channelType
        );
    }

    private boolean hasEmail(PatientEntity patient) {
        return patient != null && StringUtils.hasText(patient.getEmail());
    }

    private boolean createExecutionSafely(UUID tenantId, CampaignExecutionCreateCommand command) {
        try {
            executionService.create(tenantId, command);
            return true;
        } catch (RuntimeException ex) {
            log.warn(
                    "CarePilot reminder execution create failed. tenantId={}, campaignId={}, sourceType={}, sourceReferenceId={}, reason={}",
                    tenantId,
                    command.campaignId(),
                    command.sourceType(),
                    command.sourceReferenceId(),
                    ex.getMessage()
            );
            return false;
        }
    }

    private Map<String, Object> parseTriggerConfig(CampaignEntity campaign) {
        if (campaign == null || !StringUtils.hasText(campaign.getNotes())) {
            return Map.of();
        }
        String notes = campaign.getNotes();
        int marker = notes.indexOf(TRIGGER_CONFIG_MARKER);
        if (marker < 0) {
            return Map.of();
        }
        String json = notes.substring(marker + TRIGGER_CONFIG_MARKER.length()).trim();
        if (json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ex) {
            log.debug("Failed to parse CarePilot trigger config. campaignId={}, reason={}", campaign.getId(), ex.getMessage());
            return Map.of();
        }
    }

    private Duration parseDurationConfig(Map<String, Object> config, String key, Duration defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            if ("delayAfterMissed".equals(key) && config.get("reminderOffset") != null) {
                return parseDurationValue(config.get("reminderOffset"), defaultValue);
            }
            return defaultValue;
        }
        return parseDurationValue(value, defaultValue);
    }

    private Duration parseDurationValue(Object value, Duration defaultValue) {
        if (value instanceof Number num) {
            return Duration.ofMinutes(Math.max(1L, num.longValue()));
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Duration.parse(text.trim());
            } catch (DateTimeParseException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private boolean parseBooleanConfig(Map<String, Object> config, String key, boolean defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            String normalized = text.trim().toLowerCase();
            if ("true".equals(normalized) || "yes".equals(normalized) || "1".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized) || "no".equals(normalized) || "0".equals(normalized)) {
                return false;
            }
        }
        return defaultValue;
    }

    private int parseIntConfig(Map<String, Object> config, String key, int defaultValue, int min, int max) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value).trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    private Collection<BillStatus> parseBillStatusConfig(Object value) {
        List<BillStatus> defaults = List.of(BillStatus.UNPAID, BillStatus.ISSUED, BillStatus.PARTIALLY_PAID);
        if (value == null) {
            return defaults;
        }
        try {
            List<String> tokens;
            if (value instanceof List<?> list) {
                tokens = list.stream().map(String::valueOf).toList();
            } else {
                tokens = List.of(String.valueOf(value).split(","));
            }
            List<BillStatus> parsed = tokens.stream()
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(token -> {
                        try {
                            return BillStatus.valueOf(token.toUpperCase());
                        } catch (RuntimeException ex) {
                            return null;
                        }
                    })
                    .filter(status -> status != null)
                    .toList();
            return parsed.isEmpty() ? defaults : parsed;
        } catch (RuntimeException ex) {
            return defaults;
        }
    }

    /**
     * Resolves refill due duration from medicine lines or prescription-level fallback rules.
     */
    private long resolveRefillDays(PrescriptionRecord prescription, int configuredEstimatedRefillDays) {
        long medicineDurationDays = parseMedicineDurationDays(prescription.medicines());
        if (medicineDurationDays > 0) {
            return medicineDurationDays;
        }
        if (prescription.followUpDate() != null && prescription.finalizedAt() != null) {
            long followUpDays = ChronoUnit.DAYS.between(prescription.finalizedAt().toLocalDate(), prescription.followUpDate());
            if (followUpDays > 0) {
                return followUpDays;
            }
        }
        if (configuredEstimatedRefillDays > 0) {
            return configuredEstimatedRefillDays;
        }
        return refillEstimatedDays;
    }

    private long parseMedicineDurationDays(List<com.deepthoughtnet.clinic.prescription.service.model.PrescriptionMedicineRecord> medicines) {
        if (medicines == null || medicines.isEmpty()) {
            return -1;
        }
        long maxDays = -1;
        for (var medicine : medicines) {
            String duration = medicine == null ? null : medicine.duration();
            long parsed = parseDurationTextToDays(duration);
            if (parsed > maxDays) {
                maxDays = parsed;
            }
        }
        return maxDays;
    }

    private long parseDurationTextToDays(String durationText) {
        if (!StringUtils.hasText(durationText)) {
            return -1;
        }
        Matcher matcher = DURATION_TOKEN_PATTERN.matcher(durationText.trim());
        if (!matcher.find()) {
            return -1;
        }
        long value = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2).toLowerCase();
        if (unit.startsWith("w")) {
            return value * 7L;
        }
        if (unit.startsWith("m")) {
            return value * 30L;
        }
        return value;
    }

    private LocalTime parseLocalTimeConfig(Object rawValue, LocalTime defaultValue) {
        if (!(rawValue instanceof String value) || !StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return defaultValue;
        }
    }

    private List<PatientVaccinationRecord> mergeVaccinationCandidates(
            List<PatientVaccinationRecord> due,
            List<PatientVaccinationRecord> overdue
    ) {
        Map<UUID, PatientVaccinationRecord> merged = new LinkedHashMap<>();
        for (PatientVaccinationRecord row : due) {
            merged.put(row.id(), row);
        }
        for (PatientVaccinationRecord row : overdue) {
            merged.putIfAbsent(row.id(), row);
        }
        return merged.values().stream().toList();
    }

    private String durationWindowToken(String prefix, Duration duration) {
        long hours = duration.toHours();
        if (hours > 0 && duration.toMinutes() % 60 == 0) {
            return prefix + "_H" + hours;
        }
        return prefix + "_M" + duration.toMinutes();
    }

    private OffsetDateTime toOffsetDateTime(LocalDate date, LocalTime time) {
        LocalDateTime localDateTime = LocalDateTime.of(date, time);
        return localDateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private record CampaignTemplateBinding(CampaignEntity campaign, CampaignTemplateEntity template) {
    }
}
