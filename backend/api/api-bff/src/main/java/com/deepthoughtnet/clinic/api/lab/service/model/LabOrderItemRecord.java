package com.deepthoughtnet.clinic.api.lab.service.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LabOrderItemRecord(
        UUID id,
        UUID labTestId,
        String testCode,
        String testName,
        String category,
        String department,
        String sampleType,
        String unit,
        String referenceRange,
        String turnaroundTime,
        BigDecimal price,
        int sortOrder,
        OffsetDateTime createdAt
) {
}
