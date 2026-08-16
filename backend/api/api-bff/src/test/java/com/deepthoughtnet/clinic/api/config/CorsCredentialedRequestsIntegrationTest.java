package com.deepthoughtnet.clinic.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.CorsFilter;

class CorsCredentialedRequestsIntegrationTest {
    private static final String ALLOWED_ORIGINS_PROPERTY = "CLINIC_CORS_ALLOWED_ORIGINS";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        System.setProperty(ALLOWED_ORIGINS_PROPERTY, "https://jeevanam.deepthoughtnet.com");
        CorsConfig corsConfig = new CorsConfig();
        mockMvc = MockMvcBuilders.standaloneSetup(new CredentialedApiController())
                .addFilters(new CorsFilter(corsConfig.corsConfigurationSource()))
                .build();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(ALLOWED_ORIGINS_PROPERTY);
    }

    @Test
    void providerMePreflightFromAllowedOriginIncludesCredentialsAndOrigin() throws Exception {
        mockMvc.perform(options("/api/provider/me")
                        .header(HttpHeaders.ORIGIN, "https://jeevanam.deepthoughtnet.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type, Accept"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://jeevanam.deepthoughtnet.com"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void providerMeCredentialedGetFromAllowedOriginIncludesCredentials() throws Exception {
        mockMvc.perform(get("/api/provider/me")
                        .header(HttpHeaders.ORIGIN, "https://jeevanam.deepthoughtnet.com")
                        .header(HttpHeaders.COOKIE, "provider-session=abc123"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://jeevanam.deepthoughtnet.com"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void patientPortalCredentialedGetFromAllowedOriginIncludesCredentials() throws Exception {
        mockMvc.perform(get("/api/patient-portal/dashboard")
                        .header(HttpHeaders.ORIGIN, "https://jeevanam.deepthoughtnet.com")
                        .header(HttpHeaders.COOKIE, "patient-session=xyz789"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://jeevanam.deepthoughtnet.com"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void disallowedOriginReceivesNoCorsGrant() throws Exception {
        mockMvc.perform(options("/api/provider/me")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/provider/me")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.COOKIE, "provider-session=abc123"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @RestController
    static class CredentialedApiController {
        @GetMapping("/api/provider/me")
        ResponseEntity<String> providerMe() {
            return ResponseEntity.ok("provider");
        }

        @GetMapping("/api/patient-portal/dashboard")
        ResponseEntity<String> patientPortalDashboard() {
            return ResponseEntity.ok("patient");
        }
    }
}
