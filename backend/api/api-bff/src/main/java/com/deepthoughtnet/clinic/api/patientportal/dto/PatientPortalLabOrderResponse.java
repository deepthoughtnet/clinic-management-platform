package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PatientPortalLabOrderResponse(
        String orderNumber,
        String doctorName,
        String status,
        OffsetDateTime orderedAt,
        OffsetDateTime sampleCollectedAt,
        OffsetDateTime resultEnteredAt,
        OffsetDateTime reportGeneratedAt,
        OffsetDateTime doctorReviewedAt,
        String doctorComments,
        List<PatientPortalLabResultResponse> results
) {
}
