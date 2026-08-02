package com.deepthoughtnet.clinic.platform.providerintegration.config;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPublisher;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Configuration
public class ProviderIntegrationConfiguration {

    @Bean
    Clock providerIntegrationClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(ModuleBusinessEventPublisher.class)
    ModuleBusinessEventPublisher providerIntegrationNoOpEventPublisher() {
        return new ModuleBusinessEventPublisher() {
            @Override
            public UUID publish(ModuleBusinessEvent event) {
                return event == null ? UUID.randomUUID() : event.eventId();
            }
        };
    }
}
