package com.deepthoughtnet.clinic.api.lab.service.model;

public record LabOrderResultComponentCommand(
        String parameterName,
        String componentName,
        String resultValue,
        String unit,
        String referenceRange
) {
}
