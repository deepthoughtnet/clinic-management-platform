package com.deepthoughtnet.clinic.api.lab.dto;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LabTestRequest(
        @NotBlank(message = "Test code is required.")
        @Pattern(regexp = "^\\s*[A-Za-z0-9/_-]{1,30}\\s*$", message = "Test code must be 30 characters or fewer and may contain letters, numbers, dash, slash, or underscore.")
        String testCode,
        @NotBlank(message = "Test name is required.")
        @Size(max = 100, message = "Test name must be 100 characters or fewer.")
        @Pattern(regexp = "^\\s*.*[A-Za-z0-9].*\\s*$", message = "Test name must contain letters or numbers.")
        String testName,
        @NotBlank(message = "Category is required.")
        @Size(max = 30, message = "Category must be 30 characters or fewer.")
        String category,
        @Size(max = 60, message = "Department must be 60 characters or fewer.")
        String department,
        @Size(max = 60, message = "Sample type must be 60 characters or fewer.")
        String sampleType,
        @Size(max = 30, message = "Unit must be 30 characters or fewer.")
        String unit,
        @Size(max = 120, message = "Reference range must be 120 characters or fewer.")
        String referenceRange,
        @Pattern(regexp = "^\\d{1,3}$", message = "Turnaround time must be a whole number between 0 and 999.")
        String turnaroundTime,
        @NotNull(message = "Price is required.")
        @DecimalMin(value = "0.00", inclusive = true, message = "Price must be zero or greater.")
        @DecimalMax(value = "999999.00", inclusive = true, message = "Price exceeds the allowed maximum.")
        @Digits(integer = 6, fraction = 2, message = "Price must have at most 2 decimal places.")
        BigDecimal price,
        boolean active,
        List<LabTestParameterRequest> parameters
) {
    public record LabTestParameterRequest(
            @NotBlank(message = "Parameter name is required.")
            @Size(max = 60, message = "Parameter name must be 60 characters or fewer.")
            String parameterName,
            @Size(max = 30, message = "Unit must be 30 characters or fewer.")
            String unit,
            @Size(max = 120, message = "Normal range must be 120 characters or fewer.")
            String normalRange,
            @Size(max = 120, message = "Critical range must be 120 characters or fewer.")
            String criticalRange,
            int sortOrder
    ) {
    }
}
