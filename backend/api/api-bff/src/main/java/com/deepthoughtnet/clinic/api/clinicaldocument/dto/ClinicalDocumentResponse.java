package com.deepthoughtnet.clinic.api.clinicaldocument.dto;

import java.math.BigDecimal;

public record ClinicalDocumentResponse(
        String id,
        String patientId,
        String consultationId,
        String sourceModule,
        String sourceEntityId,
        String uploadedByUserId,
        String uploadedByName,
        String documentType,
        String title,
        String description,
        String reportDate,
        String uploadSource,
        String originalFilename,
        String mediaType,
        long sizeBytes,
        String checksumSha256,
        String storageBucket,
        String storageObjectKey,
        String visibility,
        String verificationStatus,
        String ocrStatus,
        String aiIndexStatus,
        String aiExtractionStatus,
        String aiExtractionProvider,
        String aiExtractionModel,
        BigDecimal aiExtractionConfidence,
        String aiExtractionSummary,
        String aiExtractionStructuredJson,
        String aiExtractionReviewNotes,
        String aiExtractionAcceptedJson,
        String aiExtractionOverrideReason,
        String aiExtractionReviewedByAppUserId,
        String aiExtractionReviewedAt,
        Boolean active,
        String createdAt,
        String updatedAt
) {
}
