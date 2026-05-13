package com.deepthoughtnet.clinic.messaging.sms;

import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires CarePilot SMS provider foundation while keeping startup safe with disabled defaults.
 */
@Configuration
@EnableConfigurationProperties(CarePilotSmsMessagingProperties.class)
public class SmsMessageProviderConfig {

    @Bean
    public MessageProvider smsMessageProvider(CarePilotSmsMessagingProperties properties) {
        return new SmsMessageProvider(properties);
    }
}
