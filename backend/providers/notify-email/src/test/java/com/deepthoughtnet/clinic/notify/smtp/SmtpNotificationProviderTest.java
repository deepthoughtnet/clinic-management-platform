package com.deepthoughtnet.clinic.notify.smtp;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.notify.NotificationAttachment;
import com.deepthoughtnet.clinic.notify.NotificationDeliveryException;
import com.deepthoughtnet.clinic.notify.NotificationMessage;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpNotificationProviderTest {

    @Test
    void sendsEmailWithAttachment() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        MailNotificationProperties properties = properties();
        SmtpNotificationProvider provider = new SmtpNotificationProvider(sender, properties);

        provider.send(new NotificationMessage(
                UUID.randomUUID(),
                "EMAIL",
                "customer@example.com",
                "Clinic INV-1",
                "Please find attached.",
                "{}",
                "accounts@example.com",
                List.of(new NotificationAttachment("INV-1.pdf", "application/pdf", new byte[]{1, 2, 3}))
        ));

        verify(sender).send(any(MimeMessage.class));
    }

    @Test
    void wrapsProviderFailure() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        org.mockito.Mockito.doThrow(new MailSendException("down")).when(sender).send(any(MimeMessage.class));
        SmtpNotificationProvider provider = new SmtpNotificationProvider(sender, properties());

        assertThrows(NotificationDeliveryException.class, () -> provider.send(new NotificationMessage(
                UUID.randomUUID(),
                "EMAIL",
                "customer@example.com",
                "Clinic INV-1",
                "Body",
                "{}"
        )));
    }

    private MailNotificationProperties properties() {
        MailNotificationProperties properties = new MailNotificationProperties();
        properties.setFromEmail("billing@example.com");
        properties.setFromName("Clinic");
        return properties;
    }
}
