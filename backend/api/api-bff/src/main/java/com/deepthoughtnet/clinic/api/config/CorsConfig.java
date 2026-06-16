package com.deepthoughtnet.clinic.api.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {
    private static final List<String> DEFAULT_ALLOWED_ORIGINS = List.of(
            "http://localhost:5173",
            "http://localhost:5174",
            "http://localhost:5175",
            "http://127.0.0.1:5173",
            "http://127.0.0.1:5174",
            "http://127.0.0.1:5175"
    );

    private final Environment environment;

    public CorsConfig() {
        this(new StandardEnvironment());
    }

    @Autowired
    public CorsConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(resolveAllowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Tenant-Id",
                "X-Patient-Session",
                "X-Platform-Op",
                "X-Correlation-Id",
                "Accept",
                "Origin",
                "Cache-Control",
                "Pragma"
        ));
        cfg.setExposedHeaders(List.of("WWW-Authenticate", "X-Correlation-Id"));
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    private List<String> resolveAllowedOrigins() {
        Set<String> origins = new LinkedHashSet<>(DEFAULT_ALLOWED_ORIGINS);
        String configured = environment.getProperty("CLINIC_CORS_ALLOWED_ORIGINS", "");
        for (String origin : configured.split(",")) {
            String trimmed = origin.trim();
            if (!trimmed.isEmpty()) {
                origins.add(trimmed);
            }
        }
        return new ArrayList<>(origins);
    }
}
