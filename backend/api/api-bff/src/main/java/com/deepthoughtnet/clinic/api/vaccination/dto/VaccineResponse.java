package com.deepthoughtnet.clinic.api.vaccination.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record VaccineResponse(
        String id,
        String tenantId,
        String vaccineName,
        String description,
        String ageGroup,
        Integer recommendedGapDays,
        BigDecimal defaultPrice,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
