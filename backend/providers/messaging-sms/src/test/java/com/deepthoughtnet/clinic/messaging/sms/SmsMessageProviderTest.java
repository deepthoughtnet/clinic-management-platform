package com.deepthoughtnet.clinic.messaging.sms;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageRecipient;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SmsMessageProviderTest {

    @Test
    void supportsSmsChannel() {
        SmsMessageProvider provider = new SmsMessageProvider(new CarePilotSmsMessagingProperties());

        assertThat(provider.supports(MessageChannel.SMS)).isTrue();
        assertThat(provider.supports(MessageChannel.EMAIL)).isFalse();
    }

    @Test
    void disabledProviderReturnsNotConfigured() {
        CarePilotSmsMessagingProperties properties = baseProperties();
        properties.setEnabled(false);
        SmsMessageProvider provider = new SmsMessageProvider(properties, okClient());

        var result = provider.send(request());

        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo(MessageDeliveryStatus.NOT_CONFIGURED);
    }

    @Test
    void missingApiUrlReturnsNotConfiguredForGenericHttp() {
        CarePilotSmsMessagingProperties properties = baseProperties();
        properties.setApiUrl(null);
        SmsMessageProvider provider = new SmsMessageProvider(properties, okClient());

        var result = provider.send(request());

        assertThat(result.status()).isEqualTo(MessageDeliveryStatus.NOT_CONFIGURED);
        assertThat(result.errorMessage()).contains("api-url");
    }

    @Test
    void missingApiKeyReturnsNotConfiguredForGenericHttp() {
        CarePilotSmsMessagingProperties properties = baseProperties();
        properties.setApiKey("");
        SmsMessageProvider provider = new SmsMessageProvider(properties, okClient());

        var result = provider.send(request());

        assertThat(result.status()).isEqualTo(MessageDeliveryStatus.NOT_CONFIGURED);
        assertThat(result.errorMessage()).contains("api-key");
    }

    @Test
    void http2xxReturnsSent() {
        CarePilotSmsMessagingProperties properties = baseProperties();
        SmsMessageProvider provider = new SmsMessageProvider(properties, (req, cfg) ->
                new GenericHttpSmsResponse(202, "{\"messageId\":\"abc-123\"}", "abc-123"));

        var result = provider.send(request());

        assertThat(result.success()).isTrue();
        assertThat(result.status()).isEqualTo(MessageDeliveryStatus.SENT);
        assertThat(result.providerMessageId()).isEqualTo("abc-123");
        assertThat(result.sentAt()).isNotNull();
    }

    @Test
    void httpNon2xxReturnsProviderError() {
        CarePilotSmsMessagingProperties properties = baseProperties();
        SmsMessageProvider provider = new SmsMessageProvider(properties, (req, cfg) ->
                new GenericHttpSmsResponse(500, "{\"error\":\"down\"}", "mid"));

        var result = provider.send(request());

        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo(MessageDeliveryStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo("PROVIDER_ERROR");
    }

    @Test
    void httpExceptionReturnsProviderError() {
        CarePilotSmsMessagingProperties properties = baseProperties();
        SmsMessageProvider provider = new SmsMessageProvider(properties, (req, cfg) -> {
            throw new RuntimeException("boom");
        });

        var result = provider.send(request());

        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo(MessageDeliveryStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo("PROVIDER_ERROR");
    }

    private CarePilotSmsMessagingProperties baseProperties() {
        CarePilotSmsMessagingProperties properties = new CarePilotSmsMessagingProperties();
        properties.setEnabled(true);
        properties.setProvider("generic-http");
        properties.setApiUrl("http://sms-provider/send");
        properties.setApiKey("secret");
        properties.setFromNumber("CLINIC");
        properties.setTimeoutMs(5000);
        return properties;
    }

    private MessageRequest request() {
        return new MessageRequest(
                UUID.randomUUID(),
                MessageChannel.SMS,
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

    private GenericHttpSmsClient okClient() {
        return (req, cfg) -> new GenericHttpSmsResponse(200, "{}", req.executionId().toString());
    }
}

