package com.deepthoughtnet.clinic.api.lab.service.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LabSampleReceiveCommand(
        OffsetDateTime receivedAt,
        UUID receivedBy
) {
}
