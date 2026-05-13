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
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryAttemptRepository;
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
        UUID c1 = UUID.randomUUID();
        CampaignEntity campaign = CampaignEntity.create(tenantId, "Reminder", CampaignType.CUSTOM, TriggerType.MANUAL, AudienceType.ALL_PATIENTS, null, null, UUID.randomUUID());
        campaign.activate();
        when(campaignRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(campaign));

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
}
