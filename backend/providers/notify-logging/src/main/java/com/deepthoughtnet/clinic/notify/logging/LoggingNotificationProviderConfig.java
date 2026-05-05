package com.deepthoughtnet.clinic.notify.logging;

import com.deepthoughtnet.clinic.notify.NotificationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoggingNotificationProviderConfig {

    @Bean
    @ConditionalOnMissingBean(NotificationProvider.class)
    public NotificationProvider loggingNotificationProvider() {
        return new LoggingNotificationProvider();
    }
}
