package com.deepthoughtnet.clinic.api.lab.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LabTestRequest(
        @NotBlank @Size(max = 64) String testCode,
        @NotBlank @Size(max = 256) String testName,
        @NotBlank @Size(max = 128) String category,
        @Size(max = 128) String department,
        @Size(max = 128) String sampleType,
        @Size(max = 64) String unit,
        @Size(max = 256) String referenceRange,
        @Size(max = 128) String turnaroundTime,
        @NotNull BigDecimal price,
        boolean active
) {
}
