package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record PatientPortalBillResponse(
        String billId,
        String billNumber,
        LocalDate billDate,
        String status,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal refundedAmount,
        BigDecimal dueAmount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<PatientPortalBillLineResponse> lines
) {
}
