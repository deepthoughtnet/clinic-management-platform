package com.deepthoughtnet.clinic.inventory.service.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PhysicalCountSessionRecord(
        UUID id,
        UUID tenantId,
        String sessionName,
        UUID locationId,
        String locationName,
        String scope,
        String scopeLabel,
        String reason,
        String status,
        List<PhysicalCountSessionLine> lines,
        PhysicalCountAuditFields audit,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
