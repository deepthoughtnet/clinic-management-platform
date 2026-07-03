package com.deepthoughtnet.clinic.api.lab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record LabSampleCollectionRequest(
        String labOrderItemId,
        @NotBlank @Size(max = 128) String specimenType,
        @Size(max = 128) String containerType,
        OffsetDateTime collectedAt,
        String collectedBy,
        @Size(max = 1000) String notes
) {
}
