package com.deepthoughtnet.clinic.api.lab.service.model;

import java.math.BigDecimal;

public record LabTestUpsertCommand(
        String testCode,
        String testName,
        String category,
        String department,
        String sampleType,
        String unit,
        String referenceRange,
        String turnaroundTime,
        BigDecimal price,
        boolean active
) {
}
