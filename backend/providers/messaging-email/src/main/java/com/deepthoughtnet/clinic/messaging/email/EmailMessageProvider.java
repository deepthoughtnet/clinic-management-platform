package com.deepthoughtnet.clinic.messaging.email;

import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import com.deepthoughtnet.clinic.notify.NotificationDeliveryException;
import com.deepthoughtnet.clinic.notify.NotificationMessage;
import com.deepthoughtnet.clinic.notify.NotificationProvider;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * CarePilot email provider adapter that reuses the platform notification sender.
 */
public class EmailMessageProvider implements MessageProvider {
    private final NotificationProvider notificationProvider;
    private final CarePilotEmailMessagingProperties properties;
    private final String mailProvider;
    private final boolean mailEnabled;

    public EmailMessageProvider(
            NotificationProvider notificationProvider,
            CarePilotEmailMessagingProperties properties,
            String mailProvider,
            boolean mailEnabled
    ) {
        this.notificationProvider = notificationProvider;
        this.properties = properties;
        this.mailProvider = mailProvider;
        this.mailEnabled = mailEnabled;
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
        if (!mailEnabled || !"smtp".equalsIgnoreCase(mailProvider)) {
            return MessageResult.notConfigured(providerName(), "SMTP mail provider is not configured");
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

        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (request.metadata() != null) {
                metadata.putAll(request.metadata());
            }
            if (StringUtils.hasText(properties.getFromAddress())) {
                metadata.put("fromAddress", properties.getFromAddress().trim());
            }
            notificationProvider.send(new NotificationMessage(
                    request.tenantId(),
                    "EMAIL",
                    request.recipient().address().trim(),
                    request.subject(),
                    request.body(),
                    metadata.toString()
            ));
            return new MessageResult(
                    true,
                    MessageDeliveryStatus.SENT,
                    providerName(),
                    UUID.randomUUID().toString(),
                    null,
                    null,
                    OffsetDateTime.now()
            );
        } catch (NotificationDeliveryException ex) {
            return new MessageResult(
                    false,
                    MessageDeliveryStatus.FAILED,
                    providerName(),
                    null,
                    "EMAIL_DELIVERY_FAILED",
                    ex.getMessage(),
                    null
            );
        }
    }

    @Override
    public String providerName() {
        return "carepilot-email";
    }
}
