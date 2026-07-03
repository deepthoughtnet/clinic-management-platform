package com.deepthoughtnet.clinic.api.lab.dto;

import java.math.BigDecimal;

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
            BigDecimal tenantPriceOverride,
            String tenantTatOverride,
            Integer displayOrder
    ) {
    }
}
