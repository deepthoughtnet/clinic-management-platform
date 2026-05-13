package com.deepthoughtnet.clinic.api.carepilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderReadinessStatus;
import com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderStatusResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderTestSendRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CarePilotMessagingControllerTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void statusReturnsProviderRows() {
        CarePilotMessagingStatusService statusService = mock(CarePilotMessagingStatusService.class);
        when(statusService.providerStatuses()).thenReturn(List.of(
                new ProviderStatusResponse(
                        MessageChannel.EMAIL,
                        "carepilot-email",
                        true,
                        true,
                        true,
                        ProviderReadinessStatus.READY,
                        List.of(),
                        "ok",
                        true,
                        OffsetDateTime.now(),
                        true,
                        true,
                        false,
                        true
                )
        ));
        CarePilotMessagingController controller = new CarePilotMessagingController(statusService, mock(com.deepthoughtnet.clinic.carepilot.messaging.service.MessageOrchestratorService.class));
        RequestContextHolder.set(new RequestContext(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));

        var response = controller.status();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).channel()).isEqualTo(MessageChannel.EMAIL);
    }

    @Test
    void testSendReturnsNotConfiguredResultWithoutCrash() {
        CarePilotMessagingStatusService statusService = mock(CarePilotMessagingStatusService.class);
        var orchestrator = mock(com.deepthoughtnet.clinic.carepilot.messaging.service.MessageOrchestratorService.class);
        when(orchestrator.send(any())).thenReturn(MessageResult.notConfigured("SMS_NOT_CONFIGURED", "sms disabled"));
        CarePilotMessagingController controller = new CarePilotMessagingController(statusService, orchestrator);
        RequestContextHolder.set(new RequestContext(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));

        var response = controller.testSend(
                MessageChannel.SMS,
                new ProviderTestSendRequest("+15550100", null, "hello")
        );

        assertThat(response.status()).isEqualTo(MessageDeliveryStatus.NOT_CONFIGURED);
        assertThat(response.success()).isFalse();
    }

    @Test
    void testSendRejectsUnsupportedChannel() {
        CarePilotMessagingController controller = new CarePilotMessagingController(
                mock(CarePilotMessagingStatusService.class),
                mock(com.deepthoughtnet.clinic.carepilot.messaging.service.MessageOrchestratorService.class)
        );

        assertThatThrownBy(() -> controller.testSend(
                MessageChannel.IN_APP,
                new ProviderTestSendRequest("abc", null, "hello")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EMAIL, SMS, or WHATSAPP");
    }

    @Test
    void testSendRejectsSmsWithInvalidPhoneRecipient() {
        CarePilotMessagingController controller = new CarePilotMessagingController(
                mock(CarePilotMessagingStatusService.class),
                mock(com.deepthoughtnet.clinic.carepilot.messaging.service.MessageOrchestratorService.class)
        );

        assertThatThrownBy(() -> controller.testSend(
                MessageChannel.SMS,
                new ProviderTestSendRequest("not-a-phone", null, "hello")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid phone number");
    }

    @Test
    void testSendRejectsEmailWithoutSubject() {
        CarePilotMessagingController controller = new CarePilotMessagingController(
                mock(CarePilotMessagingStatusService.class),
                mock(com.deepthoughtnet.clinic.carepilot.messaging.service.MessageOrchestratorService.class)
        );

        assertThatThrownBy(() -> controller.testSend(
                MessageChannel.EMAIL,
                new ProviderTestSendRequest("carepilot@example.com", null, "hello")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Subject is required");
    }
}
