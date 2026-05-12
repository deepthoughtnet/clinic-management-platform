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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CarePilotReminderTriggerServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private PlatformTenantManagementService tenantManagementService;
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

        when(executionRepository.existsByTenantIdAndCampaignIdAndRecipientPatientIdAndChannelTypeAndScheduledAtBetween(
                eq(tenantId), any(), any(), eq(ChannelType.EMAIL), any(), any()
        )).thenReturn(false);

        when(campaignRepository.findFirstByTenantIdAndCampaignTypeAndActiveTrueOrderByUpdatedAtDesc(eq(tenantId), any()))
                .thenAnswer(invocation -> java.util.Optional.of(campaign((CampaignType) invocation.getArgument(1))));
        when(templateRepository.findByTenantIdAndId(eq(tenantId), any()))
                .thenAnswer(invocation -> java.util.Optional.of(CampaignTemplateEntity.create(tenantId, "Template", ChannelType.EMAIL, "S", "B", true)));
    }

    @Test
    void appointmentRemindersCreate24hAnd2hExecutions() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(appointmentService.search(eq(tenantId), any())).thenReturn(List.of(new AppointmentRecord(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), "PAT-1", "John Doe", "9999999999", UUID.randomUUID(), "Dr", null,
                tomorrow, LocalTime.of(10, 0), 1, null, AppointmentType.SCHEDULED, AppointmentPriority.NORMAL,
                AppointmentStatus.BOOKED, OffsetDateTime.now(), OffsetDateTime.now()
        )));
        when(prescriptionService.list(tenantId)).thenReturn(List.of());
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of());

        service.queueDueReminders();

        verify(executionService, times(2)).create(eq(tenantId), any());
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
    void followUpReminderCreatesExecution() {
        when(appointmentService.search(eq(tenantId), any())).thenReturn(List.of());
        when(prescriptionService.list(tenantId)).thenReturn(List.of(new PrescriptionRecord(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), "PAT-1", "John", UUID.randomUUID(), "Dr",
                UUID.randomUUID(), null, "RX-1", 1, null, null, null, null, null, null,
                null, null, LocalDate.now(), PrescriptionStatus.FINALIZED, OffsetDateTime.now().minusDays(1), null,
                null, null, OffsetDateTime.now(), OffsetDateTime.now(), List.of(), List.of()
        )));
        when(vaccinationService.listDue(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any())).thenReturn(List.of());

        service.queueDueReminders();

        verify(executionService, times(1)).create(eq(tenantId), any());
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

    private CampaignEntity campaign(CampaignType type) {
        CampaignEntity entity = CampaignEntity.create(tenantId, type.name(), type, com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType.SCHEDULED,
                com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType.ALL_PATIENTS, UUID.randomUUID(), null, UUID.randomUUID());
        entity.activate();
        return entity;
    }
}
