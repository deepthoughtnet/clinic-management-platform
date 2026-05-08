package com.deepthoughtnet.clinic.api.clinicaldocument.ai.model;

public record ClinicalDocumentTextExtractionResult(
        String provider,
        String status,
        String text
) {
}
