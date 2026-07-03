package com.deepthoughtnet.clinic.api.lab.dto;

import java.time.OffsetDateTime;

public record LabSampleReceiveRequest(
        OffsetDateTime receivedAt,
        String receivedBy
) {
}
