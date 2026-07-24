package com.deepthoughtnet.clinic.api.platform.commercialcatalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.platform.commercialcatalog.CommercialCatalogDtos.CapabilitySummaryResponse;
import com.deepthoughtnet.clinic.api.platform.commercialcatalog.CommercialCatalogDtos.PageResponse;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.api.module.ModuleRouteRegistry;
import com.deepthoughtnet.clinic.identity.service.TenantSubscriptionService;
import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.Status;
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
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CommercialCatalogController.class)
@Import({
        PermissionChecker.class,
        CommercialCatalogControllerSecurityTest.MethodSecurityConfig.class
})
class CommercialCatalogControllerSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommercialCatalogApiService service;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private TenantSubscriptionService tenantSubscriptionService;

    @MockBean
    private ModuleRouteRegistry moduleRouteRegistry;

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void platformAdminCanAccessCommercialCatalogEndpoints() throws Exception {
        when(service.listCapabilities(anyString(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(
                        List.of(
                                new CapabilitySummaryResponse(
                                        UUID.randomUUID(),
                                        "HEALTHCARE_CORE",
                                        "Healthcare Core",
                                        null,
                                        Status.ACTIVE,
                                        1,
                                        true,
                                        false,
                                        6
                                )
                        ),
                        0,
                        20,
                        1,
                        1
                ));

        mockMvc.perform(get("/api/platform/commercial-catalog/capabilities"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TENANT_ADMIN")
    void tenantAdminCannotAccessCommercialCatalogEndpoints() throws Exception {
        mockMvc.perform(get("/api/platform/commercial-catalog/capabilities"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void normalTenantUserCannotAccessCommercialCatalogEndpoints() throws Exception {
        mockMvc.perform(get("/api/platform/commercial-catalog/capabilities"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void unauthenticatedRequestIsDenied() throws Exception {
        mockMvc.perform(get("/api/platform/commercial-catalog/capabilities"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }
}
