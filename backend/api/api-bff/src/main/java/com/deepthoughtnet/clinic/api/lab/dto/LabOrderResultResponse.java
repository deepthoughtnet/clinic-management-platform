package com.deepthoughtnet.clinic.api.lab.dto;

import java.time.OffsetDateTime;

public record LabOrderResultResponse(
        String id,
        String labOrderId,
        String labOrderItemId,
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
