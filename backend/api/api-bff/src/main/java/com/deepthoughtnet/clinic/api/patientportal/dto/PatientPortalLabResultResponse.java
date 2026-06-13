package com.deepthoughtnet.clinic.api.patientportal.dto;

public record PatientPortalLabResultResponse(
        String testCode,
        String testName,
        String componentName,
        String resultValue,
        String unit,
        String referenceRange
) {
}
