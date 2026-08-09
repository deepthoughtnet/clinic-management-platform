package com.deepthoughtnet.clinic.identity.service.model;

import java.util.UUID;

public record UpdateTenantUserProfileCommand(
        UUID tenantId,
        UUID appUserId,
        String displayName,
        String email,
        String username,
        String employeeCode,
        String mobile,
        String department,
        String role,
        Boolean active
) {
}
