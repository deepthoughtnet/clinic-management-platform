package com.deepthoughtnet.clinic.api.carepilot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.carepilot.dto.WebinarDtos.WebinarAnalyticsSummaryResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.WebinarDtos.WebinarAttendanceRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.WebinarDtos.WebinarRegistrationRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.WebinarDtos.WebinarStatusUpdateRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.WebinarDtos.WebinarUpsertRequest;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.carepilot.webinar.analytics.WebinarAnalyticsService;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRecord;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarSearchCriteria;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarStatus;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarType;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRegistrationRecord;
import com.deepthoughtnet.clinic.carepilot.webinar.registration.WebinarRegistrationService;
import com.deepthoughtnet.clinic.carepilot.webinar.service.WebinarService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CarePilotWebinarController.class)
@Import({
        PermissionChecker.class,
        CarePilotWebinarControllerAuthorizationTest.MethodSecurityConfig.class,
        CarePilotWebinarControllerAuthorizationTest.ControllerConfig.class
})
class CarePilotWebinarControllerAuthorizationTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID WEBINAR_ID = UUID.randomUUID();
    private static final UUID REGISTRATION_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    @Autowired private MockMvc mockMvc;
    @MockBean private WebinarService webinarService;
    @MockBean private WebinarRegistrationService registrationService;
    @MockBean private WebinarAnalyticsService analyticsService;

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    @WithMockUser(roles = "ENGAGE_MANAGER")
    void engageManagerCanUseWebinarEndpoints() throws Exception {
        setRequestContext("ENGAGE_MANAGER");
        stubWebinarWorkflows();

        mockMvc.perform(get("/api/carepilot/webinars")).andExpect(status().isOk());
        mockMvc.perform(get("/api/carepilot/webinars/{id}", WEBINAR_ID)).andExpect(status().isOk());
        mockMvc.perform(post("/api/carepilot/webinars")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"title":"Diabetes", "scheduledStartAt":"2026-07-21T10:00:00Z","scheduledEndAt":"2026-07-21T11:00:00Z","timezone":"UTC"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/carepilot/webinars/{id}", WEBINAR_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"title":"Diabetes", "scheduledStartAt":"2026-07-21T10:00:00Z","scheduledEndAt":"2026-07-21T11:00:00Z","timezone":"UTC"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/carepilot/webinars/{id}/status", WEBINAR_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"LIVE\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/carepilot/webinars/{id}/registrations", WEBINAR_ID)).andExpect(status().isOk());
        mockMvc.perform(post("/api/carepilot/webinars/{id}/register", WEBINAR_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"attendeeName\":\"Asha\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/carepilot/webinars/{id}/attendance", WEBINAR_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"registrationId\":\"" + REGISTRATION_ID + "\",\"registrationStatus\":\"ATTENDED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/carepilot/webinars/analytics/summary")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ENGAGE_EXECUTIVE")
    void engageExecutiveCanRegisterAndRecordAttendanceButCannotPublishOrCreate() throws Exception {
        setRequestContext("ENGAGE_EXECUTIVE");
        stubWebinarWorkflows();

        mockMvc.perform(get("/api/carepilot/webinars")).andExpect(status().isOk());
        mockMvc.perform(get("/api/carepilot/webinars/{id}", WEBINAR_ID)).andExpect(status().isOk());
        mockMvc.perform(get("/api/carepilot/webinars/{id}/registrations", WEBINAR_ID)).andExpect(status().isOk());
        mockMvc.perform(post("/api/carepilot/webinars/{id}/register", WEBINAR_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"attendeeName\":\"Asha\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/carepilot/webinars/{id}/attendance", WEBINAR_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"registrationId\":\"" + REGISTRATION_ID + "\",\"registrationStatus\":\"ATTENDED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/carepilot/webinars")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"title":"Diabetes", "scheduledStartAt":"2026-07-21T10:00:00Z","scheduledEndAt":"2026-07-21T11:00:00Z","timezone":"UTC"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/carepilot/webinars/{id}", WEBINAR_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"title":"Diabetes", "scheduledStartAt":"2026-07-21T10:00:00Z","scheduledEndAt":"2026-07-21T11:00:00Z","timezone":"UTC"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/carepilot/webinars/{id}/status", WEBINAR_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"LIVE\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/carepilot/webinars/analytics/summary")).andExpect(status().isForbidden());
    }

    private void stubWebinarWorkflows() {
        WebinarRecord webinar = sampleWebinar();
        WebinarRegistrationRecord registration = sampleRegistration();
        when(webinarService.search(eq(TENANT_ID), any(WebinarSearchCriteria.class), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(webinar), PageRequest.of(0, 25), 1));
        when(webinarService.find(eq(TENANT_ID), eq(WEBINAR_ID))).thenReturn(Optional.of(webinar));
        when(webinarService.create(eq(TENANT_ID), any(), eq(ACTOR_ID))).thenReturn(webinar);
        when(webinarService.update(eq(TENANT_ID), eq(WEBINAR_ID), any(), eq(ACTOR_ID))).thenReturn(webinar);
        when(webinarService.updateStatus(eq(TENANT_ID), eq(WEBINAR_ID), eq(WebinarStatus.LIVE), eq(ACTOR_ID))).thenReturn(webinar);
        when(registrationService.list(eq(TENANT_ID), eq(WEBINAR_ID), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(registration), PageRequest.of(0, 25), 1));
        when(registrationService.register(eq(TENANT_ID), eq(WEBINAR_ID), any(), eq(ACTOR_ID))).thenReturn(registration);
        when(registrationService.markAttendance(eq(TENANT_ID), eq(WEBINAR_ID), eq(REGISTRATION_ID), any())).thenReturn(registration);
        when(analyticsService.summary(eq(TENANT_ID))).thenReturn(
                new com.deepthoughtnet.clinic.carepilot.webinar.analytics.WebinarAnalyticsRecord(
                        1, 1, 0, 1, 1, 0, 100.0, 0.0, 0, java.util.Map.of(), 0
                )
        );
    }

    private WebinarRecord sampleWebinar() {
        return new WebinarRecord(
                WEBINAR_ID,
                TENANT_ID,
                "Diabetes Awareness",
                "Session",
                WebinarType.HEALTH_AWARENESS,
                WebinarStatus.SCHEDULED,
                null,
                null,
                "https://example.com/w/1",
                "Admin",
                "admin@example.com",
                OffsetDateTime.parse("2026-07-21T10:00:00Z"),
                OffsetDateTime.parse("2026-07-21T11:00:00Z"),
                "UTC",
                100,
                true,
                true,
                true,
                "care",
                ACTOR_ID,
                ACTOR_ID,
                OffsetDateTime.parse("2026-07-21T09:00:00Z"),
                OffsetDateTime.parse("2026-07-21T09:00:00Z")
        );
    }

    private WebinarRegistrationRecord sampleRegistration() {
        return new WebinarRegistrationRecord(
                REGISTRATION_ID,
                TENANT_ID,
                WEBINAR_ID,
                null,
                null,
                null,
                null,
                null,
                "Asha",
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                OffsetDateTime.parse("2026-07-21T09:10:00Z"),
                OffsetDateTime.parse("2026-07-21T09:10:00Z")
        );
    }

    private void setRequestContext(String role) {
        RequestContextHolder.set(new RequestContext(
                TenantId.of(TENANT_ID),
                ACTOR_ID,
                "tester@example.com",
                Set.of(role),
                role,
                "corr-webinar-auth"
        ));
    }

    @Configuration
    static class ControllerConfig {
        @Bean
        CarePilotWebinarController carePilotWebinarController(
                WebinarService webinarService,
                WebinarRegistrationService registrationService,
                WebinarAnalyticsService analyticsService,
                PermissionChecker permissionChecker
        ) {
            return new CarePilotWebinarController(webinarService, registrationService, analyticsService, permissionChecker);
        }
    }

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }
}
