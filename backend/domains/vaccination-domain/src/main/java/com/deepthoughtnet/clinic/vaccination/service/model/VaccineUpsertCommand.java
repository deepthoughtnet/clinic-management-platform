package com.deepthoughtnet.clinic.vaccination.service.model;

import java.math.BigDecimal;

public record VaccineUpsertCommand(
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
        java.util.UUID inventoryItemId,
        String inventoryItemCode,
        boolean stockTrackingEnabled,
        String scheduleType,
        String ageGroup,
        Integer minAgeDays,
        Integer recommendedAgeDays,
        Integer maxAgeDays,
        Integer gapDays,
        Integer recommendedGapDays,
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
        boolean active
) {
}
