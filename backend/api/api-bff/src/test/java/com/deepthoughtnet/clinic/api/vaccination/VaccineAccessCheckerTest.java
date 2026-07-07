package com.deepthoughtnet.clinic.api.vaccination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class VaccineAccessCheckerTest {
    @AfterEach
    void clear() {
        RequestContextHolder.clear();
    }

    @Test
    void receptionistCannotManageVaccineMaster() {
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        VaccineAccessChecker accessChecker = new VaccineAccessChecker(permissionChecker);
        RequestContextHolder.set(new RequestContext(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), "sub", Set.of("RECEPTIONIST"), "RECEPTIONIST", "cid"));

        when(permissionChecker.hasAnyRole("CLINIC_ADMIN", "TENANT_ADMIN", "VACCINE_MASTER_MANAGER")).thenReturn(false);
        when(permissionChecker.hasRole("PLATFORM_ADMIN")).thenReturn(false);

        assertThat(accessChecker.canManageVaccineMaster()).isFalse();
    }

    @Test
    void clinicAdminCanManageVaccineMaster() {
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        VaccineAccessChecker accessChecker = new VaccineAccessChecker(permissionChecker);
        RequestContextHolder.set(new RequestContext(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));

        when(permissionChecker.hasAnyRole("CLINIC_ADMIN", "TENANT_ADMIN", "VACCINE_MASTER_MANAGER")).thenReturn(true);

        assertThat(accessChecker.canManageVaccineMaster()).isTrue();
    }

    @Test
    void platformAdminRequiresSelectedTenant() {
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        VaccineAccessChecker accessChecker = new VaccineAccessChecker(permissionChecker);
        RequestContextHolder.set(new RequestContext(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), "sub", Set.of("PLATFORM_ADMIN"), "PLATFORM_ADMIN", "cid"));

        when(permissionChecker.hasAnyRole("CLINIC_ADMIN", "TENANT_ADMIN", "VACCINE_MASTER_MANAGER")).thenReturn(false);
        when(permissionChecker.hasRole("PLATFORM_ADMIN")).thenReturn(true);

        assertThat(accessChecker.canManageVaccineMaster()).isTrue();
    }
}
