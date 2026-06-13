package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.time.OffsetDateTime;

public record PatientPortalLabLatestResultResponse(
        String orderNumber,
        String testCode,
        String testName,
        String componentName,
        String resultValue,
        String unit,
        String referenceRange,
        OffsetDateTime resultDate,
        OffsetDateTime doctorReviewedAt,
        String doctorComments
) {
}
