package com.deepthoughtnet.clinic.api.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.me.dto.MeResponse;
import com.deepthoughtnet.clinic.api.platform.service.TenantModuleService;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.identity.service.ActiveTenantMembershipService;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.model.ActiveTenantMembershipRecord;
import com.deepthoughtnet.clinic.identity.service.model.TenantModulesRecord;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

class MeControllerTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void meAutoSelectsTheOnlyActiveMembership() {
        UUID tenantId = UUID.randomUUID();
        UUID appUserId = UUID.randomUUID();
        UUID sub = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(sub.toString())
                .claim("email", "clinic.admin@clinic.local")
                .claim("preferred_username", "clinic.admin")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, jwt, List.of())
        );
        RequestContextHolder.set(new RequestContext(null, appUserId, sub.toString(), Set.of("CLINIC_ADMIN"), null, "corr-1"));

        ActiveTenantMembershipService membershipService = mock(ActiveTenantMembershipService.class);
        PlatformTenantManagementService tenantService = mock(PlatformTenantManagementService.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        TenantModuleService tenantModuleService = mock(TenantModuleService.class);
        when(permissionChecker.currentPermissions()).thenReturn(Set.of("dashboard.read"));
        when(tenantModuleService.findForTenant(tenantId)).thenReturn(java.util.Map.of("APPOINTMENTS", true, "CONSULTATION", true));
        when(membershipService.listActiveMemberships(sub.toString(), "clinic.admin@clinic.local")).thenReturn(List.of(
                new ActiveTenantMembershipRecord(
                        tenantId,
                        "clinic-a",
                        "Clinic A",
                        "CLINIC_ADMIN",
                        "ACTIVE",
                        new TenantModulesRecord(true, true, true, true, true, true, true, true, true, true)
                )
        ));

        MeController controller = new MeController(membershipService, tenantService, permissionChecker, tenantModuleService);
        MeResponse response = controller.me();

        assertThat(response.tenantId()).isEqualTo(tenantId.toString());
        assertThat(response.activeTenantMemberships()).hasSize(1);
        assertThat(response.memberships()).hasSize(1);
        assertThat(response.activeTenantMemberships().get(0).tenantId()).isEqualTo(tenantId.toString());
        assertThat(response.enabledModules()).containsEntry("APPOINTMENTS", true);
        assertThat(response.activeTenantMemberships().get(0).enabledModules()).containsEntry("CONSULTATION", true);
    }
}
