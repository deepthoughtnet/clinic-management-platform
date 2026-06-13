package com.deepthoughtnet.clinic.api.lab.service.model;

public record LabTestParameterUpsertCommand(
        String parameterName,
        String unit,
        String normalRange,
        String criticalRange,
        int sortOrder
) {
}
