package com.deepthoughtnet.clinic.api.vaccination.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public record PatientVaccinationUpdateRequest(
        @Size(max = 256)
        String externalPlace,
        UUID proofDocumentId,
        @Size(max = 32)
        String verifiedStatus
) {
}
