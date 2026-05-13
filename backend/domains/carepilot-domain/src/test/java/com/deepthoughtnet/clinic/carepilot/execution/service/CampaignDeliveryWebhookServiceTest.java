package com.deepthoughtnet.clinic.carepilot.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryAttemptEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryAttemptRepository;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryEventRepository;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CampaignDeliveryWebhookServiceTest {

    private CampaignExecutionRepository executionRepository;
    private CampaignDeliveryAttemptRepository attemptRepository;
    private CampaignDeliveryEventRepository eventRepository;
    private CampaignDeliveryWebhookService service;

    @BeforeEach
    void setUp() {
        executionRepository = mock(CampaignExecutionRepository.class);
        attemptRepository = mock(CampaignDeliveryAttemptRepository.class);
        eventRepository = mock(CampaignDeliveryEventRepository.class);
        service = new CampaignDeliveryWebhookService(executionRepository, attemptRepository, eventRepository);
    }

    @Test
    void updatesMatchingExecutionAndPersistsEvent() {
        UUID tenantId = UUID.randomUUID();
        CampaignExecutionEntity execution = CampaignExecutionEntity.create(
                tenantId, UUID.randomUUID(), null, ChannelType.WHATSAPP, UUID.randomUUID(), OffsetDateTime.now(), null, null, null, null
        );
        execution.markSucceeded("carepilot-whatsapp-meta-cloud-api", "wamid.1");
        CampaignDeliveryAttemptEntity attempt = CampaignDeliveryAttemptEntity.create(
                tenantId, execution.getId(), 1, "carepilot-whatsapp-meta-cloud-api", ChannelType.WHATSAPP, MessageDeliveryStatus.SENT, null, null, OffsetDateTime.now()
        );

        when(eventRepository.existsByProviderNameAndProviderMessageIdAndInternalStatusAndEventTypeAndEventTimestamp(any(), any(), any(), any(), any())).thenReturn(false);
        when(executionRepository.findByProviderNameAndProviderMessageIdOrderByUpdatedAtDesc("carepilot-whatsapp-meta-cloud-api", "wamid.1"))
                .thenReturn(List.of(execution));
        when(attemptRepository.findFirstByTenantIdAndExecutionIdOrderByAttemptNumberDesc(tenantId, execution.getId()))
                .thenReturn(Optional.of(attempt));

        var result = service.applyProviderDeliveryEvent(new CampaignDeliveryWebhookService.ProviderDeliveryEventCommand(
                "carepilot-whatsapp-meta-cloud-api",
                "wamid.1",
                ChannelType.WHATSAPP,
                "delivered",
                MessageDeliveryStatus.DELIVERED,
                "WHATSAPP_STATUS",
                OffsetDateTime.now(),
                "{\"status\":\"delivered\"}"
        ));

        assertThat(result.updatedExecutions()).isEqualTo(1);
        assertThat(result.persistedEvents()).isEqualTo(1);
        verify(executionRepository).save(any(CampaignExecutionEntity.class));
        verify(eventRepository).save(any());
    }

    @Test
    void unmatchedMessageStillPersistsEventWithoutCrash() {
        when(eventRepository.existsByProviderNameAndProviderMessageIdAndInternalStatusAndEventTypeAndEventTimestamp(any(), any(), any(), any(), any())).thenReturn(false);
        when(executionRepository.findByProviderNameAndProviderMessageIdOrderByUpdatedAtDesc("carepilot-sms-generic-http", "sms-1"))
                .thenReturn(List.of());

        var result = service.applyProviderDeliveryEvent(new CampaignDeliveryWebhookService.ProviderDeliveryEventCommand(
                "carepilot-sms-generic-http",
                "sms-1",
                ChannelType.SMS,
                "delivered",
                MessageDeliveryStatus.DELIVERED,
                "SMS_STATUS",
                OffsetDateTime.now(),
                "{\"status\":\"delivered\"}"
        ));

        assertThat(result.updatedExecutions()).isZero();
        assertThat(result.persistedEvents()).isEqualTo(1);
        verify(eventRepository).save(any());
    }
}
