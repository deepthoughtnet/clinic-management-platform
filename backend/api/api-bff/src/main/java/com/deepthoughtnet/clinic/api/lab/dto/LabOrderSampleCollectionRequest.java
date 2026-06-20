package com.deepthoughtnet.clinic.api.lab.dto;

import java.time.OffsetDateTime;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LabOrderSampleCollectionRequest(
        @Size(max = 60) String sampleType,
        @Size(max = 60) String collectedBy,
        @NotNull OffsetDateTime collectedAt,
        @Size(max = 250) String notes
) {
}
