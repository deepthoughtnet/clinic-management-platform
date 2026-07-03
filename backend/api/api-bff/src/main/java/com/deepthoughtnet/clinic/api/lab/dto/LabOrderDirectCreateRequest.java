package com.deepthoughtnet.clinic.api.lab.dto;

import com.deepthoughtnet.clinic.api.lab.db.LabOrderOrigin;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LabOrderDirectCreateRequest(
        @NotNull UUID patientId,
        @NotNull LabOrderOrigin orderOrigin,
        UUID requestedByInternalDoctorId,
        @Size(max = 256) String externalDoctorName,
        @Size(max = 32) String externalDoctorMobile,
        @Size(max = 256) String externalClinicName,
        @Size(max = 128) String referralSource,
        @NotEmpty List<UUID> testIds,
        @Size(max = 250) String notes
) {
}
