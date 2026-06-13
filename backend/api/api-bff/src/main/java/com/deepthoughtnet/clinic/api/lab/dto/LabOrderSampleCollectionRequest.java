package com.deepthoughtnet.clinic.api.lab.dto;

import java.time.OffsetDateTime;
import jakarta.validation.constraints.Size;

public record LabOrderSampleCollectionRequest(
        @Size(max = 128) String sampleType,
        @Size(max = 256) String collectedBy,
        OffsetDateTime collectedAt,
        @Size(max = 4000) String notes
) {
}
