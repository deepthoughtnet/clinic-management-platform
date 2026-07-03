package com.deepthoughtnet.clinic.api.lab.dto;

import com.deepthoughtnet.clinic.api.lab.service.model.LabSampleStatusRecord;
import java.time.OffsetDateTime;

public record LabSampleResponse(
        String id,
        String labOrderId,
        String labOrderItemId,
        String accessionNumber,
        String barcodeValue,
        String specimenType,
        String containerType,
        LabSampleStatusRecord status,
        OffsetDateTime collectedAt,
        String collectedBy,
        OffsetDateTime receivedAt,
        String receivedBy,
        String rejectionReason,
        boolean recollectionRequired,
        String notes
) {
}
