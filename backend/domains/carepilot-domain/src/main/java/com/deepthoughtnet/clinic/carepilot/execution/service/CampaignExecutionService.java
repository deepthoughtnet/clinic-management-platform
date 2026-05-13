package com.deepthoughtnet.clinic.carepilot.execution.service;

import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryAttemptEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryAttemptRepository;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus;
import com.deepthoughtnet.clinic.carepilot.execution.service.model.CampaignDeliveryAttemptRecord;
import com.deepthoughtnet.clinic.carepilot.execution.service.model.CampaignExecutionCreateCommand;
import com.deepthoughtnet.clinic.carepilot.execution.service.model.CampaignExecutionRecord;
import com.deepthoughtnet.clinic.carepilot.messaging.exception.MessageDispatchException;
import com.deepthoughtnet.clinic.carepilot.messaging.service.CarePilotTemplateRenderer;
import com.deepthoughtnet.clinic.carepilot.messaging.service.MessageOrchestratorService;
import com.deepthoughtnet.clinic.carepilot.shared.util.CarePilotValidators;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateEntity;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateRepository;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageRecipient;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionMedicineRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Service that manages execution rows, retries, and delivery-attempt history. */
@Service
public class CampaignExecutionService {
    private final CampaignExecutionRepository repository;
    private final CampaignDeliveryAttemptRepository attemptRepository;
    private final MessageOrchestratorService messageOrchestratorService;
    private final CampaignTemplateRepository templateRepository;
    private final PatientRepository patientRepository;
    private final CarePilotTemplateRenderer templateRenderer;
    private final CarePilotRetryPolicy retryPolicy;
    private final BillingService billingService;
    private final PrescriptionService prescriptionService;
    private final VaccinationService vaccinationService;
    private final ClinicProfileService clinicProfileService;

    public CampaignExecutionService(
            CampaignExecutionRepository repository,
            CampaignDeliveryAttemptRepository attemptRepository,
            MessageOrchestratorService messageOrchestratorService,
            CampaignTemplateRepository templateRepository,
            PatientRepository patientRepository,
            CarePilotTemplateRenderer templateRenderer,
            CarePilotRetryPolicy retryPolicy,
            BillingService billingService,
            PrescriptionService prescriptionService,
            VaccinationService vaccinationService,
            ClinicProfileService clinicProfileService
    ) {
        this.repository = repository;
        this.attemptRepository = attemptRepository;
        this.messageOrchestratorService = messageOrchestratorService;
        this.templateRepository = templateRepository;
        this.patientRepository = patientRepository;
        this.templateRenderer = templateRenderer;
        this.retryPolicy = retryPolicy;
        this.billingService = billingService;
        this.prescriptionService = prescriptionService;
        this.vaccinationService = vaccinationService;
        this.clinicProfileService = clinicProfileService;
    }

