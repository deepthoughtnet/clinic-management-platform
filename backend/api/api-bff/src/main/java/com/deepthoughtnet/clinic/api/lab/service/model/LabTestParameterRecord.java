package com.deepthoughtnet.clinic.api.lab.service.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LabTestParameterRecord(
        UUID id,
        UUID labTestId,
        String parameterName,
        String unit,
        String normalRange,
        String criticalRange,
        int sortOrder,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
