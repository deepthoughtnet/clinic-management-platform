package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.appointment.db.AppointmentRepository;
import com.deepthoughtnet.clinic.billing.db.BillRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryAttemptEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryAttemptRepository;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionRepository;
import com.deepthoughtnet.clinic.vaccination.db.PatientVaccinationRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class CarePilotCampaignRuntimeServiceTest {
    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignExecutionRepository executionRepository;
    @Mock private CampaignDeliveryAttemptRepository attemptRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private BillRepository billRepository;
    @Mock private PatientVaccinationRepository patientVaccinationRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private CarePilotRuntimeSchedulerMonitor schedulerMonitor;

    private CarePilotCampaignRuntimeService service;
    private UUID tenantId;
    private UUID campaignId;
    private UUID executionId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CarePilotCampaignRuntimeService(
                campaignRepository,
                executionRepository,
                attemptRepository,
                patientRepository,
                appointmentRepository,
                prescriptionRepository,
                billRepository,
                patientVaccinationRepository,
                appUserRepository,
                schedulerMonitor
        );
        tenantId = UUID.randomUUID();
        campaignId = UUID.randomUUID();
        executionId = UUID.randomUUID();
    }

    @Test
    void runtimeUsesPersistedDeliveryAttemptsWithoutMixingRetryCount() {
        CampaignEntity campaign = CampaignEntity.create(tenantId, "CAM-2026-TEST", "Delivery Count Campaign", CampaignType.CUSTOM, TriggerType.MANUAL, null, null, null, UUID.randomUUID());
        CampaignExecutionEntity execution = CampaignExecutionEntity.create(
                tenantId,
                campaignId,
                null,
                ChannelType.EMAIL,
                null,
                OffsetDateTime.parse("2026-07-19T09:32:12Z"),
                null,
                null,
                null,
                null
        );
        setField(execution, "id", executionId);
        setField(execution, "status", ExecutionStatus.SUCCEEDED);
        setField(execution, "deliveryStatus", com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus.SENT);
        setField(execution, "executedAt", OffsetDateTime.parse("2026-07-19T09:35:12Z"));
        setField(execution, "lastAttemptAt", OffsetDateTime.parse("2026-07-19T09:34:12Z"));
        setField(execution, "providerName", "carepilot-email-smtp");
        setField(execution, "attemptCount", 0);

        when(campaignRepository.findByTenantIdAndId(tenantId, campaignId)).thenReturn(Optional.of(campaign));
        when(executionRepository.findByTenantIdAndCampaignIdOrderByUpdatedAtDesc(tenantId, campaignId)).thenReturn(List.of(execution));
        when(executionRepository.findTop50ByTenantIdAndCampaignIdOrderByUpdatedAtDesc(tenantId, campaignId)).thenReturn(List.of(execution));
        when(attemptRepository.findByTenantIdAndExecutionIdInOrderByAttemptedAtDesc(tenantId, List.of(executionId))).thenReturn(List.of(
                CampaignDeliveryAttemptEntity.create(
                        tenantId,
                        executionId,
                        1,
                        "carepilot-email-smtp",
                        ChannelType.EMAIL,
                        com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus.SENT,
                        null,
                        null,
                        OffsetDateTime.parse("2026-07-19T09:34:12Z")
                )
        ));
        when(schedulerMonitor.reminderSchedulerStatus()).thenReturn("DISABLED");
        when(schedulerMonitor.lastReminderScanAt(tenantId)).thenReturn(null);

        var runtime = service.runtime(tenantId, campaignId);

        assertThat(runtime.recentExecutions()).hasSize(1);
        assertThat(runtime.recentExecutions().get(0).deliveryAttemptCount()).isEqualTo(1);
        assertThat(runtime.recentExecutions().get(0).retryCount()).isZero();
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
