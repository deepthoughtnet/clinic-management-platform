package com.deepthoughtnet.clinic.carepilot.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryAttemptRepository;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus;
import com.deepthoughtnet.clinic.carepilot.execution.service.model.CampaignExecutionCreateCommand;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.carepilot.messaging.service.CarePilotTemplateRenderer;
import com.deepthoughtnet.clinic.carepilot.messaging.service.MessageOrchestratorService;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateEntity;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateRepository;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CampaignExecutionServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private CampaignExecutionRepository repository;
    private CampaignDeliveryAttemptRepository attemptRepository;
    private MessageOrchestratorService messageOrchestratorService;
    private CampaignTemplateRepository templateRepository;
    private PatientRepository patientRepository;
    private CarePilotTemplateRenderer templateRenderer;
    private BillingService billingService;
    private PrescriptionService prescriptionService;
    private VaccinationService vaccinationService;
    private ClinicProfileService clinicProfileService;
    private CampaignExecutionService service;

    @BeforeEach
    void setUp() {
        repository = mock(CampaignExecutionRepository.class);
        attemptRepository = mock(CampaignDeliveryAttemptRepository.class);
        messageOrchestratorService = mock(MessageOrchestratorService.class);
        templateRepository = mock(CampaignTemplateRepository.class);
        patientRepository = mock(PatientRepository.class);
        templateRenderer = mock(CarePilotTemplateRenderer.class);
        billingService = mock(BillingService.class);
        prescriptionService = mock(PrescriptionService.class);
        vaccinationService = mock(VaccinationService.class);
        clinicProfileService = mock(ClinicProfileService.class);
        CarePilotRetryPolicy retryPolicy = new CarePilotRetryPolicy(3, 60, 900, "FAILED,PROVIDER_NOT_AVAILABLE,NOT_CONFIGURED");
        service = new CampaignExecutionService(
                repository,
                attemptRepository,
                messageOrchestratorService,
                templateRepository,
                patientRepository,
                templateRenderer,
                retryPolicy,
                billingService,
                prescriptionService,
                vaccinationService,
                clinicProfileService
        );
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(attemptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(templateRenderer.render(any(), any(), any(), any(), any())).thenReturn(new CarePilotTemplateRenderer.RenderedTemplate("Subject", "Body"));
    }

    @Test
    void retryableFailureSchedulesNextRetryAndRecordsAttempt() {
        CampaignExecutionEntity due = CampaignExecutionEntity.create(tenantId, UUID.randomUUID(), null, ChannelType.IN_APP, null, OffsetDateTime.now().minusMinutes(1), null, null, null, null);
        when(repository.findTop100ByStatusInAndScheduledAtLessThanEqualOrderByScheduledAtAsc(any(), any())).thenReturn(List.of(due));
        when(messageOrchestratorService.send(any())).thenReturn(new MessageResult(
                false, MessageDeliveryStatus.PROVIDER_NOT_AVAILABLE, "carepilot-noop", null, "PROVIDER_NOT_AVAILABLE", "No provider", null
        ));

        int processed = service.processDueExecutions(50);

        assertThat(processed).isEqualTo(1);
        assertThat(due.getStatus()).isEqualTo(ExecutionStatus.RETRY_SCHEDULED);
        assertThat(due.getNextAttemptAt()).isNotNull();
        verify(attemptRepository).save(any());
    }

    @Test
    void notConfiguredSmsResultIsRetryableAndDoesNotCrash() {
        CampaignExecutionEntity due = CampaignExecutionEntity.create(
                tenantId,
                UUID.randomUUID(),
                null,
                ChannelType.SMS,
                UUID.randomUUID(),
                OffsetDateTime.now().minusMinutes(1),
                null,
                null,
                null,
                null
        );
        when(repository.findTop100ByStatusInAndScheduledAtLessThanEqualOrderByScheduledAtAsc(any(), any())).thenReturn(List.of(due));
        when(messageOrchestratorService.send(any())).thenReturn(new MessageResult(
                false,
                MessageDeliveryStatus.NOT_CONFIGURED,
                "SMS_NOT_CONFIGURED",
                null,
                "NOT_CONFIGURED",
                "SMS provider disabled",
                null
        ));

        int processed = service.processDueExecutions(10);

        assertThat(processed).isEqualTo(1);
        assertThat(due.getStatus()).isEqualTo(ExecutionStatus.RETRY_SCHEDULED);
        assertThat(due.getFailureReason()).isEqualTo("NOT_CONFIGURED");
    }

    @Test
    void notConfiguredWhatsAppResultIsRetryableAndDoesNotCrash() {
        CampaignExecutionEntity due = CampaignExecutionEntity.create(
                tenantId,
                UUID.randomUUID(),
                null,
                ChannelType.WHATSAPP,
                UUID.randomUUID(),
                OffsetDateTime.now().minusMinutes(1),
                null,
                null,
                null,
                null
        );
        when(repository.findTop100ByStatusInAndScheduledAtLessThanEqualOrderByScheduledAtAsc(any(), any())).thenReturn(List.of(due));
        when(messageOrchestratorService.send(any())).thenReturn(new MessageResult(
                false,
                MessageDeliveryStatus.NOT_CONFIGURED,
                "WHATSAPP_NOT_CONFIGURED",
                null,
                "NOT_CONFIGURED",
                "WhatsApp provider disabled",
                null
        ));

        int processed = service.processDueExecutions(10);

        assertThat(processed).isEqualTo(1);
        assertThat(due.getStatus()).isEqualTo(ExecutionStatus.RETRY_SCHEDULED);
        assertThat(due.getFailureReason()).isEqualTo("NOT_CONFIGURED");
    }

    @Test
    void nonRetryableFailureMarksExecutionFailed() {
        CampaignExecutionEntity due = CampaignExecutionEntity.create(tenantId, UUID.randomUUID(), null, ChannelType.SMS, UUID.randomUUID(), OffsetDateTime.now().minusMinutes(1), null, null, null, null);
        when(repository.findTop100ByStatusInAndScheduledAtLessThanEqualOrderByScheduledAtAsc(any(), any())).thenReturn(List.of(due));
        when(messageOrchestratorService.send(any())).thenReturn(new MessageResult(
                false, MessageDeliveryStatus.SKIPPED, "carepilot-noop", null, "SKIPPED", "unsupported", null
        ));

        service.processDueExecutions(50);

        assertThat(due.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(due.getFailureReason()).isEqualTo("SKIPPED");
    }

    @Test
    void maxRetriesMoveExecutionToDeadLetter() {
        CampaignExecutionEntity due = CampaignExecutionEntity.create(tenantId, UUID.randomUUID(), null, ChannelType.EMAIL, UUID.randomUUID(), OffsetDateTime.now().minusMinutes(1), null, null, null, null);
        due.markFailed("err-1", "FAILED", MessageDeliveryStatus.FAILED, OffsetDateTime.now().plusMinutes(1), 10);
        due.markFailed("err-2", "FAILED", MessageDeliveryStatus.FAILED, OffsetDateTime.now().plusMinutes(2), 10);
        when(repository.findTop100ByStatusInAndScheduledAtLessThanEqualOrderByScheduledAtAsc(any(), any())).thenReturn(List.of(due));
        when(messageOrchestratorService.send(any())).thenReturn(new MessageResult(
                false, MessageDeliveryStatus.FAILED, "email-provider", null, "EMAIL_DELIVERY_FAILED", "dispatch failed", null
        ));

        service.processDueExecutions(10);

        assertThat(due.getStatus()).isEqualTo(ExecutionStatus.DEAD_LETTER);
        assertThat(due.getAttemptCount()).isEqualTo(3);
    }

    @Test
    void manualRetryRequeuesFailedExecution() {
        UUID executionId = UUID.randomUUID();
        CampaignExecutionEntity entity = CampaignExecutionEntity.create(tenantId, UUID.randomUUID(), null, ChannelType.EMAIL, null, OffsetDateTime.now(), null, null, null, null);
        entity.markFailed("x", "FAILED", MessageDeliveryStatus.FAILED, null, 3);
        when(repository.findByTenantIdAndId(tenantId, executionId)).thenReturn(Optional.of(entity));

        var retried = service.retryExecution(tenantId, executionId);

        assertThat(retried.status()).isEqualTo(ExecutionStatus.QUEUED);
        assertThat(retried.nextAttemptAt()).isNull();
    }

    @Test
    void cancelQueuedExecutionMarksCancelledAndSkipped() {
        UUID executionId = UUID.randomUUID();
        CampaignExecutionEntity entity = CampaignExecutionEntity.create(tenantId, UUID.randomUUID(), null, ChannelType.EMAIL, null, OffsetDateTime.now().plusHours(1), null, null, null, null);
        when(repository.findByTenantIdAndId(tenantId, executionId)).thenReturn(Optional.of(entity));

        var cancelled = service.cancelExecution(tenantId, executionId, "Cancelled by admin");

        assertThat(cancelled.status()).isEqualTo(ExecutionStatus.CANCELLED);
        assertThat(cancelled.deliveryStatus()).isEqualTo(MessageDeliveryStatus.SKIPPED);
    }

    @Test
    void suppressQueuedExecutionMarksSuppressedAndSkipped() {
        UUID executionId = UUID.randomUUID();
        CampaignExecutionEntity entity = CampaignExecutionEntity.create(tenantId, UUID.randomUUID(), null, ChannelType.EMAIL, null, OffsetDateTime.now().plusHours(1), null, null, null, null);
        when(repository.findByTenantIdAndId(tenantId, executionId)).thenReturn(Optional.of(entity));

        var suppressed = service.suppressExecution(tenantId, executionId, "Suppressed by admin");

        assertThat(suppressed.status()).isEqualTo(ExecutionStatus.SUPPRESSED);
        assertThat(suppressed.deliveryStatus()).isEqualTo(MessageDeliveryStatus.SKIPPED);
    }

    @Test
    void rescheduleQueuedExecutionUpdatesSchedule() {
        UUID executionId = UUID.randomUUID();
        CampaignExecutionEntity entity = CampaignExecutionEntity.create(tenantId, UUID.randomUUID(), null, ChannelType.EMAIL, null, OffsetDateTime.now().plusHours(1), null, null, null, null);
        when(repository.findByTenantIdAndId(tenantId, executionId)).thenReturn(Optional.of(entity));
        OffsetDateTime next = OffsetDateTime.now().plusDays(1);

        var rescheduled = service.rescheduleExecution(tenantId, executionId, next, "Rescheduled by admin");

        assertThat(rescheduled.status()).isEqualTo(ExecutionStatus.QUEUED);
        assertThat(rescheduled.scheduledAt()).isEqualTo(next);
    }

    @Test
    void cancelDeliveredExecutionIsRejected() {
        UUID executionId = UUID.randomUUID();
        CampaignExecutionEntity entity = CampaignExecutionEntity.create(tenantId, UUID.randomUUID(), null, ChannelType.EMAIL, null, OffsetDateTime.now().plusHours(1), null, null, null, null);
        entity.markSucceeded("email-provider", "msg-1");
        when(repository.findByTenantIdAndId(tenantId, executionId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.cancelExecution(tenantId, executionId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("can be cancelled");
    }

    @Test
    void processDueExecutionsPassesTenantAndCampaignMetadataToMessageRequest() {
        UUID campaignId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        CampaignExecutionEntity due = CampaignExecutionEntity.create(tenantId, campaignId, null, ChannelType.EMAIL, patientId, OffsetDateTime.now().minusMinutes(1), null, null, null, null);
        when(repository.findTop100ByStatusInAndScheduledAtLessThanEqualOrderByScheduledAtAsc(any(), any())).thenReturn(List.of(due));
        when(messageOrchestratorService.send(any())).thenReturn(new MessageResult(
                true, MessageDeliveryStatus.SENT, "email-provider", "m-1", null, null, OffsetDateTime.now()
        ));
        PatientEntity patient = PatientEntity.create(tenantId, "PAT-1");
        patient.update("John", "Doe", null, null, null, "9999999999", "john@example.com", null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
        when(patientRepository.findByTenantIdAndId(tenantId, patientId)).thenReturn(Optional.of(patient));

        service.processDueExecutions(10);

        ArgumentCaptor<MessageRequest> captor = ArgumentCaptor.forClass(MessageRequest.class);
        verify(messageOrchestratorService).send(captor.capture());
        MessageRequest sent = captor.getValue();
        assertThat(sent.tenantId()).isEqualTo(tenantId);
        assertThat(sent.campaignId()).isEqualTo(campaignId);
        assertThat(sent.executionId()).isEqualTo(due.getId());
        assertThat(sent.recipient().address()).isEqualTo("john@example.com");
        assertThat(sent.metadata()).containsEntry("campaignId", campaignId.toString());
    }

    @Test
    void listAttemptsRespectsTenantScopedExecutionLookup() {
        UUID executionId = UUID.randomUUID();
        when(repository.findByTenantIdAndId(tenantId, executionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listAttempts(tenantId, executionId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Execution not found");
    }

    @Test
    void templateIsRenderedWhenTemplateExists() {
        UUID campaignId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        CampaignExecutionEntity due = CampaignExecutionEntity.create(tenantId, campaignId, templateId, ChannelType.EMAIL, patientId, OffsetDateTime.now().minusMinutes(1), null, null, null, null);
        when(repository.findTop100ByStatusInAndScheduledAtLessThanEqualOrderByScheduledAtAsc(any(), any())).thenReturn(List.of(due));
        when(messageOrchestratorService.send(any())).thenReturn(new MessageResult(
                true, MessageDeliveryStatus.SENT, "email-provider", "m-1", null, null, OffsetDateTime.now()
        ));
        PatientEntity patient = PatientEntity.create(tenantId, "PAT-2");
        patient.update("Jane", "Doe", null, null, null, "9999999999", "jane@example.com", null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
        when(patientRepository.findByTenantIdAndId(tenantId, patientId)).thenReturn(Optional.of(patient));
        CampaignTemplateEntity template = CampaignTemplateEntity.create(tenantId, "Reminder", ChannelType.EMAIL, "Hello {{patientName}}", "Body {{appointmentDate}}", true);
        when(templateRepository.findByTenantIdAndId(tenantId, templateId)).thenReturn(Optional.of(template));

        service.processDueExecutions(10);

        verify(templateRenderer).render(any(), any(), any(), any(), any());
    }
}
