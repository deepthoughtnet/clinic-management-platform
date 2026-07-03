package com.deepthoughtnet.clinic.api.lab.service.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LabSampleRecord(
        UUID id,
        UUID labOrderId,
        UUID labOrderItemId,
        String accessionNumber,
        String barcodeValue,
        String specimenType,
        String containerType,
        LabSampleStatusRecord status,
        OffsetDateTime collectedAt,
        UUID collectedBy,
        OffsetDateTime receivedAt,
        UUID receivedBy,
        String rejectionReason,
        boolean recollectionRequired,
        String notes,
        OffsetDateTime createdAt,
        UUID createdBy,
        OffsetDateTime updatedAt,
        UUID updatedBy
) {
}
