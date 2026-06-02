package com.deepthoughtnet.clinic.api.patientportal.auth;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PatientPortalAuthConfig {

    @Bean
    public PatientPortalSessionAuthenticationFilter patientPortalSessionAuthenticationFilter(
            PatientPortalSessionTokenService sessionTokenService
    ) {
        return new PatientPortalSessionAuthenticationFilter(sessionTokenService);
    }

    @Bean
    public FilterRegistrationBean<PatientPortalSessionAuthenticationFilter> patientPortalSessionAuthenticationFilterRegistration(
            PatientPortalSessionAuthenticationFilter filter
    ) {
        FilterRegistrationBean<PatientPortalSessionAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
