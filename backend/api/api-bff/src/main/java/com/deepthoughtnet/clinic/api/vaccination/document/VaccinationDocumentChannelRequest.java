package com.deepthoughtnet.clinic.api.vaccination.document;

import jakarta.validation.constraints.NotBlank;

public record VaccinationDocumentChannelRequest(
        @NotBlank String channel
) {
}
