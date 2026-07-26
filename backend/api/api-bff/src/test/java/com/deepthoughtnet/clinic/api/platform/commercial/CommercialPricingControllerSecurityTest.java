package com.deepthoughtnet.clinic.api.platform.commercial;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import com.deepthoughtnet.clinic.api.module.ModuleRouteRegistry;
import com.deepthoughtnet.clinic.api.module.runtime.TenantRuntimeEntitlementProvider;
import com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.PlanPricingResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.PricingComparisonResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.dto.CommercialPlatformDtos.PricingValidationResultResponse;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPricingEnums.BillingCycle;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPricingEnums.PricingStatus;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPricingEnums.TaxModel;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.ValidationState;
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

@WebMvcTest(CommercialPlatformController.class)
@Import({
        PermissionChecker.class,
        CommercialPricingControllerSecurityTest.MethodSecurityConfig.class
})
class CommercialPricingControllerSecurityTest {
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

    @MockBean
    private TenantRuntimeEntitlementProvider tenantRuntimeEntitlementProvider;

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void platformAdminCanAccessPricingEndpoints() throws Exception {
        UUID templateId = UUID.randomUUID();
        when(service.getPricing(any())).thenReturn(new PlanPricingResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "INR",
                BillingCycle.MONTHLY,
                "499.00",
                "4999.00",
                "0",
                null,
                TaxModel.EXCLUSIVE,
                "18",
                true,
                PricingStatus.PUBLISHED,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                UUID.randomUUID(),
                List.of(),
                List.of()
        ));
        when(service.savePricing(any(), any())).thenReturn(new PlanPricingResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "INR",
                BillingCycle.MONTHLY,
                "499.00",
                "4999.00",
                "0",
                14,
                TaxModel.EXCLUSIVE,
                "18",
                true,
                PricingStatus.PUBLISHED,
                OffsetDateTime.parse("2026-07-24T00:00:00Z"),
                UUID.randomUUID(),
                List.of(),
                List.of()
        ));
        when(service.validatePricing(any())).thenReturn(new PricingValidationResultResponse(
                ValidationState.VALID,
                true,
                0,
                0,
                List.of(),
                1,
                OffsetDateTime.parse("2026-07-24T00:00:00Z")
        ));
        when(service.comparePricing(any(), any(), any())).thenReturn(new PricingComparisonResponse(
                templateId,
                "SOLO_CLINIC",
                "Solo Clinic",
                "v1",
                "v2",
                List.of(),
                List.of(),
                List.of()
        ));

        mockMvc.perform(get("/api/platform/commercial/plan-templates/{templateId}/pricing", templateId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"trialDays\":null")));

        mockMvc.perform(put("/api/platform/commercial/plan-templates/{templateId}/pricing", templateId)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content("""
                                {"pricing":{"currency":"INR","billingCycle":"MONTHLY","monthlyPrice":"499.00","annualPrice":"4999.00","setupFee":"0","trialDays":14,"taxModel":"EXCLUSIVE","taxPercentage":"18","discountAllowed":true,"meteredRates":[],"addonPricing":[]}}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/platform/commercial/plan-templates/{templateId}/pricing/validation", templateId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/platform/commercial/plan-templates/{templateId}/pricing/compare", templateId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TENANT_ADMIN")
    void tenantAdminCannotAccessPricingEndpoints() throws Exception {
        UUID templateId = UUID.randomUUID();
        mockMvc.perform(get("/api/platform/commercial/plan-templates/{templateId}/pricing", templateId))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/platform/commercial/plan-templates/{templateId}/pricing", templateId)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content("{\"pricing\":{\"currency\":\"INR\"}}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }
}
