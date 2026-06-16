package com.deepthoughtnet.clinic.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class CorsConfigTest {
    private static final String ORIGINS_PROPERTY = "CLINIC_CORS_ALLOWED_ORIGINS";

    @AfterEach
    void tearDown() {
        System.clearProperty(ORIGINS_PROPERTY);
    }

    @Test
    void allowsLocalFrontendOriginsAndPlatformHeaders() {
        CorsConfig corsConfig = new CorsConfig();
        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        assertThat(source).isInstanceOf(UrlBasedCorsConfigurationSource.class);

        CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/platform/tenants"));
        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactlyInAnyOrder(
                "http://localhost:5173",
                "http://localhost:5174",
                "http://localhost:5175",
                "http://127.0.0.1:5173",
                "http://127.0.0.1:5174",
                "http://127.0.0.1:5175"
        );
        assertThat(configuration.getAllowedHeaders()).contains(
                "Authorization",
                "Content-Type",
                "X-Tenant-Id",
                "X-Patient-Session",
                "X-Platform-Op"
        );
        assertThat(configuration.getAllowedMethods()).contains("OPTIONS", "POST");
    }

    @Test
    void includesConfiguredUatOriginsWithoutDroppingLocalDefaults() {
        System.setProperty(ORIGINS_PROPERTY, "https://arogia.deepthoughtnet.com");
        CorsConfig corsConfig = new CorsConfig();

        CorsConfiguration configuration = corsConfig.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/platform/tenants"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).contains(
                "https://arogia.deepthoughtnet.com",
                "http://localhost:5173",
                "http://localhost:5174",
                "http://localhost:5175",
                "http://127.0.0.1:5173",
                "http://127.0.0.1:5174",
                "http://127.0.0.1:5175"
        );
    }
}
