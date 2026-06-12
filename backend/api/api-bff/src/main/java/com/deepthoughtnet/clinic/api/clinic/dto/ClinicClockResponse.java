package com.deepthoughtnet.clinic.api.clinic.dto;

import java.time.OffsetDateTime;

public record ClinicClockResponse(
        String clinicTimeZone,
        OffsetDateTime clinicNow,
        OffsetDateTime serverNowUtc
) {
}
