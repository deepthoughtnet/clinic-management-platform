package com.deepthoughtnet.clinic.identity.service.model;

import java.util.UUID;

public record CreateTenantUserCommand(
        UUID tenantId,
        String email,
        String username,
        String displayName,
        String role,
        String tempPassword
) {
}
