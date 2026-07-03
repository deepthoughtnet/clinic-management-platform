package com.deepthoughtnet.clinic.api.lab.dto;

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
            String displayName,
            Boolean active,
            Integer displayOrder
    ) {
    }
}
