package com.deepthoughtnet.clinic.messaging.email;

import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.StringUtils;

/** Opt-in MSG91 SMTP adapter used by Platform Communication Test and Engage.
 *
 * Engage selects this provider only when the explicit email-provider setting is
 * {@code msg91-email-smtp}. The selector is a registry provider ID, not the
 * generic transport value {@code smtp}.
 */
public class Msg91EmailMessageProvider implements com.deepthoughtnet.clinic.messaging.spi.MessageProvider {
    private final Msg91EmailMessagingProperties properties;
    private final JavaMailSender mailSender;

    public Msg91EmailMessageProvider(Msg91EmailMessagingProperties properties, JavaMailSender mailSender) {
        this.properties = properties;
        this.mailSender = mailSender;
    }

    @Override
    public boolean supports(MessageChannel channel) { return channel == MessageChannel.EMAIL; }

    @Override
    public String providerName() { return "msg91-email-smtp"; }

    public boolean isConfigured() {
        return properties.isEnabled()
                && StringUtils.hasText(properties.getHost())
                && properties.getPort() > 0 && properties.getPort() <= 65535
                && StringUtils.hasText(properties.getUsername())
                && StringUtils.hasText(properties.getPassword())
                && validEmail(properties.getFrom())
                && mailSender != null;
    }

    public String host() { return properties.getHost(); }
    public int port() { return properties.getPort(); }
    public String username() { return properties.getUsername(); }
    public boolean passwordConfigured() { return StringUtils.hasText(properties.getPassword()); }
    public boolean fromConfigured() { return validEmail(properties.getFrom()); }

    @Override
    public MessageResult send(MessageRequest request) {
        if (!isConfigured()) return MessageResult.notConfigured(providerName(), "MSG91 SMTP configuration is incomplete");
        if (request.recipient() == null || !validEmail(request.recipient().address())) return failed("RECIPIENT_INVALID", "Recipient email address is invalid");
        if (!StringUtils.hasText(request.subject())) return failed("SUBJECT_MISSING", "Email subject is required");
        if (!StringUtils.hasText(request.body())) return failed("BODY_MISSING", "Email body is required");
        String messageId = UUID.randomUUID().toString();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(properties.getFrom().trim());
            helper.setTo(request.recipient().address().trim());
            helper.setSubject(request.subject().trim());
            helper.setText(request.body(), false);
            message.setHeader("X-Jeevanam-Communication-Test", request.executionId() == null ? messageId : request.executionId().toString());
            mailSender.send(message);
            return new MessageResult(true, MessageDeliveryStatus.SENT, providerName(), messageId, null, null, OffsetDateTime.now());
        } catch (MessagingException | RuntimeException ex) {
            return new MessageResult(false, MessageDeliveryStatus.FAILED, providerName(), messageId, "PROVIDER_ERROR", "MSG91 SMTP provider failed to send message", null);
        }
    }

    private MessageResult failed(String code, String message) {
        return new MessageResult(false, MessageDeliveryStatus.FAILED, providerName(), null, code, message, null);
    }

    private boolean validEmail(String value) {
        if (!StringUtils.hasText(value)) return false;
        try { new InternetAddress(value.trim()).validate(); return true; } catch (Exception ex) { return false; }
    }
}
