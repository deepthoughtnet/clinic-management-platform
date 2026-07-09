package com.deepthoughtnet.clinic.api.clinicaldocument.service;

import java.math.BigDecimal;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.clinicaldocument.dto.ClinicalDocumentAiOps;
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
        ClinicalDocumentAiOps aiOps,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public ClinicalDocumentRecord(
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
        this(
                id,
                tenantId,
                patientId,
                consultationId,
                sourceModule,
                sourceEntityId,
                uploadedByUserId,
                uploadedByName,
                documentType,
                title,
                description,
                reportDate,
                uploadSource,
                originalFilename,
                mediaType,
                sizeBytes,
                checksumSha256,
                storageBucket,
                storageKey,
                visibility,
                verificationStatus,
                ocrStatus,
                aiIndexStatus,
                aiExtractionStatus,
                aiExtractionProvider,
                aiExtractionModel,
                aiExtractionConfidence,
                aiExtractionSummary,
                aiExtractionStructuredJson,
                aiExtractionReviewNotes,
                aiExtractionAcceptedJson,
                aiExtractionOverrideReason,
                aiExtractionReviewedByAppUserId,
                aiExtractionReviewedAt,
                null,
                active,
                createdAt,
                updatedAt
        );
    }

    public ClinicalDocumentRecord(
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
            Object... tail
    ) {
        this(
                id,
                tenantId,
                patientId,
                consultationId,
                sourceModule,
                sourceEntityId,
                uploadedByUserId,
                uploadedByName,
                documentType,
                title,
                description,
                reportDate,
                uploadSource,
                originalFilename,
                mediaType,
                sizeBytes,
                checksumSha256,
                storageBucket,
                storageKey,
                visibility,
                verificationStatus,
                ocrStatus,
                aiIndexStatus,
                aiExtractionStatus,
                stringAt(tail, tailBase(tail, 0)),
                stringAt(tail, tailBase(tail, 1)),
                bigDecimalAt(tail, tailBase(tail, 2)),
                stringAt(tail, tailBase(tail, 3)),
                stringAt(tail, tailBase(tail, 4)),
                stringAt(tail, tailBase(tail, 5)),
                stringAt(tail, tailBase(tail, 6)),
                stringAt(tail, tailBase(tail, 7)),
                uuidAt(tail, tailBase(tail, 8)),
                offsetDateTimeAt(tail, tailBase(tail, 9)),
                aiOpsAt(tail, tailBase(tail, 10)),
                booleanAt(tail, tailBase(tail, 11)),
                offsetDateTimeAt(tail, tailBase(tail, 12)),
                offsetDateTimeAt(tail, tailBase(tail, 13))
        );
    }

    private static int tailBase(Object[] values, int offsetFromStart) {
        int length = values == null ? 0 : values.length;
        return Math.max(0, length - 14 + offsetFromStart);
    }

    private static String stringAt(Object[] values, int index) {
        if (values == null || index < 0 || index >= values.length) {
            return null;
        }
        Object value = values[index];
        return value == null ? null : String.valueOf(value);
    }

    private static BigDecimal bigDecimalAt(Object[] values, int index) {
        if (values == null || index < 0 || index >= values.length) {
            return null;
        }
        Object value = values[index];
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return null;
    }

    private static UUID uuidAt(Object[] values, int index) {
        if (values == null || index < 0 || index >= values.length) {
            return null;
        }
        Object value = values[index];
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return null;
    }

    private static OffsetDateTime offsetDateTimeAt(Object[] values, int index) {
        if (values == null || index < 0 || index >= values.length) {
            return null;
        }
        Object value = values[index];
        if (value instanceof OffsetDateTime time) {
            return time;
        }
        return null;
    }

    private static ClinicalDocumentAiOps aiOpsAt(Object[] values, int index) {
        if (values == null || index < 0 || index >= values.length) {
            return null;
        }
        Object value = values[index];
        if (value instanceof ClinicalDocumentAiOps ops) {
            return ops;
        }
        return null;
    }

    private static boolean booleanAt(Object[] values, int index) {
        if (values == null || index < 0 || index >= values.length) {
            return false;
        }
        Object value = values[index];
        if (value instanceof Boolean bool) {
            return bool;
        }
        return false;
    }
}
