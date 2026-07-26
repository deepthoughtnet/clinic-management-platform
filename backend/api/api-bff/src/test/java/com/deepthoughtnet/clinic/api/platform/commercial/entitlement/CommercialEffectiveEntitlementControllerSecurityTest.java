package com.deepthoughtnet.clinic.api.platform.commercial.entitlement;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.module.ModuleRouteRegistry;
import com.deepthoughtnet.clinic.api.module.runtime.TenantRuntimeEntitlementProvider;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.api.platform.service.TenantModuleService;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.EffectiveEntitlementSnapshotResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.OverrideResponse;
import com.deepthoughtnet.clinic.identity.service.TenantSubscriptionService;
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

@WebMvcTest(CommercialEffectiveEntitlementController.class)
@Import({
        PermissionChecker.class,
        CommercialEffectiveEntitlementControllerSecurityTest.MethodSecurityConfig.class
})
class CommercialEffectiveEntitlementControllerSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommercialEffectiveEntitlementApiService service;

    @MockBean
    private JwtDecoder jwtDecoder;

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
    void platformAdminCanAccessEffectiveEntitlementEndpoints() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID overrideId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID planTemplateId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        var snapshot = new EffectiveEntitlementSnapshotResponse(
                snapshotId,
                tenantId,
                subscriptionId,
                planTemplateId,
                versionId,
                1,
                "ACTIVE",
                OffsetDateTime.parse("2026-07-25T00:00:00Z"),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "source-hash",
                "content-hash",
                OffsetDateTime.parse("2026-07-25T00:00:00Z"),
                actorId.toString(),
                "MANUAL_REGENERATE",
                "CURRENT",
                "VALID",
                List.of()
        );

        when(service.getCurrentSnapshot(tenantId)).thenReturn(snapshot);
        when(service.regenerate(tenantId, com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.GenerationReason.MANUAL_REGENERATE)).thenReturn(snapshot);
        when(service.listOverrides(tenantId)).thenReturn(List.of(new OverrideResponse(
                overrideId,
                "MODULE",
                "AI_COPILOT",
                "ENABLE",
                null,
                null,
                java.time.LocalDate.parse("2026-07-25"),
                null,
                "ACTIVE",
                "Pilot access",
                null,
                null,
                null,
                null,
                null,
                OffsetDateTime.parse("2026-07-25T00:00:00Z"),
                actorId,
                OffsetDateTime.parse("2026-07-25T00:00:00Z"),
                actorId,
                0L
        )));

        mockMvc.perform(get("/api/platform/commercial/tenants/{tenantId}/effective-entitlements", tenantId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/platform/commercial/tenants/{tenantId}/effective-entitlements/regenerate", tenantId)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/platform/commercial/tenants/{tenantId}/overrides", tenantId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TENANT_ADMIN")
    void tenantAdminCannotAccessEffectiveEntitlementEndpoints() throws Exception {
        mockMvc.perform(get("/api/platform/commercial/tenants/{tenantId}/effective-entitlements", UUID.randomUUID()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void unauthenticatedRequestIsDenied() throws Exception {
        mockMvc.perform(get("/api/platform/commercial/tenants/{tenantId}/effective-entitlements", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }
}
