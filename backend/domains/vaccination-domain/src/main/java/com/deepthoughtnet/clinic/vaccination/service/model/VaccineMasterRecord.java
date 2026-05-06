package com.deepthoughtnet.clinic.vaccination.service.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record VaccineMasterRecord(
        UUID id,
        UUID tenantId,
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
