package com.deepthoughtnet.clinic.api.lab.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record LabOrderItemResponse(
        String id,
        String labTestId,
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
        OffsetDateTime createdAt,
        List<LabTestResponse.LabTestParameterResponse> parameters
) {
}
