package com.deepthoughtnet.clinic.api.lab.service.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LabTestRecord(
        UUID id,
        UUID tenantId,
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
