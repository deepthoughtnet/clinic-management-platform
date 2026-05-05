package com.deepthoughtnet.clinic.identity.service.model;

import java.util.UUID;

public record ActiveTenantMembershipRecord(
        UUID tenantId,
        String tenantCode,
        String tenantName,
        String role,
        String status,
        TenantModulesRecord modules
) {
}
