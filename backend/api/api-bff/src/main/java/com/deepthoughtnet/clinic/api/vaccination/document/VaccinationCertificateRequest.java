package com.deepthoughtnet.clinic.api.vaccination.document;

import jakarta.validation.constraints.NotBlank;

public record VaccinationCertificateRequest(
        @NotBlank String certificateType,
        String vaccinationId
) {
}
