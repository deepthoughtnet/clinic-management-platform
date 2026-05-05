package com.deepthoughtnet.clinic.notify.smtp;

import com.deepthoughtnet.clinic.notify.NotificationAttachment;
import com.deepthoughtnet.clinic.notify.NotificationDeliveryException;
import com.deepthoughtnet.clinic.notify.NotificationMessage;
import com.deepthoughtnet.clinic.notify.NotificationProvider;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.StringUtils;

public class SmtpNotificationProvider implements NotificationProvider {
    private final JavaMailSender mailSender;
    private final MailNotificationProperties properties;

    public SmtpNotificationProvider(JavaMailSender mailSender, MailNotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(NotificationMessage message) {
        if (!"EMAIL".equalsIgnoreCase(message.channel())) {
            throw new NotificationDeliveryException("SMTP provider only supports EMAIL notifications", null);
        }
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    !message.attachments().isEmpty(),
                    "UTF-8"
            );
            helper.setFrom(fromAddress());
            helper.setTo(message.recipient());
            if (StringUtils.hasText(message.cc())) {
                helper.setCc(splitAddresses(message.cc()));
            }
            helper.setSubject(nullTo(message.subject(), ""));
            helper.setText(nullTo(message.body(), ""), false);
            for (NotificationAttachment attachment : message.attachments()) {
                helper.addAttachment(
                        attachment.filename(),
                        new ByteArrayResource(attachment.content()),
                        attachment.contentType()
                );
            }
            mailSender.send(mimeMessage);
        } catch (MessagingException | RuntimeException | UnsupportedEncodingException ex) {
            throw new NotificationDeliveryException("Email delivery failed", ex);
        }
    }

    private InternetAddress fromAddress() throws UnsupportedEncodingException {
        return new InternetAddress(properties.getFromEmail(), properties.getFromName());
    }

    private String[] splitAddresses(String value) {
        return value.split("\\s*[,;]\\s*");
    }

    private String nullTo(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
