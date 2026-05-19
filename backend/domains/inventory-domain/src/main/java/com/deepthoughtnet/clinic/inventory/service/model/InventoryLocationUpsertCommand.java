package com.deepthoughtnet.clinic.inventory.service.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InventoryLocationUpsertCommand(
        @NotBlank
        @Size(max = 256)
        String locationName,
        @Size(max = 64)
        String locationCode,
        @NotBlank
        @Size(max = 32)
        String locationType,
        boolean defaultLocation,
        boolean active
) {
}
