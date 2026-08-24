package com.deepthoughtnet.clinic.api.lab.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public final class LabTestCatalogueConfigDtos {
    private LabTestCatalogueConfigDtos() {
    }

    public record LabTestCatalogueConfigResponse(
            String id,
            String tenantId,
            String testCode,
            String testName,
            String category,
            boolean enabled,
            boolean active,
            BigDecimal price,
            String turnaroundTime,
            BigDecimal tenantPriceOverride,
            String tenantTatOverride,
            Integer displayOrder
    ) {
    }

    public record LabTestCatalogueConfigUpdateRequest(
            Boolean enabled,
            Boolean active,
            @DecimalMin(value = "0.00", inclusive = true, message = "Price override must be zero or greater.")
            @DecimalMax(value = "999999.00", inclusive = true, message = "Price override exceeds the allowed maximum.")
            @Digits(integer = 6, fraction = 2, message = "Price override must have at most 2 decimal places.")
            BigDecimal tenantPriceOverride,
            @Pattern(regexp = "^\\d{1,3}$", message = "TAT override must be a whole number between 0 and 999.")
            String tenantTatOverride,
            @Min(value = 0, message = "Display order must be zero or greater.")
            Integer displayOrder
    ) {
    }
}
