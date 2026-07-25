package com.deepthoughtnet.clinic.api.platform.commercial.subscription;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.module.ModuleRouteRegistry;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.LifecycleActionRequest;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.PageResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.SubscriptionDetailResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.SubscriptionHistoryResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.SubscriptionStatusCountsResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.SubscriptionSummaryResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.ValidationResultResponse;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionEnums.SubscriptionStatus;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionEnums.ValidationSeverity;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionEnums.ValidationState;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CommercialSubscriptionController.class)
@Import({
        com.deepthoughtnet.clinic.api.security.PermissionChecker.class,
        CommercialSubscriptionControllerSecurityTest.MethodSecurityConfig.class
})
class CommercialSubscriptionControllerSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommercialSubscriptionApiService service;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private TenantSubscriptionService tenantSubscriptionService;

    @MockBean
    private ModuleRouteRegistry moduleRouteRegistry;

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void platformAdminCanAccessCommercialSubscriptionEndpoints() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        SubscriptionDetailResponse detail = detail(subscriptionId, tenantId, templateId, versionId, SubscriptionStatus.ACTIVE);
        SubscriptionSummaryResponse summary = new SubscriptionSummaryResponse(
                subscriptionId,
                tenantId,
                templateId,
                "SOLO_CLINIC",
                "Solo Clinic",
                versionId,
                1,
                "v1",
                SubscriptionStatus.ACTIVE,
                LocalDate.now(),
                null,
                true,
                "Solo Clinic Subscription",
                "SUB-001",
                "Notes",
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                OffsetDateTime.parse("2026-07-24T00:00:00Z")
        );
        when(service.listSubscriptions(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(summary), 0, 20, 1, 1));
        when(service.getSubscription(subscriptionId)).thenReturn(detail);
        when(service.createSubscription(any())).thenReturn(detail);
        when(service.activate(any(), any())).thenReturn(detail);
        when(service.pause(any(), any())).thenReturn(detail);
        when(service.resume(any(), any())).thenReturn(detail);
        when(service.cancel(any(), any())).thenReturn(detail);
        when(service.replace(any(), any())).thenReturn(detail);
        when(service.history(subscriptionId)).thenReturn(List.of(new SubscriptionHistoryResponse(UUID.randomUUID(), "COMMERCIAL_SUBSCRIPTION_CREATED", null, "ACTIVE", UUID.randomUUID(), OffsetDateTime.parse("2026-07-24T00:00:00Z"), "Created")));
        when(service.getStatusCounts()).thenReturn(new SubscriptionStatusCountsResponse(1, 2, 3, 4, 5));

        mockMvc.perform(get("/api/platform/commercial/subscriptions"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/platform/commercial/subscriptions/{id}", subscriptionId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/platform/commercial/subscriptions")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content("{\"tenantId\":\"" + tenantId + "\",\"publishedVersionId\":\"" + versionId + "\",\"startDate\":\"2026-07-25\",\"autoRenew\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/platform/commercial/subscriptions/{id}/activate", subscriptionId).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/platform/commercial/subscriptions/{id}/pause", subscriptionId).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/platform/commercial/subscriptions/{id}/resume", subscriptionId).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/platform/commercial/subscriptions/{id}/cancel", subscriptionId).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/platform/commercial/subscriptions/{id}/replace", subscriptionId)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content("{\"publishedVersionId\":\"" + versionId + "\",\"startDate\":\"2026-07-25\",\"autoRenew\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/platform/commercial/subscriptions/{id}/history", subscriptionId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/platform/commercial/subscriptions/status-counts"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TENANT_ADMIN")
    void tenantAdminCannotAccessCommercialSubscriptionEndpoints() throws Exception {
        mockMvc.perform(get("/api/platform/commercial/subscriptions"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void normalTenantUserCannotAccessCommercialSubscriptionEndpoints() throws Exception {
        mockMvc.perform(get("/api/platform/commercial/subscriptions"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void unauthenticatedRequestIsDenied() throws Exception {
        mockMvc.perform(get("/api/platform/commercial/subscriptions"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    private SubscriptionDetailResponse detail(UUID subscriptionId, UUID tenantId, UUID templateId, UUID versionId, SubscriptionStatus status) {
        return new SubscriptionDetailResponse(
                subscriptionId,
                tenantId,
                templateId,
                "SOLO_CLINIC",
                "Solo Clinic",
                versionId,
                1,
                "v1",
                status,
                LocalDate.now(),
                null,
                true,
                "Solo Clinic Subscription",
                "SUB-001",
                "Notes",
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                UUID.randomUUID(),
                List.of(),
                new ValidationResultResponse(ValidationState.VALID, true, 0, 0, List.of(new com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.ValidationMessageResponse("tenantId", "OK", "Ready", null, ValidationSeverity.INFO, false)), OffsetDateTime.parse("2026-07-24T00:00:00Z"))
        );
    }
}
