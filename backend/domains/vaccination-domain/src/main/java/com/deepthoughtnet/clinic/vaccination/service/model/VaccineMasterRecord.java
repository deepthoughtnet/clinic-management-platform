package com.deepthoughtnet.clinic.vaccination.service.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record VaccineMasterRecord(
        UUID id,
        UUID tenantId,
        String vaccineName,
        String description,
        String manufacturer,
        String brandName,
        String vaccineGroup,
        Integer doseNumber,
        String route,
        String administrationSite,
        String storageTemperature,
        String ndcBarcode,
        String scheduleType,
        String ageGroup,
        Integer minAgeDays,
        Integer recommendedAgeDays,
        Integer maxAgeDays,
        Integer recommendedGapDays,
        Integer gapDays,
        Integer boosterGapDays,
        String boosterRules,
        boolean recurring,
        Integer recurrenceDays,
        String recommendationPolicy,
        String catchUpPolicy,
        Integer catchUpMaxAgeDays,
        String applicableAgeGroup,
        String clinicalIndications,
        BigDecimal defaultPrice,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
