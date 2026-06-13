package com.deepthoughtnet.clinic.api.lab.service.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LabOrderResultRecord(
        UUID id,
        UUID labOrderId,
        UUID labOrderItemId,
        String testCode,
        String testName,
        String componentName,
        String resultValue,
        String unit,
        String referenceRange,
        Integer sortOrder,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
