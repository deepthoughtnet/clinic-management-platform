package com.deepthoughtnet.clinic.api.consultation.document;

public record GeneratedConsultationDocumentResponse(
        String documentId,
        String downloadUrl,
        String expiresInSeconds,
        String filename,
        String title,
        String documentType
) {
}
