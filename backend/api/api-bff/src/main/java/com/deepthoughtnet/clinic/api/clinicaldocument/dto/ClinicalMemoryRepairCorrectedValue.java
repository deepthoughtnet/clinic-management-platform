package com.deepthoughtnet.clinic.api.clinicaldocument.dto;

public record ClinicalMemoryRepairCorrectedValue(
        String conceptKey,
        String oldValue,
        String newValue,
        String unit
) {
}
