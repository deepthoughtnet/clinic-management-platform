package com.deepthoughtnet.clinic.api.discover;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.config.CorsConfig;
import com.deepthoughtnet.clinic.api.errors.GlobalRestExceptionHandler;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.CreateProviderApplicationCommand;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingService;
import jakarta.servlet.Filter;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CorsFilter;

class ProviderOnboardingCorsIntegrationTest {
    private ProviderOnboardingService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.mock(ProviderOnboardingService.class);
        CorsConfig corsConfig = new CorsConfig();
        Filter corsFilter = new CorsFilter(corsConfig.corsConfigurationSource());
        mockMvc = MockMvcBuilders.standaloneSetup(new ProviderOnboardingController(service))
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .addFilters(corsFilter)
                .build();
    }

    @ParameterizedTest
    @MethodSource("allowedOrigins")
    void preflightFromAllowedOriginsSucceeds(String origin) throws Exception {
        mockMvc.perform(options("/api/provider-registration/providers")
                        .header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type, Accept, Idempotency-Key, If-Match, X-Requested-With"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("allowedOrigins")
    void allowedOriginsReachProviderRegistrationCreateEndpoint(String origin) throws Exception {
        when(service.create(any(CreateProviderApplicationCommand.class))).thenReturn(null);

        mockMvc.perform(post("/api/provider-registration/providers")
                        .header(HttpHeaders.ORIGIN, origin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerType": "CLINIC",
                                  "email": "clinic.%s@example.com",
                                  "phone": "+919999999999",
                                  "password": "StrongPass123",
                                  "termsAccepted": true,
                                  "privacyAccepted": true
                                }
                                """.formatted(origin.replace("http://", "").replace(":", "-").replace("/", "-"))))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));

        verify(service).create(any(CreateProviderApplicationCommand.class));
    }

    @Test
    void disallowedOriginsAreRejectedBeforeControllerInvocation() throws Exception {
        mockMvc.perform(post("/api/provider-registration/providers")
                        .header(HttpHeaders.ORIGIN, "http://malicious.example")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerType": "CLINIC",
                                  "email": "clinic.malicious@example.com",
                                  "phone": "+919999999999",
                                  "password": "StrongPass123",
                                  "termsAccepted": true,
                                  "privacyAccepted": true
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void protectedOnboardingEndpointsStillRequireTheProviderToken() throws Exception {
        mockMvc.perform(get("/api/provider-registration/providers/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5177"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("missing_header"));

        verifyNoInteractions(service);
    }

    private static Stream<String> allowedOrigins() {
        return Stream.of(
                "http://localhost:5177",
                "http://127.0.0.1:5177",
                "http://localhost:5174",
                "http://localhost:5175"
        );
    }
}
