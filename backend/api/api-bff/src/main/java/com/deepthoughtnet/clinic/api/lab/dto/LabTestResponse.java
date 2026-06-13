package com.deepthoughtnet.clinic.api.lab.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

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
        List<LabTestParameterResponse> parameters,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public record LabTestParameterResponse(
            String id,
            String labTestId,
            String parameterName,
            String unit,
            String normalRange,
            String criticalRange,
            int sortOrder,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}
