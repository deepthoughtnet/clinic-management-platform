package com.deepthoughtnet.clinic.api.vaccination;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.api.module.runtime.TenantRuntimeEntitlementProvider;
import com.deepthoughtnet.clinic.api.module.ModuleRouteRegistry;
import com.deepthoughtnet.clinic.api.vaccination.dto.VaccineRequest;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import com.deepthoughtnet.clinic.vaccination.service.model.VaccineMasterRecord;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VaccineController.class)
@Import({
        PermissionChecker.class,
        VaccineAccessChecker.class,
        VaccineControllerSecurityIntegrationTest.MethodSecurityConfig.class
})
class VaccineControllerSecurityIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VaccinationService vaccinationService;

    @MockBean
    private VaccineCsvService vaccineCsvService;

    @MockBean
    private VaccineAccessChecker vaccineAccessChecker;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private TenantRuntimeEntitlementProvider tenantRuntimeEntitlementProvider;

    @MockBean
    private ModuleRouteRegistry moduleRouteRegistry;

    @AfterEach
    void clear() {
        RequestContextHolder.clear();
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void receptionistCannotCreateVaccineMasterRecords() throws Exception {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID actorId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("RECEPTIONIST"), "RECEPTIONIST", "cid"));
        when(vaccineAccessChecker.canManageVaccineMaster()).thenReturn(false);
        mockMvc.perform(post("/api/vaccines")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "vaccineName": "Influenza",
                                  "active": true
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(vaccinationService, never()).createVaccine(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "CLINIC_ADMIN")
    void clinicAdminCanCreateVaccineMasterRecords() throws Exception {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID actorId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));
        when(vaccineAccessChecker.canManageVaccineMaster()).thenReturn(true);

        when(vaccinationService.createVaccine(eq(tenantId), any(), eq(actorId))).thenReturn(new VaccineMasterRecord(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                tenantId,
                "Influenza",
                "Seasonal influenza vaccine",
                "Acme",
                "FluGuard",
                "FLU",
                1,
                "IM",
                "Deltoid",
                "2-8 C",
                "123",
                null,
                null,
                false,
                "ADULT",
                "Adults",
                0,
                30,
                90,
                30,
                30,
                60,
                "Annual",
                false,
                365,
                "STANDARD_ADULT",
                "NONE",
                null,
                "GENERAL",
                "seasonal",
                BigDecimal.ZERO,
                true,
                OffsetDateTime.parse("2026-08-25T00:00:00Z"),
                OffsetDateTime.parse("2026-08-25T00:00:00Z")
        ));

        mockMvc.perform(post("/api/vaccines")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "vaccineName": "Influenza",
                                  "route": "IM",
                                  "scheduleType": "ADULT",
                                  "active": true
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }
}
