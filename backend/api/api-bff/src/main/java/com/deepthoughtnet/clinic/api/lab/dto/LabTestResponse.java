package com.deepthoughtnet.clinic.api.lab.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record LabTestResponse(
        String id,
        String tenantId,
        String testCode,
        String testName,
        String category,
        String department,
        String sampleType,
        String unit,
        String referenceRange,
        String turnaroundTime,
        BigDecimal price,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
