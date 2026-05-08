package com.deepthoughtnet.clinic.api.clinicaldocument.dto;

import java.math.BigDecimal;

public record ClinicalDocumentResponse(
        String id,
        String patientId,
        String consultationId,
        String appointmentId,
        String uploadedByAppUserId,
        String documentType,
        String originalFilename,
        String mediaType,
        long sizeBytes,
        String checksumSha256,
        String notes,
        String referredDoctor,
        String referredHospital,
        String referralNotes,
        String aiExtractionStatus,
        String aiExtractionProvider,
        String aiExtractionModel,
        BigDecimal aiExtractionConfidence,
        String aiExtractionSummary,
        String aiExtractionStructuredJson,
        String aiExtractionReviewNotes,
        String aiExtractionReviewedByAppUserId,
        String aiExtractionReviewedAt,
        String ocrStatus,
        String createdAt,
        String updatedAt
) {
}
