package com.deepthoughtnet.clinic.api.lab.service.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LabSampleCollectionCommand(
        UUID labOrderItemId,
        String specimenType,
        String containerType,
        OffsetDateTime collectedAt,
        UUID collectedBy,
        String notes
) {
}
