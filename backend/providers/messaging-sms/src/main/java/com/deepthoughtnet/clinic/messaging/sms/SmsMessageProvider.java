package com.deepthoughtnet.clinic.messaging.sms;

import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * Configuration-driven SMS provider with vendor-neutral generic HTTP support.
 */
public class SmsMessageProvider implements MessageProvider {
    private final CarePilotSmsMessagingProperties properties;
    private final GenericHttpSmsClient genericHttpSmsClient;

    public SmsMessageProvider(CarePilotSmsMessagingProperties properties) {
        this(properties, new DefaultGenericHttpSmsClient());
    }

    SmsMessageProvider(CarePilotSmsMessagingProperties properties, GenericHttpSmsClient genericHttpSmsClient) {
        this.properties = properties;
        this.genericHttpSmsClient = genericHttpSmsClient;
    }

    @Override
    public boolean supports(MessageChannel channel) {
        return channel == MessageChannel.SMS;
    }

    @Override
    public MessageResult send(MessageRequest request) {
        if (!properties.isEnabled()) {
            return MessageResult.notConfigured(providerName(), "carepilot.messaging.sms.enabled=false");
        }
        String provider = normalizedProvider();
        if (!StringUtils.hasText(provider) || "disabled".equalsIgnoreCase(provider)) {
            return MessageResult.notConfigured(providerName(), "carepilot.messaging.sms.provider is disabled");
        }
        if (!StringUtils.hasText(request.recipient().address())) {
            return new MessageResult(
                    false,
                    MessageDeliveryStatus.FAILED,
                    providerName(),
                    null,
                    "RECIPIENT_MISSING",
                    "Recipient phone number is required",
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
                    "SMS message body is required",
                    null
            );
        }

        if ("generic-http".equalsIgnoreCase(provider)) {
            return sendGenericHttp(request);
        }
        return MessageResult.notConfigured(providerName(), "SMS provider adapter is not implemented for provider " + provider);
    }

    @Override
    public String providerName() {
        String provider = normalizedProvider();
        if (!StringUtils.hasText(provider) || "disabled".equalsIgnoreCase(provider)) {
            return "SMS_NOT_CONFIGURED";
        }
        return "carepilot-sms-" + provider.toLowerCase();
    }

    private MessageResult sendGenericHttp(MessageRequest request) {
        String validation = validateGenericHttpConfig();
        if (validation != null) {
            return MessageResult.notConfigured(providerName(), validation);
        }
        String fallbackMessageId = request.executionId() == null ? UUID.randomUUID().toString() : request.executionId().toString();
        try {
            GenericHttpSmsResponse response = genericHttpSmsClient.send(request, properties);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new MessageResult(
                        true,
                        MessageDeliveryStatus.SENT,
                        providerName(),
                        StringUtils.hasText(response.providerMessageId()) ? response.providerMessageId() : fallbackMessageId,
                        null,
                        null,
                        OffsetDateTime.now()
                );
            }
            return new MessageResult(
                    false,
                    MessageDeliveryStatus.FAILED,
                    providerName(),
                    StringUtils.hasText(response.providerMessageId()) ? response.providerMessageId() : fallbackMessageId,
                    "PROVIDER_ERROR",
                    "SMS provider HTTP call failed with status " + response.statusCode(),
                    null
            );
        } catch (Exception ex) {
            return new MessageResult(
                    false,
                    MessageDeliveryStatus.FAILED,
                    providerName(),
                    fallbackMessageId,
                    "PROVIDER_ERROR",
                    "SMS provider transport error",
                    null
            );
        }
    }

    private String validateGenericHttpConfig() {
        if (!StringUtils.hasText(properties.getApiUrl())) {
            return "carepilot.messaging.sms.api-url is required for generic-http provider";
        }
        if (!StringUtils.hasText(properties.getApiKey())) {
            return "carepilot.messaging.sms.api-key is required for generic-http provider";
        }
        if (!StringUtils.hasText(properties.getFromNumber()) && !StringUtils.hasText(properties.getSenderId())) {
            return "carepilot.messaging.sms.from-number or carepilot.messaging.sms.sender-id is required";
        }
        return null;
    }

    private String normalizedProvider() {
        return properties.getProvider() == null ? "" : properties.getProvider().trim();
    }
}
