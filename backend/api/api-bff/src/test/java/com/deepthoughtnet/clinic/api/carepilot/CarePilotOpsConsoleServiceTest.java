package com.deepthoughtnet.clinic.api.carepilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.ops.PlatformOpsService;
import com.deepthoughtnet.clinic.api.ops.SchedulerLockMonitor;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryAttemptEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryAttemptRepository;
import com.deepthoughtnet.clinic.carepilot.execution.service.CampaignExecutionService;
import com.deepthoughtnet.clinic.carepilot.execution.service.model.CampaignExecutionRecord;
import com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CarePilotOpsConsoleServiceTest {

    @Test
    void listExecutionsKeepsExecutionAndDeliverySemanticsSeparate() {
        UUID tenantId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID succeededExecutionId = UUID.randomUUID();
        UUID queuedExecutionId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-07-19T06:30:00Z");

        CampaignExecutionService executionService = mock(CampaignExecutionService.class);
        CampaignRepository campaignRepository = mock(CampaignRepository.class);
        PatientRepository patientRepository = mock(PatientRepository.class);
        CampaignDeliveryAttemptRepository attemptRepository = mock(CampaignDeliveryAttemptRepository.class);
        PlatformOpsService platformOpsService = mock(PlatformOpsService.class);
        CarePilotMessagingStatusService messagingStatusService = mock(CarePilotMessagingStatusService.class);
        CarePilotRuntimeSchedulerMonitor reminderSchedulerMonitor = mock(CarePilotRuntimeSchedulerMonitor.class);
        SchedulerLockMonitor schedulerLockMonitor = mock(SchedulerLockMonitor.class);

        CarePilotOpsConsoleService service = new CarePilotOpsConsoleService(
                executionService,
                campaignRepository,
                patientRepository,
                attemptRepository,
                platformOpsService,
                messagingStatusService,
                reminderSchedulerMonitor,
                schedulerLockMonitor,
                true,
                30
        );

        CampaignEntity campaign = CampaignEntity.create(tenantId, "CAM-2026-000002", "Completed Campaign", CampaignType.CUSTOM, TriggerType.MANUAL, AudienceType.ALL_PATIENTS, null, null, UUID.randomUUID());
        setField(campaign, "id", campaignId);

        CampaignExecutionRecord succeededDeliveryFailed = new CampaignExecutionRecord(
                succeededExecutionId,
                tenantId,
                campaignId,
                null,
                ChannelType.EMAIL,
                null,
                createdAt,
                ExecutionStatus.SUCCEEDED,
                2,
                1,
                null,
                createdAt.plusMinutes(5),
                null,
                com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus.FAILED,
                "carepilot-email-smtp",
                "msg-1",
                "APPOINTMENT",
                null,
                "H24",
                null,
                null,
                null,
                createdAt,
                createdAt.plusMinutes(1),
                createdAt.plusMinutes(5)
        );
        CampaignExecutionRecord queued = new CampaignExecutionRecord(
                queuedExecutionId,
                tenantId,
                campaignId,
                null,
                ChannelType.EMAIL,
                null,
                createdAt.minusMinutes(20),
                ExecutionStatus.QUEUED,
                0,
                0,
                null,
                null,
                null,
                com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus.QUEUED,
                "carepilot-email-smtp",
                null,
                "APPOINTMENT",
                null,
                "H24",
                null,
                null,
                null,
                createdAt.minusMinutes(20),
                null,
                createdAt.minusMinutes(20)
        );

        when(executionService.list(tenantId)).thenReturn(List.of(succeededDeliveryFailed, queued));
        when(campaignRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(campaign));
        when(patientRepository.findByTenantIdAndIdIn(any(), any())).thenReturn(List.of());
        when(attemptRepository.findByTenantIdAndExecutionIdInOrderByAttemptedAtDesc(tenantId, List.of(succeededExecutionId, queuedExecutionId))).thenReturn(List.of(
                CampaignDeliveryAttemptEntity.create(
                        tenantId,
                        succeededExecutionId,
                        1,
                        "carepilot-email-smtp",
                        ChannelType.EMAIL,
                        com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus.FAILED,
                        null,
                        null,
                        createdAt.plusMinutes(5)
                )
        ));

        var rows = service.listExecutions(tenantId, null, null, null, null, false, null, null, null);

        assertThat(rows).hasSize(2);
        var succeededRow = rows.stream().filter(row -> row.status() == ExecutionStatus.SUCCEEDED).findFirst().orElseThrow();
        var queuedRow = rows.stream().filter(row -> row.status() == ExecutionStatus.QUEUED).findFirst().orElseThrow();

        assertThat(succeededRow.deliveryStatus()).isEqualTo(com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus.FAILED.name());
        assertThat(succeededRow.queueAgeMinutes()).isEqualTo(-1L);
        assertThat(succeededRow.deliveryAttemptCount()).isEqualTo(1);
        assertThat(succeededRow.retryCount()).isEqualTo(2);

        assertThat(queuedRow.deliveryStatus()).isEqualTo(com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus.QUEUED.name());
        assertThat(queuedRow.queueAgeMinutes()).isGreaterThan(0L);
        assertThat(queuedRow.deliveryAttemptCount()).isZero();
        assertThat(queuedRow.retryCount()).isZero();
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
