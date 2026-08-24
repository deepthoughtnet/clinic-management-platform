package com.deepthoughtnet.clinic.api.lab.service.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record LabOrderedTestRecord(
        UUID labOrderItemId,
        UUID labTestId,
        String testCode,
        String testName,
        String category,
        String department,
        String sampleType,
        String unit,
        String referenceRange,
        String turnaroundTime,
        BigDecimal price,
        int sortOrder,
        String state,
        int latestResultRevision,
        String latestVerificationDecision,
        OffsetDateTime latestVerificationAt,
        UUID latestVerificationBy,
        Integer latestPublicationArtifactNumber,
        OffsetDateTime latestPublicationAt,
        UUID latestPublicationBy,
        List<String> publicationChannels,
        String latestResultSnapshotJson,
        OffsetDateTime latestResultEnteredAt,
        UUID latestResultEnteredBy,
        List<LabOrderedTestSpecimenRecord> specimenLinks
) {
}
