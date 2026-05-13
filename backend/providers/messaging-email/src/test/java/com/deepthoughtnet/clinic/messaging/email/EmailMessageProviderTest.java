package com.deepthoughtnet.clinic.messaging.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageRecipient;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class EmailMessageProviderTest {

    @Test
    void disabledProviderReturnsNotConfigured() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        CarePilotEmailMessagingProperties properties = new CarePilotEmailMessagingProperties();
        properties.setEnabled(false);
        EmailMessageProvider provider = new EmailMessageProvider(properties, mailSender, "smtp", true, "smtp.example.com");

        var result = provider.send(request("p@example.com", "Subject", "Body"));

        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo(MessageDeliveryStatus.NOT_CONFIGURED);
    }

    @Test
    void missingFromAddressReturnsNotConfigured() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        CarePilotEmailMessagingProperties properties = new CarePilotEmailMessagingProperties();
        properties.setEnabled(true);
        properties.setFromAddress(" ");
        EmailMessageProvider provider = new EmailMessageProvider(properties, mailSender, "smtp", true, "smtp.example.com");

        var result = provider.send(request("p@example.com", "Subject", "Body"));

        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo(MessageDeliveryStatus.NOT_CONFIGURED);
        assertThat(result.errorMessage()).contains("from-address");
    }

    @Test
    void missingSmtpHostReturnsNotConfigured() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        CarePilotEmailMessagingProperties properties = new CarePilotEmailMessagingProperties();
        properties.setEnabled(true);
        properties.setFromAddress("carepilot@example.com");
        EmailMessageProvider provider = new EmailMessageProvider(properties, mailSender, "smtp", true, "");

        var result = provider.send(request("p@example.com", "Subject", "Body"));

        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo(MessageDeliveryStatus.NOT_CONFIGURED);
    }

    @Test
    void providerErrorReturnsFailedResult() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage((jakarta.mail.Session) null);
        org.mockito.Mockito.when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("down")).when(mailSender).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
        CarePilotEmailMessagingProperties properties = new CarePilotEmailMessagingProperties();
        properties.setEnabled(true);
        properties.setFromAddress("carepilot@example.com");
        EmailMessageProvider provider = new EmailMessageProvider(properties, mailSender, "smtp", true, "smtp.example.com");

        var result = provider.send(request("p@example.com", "Subject", "Body"));

        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo(MessageDeliveryStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo("PROVIDER_ERROR");
    }

    @Test
    void successfulSendReturnsSentResult() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage((jakarta.mail.Session) null);
        org.mockito.Mockito.when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        CarePilotEmailMessagingProperties properties = new CarePilotEmailMessagingProperties();
        properties.setEnabled(true);
        properties.setFromAddress("carepilot@example.com");
        EmailMessageProvider provider = new EmailMessageProvider(properties, mailSender, "smtp", true, "smtp.example.com");

        var result = provider.send(request("p@example.com", "Subject", "Body"));

        verify(mailSender).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
        assertThat(result.success()).isTrue();
        assertThat(result.status()).isEqualTo(MessageDeliveryStatus.SENT);
        assertThat(result.providerMessageId()).isNotBlank();
    }

    private MessageRequest request(String recipient, String subject, String body) {
        return new MessageRequest(
                UUID.randomUUID(), MessageChannel.EMAIL, new MessageRecipient(recipient, null),
                subject, body, null, null, null, null, Map.of()
        );
    }
}
