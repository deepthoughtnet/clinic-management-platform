package com.deepthoughtnet.clinic.api.module;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ModuleEntitlementConfig implements WebMvcConfigurer {
    private final ModuleEntitlementInterceptor interceptor;

    public ModuleEntitlementConfig(ModuleEntitlementInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns(
                        "/api/patients/**",
                        "/api/appointments/**",
                        "/api/consultations/**",
                        "/api/prescriptions/**",
                        "/api/billing/**",
                        "/api/vaccinations/**",
                        "/api/inventory/**",
                        "/api/reports/**",
                        "/api/ai/**"
                );
    }
}
