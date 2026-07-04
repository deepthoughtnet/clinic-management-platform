package com.deepthoughtnet.clinic.api.consultation.document;

import jakarta.validation.constraints.NotBlank;

public record ConsultationGeneratedDocumentRequest(
        @NotBlank String title,
        @NotBlank String documentType,
        @NotBlank String body,
        String language,
        String notes,
        String visibility
) {
}
