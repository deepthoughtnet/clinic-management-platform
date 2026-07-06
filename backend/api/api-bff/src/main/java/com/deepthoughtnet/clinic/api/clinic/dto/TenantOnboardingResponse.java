package com.deepthoughtnet.clinic.api.clinic.dto;

import java.time.OffsetDateTime;

public record TenantOnboardingResponse(
        String tenantId,
        boolean completed,
        boolean skipped,
        OffsetDateTime completedAt,
        OffsetDateTime skippedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        boolean requiresSetup
) {
}
