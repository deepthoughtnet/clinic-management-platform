package com.deepthoughtnet.clinic.identity.service.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantUserRecord(
        UUID appUserId,
        UUID tenantId,
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
