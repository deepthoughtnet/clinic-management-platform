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
import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageRecipient;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    public CampaignExecutionService(
            CampaignExecutionRepository repository,
            CampaignDeliveryAttemptRepository attemptRepository,
            MessageOrchestratorService messageOrchestratorService,
            CampaignTemplateRepository templateRepository,
            PatientRepository patientRepository,
            CarePilotTemplateRenderer templateRenderer,
            CarePilotRetryPolicy retryPolicy
    ) {
        this.repository = repository;
        this.attemptRepository = attemptRepository;
        this.messageOrchestratorService = messageOrchestratorService;
        this.templateRepository = templateRepository;
        this.patientRepository = patientRepository;
        this.templateRenderer = templateRenderer;
        this.retryPolicy = retryPolicy;
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
                scheduledAt
        );
        return toRecord(repository.save(entity));
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
            CarePilotTemplateRenderer.RenderedTemplate rendered = templateRenderer.render(
                    execution.getCampaignId(),
                    template,
                    patient,
                    execution.getScheduledAt()
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
                entity.getProviderName(), entity.getProviderMessageId(), entity.getLastAttemptAt(), entity.getFailureReason(),
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
