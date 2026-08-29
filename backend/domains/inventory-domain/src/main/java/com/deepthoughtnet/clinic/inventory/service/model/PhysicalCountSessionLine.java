package com.deepthoughtnet.clinic.inventory.service.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record PhysicalCountSessionLine(
        @NotBlank
        @Size(max = 120)
        String id,
        @NotNull
        UUID medicineId,
        @NotBlank
        @Size(max = 256)
        String medicineName,
        @NotBlank
        @Size(max = 128)
        String batchNumber,
        @NotNull
        UUID locationId,
        @NotBlank
        @Size(max = 256)
        String locationName,
        @NotNull
        UUID stockBatchId,
        int systemQty,
        @Size(max = 32)
        String countedQty,
        @Size(max = 500)
        String reason,
        @Size(max = 500)
        String reviewerRemarks,
        boolean flagged,
        boolean reviewed
) {
}
