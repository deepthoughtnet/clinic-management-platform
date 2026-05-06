package com.deepthoughtnet.clinic.api.vaccination.dto;

import java.math.BigDecimal;

public record VaccineRequest(
        String vaccineName,
        String description,
        String ageGroup,
        Integer recommendedGapDays,
        BigDecimal defaultPrice,
        boolean active
) {
}
