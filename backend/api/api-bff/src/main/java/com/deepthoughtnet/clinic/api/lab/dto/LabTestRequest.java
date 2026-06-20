package com.deepthoughtnet.clinic.api.lab.dto;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LabTestRequest(
        @Size(max = 30) String testCode,
        @NotBlank @Size(max = 100) String testName,
        @NotBlank @Size(max = 30) String category,
        @Size(max = 60) String department,
        @Size(max = 60) String sampleType,
        @Size(max = 30) String unit,
        @Size(max = 120) String referenceRange,
        @Size(max = 30) String turnaroundTime,
        @NotNull @DecimalMin(value = "0.00", inclusive = true) @DecimalMax(value = "999999.00", inclusive = true) @Digits(integer = 6, fraction = 2) BigDecimal price,
        boolean active,
        List<LabTestParameterRequest> parameters
) {
    public record LabTestParameterRequest(
            @NotBlank @Size(max = 60) String parameterName,
            @Size(max = 30) String unit,
            @Size(max = 120) String normalRange,
            @Size(max = 120) String criticalRange,
            int sortOrder
    ) {
    }
}
