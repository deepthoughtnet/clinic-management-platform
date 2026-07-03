package com.deepthoughtnet.clinic.api.clinicaldocument.service;

import java.math.BigDecimal;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ClinicalDocumentRecord(
        UUID id,
        UUID tenantId,
        UUID patientId,
        UUID consultationId,
        String sourceModule,
        String sourceEntityId,
        UUID uploadedByUserId,
        String uploadedByName,
        ClinicalDocumentType documentType,
        String title,
        String description,
        LocalDate reportDate,
        String uploadSource,
        String originalFilename,
        String mediaType,
        long sizeBytes,
        String checksumSha256,
        String storageBucket,
        String storageKey,
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
        UUID aiExtractionReviewedByAppUserId,
        OffsetDateTime aiExtractionReviewedAt,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
