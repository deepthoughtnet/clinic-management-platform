package com.deepthoughtnet.clinic.api.carepilot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.carepilot.dto.OpsConsoleDtos.OpsReadinessResponse;
import com.deepthoughtnet.clinic.carepilot.analytics.service.CarePilotAnalyticsService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import({
        com.deepthoughtnet.clinic.api.security.PermissionChecker.class,
        CarePilotOpsControllerTest.MethodSecurityConfig.class,
        CarePilotOpsController.class
})
class CarePilotOpsControllerTest {
    private static final UUID TENANT_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CarePilotAnalyticsService analyticsService;

    @MockBean
    private CarePilotOpsConsoleService opsConsoleService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    @WithMockUser(roles = "CLINIC_ADMIN")
    void clinicAdminCanLoadOpsExecutionsAndReadiness() throws Exception {
        RequestContextHolder.set(new RequestContext(
                TenantId.of(TENANT_ID),
                UUID.randomUUID(),
                "clinic.admin@test",
                Set.of("CLINIC_ADMIN"),
                "CLINIC_ADMIN",
                "corr-ops-test"
        ));
        when(opsConsoleService.listExecutions(any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any())).thenReturn(List.of());
        when(opsConsoleService.listQueuedExecutions(any(), any(), any(), any(), anyBoolean(), any(), any(), any())).thenReturn(List.of());
        when(opsConsoleService.readiness(any())).thenReturn(new OpsReadinessResponse(
                true,
                OffsetDateTime.parse("2026-07-19T00:00:00Z"),
                null,
                true,
                OffsetDateTime.parse("2026-07-19T00:00:00Z"),
                0,
                0,
                0,
                0,
                0,
                null,
                List.of()
        ));

        mockMvc.perform(get("/api/carepilot/ops/executions").param("campaignRef", "CAM-2026-000002").with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/carepilot/ops/queued-executions").param("campaignRef", "CAM-2026-000002").with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/carepilot/ops/readiness").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void receptionistGetsForbiddenForOpsExecutions() throws Exception {
        RequestContextHolder.set(new RequestContext(
                TenantId.of(TENANT_ID),
                UUID.randomUUID(),
                "reception@test",
                Set.of("RECEPTIONIST"),
                "RECEPTIONIST",
                "corr-ops-forbidden"
        ));

        mockMvc.perform(get("/api/carepilot/ops/executions").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @org.springframework.context.annotation.Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }
}