    @Transactional
    /** Creates a tenant-scoped execution row for later scheduler dispatch. */
    public CampaignExecutionRecord create(UUID tenantId, CampaignExecutionCreateCommand command) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(command.campaignId(), "campaignId");
        if (command.channelType() == null) {
            throw new IllegalArgumentException("channelType is required");
        }
        OffsetDateTime scheduledAt = command.scheduledAt() == null ? OffsetDateTime.now() : command.scheduledAt();
        CampaignExecutionEntity entity = CampaignExecutionEntity.create(
                tenantId,
                command.campaignId(),
                command.templateId(),
                command.channelType(),
                command.recipientPatientId(),
                scheduledAt,
                command.sourceType(),
                command.sourceReferenceId(),
                command.reminderWindow(),
                command.referenceDateTime()
        );
        try {
            return toRecord(repository.save(entity));
        } catch (DataIntegrityViolationException ex) {
            if (command.sourceReferenceId() != null && StringUtils.hasText(command.reminderWindow())) {
                return repository.findFirstByTenantIdAndCampaignIdAndSourceReferenceIdAndReminderWindowAndChannelType(
                                tenantId,
                                command.campaignId(),
                                command.sourceReferenceId(),
                                command.reminderWindow(),
                                command.channelType()
                        )
                        .map(this::toRecord)
                        .orElseThrow(() -> ex);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    /** Lists execution rows in reverse chronological order for a tenant. */
    public List<CampaignExecutionRecord> list(UUID tenantId) {
        CarePilotValidators.requireTenant(tenantId);
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(this::toRecord).toList();
    }

    @Transactional
    /** Marks execution successful for administrative/manual correction flows. */
    public CampaignExecutionRecord markSuccess(UUID tenantId, UUID executionId) {
        CampaignExecutionEntity entity = requireExecution(tenantId, executionId);
        entity.markSucceeded(entity.getProviderName(), entity.getProviderMessageId());
        return toRecord(repository.save(entity));
    }

    @Transactional
    /** Marks execution failed for administrative/manual correction flows. */
    public CampaignExecutionRecord markFailure(UUID tenantId, UUID executionId, String error, OffsetDateTime nextAttemptAt) {
        CampaignExecutionEntity entity = requireExecution(tenantId, executionId);
        entity.markFailed(error, "MANUAL_FAILURE", MessageDeliveryStatus.FAILED, nextAttemptAt, retryPolicy.maxRetries());
        return toRecord(repository.save(entity));
    }

    @Transactional(readOnly = true)
    /** Lists terminal failed/dead-letter executions for admin and audit review. */
    public List<CampaignExecutionRecord> listFailed(UUID tenantId) {
        CarePilotValidators.requireTenant(tenantId);
        return repository.findByTenantIdAndStatusInOrderByUpdatedAtDesc(
                        tenantId,
                        List.of(ExecutionStatus.FAILED, ExecutionStatus.DEAD_LETTER)
                )
                .stream()
                .map(this::toRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    /** Returns immutable delivery-attempt history for a tenant execution. */
    public List<CampaignDeliveryAttemptRecord> listAttempts(UUID tenantId, UUID executionId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(executionId, "executionId");
        requireExecution(tenantId, executionId);
        return attemptRepository.findByTenantIdAndExecutionIdOrderByAttemptNumberDesc(tenantId, executionId)
                .stream()
                .map(this::toAttemptRecord)
                .toList();
    }

    @Transactional
    /** Requeues terminal failed/dead-letter rows for a controlled resend attempt. */
    public CampaignExecutionRecord retryExecution(UUID tenantId, UUID executionId) {
        CampaignExecutionEntity entity = requireExecution(tenantId, executionId);
        if (entity.getStatus() != ExecutionStatus.FAILED && entity.getStatus() != ExecutionStatus.DEAD_LETTER) {
            throw new IllegalArgumentException("Only failed or dead-letter executions can be retried");
        }
        entity.markQueuedForRetry();
        return toRecord(repository.save(entity));
    }

    @Transactional
    /** Cancels an in-flight reminder execution before final delivery outcome. */
    public CampaignExecutionRecord cancelExecution(UUID tenantId, UUID executionId, String reason) {
        CampaignExecutionEntity entity = requireExecution(tenantId, executionId);
        ensureMutableReminderState(entity, "cancelled");
        entity.markCancelled(StringUtils.hasText(reason) ? reason : "CANCELLED_BY_OPERATOR");
        return toRecord(repository.save(entity));
    }

    @Transactional
    /** Suppresses an in-flight reminder execution to prevent future processing. */
    public CampaignExecutionRecord suppressExecution(UUID tenantId, UUID executionId, String reason) {
        CampaignExecutionEntity entity = requireExecution(tenantId, executionId);
        ensureMutableReminderState(entity, "suppressed");
        entity.markSuppressed(StringUtils.hasText(reason) ? reason : "SUPPRESSED_BY_OPERATOR");
        return toRecord(repository.save(entity));
    }

    @Transactional
    /** Reschedules an in-flight reminder execution to a new future scheduled time. */
    public CampaignExecutionRecord rescheduleExecution(UUID tenantId, UUID executionId, OffsetDateTime newScheduledAt, String reason) {
        CampaignExecutionEntity entity = requireExecution(tenantId, executionId);
        ensureMutableReminderState(entity, "rescheduled");
        if (newScheduledAt == null) {
            throw new IllegalArgumentException("newScheduledAt is required");
        }
        if (!newScheduledAt.isAfter(OffsetDateTime.now().plusSeconds(30))) {
            throw new IllegalArgumentException("newScheduledAt must be in the future");
        }
        String resolvedReason = StringUtils.hasText(reason) ? reason.trim() : "BY_OPERATOR";
        if (!resolvedReason.startsWith("RESCHEDULED_")) {
            resolvedReason = "RESCHEDULED_" + resolvedReason;
        }
        entity.markRescheduled(newScheduledAt, resolvedReason);
        return toRecord(repository.save(entity));
    }

    @Transactional
    /** Processes due executions in a bounded batch and persists every attempt outcome. */
    public int processDueExecutions(int batchSize) {
        Collection<ExecutionStatus> statuses = List.of(ExecutionStatus.QUEUED, ExecutionStatus.RETRY_SCHEDULED);
        List<CampaignExecutionEntity> due = repository.findTop100ByStatusInAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                statuses,
                OffsetDateTime.now()
        );
        int processed = 0;
        for (CampaignExecutionEntity execution : due) {
            if (processed >= batchSize) {
                break;
            }
            processed += processSingleExecution(execution);
        }
        return processed;
    }

    private int processSingleExecution(CampaignExecutionEntity execution) {
        execution.markProcessing();
        MessageResult messageResult;
        try {
            messageResult = messageOrchestratorService.send(toMessageRequest(execution));
        } catch (MessageDispatchException ex) {
            messageResult = new MessageResult(
                    false,
                    MessageDeliveryStatus.FAILED,
                    "carepilot-orchestrator",
                    null,
                    "DISPATCH_EXCEPTION",
                    ex.getMessage(),
                    null
            );
        }
        OffsetDateTime attemptedAt = OffsetDateTime.now();

        attemptRepository.save(CampaignDeliveryAttemptEntity.create(
                execution.getTenantId(),
                execution.getId(),
                execution.getAttemptCount() + 1,
                messageResult.providerName(),
                execution.getChannelType(),
                messageResult.status(),
                messageResult.errorCode(),
                messageResult.errorMessage(),
                attemptedAt
        ));

        if (messageResult.success() && messageResult.status() == MessageDeliveryStatus.SENT) {
            execution.markSucceeded(messageResult.providerName(), messageResult.providerMessageId());
        } else {
            boolean retryable = retryPolicy.isRetryable(messageResult.status());
            OffsetDateTime nextRetryAt = retryable ? retryPolicy.computeNextRetryAt(execution.getAttemptCount() + 1) : null;
            execution.markFailed(
                    messageResult.errorMessage(),
                    messageResult.errorCode(),
                    messageResult.status(),
                    nextRetryAt,
                    retryPolicy.maxRetries()
            );
        }

        repository.save(execution);
        return 1;
    }

    private void ensureMutableReminderState(CampaignExecutionEntity entity, String action) {
        ExecutionStatus status = entity.getStatus();
        if (!(status == ExecutionStatus.QUEUED || status == ExecutionStatus.RETRY_SCHEDULED || status == ExecutionStatus.PROCESSING)) {
            throw new IllegalArgumentException("Only queued/retrying reminders can be " + action);
        }
        MessageDeliveryStatus deliveryStatus = entity.getDeliveryStatus();
        if (deliveryStatus == MessageDeliveryStatus.DELIVERED
                || deliveryStatus == MessageDeliveryStatus.READ
                || status == ExecutionStatus.SUCCEEDED) {
            throw new IllegalArgumentException("Delivered/read reminders cannot be " + action);
        }
    }

    private MessageRequest toMessageRequest(CampaignExecutionEntity execution) {
        MessageChannel messageChannel = toMessageChannel(execution.getChannelType());
        PatientEntity patient = execution.getRecipientPatientId() == null
                ? null
                : patientRepository.findByTenantIdAndId(execution.getTenantId(), execution.getRecipientPatientId()).orElse(null);
        String recipient = resolveRecipientAddress(execution, patient, messageChannel);

        CampaignTemplateEntity template = execution.getTemplateId() == null
                ? null
                : templateRepository.findByTenantIdAndId(execution.getTenantId(), execution.getTemplateId()).orElse(null);

        String subject = "CarePilot Reminder";
        String body = "CarePilot reminder";
        if (template != null) {
            Map<String, String> templateValues = buildTemplateValues(execution, patient);
            CarePilotTemplateRenderer.RenderedTemplate rendered = templateRenderer.render(
                    execution.getCampaignId(),
                    template,
                    patient,
                    execution.getReferenceDateTime() == null ? execution.getScheduledAt() : execution.getReferenceDateTime(),
                    templateValues
            );
            if (StringUtils.hasText(rendered.subject())) {
                subject = rendered.subject();
            }
            if (StringUtils.hasText(rendered.body())) {
                body = rendered.body();
            }
        }

        return new MessageRequest(
                execution.getTenantId(),
                messageChannel,
                new MessageRecipient(recipient, null),
                subject,
                body,
                execution.getTemplateId(),
                null,
                execution.getCampaignId(),
                execution.getId(),
                Map.of("campaignId", execution.getCampaignId().toString())
        );
    }

    /**
     * Builds source-specific placeholders while preserving safe defaults for missing source data.
     */
    private Map<String, String> buildTemplateValues(CampaignExecutionEntity execution, PatientEntity patient) {
        Map<String, String> values = new LinkedHashMap<>();
        String patientName = patient == null ? "Patient" : ((patient.getFirstName() == null ? "" : patient.getFirstName()) + " "
                + (patient.getLastName() == null ? "" : patient.getLastName())).trim();
        values.put("patientName", StringUtils.hasText(patientName) ? patientName : "Patient");
        values.put("clinicName", clinicProfileService.findByTenantId(execution.getTenantId())
                .map(this::resolveClinicDisplayName)
                .orElse("Clinic"));
        values.put("doctorName", "");
        values.put("billNumber", "");
        values.put("billDate", "");
        values.put("billDueDate", "");
        values.put("amountDue", "");
        values.put("medicineName", "");
        values.put("prescriptionDate", "");
        values.put("refillDueDate", "");
        values.put("vaccineName", "");
        values.put("vaccinationDueDate", "");
        values.put("vaccinationStatus", "");
        values.put("birthdayDate", "");
        values.put("age", "");
        values.put("clinicPhone", clinicProfileService.findByTenantId(execution.getTenantId())
                .map(ClinicProfileRecord::phone)
                .filter(StringUtils::hasText)
                .orElse(""));

        String sourceType = execution.getSourceType() == null ? "" : execution.getSourceType().trim().toUpperCase();
        if ("BILL".equals(sourceType) && execution.getSourceReferenceId() != null) {
            billingService.findById(execution.getTenantId(), execution.getSourceReferenceId()).ifPresent(bill -> fillBillValues(values, bill));
        } else if ("PRESCRIPTION".equals(sourceType) && execution.getSourceReferenceId() != null) {
            prescriptionService.findById(execution.getTenantId(), execution.getSourceReferenceId()).ifPresent(p -> fillPrescriptionValues(values, p, execution));
        } else if ("FOLLOW_UP".equals(sourceType) && execution.getSourceReferenceId() != null) {
            prescriptionService.findById(execution.getTenantId(), execution.getSourceReferenceId()).ifPresent(p -> values.put("doctorName", safeText(p.doctorName())));
        } else if ("VACCINATION".equals(sourceType) && execution.getSourceReferenceId() != null) {
            vaccinationService.findById(execution.getTenantId(), execution.getSourceReferenceId()).ifPresent(v -> fillVaccinationValues(values, v, execution));
        } else if ("PATIENT_BIRTHDAY".equals(sourceType)) {
            fillBirthdayValues(values, patient);
        }
        return values;
    }

    private void fillBillValues(Map<String, String> values, BillRecord bill) {
        values.put("billNumber", safeText(bill.billNumber()));
        values.put("billDate", bill.billDate() == null ? "" : bill.billDate().toString());
        values.put("billDueDate", bill.billDate() == null ? "" : bill.billDate().toString());
        values.put("amountDue", bill.dueAmount() == null ? "" : bill.dueAmount().toPlainString());
    }

    private void fillPrescriptionValues(Map<String, String> values, PrescriptionRecord prescription, CampaignExecutionEntity execution) {
        values.put("doctorName", safeText(prescription.doctorName()));
        values.put("medicineName", summarizeMedicines(prescription.medicines()));
        values.put("prescriptionDate", prescription.finalizedAt() == null ? "" : prescription.finalizedAt().toLocalDate().toString());
        OffsetDateTime refillDue = execution.getReferenceDateTime();
        values.put("refillDueDate", refillDue == null ? "" : refillDue.toLocalDate().toString());
    }

    private String summarizeMedicines(List<PrescriptionMedicineRecord> medicines) {
        if (medicines == null || medicines.isEmpty()) {
            return "";
        }
        List<String> names = medicines.stream()
                .map(PrescriptionMedicineRecord::medicineName)
                .filter(StringUtils::hasText)
                .toList();
        if (names.isEmpty()) {
            return "";
        }
        if (names.size() == 1) {
            return names.get(0);
        }
        return names.get(0) + " + " + (names.size() - 1) + " more";
    }

    private String resolveClinicDisplayName(ClinicProfileRecord record) {
        if (record == null) {
            return "Clinic";
        }
        if (StringUtils.hasText(record.displayName())) {
            return record.displayName().trim();
        }
        if (StringUtils.hasText(record.clinicName())) {
            return record.clinicName().trim();
        }
        return "Clinic";
    }

    private String safeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private void fillVaccinationValues(
            Map<String, String> values,
            com.deepthoughtnet.clinic.vaccination.service.model.PatientVaccinationRecord vaccination,
            CampaignExecutionEntity execution
    ) {
        values.put("vaccineName", safeText(vaccination.vaccineName()));
        LocalDate dueDate = execution.getReferenceDateTime() == null
                ? vaccination.nextDueDate()
                : execution.getReferenceDateTime().toLocalDate();
        values.put("vaccinationDueDate", dueDate == null ? "" : dueDate.toString());
        if (dueDate == null) {
            values.put("vaccinationStatus", "");
        } else if (dueDate.isBefore(LocalDate.now())) {
            values.put("vaccinationStatus", "OVERDUE");
        } else if (dueDate.isEqual(LocalDate.now())) {
            values.put("vaccinationStatus", "DUE_TODAY");
        } else {
            values.put("vaccinationStatus", "UPCOMING");
        }
        values.put("doctorName", safeText(vaccination.administeredByUserName()));
    }

    private void fillBirthdayValues(Map<String, String> values, PatientEntity patient) {
        if (patient == null || patient.getDateOfBirth() == null) {
            return;
        }
        values.put("birthdayDate", patient.getDateOfBirth().toString());
        int age = Period.between(patient.getDateOfBirth(), LocalDate.now()).getYears();
        values.put("age", age < 0 ? "" : Integer.toString(age));
    }

    private String resolveRecipientAddress(
            CampaignExecutionEntity execution,
            PatientEntity patient,
            MessageChannel channel
    ) {
        if (channel == MessageChannel.EMAIL) {
            if (patient != null && StringUtils.hasText(patient.getEmail())) {
                return patient.getEmail().trim();
            }
            // Delivery should be recorded as a failed attempt instead of crashing scheduler execution.
            throw new MessageDispatchException("RECIPIENT_EMAIL_MISSING");
        }

        if (execution.getRecipientPatientId() != null) {
            return execution.getRecipientPatientId().toString();
        }
        return "carepilot-recipient";
    }

    private MessageChannel toMessageChannel(com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType channelType) {
        return switch (channelType) {
            case EMAIL -> MessageChannel.EMAIL;
            case SMS -> MessageChannel.SMS;
            case WHATSAPP -> MessageChannel.WHATSAPP;
            case IN_APP, APP_NOTIFICATION -> MessageChannel.IN_APP;
        };
    }

    private CampaignExecutionEntity requireExecution(UUID tenantId, UUID executionId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(executionId, "executionId");
        return repository.findByTenantIdAndId(tenantId, executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found"));
    }

    private CampaignExecutionRecord toRecord(CampaignExecutionEntity entity) {
        return new CampaignExecutionRecord(
                entity.getId(), entity.getTenantId(), entity.getCampaignId(), entity.getTemplateId(), entity.getChannelType(),
                entity.getRecipientPatientId(), entity.getScheduledAt(), entity.getStatus(), entity.getAttemptCount(),
                entity.getLastError(), entity.getExecutedAt(), entity.getNextAttemptAt(), entity.getDeliveryStatus(),
                entity.getProviderName(), entity.getProviderMessageId(), entity.getSourceType(), entity.getSourceReferenceId(),
                entity.getReminderWindow(), entity.getReferenceDateTime(), entity.getLastAttemptAt(), entity.getFailureReason(),
                entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    private CampaignDeliveryAttemptRecord toAttemptRecord(CampaignDeliveryAttemptEntity entity) {
        return new CampaignDeliveryAttemptRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getExecutionId(),
                entity.getAttemptNumber(),
                entity.getProviderName(),
                entity.getChannelType(),
                entity.getDeliveryStatus(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getAttemptedAt()
        );
    }
}
