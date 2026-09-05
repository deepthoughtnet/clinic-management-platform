package com.deepthoughtnet.clinic.api.clinicaldocument.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ClinicalDocumentFindingReviewResponse(
        String documentId,
        String documentTitle,
        String reportDate,
        String mediaType,
        boolean reviewCompleted,
        String reviewedBy,
        String reviewedByDisplayName,
        OffsetDateTime reviewedAt,
        List<Finding> findings,
        int pendingCount
) {
    public record Finding(
            UUID id,
            String conceptKey,
            String findingName,
            String originalValue,
            String originalUnit,
            String originalReferenceRange,
            String value,
            String unit,
            String referenceRange,
            String flag,
            String evidenceText,
            String verificationStatus,
            String decision,
            UUID reviewedBy,
            OffsetDateTime reviewedAt
    ) {}
}
