package com.deepthoughtnet.clinic.api.lab.service.model;

import java.math.BigDecimal;
import java.util.List;

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
        boolean active,
        List<LabTestParameterUpsertCommand> parameters
) {
}
