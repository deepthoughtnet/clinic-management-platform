package com.deepthoughtnet.clinic.api.clinicaldocument.dto;

public record AiExtractionReviewRequest(
        boolean approved,
        boolean saveToPatientHistory,
        String reviewNotes
) {
}
