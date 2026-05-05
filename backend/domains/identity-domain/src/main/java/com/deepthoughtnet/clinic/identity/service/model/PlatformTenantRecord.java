package com.deepthoughtnet.clinic.identity.service.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PlatformTenantRecord(
        UUID id,
        String code,
        String name,
        String planId,
        String status,
        TenantModulesRecord modules,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
