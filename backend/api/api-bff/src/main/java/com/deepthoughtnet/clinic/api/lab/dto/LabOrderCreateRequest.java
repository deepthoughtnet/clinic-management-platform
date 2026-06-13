package com.deepthoughtnet.clinic.api.lab.dto;

import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record LabOrderCreateRequest(
        @NotEmpty List<UUID> testIds,
        @Size(max = 4000) String notes
) {
}
