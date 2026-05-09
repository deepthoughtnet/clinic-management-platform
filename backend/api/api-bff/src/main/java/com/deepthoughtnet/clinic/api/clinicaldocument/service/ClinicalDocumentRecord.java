package com.deepthoughtnet.clinic.api.clinicaldocument.service;

import java.math.BigDecimal;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ClinicalDocumentRecord(
        UUID id,
        UUID tenantId,
        UUID patientId,
        UUID consultationId,
        UUID appointmentId,
        UUID uploadedByAppUserId,
        ClinicalDocumentType documentType,
        String originalFilename,
        String mediaType,
        long sizeBytes,
        String checksumSha256,
        String storageKey,
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
        String aiExtractionAcceptedJson,
        String aiExtractionOverrideReason,
        UUID aiExtractionReviewedByAppUserId,
        OffsetDateTime aiExtractionReviewedAt,
        String ocrStatus,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
