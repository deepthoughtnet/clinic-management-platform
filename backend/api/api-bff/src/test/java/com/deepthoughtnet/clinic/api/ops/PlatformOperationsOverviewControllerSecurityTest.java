package com.deepthoughtnet.clinic.api.ops;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.ComponentHealthResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.ComponentHealthStatus;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformOperationsDiagnosticResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformOperationsAiSummaryResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformOperationsOverviewResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformOperationsOverviewSummaryResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformOperationsReleaseResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformOperationsRuntimeResponse;
import com.deepthoughtnet.clinic.api.module.ModuleRouteRegistry;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.api.module.runtime.TenantRuntimeEntitlementProvider;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlatformOperationsOverviewController.class)
@Import({
        PermissionChecker.class,
        PlatformOperationsOverviewControllerSecurityTest.MethodSecurityConfig.class
})
class PlatformOperationsOverviewControllerSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlatformOperationsOverviewService overviewService;

    @MockBean
    private PlatformOperationsDiagnosticsService diagnosticsService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private TenantRuntimeEntitlementProvider tenantRuntimeEntitlementProvider;

    @MockBean
    private ModuleRouteRegistry moduleRouteRegistry;

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void platformAdminCanAccessPlatformOverview() throws Exception {
        when(overviewService.overview()).thenReturn(sampleOverview());
        when(diagnosticsService.test("api")).thenReturn(sampleDiagnostic());

        mockMvc.perform(get("/api/platform/operations/overview"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/platform/operations/health/api/test").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TENANT_ADMIN")
    void tenantAdminCannotAccessPlatformOverview() throws Exception {
        mockMvc.perform(get("/api/platform/operations/overview"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(overviewService);
        verifyNoInteractions(diagnosticsService);
    }

    @Test
    void unauthenticatedRequestIsDenied() throws Exception {
        mockMvc.perform(get("/api/platform/operations/overview"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(overviewService);
        verifyNoInteractions(diagnosticsService);
    }

    private PlatformOperationsOverviewResponse sampleOverview() {
        return new PlatformOperationsOverviewResponse(
                new PlatformOperationsOverviewSummaryResponse(ComponentHealthStatus.HEALTHY, 1, 0, 0, 0, Instant.parse("2026-09-05T12:00:00Z")),
                List.of(new ComponentHealthResponse("API", ComponentHealthStatus.HEALTHY, "OK", Instant.parse("2026-09-05T12:00:00Z"), Instant.parse("2026-09-05T12:00:00Z"), null, 1L, Map.of())),
                new PlatformOperationsAiSummaryResponse(1, 1, 0, Instant.parse("2026-09-05T12:00:00Z"), Map.of(), Map.of()),
                new PlatformOperationsReleaseResponse("jeevanam-prod-2026-09-05-01", "6f79bc84", "PRODUCTION", Instant.parse("2026-09-05T12:00:00Z"), Instant.parse("2026-09-05T12:05:00Z"), "1.0.0"),
                new PlatformOperationsRuntimeResponse(true, "UP", 123L, Instant.parse("2026-09-05T11:59:00Z"), Instant.parse("2026-09-05T12:00:00Z"))
        );
    }

    private PlatformOperationsDiagnosticResponse sampleDiagnostic() {
        return new PlatformOperationsDiagnosticResponse("API", true, ComponentHealthStatus.HEALTHY, "Application runtime available", 1L, Instant.parse("2026-09-05T12:00:00Z"), Map.of());
    }

    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }
}
