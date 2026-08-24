package com.deepthoughtnet.clinic.api.lab.service.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LabOrderedTestSpecimenRecord(
        UUID labOrderSampleId,
        String accessionNumber,
        String barcodeValue,
        String specimenType,
        String containerType,
        String sampleStatus,
        boolean active,
        OffsetDateTime collectedAt,
        OffsetDateTime receivedAt,
        OffsetDateTime linkedAt,
        OffsetDateTime unlinkedAt
) {
}
