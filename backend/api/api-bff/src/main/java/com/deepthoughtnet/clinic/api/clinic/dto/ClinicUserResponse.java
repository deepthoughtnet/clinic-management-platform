package com.deepthoughtnet.clinic.api.clinic.dto;

import java.time.OffsetDateTime;

public record ClinicUserResponse(
        String appUserId,
        String tenantId,
        String keycloakSub,
        String email,
        String displayName,
        String userStatus,
        String membershipRole,
        String membershipStatus,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String provisioningStatus
) {
}
