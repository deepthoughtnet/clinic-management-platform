package com.deepthoughtnet.clinic.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class CorsConfigTest {

    private final CorsConfig corsConfig = new CorsConfig();

    @Test
    void allowsLocalFrontendOriginsAndPlatformHeaders() {
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
    void keepsCorsTightToLocalDevelopmentHosts() {
        CorsConfiguration configuration = corsConfig.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/platform/tenants"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).allMatch(origin -> List.of(
                "http://localhost:5173",
                "http://localhost:5174",
                "http://localhost:5175",
                "http://127.0.0.1:5173",
                "http://127.0.0.1:5174",
                "http://127.0.0.1:5175"
        ).contains(origin));
    }
}
