package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PatientPortalBillResponse(
        String billNumber,
        String billType,
        LocalDate billDate,
        String status,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal dueAmount,
        PatientPortalBillReceiptSummaryResponse latestReceipt,
        List<PatientPortalBillLineResponse> lines
) {
}
