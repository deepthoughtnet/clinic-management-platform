package com.deepthoughtnet.clinic.api.vaccination;

import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import org.springframework.stereotype.Component;

@Component("vaccineAccessChecker")
public class VaccineAccessChecker {
    private final PermissionChecker permissionChecker;

    public VaccineAccessChecker(PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    public boolean canManageVaccineMaster() {
        return permissionChecker.hasAnyRole("CLINIC_ADMIN", "TENANT_ADMIN", "VACCINE_MASTER_MANAGER")
                || (permissionChecker.hasRole("PLATFORM_ADMIN") && RequestContextHolder.get() != null && RequestContextHolder.get().tenantId() != null);
    }
}
