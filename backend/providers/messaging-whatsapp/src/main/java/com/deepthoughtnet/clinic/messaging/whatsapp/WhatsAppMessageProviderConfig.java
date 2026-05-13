package com.deepthoughtnet.clinic.messaging.whatsapp;

import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import com.deepthoughtnet.clinic.messaging.whatsapp.meta.MetaWhatsAppMessageProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires CarePilot WhatsApp provider foundation while keeping startup safe with disabled defaults.
 */
@Configuration
@EnableConfigurationProperties(CarePilotWhatsAppMessagingProperties.class)
public class WhatsAppMessageProviderConfig {

    @Bean
    public MessageProvider whatsAppMessageProvider(CarePilotWhatsAppMessagingProperties properties) {
        return new MetaWhatsAppMessageProvider(properties);
    }
}
