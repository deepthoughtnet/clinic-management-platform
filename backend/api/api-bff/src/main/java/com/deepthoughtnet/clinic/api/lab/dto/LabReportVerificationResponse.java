package com.deepthoughtnet.clinic.api.lab.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record LabReportVerificationResponse(
        boolean valid,
        String reportVerificationToken,
        String orderNumber,
        String clinicName,
        String status,
        String verificationState,
        OffsetDateTime publishedAt,
        String reportFilename,
        int versionNumber,
        String reportMode,
        String reportType,
        String reportStatus,
        String verificationUrl,
        List<java.util.UUID> includedOrderedTestIds
) {
}
