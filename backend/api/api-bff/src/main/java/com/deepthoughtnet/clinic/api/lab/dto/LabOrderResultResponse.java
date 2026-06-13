package com.deepthoughtnet.clinic.api.lab.dto;

import java.time.OffsetDateTime;

public record LabOrderResultResponse(
        String id,
        String labOrderId,
        String labOrderItemId,
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
