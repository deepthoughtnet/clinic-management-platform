package com.deepthoughtnet.clinic.api.clinicaldocument.dto;

public record ClinicalDocumentFindingReviewDecisionRequest(
        String decision,
        String value,
        String unit,
        String referenceRange,
        String flag,
        String reviewNotes
) {}
