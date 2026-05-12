package com.deepthoughtnet.clinic.messaging.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageRecipient;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.notify.NotificationDeliveryException;
import com.deepthoughtnet.clinic.notify.NotificationProvider;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmailMessageProviderTest {

    @Test
    void disabledProviderReturnsNotConfigured() {
        NotificationProvider notificationProvider = mock(NotificationProvider.class);
        CarePilotEmailMessagingProperties properties = new CarePilotEmailMessagingProperties();
        properties.setEnabled(false);
        EmailMessageProvider provider = new EmailMessageProvider(notificationProvider, properties, "smtp", true);

        var result = provider.send(new MessageRequest(
                UUID.randomUUID(), MessageChannel.EMAIL, new MessageRecipient("p@example.com", null),
                "Subject", "Body", null, null, null, null, Map.of()
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo(MessageDeliveryStatus.NOT_CONFIGURED);
    }

    @Test
    void smtpNotConfiguredReturnsNotConfigured() {
        NotificationProvider notificationProvider = mock(NotificationProvider.class);
        CarePilotEmailMessagingProperties properties = new CarePilotEmailMessagingProperties();
        properties.setEnabled(true);
        EmailMessageProvider provider = new EmailMessageProvider(notificationProvider, properties, "logging", false);

        var result = provider.send(new MessageRequest(
                UUID.randomUUID(), MessageChannel.EMAIL, new MessageRecipient("p@example.com", null),
                "Subject", "Body", null, null, null, null, Map.of()
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo(MessageDeliveryStatus.NOT_CONFIGURED);
    }

    @Test
    void deliveryFailureReturnsFailedResult() {
        NotificationProvider notificationProvider = mock(NotificationProvider.class);
        doThrow(new NotificationDeliveryException("down", null)).when(notificationProvider).send(org.mockito.ArgumentMatchers.any());
        CarePilotEmailMessagingProperties properties = new CarePilotEmailMessagingProperties();
        properties.setEnabled(true);
        EmailMessageProvider provider = new EmailMessageProvider(notificationProvider, properties, "smtp", true);

        var result = provider.send(new MessageRequest(
                UUID.randomUUID(), MessageChannel.EMAIL, new MessageRecipient("p@example.com", null),
                "Subject", "Body", null, null, null, null, Map.of()
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo(MessageDeliveryStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo("EMAIL_DELIVERY_FAILED");
    }

    @Test
    void successfulSendReturnsSentResult() {
        NotificationProvider notificationProvider = mock(NotificationProvider.class);
        CarePilotEmailMessagingProperties properties = new CarePilotEmailMessagingProperties();
        properties.setEnabled(true);
        properties.setFromAddress("carepilot@example.com");
        EmailMessageProvider provider = new EmailMessageProvider(notificationProvider, properties, "smtp", true);

        var result = provider.send(new MessageRequest(
                UUID.randomUUID(), MessageChannel.EMAIL, new MessageRecipient("p@example.com", null),
                "Subject", "Body", null, null, null, null, Map.of("k", "v")
        ));

        verify(notificationProvider).send(org.mockito.ArgumentMatchers.any());
        assertThat(result.success()).isTrue();
        assertThat(result.status()).isEqualTo(MessageDeliveryStatus.SENT);
        assertThat(result.providerMessageId()).isNotBlank();
    }
}
