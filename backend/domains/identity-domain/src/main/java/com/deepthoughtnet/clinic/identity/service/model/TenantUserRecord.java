package com.deepthoughtnet.clinic.identity.service.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantUserRecord(
        UUID appUserId,
        UUID tenantId,
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
    public TenantUserRecord(
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
        this(appUserId, tenantId, keycloakSub, email, null, null, displayName, userStatus, membershipRole, membershipStatus, null, null, null, createdAt, updatedAt, provisioningStatus);
    }

    public TenantUserRecord(
            UUID appUserId,
            UUID tenantId,
            String keycloakSub,
            String email,
            String username,
            String department,
            String displayName,
            String userStatus,
            String membershipRole,
            String membershipStatus,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String provisioningStatus
    ) {
        this(appUserId, tenantId, keycloakSub, email, username, department, displayName, userStatus, membershipRole, membershipStatus, null, null, null, createdAt, updatedAt, provisioningStatus);
    }
}
