package com.deepthoughtnet.clinic.identity.service.model;

import java.util.UUID;

public record CreateTenantUserCommand(
        UUID tenantId,
        String email,
        String username,
        String firstName,
        String lastName,
        String displayName,
        String role,
        String tempPassword,
        String employeeCode,
        String mobile,
        String department
) {
    public CreateTenantUserCommand(
            UUID tenantId,
            String email,
            String username,
            String displayName,
            String role,
            String tempPassword
    ) {
        this(tenantId, email, username, null, null, displayName, role, tempPassword, null, null, null);
    }

    public CreateTenantUserCommand(
            UUID tenantId,
            String email,
            String username,
            String firstName,
            String lastName,
            String displayName,
            String role,
            String tempPassword
    ) {
        this(tenantId, email, username, firstName, lastName, displayName, role, tempPassword, null, null, null);
    }
}
