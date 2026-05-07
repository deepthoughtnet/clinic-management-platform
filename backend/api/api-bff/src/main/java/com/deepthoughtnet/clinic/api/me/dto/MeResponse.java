package com.deepthoughtnet.clinic.api.me.dto;

import java.util.List;
import java.util.Set;

public record MeResponse(
        String email,
        String username,
        boolean platformAdmin,
        String tenantId,
        String appUserId,
        String subject,
        Set<String> tokenRoles,
        String tenantRole,
        Set<String> permissions,
        TenantModulesResponse modules,
        String correlationId,
        List<ActiveTenantMembershipResponse> memberships,
        List<ActiveTenantMembershipResponse> activeTenantMemberships
) {
    public record TenantModulesResponse(
            boolean dashboard,
            boolean patients,
            boolean appointments,
            boolean consultations,
            boolean prescriptions,
            boolean billing,
            boolean vaccinations,
            boolean inventory,
            boolean reports
    ) {}

    public record ActiveTenantMembershipResponse(
            String tenantId,
            String tenantCode,
            String tenantName,
            String role,
            String status,
            boolean active,
            TenantModulesResponse modules
    ) {}
}
