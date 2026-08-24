package com.deepthoughtnet.clinic.api.lab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class LabCategoryConfigDtos {
    private LabCategoryConfigDtos() {
    }

    public record LabCategoryConfigResponse(
            String categoryCode,
            String displayName,
            boolean active,
            Integer displayOrder
    ) {
    }

    public record LabCategoryConfigUpdateRequest(
            @NotBlank(message = "Display name is required.")
            @Size(max = 128, message = "Display name must be 128 characters or fewer.")
            String displayName,
            Boolean active,
            @jakarta.validation.constraints.Min(value = 0, message = "Display order must be zero or greater.")
            Integer displayOrder
    ) {
    }
}
