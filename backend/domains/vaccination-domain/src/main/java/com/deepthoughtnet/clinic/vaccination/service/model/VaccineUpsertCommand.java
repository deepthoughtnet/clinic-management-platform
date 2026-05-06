package com.deepthoughtnet.clinic.vaccination.service.model;

import java.math.BigDecimal;

public record VaccineUpsertCommand(
        String vaccineName,
        String description,
        String ageGroup,
        Integer recommendedGapDays,
        BigDecimal defaultPrice,
        boolean active
) {
}
