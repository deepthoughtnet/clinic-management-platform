package com.deepthoughtnet.clinic.api.lab.dto;

import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LabOrderCreateRequest(
        @NotNull(message = "Patient is required.") UUID patientId,
        @NotEmpty(message = "Select at least one lab test.") List<UUID> testIds,
        @Size(max = 250, message = "Notes must be 250 characters or fewer.") String notes
) {
}
