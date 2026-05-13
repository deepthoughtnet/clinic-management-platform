package com.deepthoughtnet.clinic.messaging.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageRecipient;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.whatsapp.meta.MetaWhatsAppHttpClient;
import com.deepthoughtnet.clinic.messaging.whatsapp.meta.MetaWhatsAppHttpResponse;
import com.deepthoughtnet.clinic.messaging.whatsapp.meta.MetaWhatsAppMessageProvider;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WhatsAppMessageProviderTest {

    @Test
    void supportsWhatsAppChannel() {
        MetaWhatsAppMessageProvider provider = new MetaWhatsAppMessageProvider(baseProperties(), okClient());

        assertThat(provider.supports(MessageChannel.WHATSAPP)).isTrue();
        assertThat(provider.supports(MessageChannel.EMAIL)).isFalse();
    }

    @Test
    void disabledProviderReturnsNotConfigured() {
        CarePilotWhatsAppMessagingProperties properties = baseProperties();
        properties.setEnabled(false);
        MetaWhatsAppMessageProvider provider = new MetaWhatsAppMessageProvider(properties, okClient());

        var result = provider.send(request());

        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo(MessageDeliveryStatus.NOT_CONFIGURED);
        assertThat(result.providerName()).isEqualTo("carepilot-whatsapp-meta-cloud-api");
    }

    @Test
    void missingConfigReturnsNotConfigured() {
        CarePilotWhatsAppMessagingProperties properties = baseProperties();
        properties.setApiUrl("");
        MetaWhatsAppMessageProvider provider = new MetaWhatsAppMessageProvider(properties, okClient());

        var result = provider.send(request());

        assertThat(result.status()).isEqualTo(MessageDeliveryStatus.NOT_CONFIGURED);
        assertThat(result.errorMessage()).contains("api-url");
    }

    @Test
    void http2xxReturnsSuccess() {
        MetaWhatsAppMessageProvider provider = new MetaWhatsAppMessageProvider(baseProperties(), (req, props) ->
                new MetaWhatsAppHttpResponse(200, "{\"messages\":[{\"id\":\"wamid.123\"}]}", "wamid.123"));

        var result = provider.send(request());

        assertThat(result.success()).isTrue();
        assertThat(result.status()).isEqualTo(MessageDeliveryStatus.SENT);
        assertThat(result.providerMessageId()).isEqualTo("wamid.123");
    }

    @Test
    void non2xxReturnsProviderError() {
        MetaWhatsAppMessageProvider provider = new MetaWhatsAppMessageProvider(baseProperties(), (req, props) ->
                new MetaWhatsAppHttpResponse(401, "{\"error\":\"invalid\"}", "fallback"));

        var result = provider.send(request());

        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo(MessageDeliveryStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo("PROVIDER_ERROR");
    }

    @Test
    void httpExceptionHandledSafely() {
        MetaWhatsAppMessageProvider provider = new MetaWhatsAppMessageProvider(baseProperties(), (req, props) -> {
            throw new RuntimeException("down");
        });

        var result = provider.send(request());

        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo(MessageDeliveryStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo("PROVIDER_ERROR");
    }

    private CarePilotWhatsAppMessagingProperties baseProperties() {
        CarePilotWhatsAppMessagingProperties properties = new CarePilotWhatsAppMessagingProperties();
        properties.setEnabled(true);
        properties.setProvider("meta-cloud-api");
        properties.setApiUrl("https://graph.facebook.com/v18.0/123/messages");
        properties.setAccessToken("secret");
        properties.setPhoneNumberId("123");
        properties.setBusinessAccountId("biz-1");
        properties.setTimeoutMs(5000);
        return properties;
    }

    private MessageRequest request() {
        return new MessageRequest(
                UUID.randomUUID(),
                MessageChannel.WHATSAPP,
                new MessageRecipient("+15550100", null),
                null,
                "Body",
                null,
                null,
                null,
                UUID.randomUUID(),
                Map.of()
        );
    }

    private MetaWhatsAppHttpClient okClient() {
        return (req, props) -> new MetaWhatsAppHttpResponse(200, "{}", req.executionId().toString());
    }
}

