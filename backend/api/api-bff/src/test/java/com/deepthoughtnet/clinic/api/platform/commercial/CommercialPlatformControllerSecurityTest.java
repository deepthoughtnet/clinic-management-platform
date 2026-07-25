package com.deepthoughtnet.clinic.api.platform.commercial;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.module.ModuleRouteRegistry;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.ClonePlanTemplateRequest;
import com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.OverviewResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.PlanVersionDetailResponse;
import com.deepthoughtnet.clinic.identity.service.TenantSubscriptionService;
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

@WebMvcTest(CommercialPlatformController.class)
@Import({
        PermissionChecker.class,
        CommercialPlatformControllerSecurityTest.MethodSecurityConfig.class
})
class CommercialPlatformControllerSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommercialPlatformApiService service;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private TenantSubscriptionService tenantSubscriptionService;

    @MockBean
    private ModuleRouteRegistry moduleRouteRegistry;

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void platformAdminCanAccessCommercialPlatformEndpoints() throws Exception {
        when(service.getOverview()).thenReturn(new OverviewResponse(java.util.List.of(), java.util.List.of(), java.util.List.of()));
        when(service.publishVersion(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PlanVersionDetailResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        "v1",
                        com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.PublicationStatus.PUBLISHED,
                        java.time.OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                        UUID.randomUUID(),
                        "Initial publish",
                        1,
                        "0123456789abcdef",
                        0,
                        0,
                        0,
                        0,
                        0,
                        "{}"
                ));
        when(service.cloneTemplate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.TemplateDetailResponse(
                        UUID.randomUUID(),
                        "SOLO_CLINIC_PLUS",
                        "Solo Clinic Plus",
                        null,
                        com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.TargetSegment.SOLO,
                        com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.TemplateStatus.DRAFT,
                        0,
                        1,
                        null,
                        com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.DraftStatus.DRAFT,
                        false,
                        new com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.PlanValidationResultResponse(
                                com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.ValidationState.NOT_VALIDATED,
                                false,
                                0,
                                0,
                                java.util.List.of(),
                                1,
                                null
                        ),
                        java.time.OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                        new com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.PlanDraftResponse(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                1,
                                com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.DraftStatus.DRAFT,
                                null,
                                "NOT_VALIDATED",
                                false,
                                new com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.PlanValidationResultResponse(
                                        com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.ValidationState.NOT_VALIDATED,
                                        false,
                                        0,
                                        0,
                                        java.util.List.of(),
                                        1,
                                        null
                                ),
                                java.time.OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                                UUID.randomUUID(),
                                new com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.DraftConfigurationResponse(
                                        java.util.List.of(),
                                        java.util.List.of(),
                                        java.util.List.of(),
                                        java.util.List.of(),
                                        java.util.List.of()
                                ),
                                java.util.List.of()
                        ),
                        null
                ));

        mockMvc.perform(get("/api/platform/commercial/overview"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/platform/commercial/plan-templates/{templateId}/versions", UUID.randomUUID())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/platform/commercial/plan-templates/{sourceTemplateId}/clone", UUID.randomUUID())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"SOLO_CLINIC_PLUS\",\"name\":\"Solo Clinic Plus\",\"targetSegment\":\"SOLO\",\"status\":\"DRAFT\",\"displayOrder\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TENANT_ADMIN")
    void tenantAdminCannotAccessCommercialPlatformEndpoints() throws Exception {
        mockMvc.perform(get("/api/platform/commercial/overview"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void normalTenantUserCannotAccessCommercialPlatformEndpoints() throws Exception {
        mockMvc.perform(get("/api/platform/commercial/overview"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void unauthenticatedRequestIsDenied() throws Exception {
        mockMvc.perform(get("/api/platform/commercial/overview"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }
}
