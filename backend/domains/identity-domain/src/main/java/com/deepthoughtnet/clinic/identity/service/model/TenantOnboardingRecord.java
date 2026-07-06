package com.deepthoughtnet.clinic.identity.service.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantOnboardingRecord(
        UUID id,
        UUID tenantId,
        boolean completed,
        boolean skipped,
        OffsetDateTime completedAt,
        OffsetDateTime skippedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public boolean requiresSetup() {
        return !completed;
    }
}
