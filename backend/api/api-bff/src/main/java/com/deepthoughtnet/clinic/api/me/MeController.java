package com.deepthoughtnet.clinic.api.me;

import com.deepthoughtnet.clinic.api.me.dto.MeResponse;
import com.deepthoughtnet.clinic.identity.service.ActiveTenantMembershipService;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {
    private final ActiveTenantMembershipService activeTenantMembershipService;
    private final PlatformTenantManagementService platformTenantManagementService;
    private final PermissionChecker permissionChecker;

    public MeController(
            ActiveTenantMembershipService activeTenantMembershipService,
            PlatformTenantManagementService platformTenantManagementService,
            PermissionChecker permissionChecker
    ) {
        this.activeTenantMembershipService = activeTenantMembershipService;
        this.platformTenantManagementService = platformTenantManagementService;
        this.permissionChecker = permissionChecker;
    }

    @GetMapping("/api/me")
    @PreAuthorize("isAuthenticated()")
    public MeResponse me() {
        var ctx = RequestContextHolder.require();
        var activeMemberships = activeTenantMembershipService.listActiveMemberships(ctx.keycloakSub())
                .stream()
                .map(membership -> new MeResponse.ActiveTenantMembershipResponse(
                        membership.tenantId(),
                        membership.tenantCode(),
                        membership.tenantName(),
                        membership.role(),
                        membership.status(),
                        toResponse(membership.modules())
                ))
                .toList();
        var currentModules = activeMemberships.stream()
                .filter(membership -> ctx.tenantId() != null && ctx.tenantId().value().equals(membership.tenantId()))
                .findFirst()
                .map(MeResponse.ActiveTenantMembershipResponse::modules)
                .orElseGet(() -> {
                    if (ctx.tenantId() == null) {
                        return null;
                    }
                    try {
                        var tenant = platformTenantManagementService.get(ctx.tenantId().value());
                        return toResponse(tenant.modules());
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }
                });

        return new MeResponse(
                ctx.tenantId() == null ? null : ctx.tenantId().value(),
                ctx.appUserId(),
                ctx.keycloakSub(),
                ctx.tokenRoles(),
                ctx.tenantRole(),
                permissionChecker.currentPermissions(),
                currentModules,
                ctx.correlationId(),
                activeMemberships
        );
    }

    private MeResponse.TenantModulesResponse toResponse(
            com.deepthoughtnet.clinic.identity.service.model.TenantModulesRecord modules
    ) {
        if (modules == null) {
            return null;
        }
        return new MeResponse.TenantModulesResponse(
                modules.dashboard(),
                modules.patients(),
                modules.appointments(),
                modules.consultations(),
                modules.prescriptions(),
                modules.billing(),
                modules.vaccinations(),
                modules.inventory(),
                modules.reports()
        );
    }
}
