package com.deepthoughtnet.clinic.laboratory.service.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LaboratorySpecimenLinkView(
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
