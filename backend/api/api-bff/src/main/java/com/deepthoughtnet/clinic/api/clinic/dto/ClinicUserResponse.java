package com.deepthoughtnet.clinic.api.clinic.dto;

import java.time.OffsetDateTime;

public record ClinicUserResponse(
        String appUserId,
        String tenantId,
        String keycloakSub,
        String email,
        String username,
        String department,
        String displayName,
        String userStatus,
        String membershipRole,
        String membershipStatus,
        String employeeCode,
        String mobile,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String provisioningStatus
) {
}
