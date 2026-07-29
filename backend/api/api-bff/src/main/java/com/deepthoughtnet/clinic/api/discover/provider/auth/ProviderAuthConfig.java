package com.deepthoughtnet.clinic.api.discover.provider.auth;

import com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProviderAuthConfig {
    @Bean
    public ProviderSessionAuthenticationFilter providerSessionAuthenticationFilter(DiscoverVerificationService verificationService) {
        return new ProviderSessionAuthenticationFilter(verificationService);
    }

    @Bean
    public FilterRegistrationBean<ProviderSessionAuthenticationFilter> providerSessionAuthenticationFilterRegistration(
            ProviderSessionAuthenticationFilter filter
    ) {
        FilterRegistrationBean<ProviderSessionAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
