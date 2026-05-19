package com.deepthoughtnet.clinic.inventory.service.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryLocationRecord(
        UUID id,
        UUID tenantId,
        String locationName,
        String locationCode,
        String locationType,
        boolean defaultLocation,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
