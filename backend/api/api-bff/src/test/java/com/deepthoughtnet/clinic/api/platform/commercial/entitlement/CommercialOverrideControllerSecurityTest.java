package com.deepthoughtnet.clinic.api.platform.commercial.entitlement;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.module.ModuleRouteRegistry;
import com.deepthoughtnet.clinic.api.module.runtime.TenantRuntimeEntitlementProvider;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.CreateOverrideRequest;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.OverrideImpactPreviewResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.OverrideResponse;
import com.deepthoughtnet.clinic.api.platform.service.TenantModuleService;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.identity.service.TenantSubscriptionService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@WebMvcTest(CommercialEffectiveEntitlementController.class)
@Import({
        PermissionChecker.class,
        CommercialOverrideControllerSecurityTest.MethodSecurityConfig.class
})
class CommercialOverrideControllerSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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
    void platformAdminCanPreviewAndSubmitOverrides() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID overrideId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        CreateOverrideRequest request = new CreateOverrideRequest(
                "MODULE",
                "AI_COPILOT",
                "ENABLE",
                null,
                null,
                LocalDate.parse("2026-07-25"),
                null,
                "Pilot AI access",
                null,
                subscriptionId
        );

        when(service.previewOverride(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.any())).thenReturn(
                new OverrideImpactPreviewResponse(
                        tenantId,
                        subscriptionId,
                        "MODULE",
                        "AI_COPILOT",
                        "ENABLE",
                        "Disabled",
                        "Enabled",
                        "MODULE",
                        "No runtime change while commercial runtime is disabled",
                        List.of("Clinical Reasoning becomes eligible"),
                        List.of()
                )
        );
        when(service.submitOverride(tenantId, overrideId)).thenReturn(new OverrideResponse(
                overrideId,
                "MODULE",
                "AI_COPILOT",
                "ENABLE",
                null,
                null,
                LocalDate.parse("2026-07-25"),
                null,
                "PENDING_APPROVAL",
                "Pilot AI access",
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
        ));

        mockMvc.perform(post("/api/platform/commercial/tenants/{tenantId}/overrides/impact-preview", tenantId)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/platform/commercial/tenants/{tenantId}/overrides/{overrideId}/submit", tenantId, overrideId)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TENANT_ADMIN")
    void tenantAdminCannotAccessOverrideEndpoints() throws Exception {
        mockMvc.perform(post("/api/platform/commercial/tenants/{tenantId}/overrides/impact-preview", UUID.randomUUID())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"MODULE\",\"targetCode\":\"AI_COPILOT\",\"operation\":\"ENABLE\",\"effectiveFrom\":\"2026-07-25\",\"reason\":\"Pilot AI access\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void unauthenticatedRequestIsDenied() throws Exception {
        mockMvc.perform(post("/api/platform/commercial/tenants/{tenantId}/overrides/impact-preview", UUID.randomUUID())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"MODULE\",\"targetCode\":\"AI_COPILOT\",\"operation\":\"ENABLE\",\"effectiveFrom\":\"2026-07-25\",\"reason\":\"Pilot AI access\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }
}
