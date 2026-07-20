package com.deepthoughtnet.clinic.carepilot.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryAttemptEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryAttemptRepository;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryEventEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryEventRepository;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CarePilotAnalyticsServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private CampaignRepository campaignRepository;
    private CampaignExecutionRepository executionRepository;
    private CampaignDeliveryAttemptRepository attemptRepository;
    private CampaignDeliveryEventRepository eventRepository;
    private CarePilotAnalyticsService service;

    @BeforeEach
    void setUp() {
        campaignRepository = mock(CampaignRepository.class);
        executionRepository = mock(CampaignExecutionRepository.class);
        attemptRepository = mock(CampaignDeliveryAttemptRepository.class);
        eventRepository = mock(CampaignDeliveryEventRepository.class);
        service = new CarePilotAnalyticsService(campaignRepository, executionRepository, attemptRepository, eventRepository);
    }

    @Test
    void summaryReturnsZeroMetricsForEmptyData() {
        when(campaignRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of());
        when(executionRepository.findByTenantIdAndScheduledAtBetweenOrderByScheduledAtDesc(any(), any(), any())).thenReturn(List.of());
        when(eventRepository.findByTenantIdAndExecutionIdOrderByEventTimestampAsc(any(), any())).thenReturn(List.of());

        var summary = service.summary(tenantId, null, null, null);

        assertThat(summary.totalCampaigns()).isZero();
        assertThat(summary.totalExecutions()).isZero();
        assertThat(summary.successRate()).isZero();
        assertThat(summary.failureRate()).isZero();
    }

    @Test
    void summaryCountsStatusesAndCampaignFilterWorks() {
        CampaignEntity campaign = CampaignEntity.create(tenantId, "CAM-2026-000001", "Reminder", CampaignType.CUSTOM, TriggerType.MANUAL, AudienceType.ALL_PATIENTS, null, null, UUID.randomUUID());
        campaign.activate();
        when(campaignRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(campaign));

        UUID c1 = campaign.getId();
        CampaignExecutionEntity success = CampaignExecutionEntity.create(tenantId, c1, null, ChannelType.EMAIL, null, OffsetDateTime.now().minusDays(1), null, null, null, null);
        success.markSucceeded("email", "m1");
        CampaignExecutionEntity failed = CampaignExecutionEntity.create(tenantId, c1, null, ChannelType.EMAIL, null, OffsetDateTime.now().minusDays(1), null, null, null, null);
        failed.markFailed("err", "FAILED", MessageDeliveryStatus.FAILED, null, 3);

        when(executionRepository.findByTenantIdAndCampaignIdAndScheduledAtBetweenOrderByScheduledAtDesc(any(), any(), any(), any()))
                .thenReturn(List.of(success, failed));
        when(attemptRepository.findByTenantIdAndExecutionIdInOrderByAttemptedAtDesc(any(), any())).thenReturn(List.of());
        when(eventRepository.findByTenantIdAndExecutionIdOrderByEventTimestampAsc(any(), any())).thenReturn(List.of());

        var summary = service.summary(tenantId, LocalDate.now().minusDays(2), LocalDate.now(), c1);

        assertThat(summary.totalExecutions()).isEqualTo(2);
        assertThat(summary.successfulExecutions()).isEqualTo(1);
        assertThat(summary.failedExecutions()).isEqualTo(1);
        assertThat(summary.executionsByCampaign()).hasSize(1);
        assertThat(summary.executionsByCampaign().getFirst().campaignReference()).isEqualTo("CAM-2026-000001");
    }

    @Test
    void summaryCountsDeliveryProgressionFromExecutionStateOnly() {
        UUID campaignId = UUID.randomUUID();
        CampaignEntity campaign = CampaignEntity.create(tenantId, "CAM-2026-000002", "UAT Manual Appointment Campaign", CampaignType.APPOINTMENT_REMINDER, TriggerType.MANUAL, AudienceType.ALL_PATIENTS, null, null, UUID.randomUUID());
        campaign.activate();
        when(campaignRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(campaign));

        CampaignExecutionEntity queued = CampaignExecutionEntity.create(tenantId, campaignId, null, ChannelType.EMAIL, null, OffsetDateTime.now().minusHours(2), null, null, null, null);
        CampaignExecutionEntity sent = CampaignExecutionEntity.create(tenantId, campaignId, null, ChannelType.EMAIL, null, OffsetDateTime.now().minusHours(2), null, null, null, null);
        sent.markSucceeded("email", "msg-1");
        CampaignExecutionEntity delivered = CampaignExecutionEntity.create(tenantId, campaignId, null, ChannelType.EMAIL, null, OffsetDateTime.now().minusHours(2), null, null, null, null);
        delivered.markSucceeded("email", "msg-2");
        delivered.markDeliveryLifecycleStatus(MessageDeliveryStatus.DELIVERED, null, OffsetDateTime.now().minusMinutes(30));
        CampaignExecutionEntity read = CampaignExecutionEntity.create(tenantId, campaignId, null, ChannelType.EMAIL, null, OffsetDateTime.now().minusHours(2), null, null, null, null);
        read.markSucceeded("email", "msg-3");
        read.markDeliveryLifecycleStatus(MessageDeliveryStatus.READ, null, OffsetDateTime.now().minusMinutes(20));
        CampaignExecutionEntity undelivered = CampaignExecutionEntity.create(tenantId, campaignId, null, ChannelType.EMAIL, null, OffsetDateTime.now().minusHours(2), null, null, null, null);
        undelivered.markSucceeded("email", "msg-4");
        undelivered.markDeliveryLifecycleStatus(MessageDeliveryStatus.UNDELIVERED, "undeliverable", OffsetDateTime.now().minusMinutes(10));

        when(executionRepository.findByTenantIdAndCampaignIdAndScheduledAtBetweenOrderByScheduledAtDesc(any(), any(), any(), any()))
                .thenReturn(List.of(queued, sent, delivered, read, undelivered));
        when(attemptRepository.findByTenantIdAndExecutionIdInOrderByAttemptedAtDesc(any(), any())).thenReturn(List.of());
        when(eventRepository.findByTenantIdAndExecutionIdOrderByEventTimestampAsc(any(), any())).thenReturn(List.of());

        var summary = service.summary(tenantId, LocalDate.now().minusDays(2), LocalDate.now(), campaignId);

        assertThat(summary.totalExecutions()).isEqualTo(5);
        assertThat(summary.queuedExecutions()).isEqualTo(1);
        assertThat(summary.sentExecutions()).isEqualTo(1);
        assertThat(summary.deliveredExecutions()).isEqualTo(1);
        assertThat(summary.readExecutions()).isEqualTo(1);
        assertThat(summary.undeliveredExecutions()).isEqualTo(1);
        assertThat(summary.successfulExecutions()).isEqualTo(4);
    }

    @Test
    void summaryCountsRetriesIndependentlyFromDeliveryProgression() {
        UUID campaignId = UUID.randomUUID();
        CampaignEntity campaign = CampaignEntity.create(tenantId, "CAM-2026-000003", "Retry Campaign", CampaignType.CUSTOM, TriggerType.MANUAL, AudienceType.ALL_PATIENTS, null, null, UUID.randomUUID());
        campaign.activate();
        when(campaignRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(campaign));

        CampaignExecutionEntity sent = CampaignExecutionEntity.create(tenantId, campaignId, null, ChannelType.EMAIL, null, OffsetDateTime.now().minusHours(2), null, null, null, null);
        sent.markSucceeded("email", "msg-1");
        CampaignExecutionEntity retrying = CampaignExecutionEntity.create(tenantId, campaignId, null, ChannelType.EMAIL, null, OffsetDateTime.now().minusHours(2), null, null, null, null);
        retrying.markFailed("err", "FAILED", MessageDeliveryStatus.FAILED, OffsetDateTime.now().plusMinutes(15), 3);
        when(executionRepository.findByTenantIdAndCampaignIdAndScheduledAtBetweenOrderByScheduledAtDesc(any(), any(), any(), any()))
                .thenReturn(List.of(sent, retrying));
        when(attemptRepository.findByTenantIdAndExecutionIdInOrderByAttemptedAtDesc(any(), any())).thenReturn(List.of());
        when(eventRepository.findByTenantIdAndExecutionIdOrderByEventTimestampAsc(any(), any())).thenReturn(List.of());

        var summary = service.summary(tenantId, LocalDate.now().minusDays(2), LocalDate.now(), campaignId);

        assertThat(summary.totalExecutions()).isEqualTo(2);
        assertThat(summary.sentExecutions()).isEqualTo(1);
        assertThat(summary.retryingExecutions()).isEqualTo(1);
        assertThat(summary.failedExecutions()).isZero();
    }

    @Test
    void listFailedExecutionsAppliesFilters() {
        UUID c1 = UUID.randomUUID();
        CampaignExecutionEntity failed = CampaignExecutionEntity.create(tenantId, c1, null, ChannelType.EMAIL, null, OffsetDateTime.now().minusDays(1), null, null, null, null);
        failed.markFailed("err", "FAILED", MessageDeliveryStatus.FAILED, null, 3);
        CampaignExecutionEntity dead = CampaignExecutionEntity.create(tenantId, c1, null, ChannelType.IN_APP, null, OffsetDateTime.now().minusDays(1), null, null, null, null);
        dead.markFailed("err", "FAILED", MessageDeliveryStatus.FAILED, OffsetDateTime.now().plusMinutes(2), 1);

        when(executionRepository.findByTenantIdAndStatusInAndScheduledAtBetweenOrderByUpdatedAtDesc(any(), any(), any(), any()))
                .thenReturn(List.of(failed, dead));

        var rows = service.listFailedExecutions(tenantId, null, null, null, ChannelType.EMAIL, null, null, true);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).channelType()).isEqualTo(ChannelType.EMAIL);
        assertThat(rows.get(0).status()).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    void timelineReturnsAttemptsForTenantExecution() {
        UUID executionId = UUID.randomUUID();
        CampaignExecutionEntity execution = CampaignExecutionEntity.create(tenantId, UUID.randomUUID(), null, ChannelType.EMAIL, null, OffsetDateTime.now().minusDays(1), null, null, null, null);
        when(executionRepository.findByTenantIdAndId(tenantId, executionId)).thenReturn(Optional.of(execution));
        when(attemptRepository.findByTenantIdAndExecutionIdOrderByAttemptNumberDesc(tenantId, executionId)).thenReturn(List.of());
        when(eventRepository.findByTenantIdAndExecutionIdOrderByEventTimestampAsc(tenantId, executionId)).thenReturn(List.of());

        var timeline = service.timeline(tenantId, executionId);

        assertThat(timeline.execution().tenantId()).isEqualTo(tenantId);
        assertThat(timeline.deliveryAttempts()).isEmpty();
        assertThat(timeline.deliveryEvents()).isEmpty();
    }

    @Test
    void timelineUsesBusinessLabelsAndOmitsLastAttemptSummary() {
        UUID executionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        CampaignExecutionEntity execution = CampaignExecutionEntity.create(tenantId, UUID.randomUUID(), null, ChannelType.EMAIL, null, now.minusHours(1), null, null, null, null);
        execution.markSucceeded("carepilot-email-smtp", "msg-1");
        execution.markDeliveryLifecycleStatus(MessageDeliveryStatus.SENT, null, now.minusMinutes(30));
        setField(execution, "createdAt", now.minusMinutes(30));
        setField(execution, "updatedAt", now.minusMinutes(30));
        setField(execution, "executedAt", now.minusMinutes(30));
        setField(execution, "lastAttemptAt", now.minusMinutes(30));

        when(executionRepository.findByTenantIdAndId(tenantId, executionId)).thenReturn(Optional.of(execution));
        when(attemptRepository.findByTenantIdAndExecutionIdOrderByAttemptNumberDesc(tenantId, executionId)).thenReturn(List.of(
                CampaignDeliveryAttemptEntity.create(
                        tenantId,
                        executionId,
                        1,
                        "carepilot-email-smtp",
                        ChannelType.EMAIL,
                        MessageDeliveryStatus.SENT,
                        null,
                        null,
                        now.minusMinutes(30)
                )
        ));
        when(eventRepository.findByTenantIdAndExecutionIdOrderByEventTimestampAsc(tenantId, executionId)).thenReturn(List.of());

        var timeline = service.timeline(tenantId, executionId);

        assertThat(timeline.statusEvents())
                .extracting(com.deepthoughtnet.clinic.carepilot.analytics.service.model.ExecutionTimelineEventRecord::reasonLabel)
                .containsExactly("Queued", "Dispatch Started/Acquired", "Email Sent", "Execution Succeeded");
        assertThat(timeline.statusEvents())
                .extracting(com.deepthoughtnet.clinic.carepilot.analytics.service.model.ExecutionTimelineEventRecord::reasonCode)
                .doesNotContain("LAST_ATTEMPT");
        assertThat(timeline.statusEvents().stream().map(com.deepthoughtnet.clinic.carepilot.analytics.service.model.ExecutionTimelineEventRecord::at).distinct()).hasSize(1);
    }

    @Test
    void timelineOrdersAcquisitionBeforeDeliveryAndCompletionWhenTimestampsTie() {
        UUID executionId = UUID.randomUUID();
        OffsetDateTime sameTime = OffsetDateTime.parse("2026-07-19T09:32:12Z");
        CampaignExecutionEntity execution = CampaignExecutionEntity.create(tenantId, UUID.randomUUID(), null, ChannelType.SMS, null, sameTime, null, null, null, null);
        setField(execution, "createdAt", sameTime);
        setField(execution, "updatedAt", sameTime);
        setField(execution, "executedAt", sameTime);
        setField(execution, "lastAttemptAt", sameTime);
        setField(execution, "deliveryStatus", MessageDeliveryStatus.SENT);
        setField(execution, "providerName", "carepilot sms");

        when(executionRepository.findByTenantIdAndId(tenantId, executionId)).thenReturn(Optional.of(execution));
        when(attemptRepository.findByTenantIdAndExecutionIdOrderByAttemptNumberDesc(tenantId, executionId)).thenReturn(List.of(
                CampaignDeliveryAttemptEntity.create(
                        tenantId,
                        executionId,
                        1,
                        "carepilot sms",
                        ChannelType.SMS,
                        MessageDeliveryStatus.SENT,
                        null,
                        null,
                        sameTime
                )
        ));
        when(eventRepository.findByTenantIdAndExecutionIdOrderByEventTimestampAsc(tenantId, executionId)).thenReturn(List.of(
                CampaignDeliveryEventEntity.create(
                        tenantId,
                        executionId,
                        null,
                        "carepilot sms",
                        "DELIVERED",
                        ChannelType.SMS,
                        "DELIVERED",
                        MessageDeliveryStatus.DELIVERED,
                        "provider-delivery",
                        sameTime,
                        null
                )
        ));

        var timeline = service.timeline(tenantId, executionId);

        assertThat(timeline.statusEvents())
                .extracting(com.deepthoughtnet.clinic.carepilot.analytics.service.model.ExecutionTimelineEventRecord::reasonLabel)
                .containsExactly("Queued", "Dispatch Started/Acquired", "SMS Sent", "Delivered", "Execution Succeeded");
        assertThat(timeline.statusEvents())
                .extracting(com.deepthoughtnet.clinic.carepilot.analytics.service.model.ExecutionTimelineEventRecord::status)
                .containsExactly("QUEUED", "PROCESSING", "SENT", "DELIVERED", "SUCCEEDED");
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
