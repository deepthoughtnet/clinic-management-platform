package com.deepthoughtnet.clinic.messaging.email;

import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import com.deepthoughtnet.clinic.notify.NotificationProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires CarePilot email provider foundation while keeping startup safe without secrets.
 */
@Configuration
@EnableConfigurationProperties(CarePilotEmailMessagingProperties.class)
public class EmailMessageProviderConfig {

    @Bean
    public MessageProvider emailMessageProvider(
            NotificationProvider notificationProvider,
            CarePilotEmailMessagingProperties properties,
            @Value("${clinic.mail.provider:logging}") String mailProvider,
            @Value("${clinic.mail.enabled:false}") boolean mailEnabled
    ) {
        return new EmailMessageProvider(notificationProvider, properties, mailProvider, mailEnabled);
    }
}
