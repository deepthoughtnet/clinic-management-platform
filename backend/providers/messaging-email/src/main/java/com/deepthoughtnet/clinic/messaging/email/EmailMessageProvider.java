package com.deepthoughtnet.clinic.messaging.email;

import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.StringUtils;

/**
 * CarePilot email provider adapter that dispatches through SMTP-capable JavaMailSender.
 */
public class EmailMessageProvider implements MessageProvider {
    private final CarePilotEmailMessagingProperties properties;
    private final JavaMailSender mailSender;
    private final String mailProvider;
    private final boolean mailEnabled;
    private final String smtpHost;

    public EmailMessageProvider(
            CarePilotEmailMessagingProperties properties,
            JavaMailSender mailSender,
            String mailProvider,
            boolean mailEnabled,
            String smtpHost
    ) {
        this.properties = properties;
        this.mailSender = mailSender;
        this.mailProvider = mailProvider;
        this.mailEnabled = mailEnabled;
        this.smtpHost = smtpHost;
    }

    @Override
    public boolean supports(MessageChannel channel) {
        return channel == MessageChannel.EMAIL;
    }

    @Override
    public MessageResult send(MessageRequest request) {
        if (!properties.isEnabled()) {
            return MessageResult.notConfigured(providerName(), "carepilot.messaging.email.enabled=false");
        }
        if (!StringUtils.hasText(properties.getFromAddress())) {
            return MessageResult.notConfigured(providerName(), "carepilot.messaging.email.from-address is required");
        }
        if (!isSmtpConfigured()) {
            return MessageResult.notConfigured(providerName(), "SMTP host/provider is not configured");
        }
        if (mailSender == null) {
            return MessageResult.providerUnavailable(providerName(), "JavaMailSender bean is not available");
        }
        if (request.recipient() == null || !StringUtils.hasText(request.recipient().address())) {
            return new MessageResult(
                    false,
                    MessageDeliveryStatus.FAILED,
                    providerName(),
                    null,
                    "RECIPIENT_MISSING",
                    "Recipient email is required for CarePilot email delivery",
                    null
            );
        }
        if (!isValidEmail(request.recipient().address())) {
            return new MessageResult(
                    false,
                    MessageDeliveryStatus.FAILED,
                    providerName(),
                    null,
                    "RECIPIENT_INVALID",
                    "Recipient email address is invalid",
                    null
            );
        }
        if (!StringUtils.hasText(request.subject())) {
            return new MessageResult(
                    false,
                    MessageDeliveryStatus.FAILED,
                    providerName(),
                    null,
                    "SUBJECT_MISSING",
                    "Email subject is required",
                    null
            );
        }
        if (!StringUtils.hasText(request.body())) {
            return new MessageResult(
                    false,
                    MessageDeliveryStatus.FAILED,
                    providerName(),
                    null,
                    "BODY_MISSING",
                    "Email body is required",
                    null
            );
        }

        String providerMessageId = UUID.randomUUID().toString();
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(properties.getFromAddress().trim());
            helper.setTo(request.recipient().address().trim());
            helper.setSubject(Objects.requireNonNull(request.subject()).trim());
            helper.setText(Objects.requireNonNull(request.body()), false);
            mimeMessage.setHeader("X-CarePilot-Execution-Id", request.executionId() == null ? providerMessageId : request.executionId().toString());
            mailSender.send(mimeMessage);

            return new MessageResult(
                    true,
                    MessageDeliveryStatus.SENT,
                    providerName(),
                    providerMessageId,
                    null,
                    null,
                    OffsetDateTime.now()
            );
        } catch (MessagingException | RuntimeException ex) {
            return new MessageResult(
                    false,
                    MessageDeliveryStatus.FAILED,
                    providerName(),
                    providerMessageId,
                    "PROVIDER_ERROR",
                    "Email provider failed to send message",
                    null
            );
        }
    }

    @Override
    public String providerName() {
        return "carepilot-email-smtp";
    }

    private boolean isSmtpConfigured() {
        return mailEnabled
                && "smtp".equalsIgnoreCase(mailProvider)
                && StringUtils.hasText(smtpHost);
    }

    private boolean isValidEmail(String value) {
        try {
            InternetAddress address = new InternetAddress(value);
            address.validate();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
