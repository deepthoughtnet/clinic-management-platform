package com.deepthoughtnet.clinic.messaging.whatsapp.meta;

import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import com.deepthoughtnet.clinic.messaging.whatsapp.CarePilotWhatsAppMessagingProperties;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * Meta WhatsApp Cloud API provider adapter for CarePilot.
 */
public class MetaWhatsAppMessageProvider implements MessageProvider {
    private final CarePilotWhatsAppMessagingProperties properties;
    private final MetaWhatsAppHttpClient httpClient;

    public MetaWhatsAppMessageProvider(CarePilotWhatsAppMessagingProperties properties) {
        this(properties, new DefaultMetaWhatsAppHttpClient());
    }

    /**
     * Public for deterministic testing and explicit dependency injection in configuration.
     */
    public MetaWhatsAppMessageProvider(CarePilotWhatsAppMessagingProperties properties, MetaWhatsAppHttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    public boolean supports(MessageChannel channel) {
        return channel == MessageChannel.WHATSAPP;
    }

    @Override
    public MessageResult send(MessageRequest request) {
        if (!properties.isEnabled()) {
            return MessageResult.notConfigured(providerName(), "carepilot.messaging.whatsapp.enabled=false");
        }
        String provider = normalizedProvider();
        if (!"meta-cloud-api".equalsIgnoreCase(provider)) {
            return MessageResult.notConfigured(providerName(), "carepilot.messaging.whatsapp.provider is disabled or unsupported");
        }
        String configError = validateConfig();
        if (configError != null) {
            return MessageResult.notConfigured(providerName(), configError);
        }
        if (!StringUtils.hasText(request.recipient().address())) {
            return new MessageResult(false, MessageDeliveryStatus.FAILED, providerName(), null, "RECIPIENT_MISSING", "Recipient phone number is required", null);
        }
        if (!StringUtils.hasText(request.body())) {
            return new MessageResult(false, MessageDeliveryStatus.FAILED, providerName(), null, "BODY_MISSING", "WhatsApp message body is required", null);
        }

        String fallbackId = request.executionId() == null ? UUID.randomUUID().toString() : request.executionId().toString();
        try {
            MetaWhatsAppHttpResponse response = httpClient.sendText(request, properties);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new MessageResult(
                        true,
                        MessageDeliveryStatus.SENT,
                        providerName(),
                        StringUtils.hasText(response.messageId()) ? response.messageId() : fallbackId,
                        null,
                        null,
                        OffsetDateTime.now()
                );
            }
            return new MessageResult(
                    false,
                    MessageDeliveryStatus.FAILED,
                    providerName(),
                    StringUtils.hasText(response.messageId()) ? response.messageId() : fallbackId,
                    "PROVIDER_ERROR",
                    "Meta WhatsApp API returned HTTP " + response.statusCode(),
                    null
            );
        } catch (Exception ex) {
            return new MessageResult(
                    false,
                    MessageDeliveryStatus.FAILED,
                    providerName(),
                    fallbackId,
                    "PROVIDER_ERROR",
                    "Meta WhatsApp API transport error",
                    null
            );
        }
    }

    @Override
    public String providerName() {
        String provider = normalizedProvider();
        if (!StringUtils.hasText(provider) || "disabled".equalsIgnoreCase(provider)) {
            return "WHATSAPP_NOT_CONFIGURED";
        }
        return "carepilot-whatsapp-" + provider.toLowerCase();
    }

    private String validateConfig() {
        if (!StringUtils.hasText(properties.getApiUrl())) {
            return "carepilot.messaging.whatsapp.api-url is required for meta-cloud-api provider";
        }
        if (!StringUtils.hasText(properties.getAccessToken())) {
            return "carepilot.messaging.whatsapp.access-token is required for meta-cloud-api provider";
        }
        if (!StringUtils.hasText(properties.getPhoneNumberId())) {
            return "carepilot.messaging.whatsapp.phone-number-id is required for meta-cloud-api provider";
        }
        return null;
    }

    private String normalizedProvider() {
        return properties.getProvider() == null ? "" : properties.getProvider().trim();
    }
}
