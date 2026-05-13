package com.deepthoughtnet.clinic.api.carepilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.deepthoughtnet.clinic.carepilot.execution.service.CampaignDeliveryWebhookService;
import com.deepthoughtnet.clinic.carepilot.execution.service.CampaignDeliveryWebhookService.DeliveryWebhookUpdateResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CarePilotDeliveryWebhookControllerTest {

    @Test
    void whatsappVerifyChallengeSucceedsWithExpectedToken() {
        CarePilotDeliveryWebhookController controller = new CarePilotDeliveryWebhookController(
                mock(CampaignDeliveryWebhookService.class),
                new ObjectMapper(),
                "verify-token",
                "",
                ""
        );

        var response = controller.verifyWhatsAppWebhook("subscribe", "verify-token", "abc123");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo("abc123");
    }

    @Test
    void whatsappVerifyChallengeFailsWithWrongToken() {
        CarePilotDeliveryWebhookController controller = new CarePilotDeliveryWebhookController(
                mock(CampaignDeliveryWebhookService.class),
                new ObjectMapper(),
                "verify-token",
                "",
                ""
        );

        var response = controller.verifyWhatsAppWebhook("subscribe", "wrong", "abc123");

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void smsWebhookSecretMismatchRejected() {
        CarePilotDeliveryWebhookController controller = new CarePilotDeliveryWebhookController(
                mock(CampaignDeliveryWebhookService.class),
                new ObjectMapper(),
                "",
                "",
                "expected-secret"
        );

        var response = controller.ingestSmsWebhook("{\"providerMessageId\":\"m1\",\"status\":\"delivered\"}", "bad-secret");

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void whatsappStatusWebhookCallsService() {
        CampaignDeliveryWebhookService service = mock(CampaignDeliveryWebhookService.class);
        org.mockito.Mockito.when(service.applyProviderDeliveryEvent(any())).thenReturn(new DeliveryWebhookUpdateResult(1, 1, false));
        CarePilotDeliveryWebhookController controller = new CarePilotDeliveryWebhookController(
                service,
                new ObjectMapper(),
                "",
                "",
                ""
        );

        String payload = "{\"entry\":[{\"changes\":[{\"value\":{\"statuses\":[{\"id\":\"wamid.1\",\"status\":\"read\",\"timestamp\":\"1710000000\"}]}}]}]}";
        var response = controller.ingestWhatsAppWebhook(payload, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(service).applyProviderDeliveryEvent(any());
    }
}
