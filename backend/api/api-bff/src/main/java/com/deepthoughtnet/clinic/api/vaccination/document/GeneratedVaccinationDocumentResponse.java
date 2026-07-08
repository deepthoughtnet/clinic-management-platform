package com.deepthoughtnet.clinic.api.vaccination.document;

public record GeneratedVaccinationDocumentResponse(
        String documentId,
        String downloadUrl,
        String expiresInSeconds,
        String filename,
        String title,
        String documentNumber,
        String generatedAt,
        String generatedBy
) {
}
