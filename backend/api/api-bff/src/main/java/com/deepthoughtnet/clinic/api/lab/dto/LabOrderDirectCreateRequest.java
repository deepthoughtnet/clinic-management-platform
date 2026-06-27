package com.deepthoughtnet.clinic.api.lab.dto;

import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LabOrderDirectCreateRequest(
        @NotNull UUID patientId,
        UUID doctorUserId,
        @NotEmpty List<UUID> testIds,
        @Size(max = 250) String notes
) {
}
