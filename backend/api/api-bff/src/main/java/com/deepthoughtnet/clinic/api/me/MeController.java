package com.deepthoughtnet.clinic.api.me;

import com.deepthoughtnet.clinic.api.me.dto.MeResponse;
import com.deepthoughtnet.clinic.identity.service.ActiveTenantMembershipService;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.model.ActiveTenantMembershipRecord;
import com.deepthoughtnet.clinic.identity.service.model.TenantModulesRecord;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.Locale;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
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
        Jwt jwt = currentJwt();
        String email = jwt == null ? null : jwt.getClaimAsString("email");
        String username = resolveUsername(jwt, ctx.keycloakSub());
        boolean platformAdmin = ctx.tokenRoles() != null && ctx.tokenRoles().contains("PLATFORM_ADMIN");

        List<ActiveTenantMembershipRecord> memberships = activeTenantMembershipService.listActiveMemberships(ctx.keycloakSub(), email);
        List<MeResponse.ActiveTenantMembershipResponse> activeMemberships = memberships.stream()
                .map(membership -> new MeResponse.ActiveTenantMembershipResponse(
                        membership.tenantId() == null ? null : membership.tenantId().toString(),
                        membership.tenantCode(),
                        membership.tenantName(),
                        membership.role(),
                        membership.status(),
                        "ACTIVE".equalsIgnoreCase(membership.status()),
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
                email,
                username,
                platformAdmin,
                ctx.tenantId() == null ? null : ctx.tenantId().value().toString(),
                ctx.appUserId() == null ? null : ctx.appUserId().toString(),
                ctx.keycloakSub(),
                ctx.tokenRoles(),
                ctx.tenantRole(),
                permissionChecker.currentPermissions(),
                currentModules,
                ctx.correlationId(),
                activeMemberships,
                activeMemberships
        );
    }

    private Jwt currentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        return principal instanceof Jwt jwt ? jwt : null;
    }

    private String resolveUsername(Jwt jwt, String fallback) {
        if (jwt == null) {
            return fallback;
        }
        String preferred = jwt.getClaimAsString("preferred_username");
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        String email = jwt.getClaimAsString("email");
        if (email != null && !email.isBlank()) {
            return email.trim().toLowerCase(Locale.ROOT);
        }
        return fallback;
    }

    private MeResponse.TenantModulesResponse toResponse(
            TenantModulesRecord modules
    ) {
        if (modules == null) {
            return null;
        }
        return new MeResponse.TenantModulesResponse(
                modules.clinicAutomation(),
                modules.clinicGeneration(),
                modules.reconciliation(),
                modules.decisioning(),
                modules.aiCopilot(),
                modules.agentIntake(),
                modules.gstFiling(),
                modules.doctorIntelligence(),
                modules.teleCalling()
        );
    }
}
