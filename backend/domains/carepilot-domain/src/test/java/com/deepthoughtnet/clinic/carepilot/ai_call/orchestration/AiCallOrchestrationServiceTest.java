package com.deepthoughtnet.clinic.carepilot.ai_call.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.ai_call.db.AiCallCampaignRepository;
import com.deepthoughtnet.clinic.carepilot.ai_call.db.AiCallEventRepository;
import com.deepthoughtnet.clinic.carepilot.ai_call.db.AiCallExecutionEntity;
import com.deepthoughtnet.clinic.carepilot.ai_call.db.AiCallExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.ai_call.db.AiCallTranscriptEntity;
import com.deepthoughtnet.clinic.carepilot.ai_call.db.AiCallTranscriptRepository;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallExecutionStatus;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.model.NotificationChannelPreference;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.TenantNotificationSettingsService;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.model.NotificationSettingsRecord;
import com.deepthoughtnet.clinic.carepilot.ai_call.provider.VoiceCallProviderRegistry;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallProvider;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallResult;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallTranscript;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiCallOrchestrationServiceTest {
    @Mock
    private AiCallCampaignRepository campaignRepository;
    @Mock
    private AiCallExecutionRepository executionRepository;
    @Mock
    private AiCallTranscriptRepository transcriptRepository;
    @Mock
    private AiCallEventRepository eventRepository;
    @Mock
    private VoiceCallProviderRegistry providerRegistry;
    @Mock
    private TenantNotificationSettingsService notificationSettingsService;
    @Mock
    private VoiceCallProvider provider;

    private AiCallOrchestrationService service;

    @BeforeEach
    void setUp() {
        service = new AiCallOrchestrationService(
                campaignRepository,
                executionRepository,
                transcriptRepository,
                eventRepository,
                providerRegistry,
                notificationSettingsService,
                25,
                3,
                300,
                3600,
                10,
                5,
                2
        );
    }

    @Test
    void dispatchPersistsTranscriptAndCompletion() {
        UUID tenantId = UUID.randomUUID();
        AiCallExecutionEntity execution = AiCallExecutionEntity.create(tenantId, UUID.randomUUID(), null, null, "+10000000000", OffsetDateTime.now().minusMinutes(1));
        execution.setExecutionStatus(AiCallExecutionStatus.QUEUED);

        when(executionRepository.findByTenantIdAndExecutionStatusInAndScheduledAtLessThanEqual(any(), any(), any())).thenReturn(List.of(execution));
        when(executionRepository.countByTenantIdAndStatuses(any(), any())).thenReturn(0L);
        when(eventRepository.countByTenantIdAndCreatedAtGreaterThanEqual(any(), any())).thenReturn(0L);
        when(notificationSettingsService.getOrCreate(any())).thenReturn(new NotificationSettingsRecord(
                UUID.randomUUID(),
                tenantId,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                null,
                null,
                null,
                NotificationChannelPreference.EMAIL,
                null,
                true,
                true,
                true,
                10,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null,
                null
        ));
        when(notificationSettingsService.applyQuietHours(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(providerRegistry.resolvePrimary()).thenReturn(provider);
        when(provider.placeCall(any())).thenReturn(new VoiceCallResult(
                VoiceCallStatus.COMPLETED,
                "mock-voice",
                "abc",
                null,
                OffsetDateTime.now().minusSeconds(40),
                OffsetDateTime.now(),
                new VoiceCallTranscript("text", "summary", "NEUTRAL", "OK", false)
        ));
        when(executionRepository.save(any(AiCallExecutionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transcriptRepository.save(any(AiCallTranscriptEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiCallOrchestrationService.DispatchBatchResult processed = service.dispatchDueExecutions(tenantId);
        assertThat(processed.processed()).isEqualTo(1);
        assertThat(processed.dispatched()).isEqualTo(1);
        assertThat(processed.failed()).isZero();
        assertThat(processed.skipped()).isZero();
        assertThat(execution.getExecutionStatus()).isEqualTo(AiCallExecutionStatus.COMPLETED);
        assertThat(execution.getTranscriptId()).isNotNull();
    }

    @Test
    void dispatchFailsGracefullyWhenPrimaryProviderMissing() {
        UUID tenantId = UUID.randomUUID();
        AiCallExecutionEntity execution = AiCallExecutionEntity.create(tenantId, UUID.randomUUID(), null, null, "+10000000000", OffsetDateTime.now().minusMinutes(1));
        execution.setExecutionStatus(AiCallExecutionStatus.QUEUED);
        mockSchedulerDependencies(tenantId, execution);
        when(providerRegistry.resolvePrimary()).thenReturn(null);
        when(executionRepository.save(any(AiCallExecutionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiCallOrchestrationService.DispatchBatchResult processed = service.dispatchDueExecutions(tenantId);
        assertThat(processed.processed()).isEqualTo(1);
        assertThat(processed.dispatched()).isZero();
        assertThat(processed.failed()).isEqualTo(1);
        assertThat(execution.getExecutionStatus()).isEqualTo(AiCallExecutionStatus.FAILED);
        assertThat(execution.getFailureReason()).isEqualTo("NO_PROVIDER_CONFIGURED");
        verify(provider, never()).placeCall(any());
    }

    @Test
    void dispatchUsesFallbackWhenPrimaryFailsWithRetryableError() {
        UUID tenantId = UUID.randomUUID();
        AiCallExecutionEntity execution = AiCallExecutionEntity.create(tenantId, UUID.randomUUID(), null, null, "+10000000000", OffsetDateTime.now().minusMinutes(1));
        execution.setExecutionStatus(AiCallExecutionStatus.QUEUED);
        mockSchedulerDependencies(tenantId, execution);
        VoiceCallProvider fallback = org.mockito.Mockito.mock(VoiceCallProvider.class);
        when(providerRegistry.resolvePrimary()).thenReturn(provider);
        when(providerRegistry.failoverEnabled()).thenReturn(true);
        when(providerRegistry.resolveFallback()).thenReturn(fallback);
        when(provider.providerName()).thenReturn("primary");
        when(fallback.providerName()).thenReturn("fallback");
        when(provider.placeCall(any())).thenReturn(new VoiceCallResult(
                VoiceCallStatus.FAILED,
                "primary",
                "p-1",
                "TEMPORARY_PROVIDER_ERROR",
                OffsetDateTime.now().minusSeconds(30),
                OffsetDateTime.now().minusSeconds(20),
                null
        ));
        when(fallback.placeCall(any())).thenReturn(new VoiceCallResult(
                VoiceCallStatus.COMPLETED,
                "fallback",
                "f-1",
                null,
                OffsetDateTime.now().minusSeconds(10),
                OffsetDateTime.now(),
                null
        ));
        when(executionRepository.save(any(AiCallExecutionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiCallOrchestrationService.DispatchBatchResult processed = service.dispatchDueExecutions(tenantId);
        assertThat(processed.dispatched()).isEqualTo(1);
        assertThat(execution.getExecutionStatus()).isEqualTo(AiCallExecutionStatus.COMPLETED);
        assertThat(execution.isFailoverAttempted()).isTrue();
        assertThat(execution.getProviderName()).isEqualTo("fallback");
    }

    @Test
    void dispatchFailsSafelyWhenNoProviderAvailable() {
        UUID tenantId = UUID.randomUUID();
        AiCallExecutionEntity execution = AiCallExecutionEntity.create(tenantId, UUID.randomUUID(), null, null, "+10000000000", OffsetDateTime.now().minusMinutes(1));
        execution.setExecutionStatus(AiCallExecutionStatus.QUEUED);
        mockSchedulerDependencies(tenantId, execution);
        when(providerRegistry.resolvePrimary()).thenReturn(null);
        when(executionRepository.save(any(AiCallExecutionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiCallOrchestrationService.DispatchBatchResult processed = service.dispatchDueExecutions(tenantId);
        assertThat(processed.failed()).isEqualTo(1);
        assertThat(execution.getExecutionStatus()).isEqualTo(AiCallExecutionStatus.FAILED);
        assertThat(execution.getFailureReason()).isEqualTo("NO_PROVIDER_CONFIGURED");
    }

    private void mockSchedulerDependencies(UUID tenantId, AiCallExecutionEntity execution) {
        when(executionRepository.findByTenantIdAndExecutionStatusInAndScheduledAtLessThanEqual(any(), any(), any()))
                .thenReturn(List.of(execution));
        when(executionRepository.countByTenantIdAndStatuses(any(), any())).thenReturn(0L);
        when(eventRepository.countByTenantIdAndCreatedAtGreaterThanEqual(any(), any())).thenReturn(0L);
        when(notificationSettingsService.getOrCreate(any())).thenReturn(new NotificationSettingsRecord(
                UUID.randomUUID(),
                tenantId,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                null,
                null,
                null,
                NotificationChannelPreference.EMAIL,
                null,
                true,
                true,
                true,
                10,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null,
                null
        ));
        when(notificationSettingsService.applyQuietHours(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
    }
}
