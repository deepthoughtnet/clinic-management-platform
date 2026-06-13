package com.deepthoughtnet.clinic.api.lab.service.model;

import java.time.OffsetDateTime;

public record LabOrderSampleCollectionCommand(
        String sampleType,
        String collectedBy,
        OffsetDateTime collectedAt,
        String notes
) {
}
