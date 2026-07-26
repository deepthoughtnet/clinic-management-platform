package com.deepthoughtnet.clinic.api.config;

import com.deepthoughtnet.clinic.platform.core.config.CommercialRuntimeProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommercialRuntimeConfiguration {
    @Bean
    @ConfigurationProperties(prefix = "commercial.runtime")
    public CommercialRuntimeProperties commercialRuntimeProperties() {
        return new CommercialRuntimeProperties();
    }
}
