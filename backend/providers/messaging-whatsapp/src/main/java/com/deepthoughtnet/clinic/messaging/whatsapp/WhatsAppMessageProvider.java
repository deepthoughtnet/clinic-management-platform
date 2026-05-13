package com.deepthoughtnet.clinic.messaging.whatsapp;

import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import org.springframework.util.StringUtils;

/**
 * Configuration-driven WhatsApp provider foundation.
 *
 * <p>This adapter is intentionally vendor-neutral for now and returns NOT_CONFIGURED
 * until a concrete external provider SDK integration is implemented.</p>
 */
public class WhatsAppMessageProvider implements MessageProvider {
    private final CarePilotWhatsAppMessagingProperties properties;

    public WhatsAppMessageProvider(CarePilotWhatsAppMessagingProperties properties) {
        this.properties = properties;
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
        if (!StringUtils.hasText(properties.getProvider()) || "disabled".equalsIgnoreCase(properties.getProvider().trim())) {
            return MessageResult.notConfigured(providerName(), "carepilot.messaging.whatsapp.provider is disabled");
        }
        if (!StringUtils.hasText(properties.getFromNumber())) {
            return MessageResult.notConfigured(providerName(), "carepilot.messaging.whatsapp.from-number is required");
        }

        // Foundation-only stage: avoid false success before real vendor adapter implementation exists.
        return MessageResult.notConfigured(providerName(), "WhatsApp provider adapter is not implemented for provider " + properties.getProvider().trim());
    }

    @Override
    public String providerName() {
        if (!StringUtils.hasText(properties.getProvider()) || "disabled".equalsIgnoreCase(properties.getProvider().trim())) {
            return "WHATSAPP_NOT_CONFIGURED";
        }
        return "carepilot-whatsapp-" + properties.getProvider().trim().toLowerCase();
    }
}
