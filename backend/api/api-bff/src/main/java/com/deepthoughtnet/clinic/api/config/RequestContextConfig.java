package com.deepthoughtnet.clinic.api.config;

import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import com.deepthoughtnet.clinic.identity.service.AppUserProvisionerImpl;
import com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner;
import com.deepthoughtnet.clinic.platform.core.security.AuthContextExtractor;
import com.deepthoughtnet.clinic.platform.core.security.TenantRoleResolver;
import com.deepthoughtnet.clinic.platform.security.kc.KeycloakAuthContextExtractor;
import com.deepthoughtnet.clinic.platform.spring.security.TenantRoleAuthorityFilter;
import com.deepthoughtnet.clinic.platform.spring.web.RequestContextFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class RequestContextConfig {
    @Bean
    public AuthContextExtractor authContextExtractor(TenantRepository tenantRepository) {
        return new KeycloakAuthContextExtractor(code -> tenantRepository.findByCode(code).map(tenant -> tenant.getId()));
    }

    @Bean
    public AppUserProvisioner appUserProvisioner(AppUserRepository repo) {
        return new AppUserProvisionerImpl(repo);
    }

    @Bean
    public RequestContextFilter clinicRequestContextFilter(
            AuthContextExtractor auth,
            AppUserProvisioner provisioner,
            TenantRoleResolver tenantRoleResolver,
            ObjectMapper objectMapper
    ) {
        return new RequestContextFilter(auth, provisioner, tenantRoleResolver, objectMapper);
    }

    @Bean
    public TenantRoleAuthorityFilter clinicTenantRoleAuthorityFilter() {
        return new TenantRoleAuthorityFilter();
    }

    @Bean
    public FilterRegistrationBean<RequestContextFilter> clinicRequestContextFilterRegistration(
            RequestContextFilter filter
    ) {
        FilterRegistrationBean<RequestContextFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<TenantRoleAuthorityFilter> clinicTenantRoleAuthorityFilterRegistration(
            TenantRoleAuthorityFilter filter
    ) {
        FilterRegistrationBean<TenantRoleAuthorityFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
