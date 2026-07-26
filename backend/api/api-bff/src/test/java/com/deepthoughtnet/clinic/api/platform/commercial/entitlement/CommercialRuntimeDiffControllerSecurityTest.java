package com.deepthoughtnet.clinic.api.platform.commercial.entitlement;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.ComparisonResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.LegacyComparisonResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.RuntimeDiffSummaryResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.RuntimeDiffTenantResponse;
import com.deepthoughtnet.clinic.api.module.ModuleRouteRegistry;
import com.deepthoughtnet.clinic.api.module.runtime.TenantRuntimeEntitlementProvider;
import com.deepthoughtnet.clinic.api.platform.service.TenantModuleService;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.identity.service.TenantSubscriptionService;
import com.deepthoughtnet.clinic.platform.core.config.CommercialRuntimeProperties;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CommercialRuntimeDiffController.class)
@Import({
        PermissionChecker.class,
        CommercialRuntimeDiffControllerSecurityTest.MethodSecurityConfig.class
})
class CommercialRuntimeDiffControllerSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommercialRuntimeDiffApiService service;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private CommercialRuntimeProperties runtimeProperties;

    @MockBean
    private TenantSubscriptionService tenantSubscriptionService;

    @MockBean
    private ModuleRouteRegistry moduleRouteRegistry;

    @MockBean
    private TenantRuntimeEntitlementProvider tenantRuntimeEntitlementProvider;

    @MockBean
    private TenantModuleService tenantModuleService;

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void platformAdminCanAccessRuntimeDiffEndpoints() throws Exception {
        UUID tenantId = UUID.randomUUID();
        var summary = new RuntimeDiffSummaryResponse(1, 1, 0, 0, 1, 0, 0, 0, 0, 0, false, false, 0);
        var tenant = new RuntimeDiffTenantResponse(
                tenantId,
                "Demo Clinic",
                "demo-clinic",
                "ACTIVE · solo-clinic",
                "Demo Clinic Subscription",
                "ACTIVE",
                "Solo Clinic",
                "Version 2",
                "CURRENT",
                "VALID",
                OffsetDateTime.parse("2026-07-25T00:00:00Z"),
                "MATCH",
                0,
                0,
                0,
                0,
                "Legacy Runtime — Authoritative",
                "READY",
                "READY",
                List.of(),
                List.of(),
                "Ready for shadow mode",
                "/platform/commercial/runtime-diff",
                List.of()
        );
        var comparison = new LegacyComparisonResponse(tenantId, List.of(new ComparisonResponse("APPOINTMENTS", "Appointments", "MATCH", "true", "true", "module")), List.of(), List.of());

        when(service.summary()).thenReturn(summary);
        when(service.tenants()).thenReturn(List.of(tenant));
        when(service.tenant(tenantId)).thenReturn(tenant);
        when(service.compare(tenantId)).thenReturn(comparison);

        mockMvc.perform(get("/api/platform/commercial/runtime-diff/summary"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/platform/commercial/runtime-diff/tenants"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/platform/commercial/runtime-diff/tenants/{tenantId}", tenantId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/platform/commercial/runtime-diff/tenants/{tenantId}/compare", tenantId)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TENANT_ADMIN")
    void tenantAdminCannotAccessRuntimeDiffEndpoints() throws Exception {
        mockMvc.perform(get("/api/platform/commercial/runtime-diff/summary"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void unauthenticatedRequestIsDenied() throws Exception {
        mockMvc.perform(get("/api/platform/commercial/runtime-diff/summary"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }
}
