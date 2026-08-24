package com.deepthoughtnet.clinic.laboratory.service.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LaboratoryResultRevisionCommand(
        UUID labOrderItemId,
        UUID sourceResultId,
        int revisionNumber,
        String resultSnapshotJson,
        String comments,
        String submissionState,
        OffsetDateTime enteredAt,
        UUID enteredBy
) {
}
