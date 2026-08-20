package com.deepthoughtnet.clinic.api.tenant.dto;

public record UpdateTenantUserProfileRequest(
        String displayName,
        String email,
        String username,
        String employeeCode,
        String mobile,
        String department,
        String role,
        boolean active
) {
}
