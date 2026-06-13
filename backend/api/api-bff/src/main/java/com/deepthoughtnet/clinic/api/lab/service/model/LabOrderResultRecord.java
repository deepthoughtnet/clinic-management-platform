package com.deepthoughtnet.clinic.api.lab.service.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LabOrderResultRecord(
        UUID id,
        UUID labOrderId,
        UUID labOrderItemId,
        String testCode,
        String testName,
        String parameterName,
        String componentName,
        String resultValue,
        String unit,
        String referenceRange,
        Integer sortOrder,
        String resultFlag,
        boolean criticalResult,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
