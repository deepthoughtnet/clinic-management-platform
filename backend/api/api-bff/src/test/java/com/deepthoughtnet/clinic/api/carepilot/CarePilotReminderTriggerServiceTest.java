package com.deepthoughtnet.clinic.api.carepilot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentSearchCriteria;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.billing.service.model.DiscountType;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.execution.service.CampaignExecutionService;
import com.deepthoughtnet.clinic.carepilot.execution.service.model.CampaignExecutionCreateCommand;
import com.deepthoughtnet.clinic.carepilot.featureflag.service.FeatureFlagService;
import com.deepthoughtnet.clinic.carepilot.featureflag.service.model.FeatureFlagRecord;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateEntity;
import com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateRepository;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionStatus;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import com.deepthoughtnet.clinic.vaccination.service.model.PatientVaccinationRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CarePilotReminderTriggerServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private PlatformTenantManagementService tenantManagementService;
    private CarePilotRuntimeSchedulerMonitor runtimeSchedulerMonitor;
    private FeatureFlagService featureFlagService;
    private CampaignRepository campaignRepository;
    private CampaignTemplateRepository templateRepository;
    private CampaignExecutionRepository executionRepository;
    private CampaignExecutionService executionService;
    private AppointmentService appointmentService;
    private PrescriptionService prescriptionService;
    private VaccinationService vaccinationService;
    private BillingService billingService;
    private PatientRepository patientRepository;
    private CarePilotReminderTriggerService service;

    @BeforeEach
    void setUp() {
        tenantManagementService = mock(PlatformTenantManagementService.class);
        runtimeSchedulerMonitor = mock(CarePilotRuntimeSchedulerMonitor.class);
        featureFlagService = mock(FeatureFlagService.class);
        campaignRepository = mock(CampaignRepository.class);
        templateRepository = mock(CampaignTemplateRepository.class);
        executionRepository = mock(CampaignExecutionRepository.class);
        executionService = mock(CampaignExecutionService.class);
        appointmentService = mock(AppointmentService.class);
        prescriptionService = mock(PrescriptionService.class);
        vaccinationService = mock(VaccinationService.class);
        billingService = mock(BillingService.class);
        patientRepository = mock(PatientRepository.class);

        service = new CarePilotReminderTriggerService(
                tenantManagementService,
                runtimeSchedulerMonitor,
                featureFlagService,
                campaignRepository,
                templateRepository,
                executionRepository,
                executionService,
                appointmentService,
                prescriptionService,
                vaccinationService,
                billingService,
                patientRepository,
                new ObjectMapper(),
                30,
                3,
                100
        );

        when(tenantManagementService.list()).thenReturn(List.of(new PlatformTenantRecord(
                tenantId,
                "T-1",
                "Tenant 1",
                null,
                "ACTIVE",
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        )));
        when(featureFlagService.carePilotForTenant(tenantId)).thenReturn(new FeatureFlagRecord(tenantId, true, "test"));

        PatientEntity patient = PatientEntity.create(tenantId, "PAT-1");
        patient.update("John", "Doe", null, null, null, "9999999999", "john@example.com", null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
        when(patientRepository.findByTenantIdAndId(eq(tenantId), any())).thenReturn(java.util.Optional.of(patient));
        when(patientRepository.findByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of(patient));

        when(executionRepository.existsByTenantIdAndCampaignIdAndRecipientPatientIdAndChannelTypeAndScheduledAtBetween(
                eq(tenantId), any(), any(), eq(ChannelType.EMAIL), any(), any()
        )).thenReturn(false);
        when(executionRepository.existsByTenantIdAndCampaignIdAndSourceReferenceIdAndReminderWindowAndChannelType(
                eq(tenantId), any(), any(), any(), eq(ChannelType.EMAIL)
        )).thenReturn(false);

        when(campaignRepository.findFirstByTenantIdAndCampaignTypeAndActiveTrueOrderByUpdatedAtDesc(eq(tenantId), any()))
                .thenAnswer(invocation -> {
                    CampaignType type = invocation.getArgument(1);
                    if (type == CampaignType.MISSED_APPOINTMENT_FOLLOW_UP) {
                        return java.util.Optional.empty();
                    }
                    return java.util.Optional.of(campaign(type));
                });
        when(templateRepository.findByTenantIdAndId(eq(tenantId), any()))
                .thenAnswer(invocation -> java.util.Optional.of(CampaignTemplateEntity.create(tenantId, "Template", ChannelType.EMAIL, "S", "B", true)));
        when(vaccinationService.listOverdue(tenantId)).thenReturn(List.of());
    }

    @Test
    void appointmentRemindersCreate24hAnd2hExecutions() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        AppointmentRecord appointment = new AppointmentRecord(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), "PAT-1", "John Doe", "9999999999", UUID.randomUUID(), "Dr", null,
                tomorrow, LocalTime.of(10, 0), 1, null, AppointmentType.SCHEDULED, AppointmentPriority.NORMAL,
                AppointmentStatus.BOOKED, OffsetDateTime.now(), OffsetDateTime.now()
        );
        when(appointmentService.search(eq(tenantId), any())).thenAnswer(invocation -> {
            AppointmentSearchCriteria criteria = invocation.getArgument(1);
            return tomorrow.equals(criteria.appointmentDate()) ? List.of(appointment) : List.of();
        });
        when(prescriptionService.list(tenantId)).thenReturn(List.of());
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of());

        service.queueDueReminders();

        verify(executionService, times(1)).create(eq(tenantId), any());
    }

    @Test
    void cancelledAppointmentIsIgnored() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(appointmentService.search(eq(tenantId), any())).thenReturn(List.of(new AppointmentRecord(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), "PAT-1", "John Doe", "9999999999", UUID.randomUUID(), "Dr", null,
                tomorrow, LocalTime.of(10, 0), 1, null, AppointmentType.SCHEDULED, AppointmentPriority.NORMAL,
                AppointmentStatus.CANCELLED, OffsetDateTime.now(), OffsetDateTime.now()
        )));
        when(prescriptionService.list(tenantId)).thenReturn(List.of());
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of());

        service.queueDueReminders();

        verify(executionService, never()).create(eq(tenantId), any());
    }

    @Test
    void completedAppointmentIsIgnored() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(appointmentService.search(eq(tenantId), any())).thenReturn(List.of(new AppointmentRecord(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), "PAT-1", "John Doe", "9999999999", UUID.randomUUID(), "Dr", null,
                tomorrow, LocalTime.of(10, 0), 1, null, AppointmentType.SCHEDULED, AppointmentPriority.NORMAL,
                AppointmentStatus.COMPLETED, OffsetDateTime.now(), OffsetDateTime.now()
        )));
        when(prescriptionService.list(tenantId)).thenReturn(List.of());
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of());

        service.queueDueReminders();

        verify(executionService, never()).create(eq(tenantId), any());
    }

    @Test
    void duplicateReminderIsPrevented() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(appointmentService.search(eq(tenantId), any())).thenReturn(List.of(new AppointmentRecord(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), "PAT-1", "John Doe", "9999999999", UUID.randomUUID(), "Dr", null,
                tomorrow, LocalTime.of(10, 0), 1, null, AppointmentType.SCHEDULED, AppointmentPriority.NORMAL,
                AppointmentStatus.BOOKED, OffsetDateTime.now(), OffsetDateTime.now()
        )));
        when(executionRepository.existsByTenantIdAndCampaignIdAndRecipientPatientIdAndChannelTypeAndScheduledAtBetween(
                eq(tenantId), any(), any(), eq(ChannelType.EMAIL), any(), any()
        )).thenReturn(true);
        when(prescriptionService.list(tenantId)).thenReturn(List.of());
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of());

        service.queueDueReminders();

        verify(executionService, never()).create(eq(tenantId), any());
    }

    @Test
    void missingPatientEmailSkipsAppointmentReminderSafely() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(appointmentService.search(eq(tenantId), any())).thenReturn(List.of(new AppointmentRecord(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), "PAT-1", "John Doe", "9999999999", UUID.randomUUID(), "Dr", null,
                tomorrow, LocalTime.of(10, 0), 1, null, AppointmentType.SCHEDULED, AppointmentPriority.NORMAL,
                AppointmentStatus.BOOKED, OffsetDateTime.now(), OffsetDateTime.now()
        )));
        when(patientRepository.findByTenantIdAndId(eq(tenantId), any())).thenReturn(java.util.Optional.empty());
        when(prescriptionService.list(tenantId)).thenReturn(List.of());
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of());

        service.queueDueReminders();

        verify(executionService, never()).create(eq(tenantId), any());
    }

    @Test
    void followUpReminderCreatesExecution() {
        when(appointmentService.search(eq(tenantId), any())).thenReturn(List.of());
        when(prescriptionService.list(tenantId)).thenReturn(List.of(new PrescriptionRecord(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), "PAT-1", "John", UUID.randomUUID(), "Dr",
                UUID.randomUUID(), null, "RX-1", 1, null, null, null, null, null, null,
                null, null, LocalDate.now().plusDays(2), PrescriptionStatus.FINALIZED, OffsetDateTime.now().minusDays(1), null,
                null, null, OffsetDateTime.now(), OffsetDateTime.now(), List.of(), List.of()
        )));
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of());

        service.queueDueReminders();

        verify(executionService, times(1)).create(eq(tenantId), any());
    }

    @Test
    void missedAppointmentFollowUpCreatesExecution() {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        LocalDate today = LocalDate.now();
        when(appointmentService.search(eq(tenantId), any())).thenAnswer(invocation -> {
            AppointmentSearchCriteria criteria = invocation.getArgument(1);
            if (criteria.status() == AppointmentStatus.NO_SHOW && today.equals(criteria.appointmentDate())) {
                return List.of(new AppointmentRecord(
                        appointmentId, tenantId, patientId, "PAT-1", "John Doe", "9999999999", UUID.randomUUID(), "Dr", null,
                        today, LocalTime.now().plusHours(2), 1, null, AppointmentType.SCHEDULED, AppointmentPriority.NORMAL,
                        AppointmentStatus.NO_SHOW, OffsetDateTime.now(), OffsetDateTime.now()
                ));
            }
            return List.of();
        });
        when(campaignRepository.findFirstByTenantIdAndCampaignTypeAndActiveTrueOrderByUpdatedAtDesc(eq(tenantId), eq(CampaignType.MISSED_APPOINTMENT_FOLLOW_UP)))
                .thenReturn(java.util.Optional.of(campaign(CampaignType.MISSED_APPOINTMENT_FOLLOW_UP)));
        when(prescriptionService.list(tenantId)).thenReturn(List.of());
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of());

        service.queueDueReminders();

        verify(executionService, times(1)).create(eq(tenantId), any());
    }

    @Test
    void followUpReminderRespectsConfiguredOffsetFromNotes() {
        UUID patientId = UUID.randomUUID();
        LocalDate followUpDate = LocalDate.now().plusDays(2);
        CampaignEntity configuredFollowUpCampaign = campaign(CampaignType.FOLLOW_UP_REMINDER);
        configuredFollowUpCampaign = CampaignEntity.create(
                tenantId,
                "FOLLOW_UP_REMINDER",
                CampaignType.FOLLOW_UP_REMINDER,
                com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType.SCHEDULED,
                com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType.ALL_PATIENTS,
                UUID.randomUUID(),
                "[Trigger Config]\n{\"reminderOffset\":\"PT2H\"}",
                UUID.randomUUID()
        );
        configuredFollowUpCampaign.activate();
        when(campaignRepository.findFirstByTenantIdAndCampaignTypeAndActiveTrueOrderByUpdatedAtDesc(eq(tenantId), eq(CampaignType.FOLLOW_UP_REMINDER)))
                .thenReturn(java.util.Optional.of(configuredFollowUpCampaign));
        when(templateRepository.findByTenantIdAndId(eq(tenantId), eq(configuredFollowUpCampaign.getTemplateId())))
                .thenReturn(java.util.Optional.of(CampaignTemplateEntity.create(tenantId, "Template", ChannelType.EMAIL, "S", "B", true)));
        when(appointmentService.search(eq(tenantId), any())).thenReturn(List.of());
        when(prescriptionService.list(tenantId)).thenReturn(List.of(new PrescriptionRecord(
                UUID.randomUUID(), tenantId, patientId, "PAT-1", "John", UUID.randomUUID(), "Dr",
                UUID.randomUUID(), null, "RX-1", 1, null, null, null, null, null, null,
                null, null, followUpDate, PrescriptionStatus.FINALIZED, OffsetDateTime.now().minusDays(1), null,
                null, null, OffsetDateTime.now(), OffsetDateTime.now(), List.of(), List.of()
        )));
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of());

        service.queueDueReminders();

        ArgumentCaptor<CampaignExecutionCreateCommand> captor = ArgumentCaptor.forClass(CampaignExecutionCreateCommand.class);
        verify(executionService, times(1)).create(eq(tenantId), captor.capture());
        CampaignExecutionCreateCommand command = captor.getValue();
        OffsetDateTime expected = followUpDate.atStartOfDay().atOffset(java.time.ZoneOffset.UTC).minusHours(2);
        org.assertj.core.api.Assertions.assertThat(command.scheduledAt()).isEqualTo(expected);
        org.assertj.core.api.Assertions.assertThat(command.sourceType()).isEqualTo("FOLLOW_UP");
    }

    @Test
    void refillReminderCreatesExecution() {
        when(appointmentService.search(eq(tenantId), any())).thenReturn(List.of());
        when(prescriptionService.list(tenantId)).thenReturn(List.of(new PrescriptionRecord(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), "PAT-1", "John", UUID.randomUUID(), "Dr",
                UUID.randomUUID(), null, "RX-1", 1, null, null, null, null, null, null,
                null, null, null, PrescriptionStatus.FINALIZED, OffsetDateTime.now().minusDays(31), null,
                null, null, OffsetDateTime.now(), OffsetDateTime.now(), List.of(), List.of()
        )));
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of());

        service.queueDueReminders();

        verify(executionService, times(1)).create(eq(tenantId), any());
    }

    @Test
    void refillReminderUsesEstimatedRefillDaysFromConfig() {
        CampaignEntity refillCampaign = CampaignEntity.create(
                tenantId,
                "REFILL_REMINDER",
                CampaignType.REFILL_REMINDER,
                com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType.SCHEDULED,
                com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType.ALL_PATIENTS,
                UUID.randomUUID(),
                "[Trigger Config]\n{\"estimatedRefillDays\":10}",
                UUID.randomUUID()
        );
        refillCampaign.activate();
        when(campaignRepository.findFirstByTenantIdAndCampaignTypeAndActiveTrueOrderByUpdatedAtDesc(eq(tenantId), eq(CampaignType.REFILL_REMINDER)))
                .thenReturn(java.util.Optional.of(refillCampaign));
        when(templateRepository.findByTenantIdAndId(eq(tenantId), eq(refillCampaign.getTemplateId())))
                .thenReturn(java.util.Optional.of(CampaignTemplateEntity.create(tenantId, "Template", ChannelType.EMAIL, "S", "B", true)));

        when(appointmentService.search(eq(tenantId), any())).thenReturn(List.of());
        when(prescriptionService.list(tenantId)).thenReturn(List.of(new PrescriptionRecord(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), "PAT-1", "John", UUID.randomUUID(), "Dr",
                UUID.randomUUID(), null, "RX-1", 1, null, null, null, null, null, null,
                null, null, null, PrescriptionStatus.FINALIZED, OffsetDateTime.now().minusDays(11), null,
                null, null, OffsetDateTime.now(), OffsetDateTime.now(), List.of(), List.of()
        )));
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of());

        service.queueDueReminders();

        ArgumentCaptor<CampaignExecutionCreateCommand> captor = ArgumentCaptor.forClass(CampaignExecutionCreateCommand.class);
        verify(executionService, times(1)).create(eq(tenantId), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().sourceType()).isEqualTo("PRESCRIPTION");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().sourceReferenceId()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(captor.getValue().reminderWindow()).contains("REFILL_DUE_");
    }

    @Test
    void refillReminderUsesMedicineDurationWhenAvailable() {
        when(appointmentService.search(eq(tenantId), any())).thenReturn(List.of());
        when(prescriptionService.list(tenantId)).thenReturn(List.of(new PrescriptionRecord(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), "PAT-1", "John", UUID.randomUUID(), "Dr",
                UUID.randomUUID(), null, "RX-1", 1, null, null, null, null, null, null,
                null, null, null, PrescriptionStatus.FINALIZED, OffsetDateTime.now().minusDays(6), null,
                null, null, OffsetDateTime.now(), OffsetDateTime.now(),
                List.of(new com.deepthoughtnet.clinic.prescription.service.model.PrescriptionMedicineRecord("A", null, null, "1", "1", "5 days", null, null, 1)),
                List.of()
        )));
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of());

        service.queueDueReminders();

        verify(executionService, times(1)).create(eq(tenantId), any());
    }

    @Test
    void paidBillIsIgnoredForBillingReminder() {
        when(appointmentService.search(eq(tenantId), any())).thenReturn(List.of());
        when(prescriptionService.list(tenantId)).thenReturn(List.of());
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of(new BillRecord(
                UUID.randomUUID(), tenantId, "B-1", UUID.randomUUID(), "PAT-1", "John", null, null,
                LocalDate.now().minusDays(10), BillStatus.PAID, BigDecimal.TEN, DiscountType.NONE, BigDecimal.ZERO,
                BigDecimal.ZERO, null, null, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                BigDecimal.TEN, BigDecimal.ZERO, null, null, OffsetDateTime.now(), OffsetDateTime.now(), List.of()
        )));

        service.queueDueReminders();

        verify(executionService, never()).create(eq(tenantId), any());
    }

    @Test
    void overdueUnpaidBillCreatesExecutionWithSourceReference() {
        UUID billId = UUID.randomUUID();
        when(appointmentService.search(eq(tenantId), any())).thenReturn(List.of());
        when(prescriptionService.list(tenantId)).thenReturn(List.of());
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of(new BillRecord(
                billId, tenantId, "B-1", UUID.randomUUID(), "PAT-1", "John", null, null,
                LocalDate.now().minusDays(10), BillStatus.UNPAID, BigDecimal.TEN, DiscountType.NONE, BigDecimal.ZERO,
                BigDecimal.ZERO, null, null, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.TEN, null, null, OffsetDateTime.now(), OffsetDateTime.now(), List.of()
        )));

        service.queueDueReminders();

        ArgumentCaptor<CampaignExecutionCreateCommand> captor = ArgumentCaptor.forClass(CampaignExecutionCreateCommand.class);
        verify(executionService, times(1)).create(eq(tenantId), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().sourceType()).isEqualTo("BILL");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().sourceReferenceId()).isEqualTo(billId);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().reminderWindow()).contains("OD3");
    }

    @Test
    void refundedAndCancelledBillsAreIgnored() {
        when(appointmentService.search(eq(tenantId), any())).thenReturn(List.of());
        when(prescriptionService.list(tenantId)).thenReturn(List.of());
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of(
                new BillRecord(UUID.randomUUID(), tenantId, "B-1", UUID.randomUUID(), "PAT-1", "John", null, null,
                        LocalDate.now().minusDays(10), BillStatus.REFUNDED, BigDecimal.TEN, DiscountType.NONE, BigDecimal.ZERO,
                        BigDecimal.ZERO, null, null, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                        BigDecimal.TEN, BigDecimal.ZERO, null, null, OffsetDateTime.now(), OffsetDateTime.now(), List.of()),
                new BillRecord(UUID.randomUUID(), tenantId, "B-2", UUID.randomUUID(), "PAT-1", "John", null, null,
                        LocalDate.now().minusDays(10), BillStatus.CANCELLED, BigDecimal.TEN, DiscountType.NONE, BigDecimal.ZERO,
                        BigDecimal.ZERO, null, null, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                        BigDecimal.TEN, BigDecimal.ZERO, null, null, OffsetDateTime.now(), OffsetDateTime.now(), List.of())
        ));

        service.queueDueReminders();

        verify(executionService, never()).create(eq(tenantId), any());
    }

    @Test
    void billingReminderRespectsConfiguredOverdueDays() {
        CampaignEntity billingCampaign = CampaignEntity.create(
                tenantId,
                "BILLING_REMINDER",
                CampaignType.BILLING_REMINDER,
                com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType.SCHEDULED,
                com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType.ALL_PATIENTS,
                UUID.randomUUID(),
                "[Trigger Config]\n{\"overdueDays\":7}",
                UUID.randomUUID()
        );
        billingCampaign.activate();
        when(campaignRepository.findFirstByTenantIdAndCampaignTypeAndActiveTrueOrderByUpdatedAtDesc(eq(tenantId), eq(CampaignType.BILLING_REMINDER)))
                .thenReturn(java.util.Optional.of(billingCampaign));
        when(templateRepository.findByTenantIdAndId(eq(tenantId), eq(billingCampaign.getTemplateId())))
                .thenReturn(java.util.Optional.of(CampaignTemplateEntity.create(tenantId, "Template", ChannelType.EMAIL, "S", "B", true)));

        when(appointmentService.search(eq(tenantId), any())).thenReturn(List.of());
        when(prescriptionService.list(tenantId)).thenReturn(List.of());
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of(new BillRecord(
                UUID.randomUUID(), tenantId, "B-1", UUID.randomUUID(), "PAT-1", "John", null, null,
                LocalDate.now().minusDays(5), BillStatus.UNPAID, BigDecimal.TEN, DiscountType.NONE, BigDecimal.ZERO,
                BigDecimal.ZERO, null, null, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.TEN, null, null, OffsetDateTime.now(), OffsetDateTime.now(), List.of()
        )));

        service.queueDueReminders();

        verify(executionService, never()).create(eq(tenantId), any());
    }

    @Test
    void duplicateBillWindowPreventsExecution() {
        when(executionRepository.existsByTenantIdAndCampaignIdAndSourceReferenceIdAndReminderWindowAndChannelType(
                eq(tenantId), any(), any(), any(), eq(ChannelType.EMAIL)
        )).thenReturn(true);
        when(appointmentService.search(eq(tenantId), any())).thenReturn(List.of());
        when(prescriptionService.list(tenantId)).thenReturn(List.of());
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of(new BillRecord(
                UUID.randomUUID(), tenantId, "B-1", UUID.randomUUID(), "PAT-1", "John", null, null,
                LocalDate.now().minusDays(10), BillStatus.UNPAID, BigDecimal.TEN, DiscountType.NONE, BigDecimal.ZERO,
                BigDecimal.ZERO, null, null, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.TEN, null, null, OffsetDateTime.now(), OffsetDateTime.now(), List.of()
        )));

        service.queueDueReminders();

        verify(executionService, never()).create(eq(tenantId), any());
    }

    @Test
    void vaccinationReminderCreatesExecutionForUpcomingDueRecord() {
        when(appointmentService.search(eq(tenantId), any())).thenReturn(List.of());
        when(prescriptionService.list(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of());
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of(new PatientVaccinationRecord(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), "PAT-1", "John Doe", UUID.randomUUID(), "Influenza",
                1, LocalDate.now().minusDays(20), LocalDate.now().plusDays(1), null, null, null, null, OffsetDateTime.now()
        )));
        when(vaccinationService.listOverdue(tenantId)).thenReturn(List.of());

        service.queueDueReminders();

        ArgumentCaptor<CampaignExecutionCreateCommand> captor = ArgumentCaptor.forClass(CampaignExecutionCreateCommand.class);
        verify(executionService, times(1)).create(eq(tenantId), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().sourceType()).isEqualTo("VACCINATION");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().sourceReferenceId()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(captor.getValue().reminderWindow()).contains("VAX_");
    }

    @Test
    void overdueVaccinationIsIncludedByDefault() {
        when(appointmentService.search(eq(tenantId), any())).thenReturn(List.of());
        when(prescriptionService.list(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of());
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of());
        when(vaccinationService.listOverdue(tenantId)).thenReturn(List.of(new PatientVaccinationRecord(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), "PAT-1", "John Doe", UUID.randomUUID(), "Hepatitis",
                2, LocalDate.now().minusDays(90), LocalDate.now().minusDays(2), null, null, null, null, OffsetDateTime.now()
        )));

        service.queueDueReminders();

        verify(executionService, times(1)).create(eq(tenantId), any());
    }

    @Test
    void vaccinationDuplicateWindowPreventsExecution() {
        when(executionRepository.existsByTenantIdAndCampaignIdAndSourceReferenceIdAndReminderWindowAndChannelType(
                eq(tenantId), any(), any(), any(), eq(ChannelType.EMAIL)
        )).thenReturn(true);
        when(appointmentService.search(eq(tenantId), any())).thenReturn(List.of());
        when(prescriptionService.list(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of());
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of(new PatientVaccinationRecord(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), "PAT-1", "John Doe", UUID.randomUUID(), "Tetanus",
                1, LocalDate.now().minusDays(30), LocalDate.now(), null, null, null, null, OffsetDateTime.now()
        )));
        when(vaccinationService.listOverdue(tenantId)).thenReturn(List.of());

        service.queueDueReminders();

        verify(executionService, never()).create(eq(tenantId), any());
    }

    @Test
    void birthdayTodayGeneratesWellnessExecution() {
        PatientEntity patient = PatientEntity.create(tenantId, "PAT-BDAY");
        LocalDate dob = LocalDate.of(1990, LocalDate.now().getMonthValue(), LocalDate.now().getDayOfMonth());
        patient.update("Birthday", "Patient", null, dob, null, "9999999999", "birthday@example.com", null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
        when(patientRepository.findByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of(patient));
        when(patientRepository.findByTenantIdAndId(eq(tenantId), eq(patient.getId()))).thenReturn(java.util.Optional.of(patient));
        when(appointmentService.search(eq(tenantId), any())).thenReturn(List.of());
        when(prescriptionService.list(tenantId)).thenReturn(List.of());
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of());
        when(vaccinationService.listOverdue(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of());

        service.queueDueReminders();

        ArgumentCaptor<CampaignExecutionCreateCommand> captor = ArgumentCaptor.forClass(CampaignExecutionCreateCommand.class);
        verify(executionService, times(1)).create(eq(tenantId), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().sourceType()).isEqualTo("PATIENT_BIRTHDAY");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().sourceReferenceId()).isEqualTo(patient.getId());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().reminderWindow()).contains("BDAY_");
    }

    @Test
    void birthdayDaysBeforeConfigRespected() {
        CampaignEntity wellnessCampaign = CampaignEntity.create(
                tenantId,
                "WELLNESS_MESSAGE",
                CampaignType.WELLNESS_MESSAGE,
                com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType.SCHEDULED,
                com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType.ALL_PATIENTS,
                UUID.randomUUID(),
                "[Trigger Config]\n{\"daysBeforeBirthday\":2}",
                UUID.randomUUID()
        );
        wellnessCampaign.activate();
        when(campaignRepository.findFirstByTenantIdAndCampaignTypeAndActiveTrueOrderByUpdatedAtDesc(eq(tenantId), eq(CampaignType.WELLNESS_MESSAGE)))
                .thenReturn(java.util.Optional.of(wellnessCampaign));
        when(templateRepository.findByTenantIdAndId(eq(tenantId), eq(wellnessCampaign.getTemplateId())))
                .thenReturn(java.util.Optional.of(CampaignTemplateEntity.create(tenantId, "Template", ChannelType.EMAIL, "S", "B", true)));

        PatientEntity patient = PatientEntity.create(tenantId, "PAT-BDAY-2");
        LocalDate target = LocalDate.now().plusDays(2);
        LocalDate dob = LocalDate.of(1985, target.getMonthValue(), target.getDayOfMonth());
        patient.update("Early", "Birthday", null, dob, null, "9999999999", "early@example.com", null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
        when(patientRepository.findByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of(patient));
        when(patientRepository.findByTenantIdAndId(eq(tenantId), eq(patient.getId()))).thenReturn(java.util.Optional.of(patient));
        when(appointmentService.search(eq(tenantId), any())).thenReturn(List.of());
        when(prescriptionService.list(tenantId)).thenReturn(List.of());
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of());
        when(vaccinationService.listOverdue(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of());

        service.queueDueReminders();

        verify(executionService, times(1)).create(eq(tenantId), any());
    }

    @Test
    void birthdayPerYearDuplicatePreventsExecution() {
        PatientEntity patient = PatientEntity.create(tenantId, "PAT-BDAY-DUP");
        LocalDate dob = LocalDate.of(1992, LocalDate.now().getMonthValue(), LocalDate.now().getDayOfMonth());
        patient.update("Dup", "Birthday", null, dob, null, "9999999999", "dup@example.com", null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
        when(patientRepository.findByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of(patient));
        when(patientRepository.findByTenantIdAndId(eq(tenantId), eq(patient.getId()))).thenReturn(java.util.Optional.of(patient));
        when(executionRepository.existsByTenantIdAndCampaignIdAndSourceReferenceIdAndReminderWindowAndChannelType(
                eq(tenantId), any(), eq(patient.getId()), any(), eq(ChannelType.EMAIL)
        )).thenReturn(true);
        when(appointmentService.search(eq(tenantId), any())).thenReturn(List.of());
        when(prescriptionService.list(tenantId)).thenReturn(List.of());
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of());
        when(vaccinationService.listOverdue(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of());

        service.queueDueReminders();

        verify(executionService, never()).create(eq(tenantId), any());
    }

    private CampaignEntity campaign(CampaignType type) {
        CampaignEntity entity = CampaignEntity.create(tenantId, type.name(), type, com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType.SCHEDULED,
                com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType.ALL_PATIENTS, UUID.randomUUID(), null, UUID.randomUUID());
        entity.activate();
        return entity;
    }
}
