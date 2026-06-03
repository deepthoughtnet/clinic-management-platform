package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PatientPortalBillReceiptSummaryResponse(
        String receiptNumber,
        LocalDate receiptDate,
        BigDecimal amount
) {
}
