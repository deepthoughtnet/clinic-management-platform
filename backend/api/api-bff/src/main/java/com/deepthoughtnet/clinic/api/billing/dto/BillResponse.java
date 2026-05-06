package com.deepthoughtnet.clinic.api.billing.dto;

import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record BillResponse(
        String id,
        String tenantId,
        String billNumber,
        String patientId,
        String patientNumber,
        String patientName,
        String consultationId,
        String appointmentId,
        LocalDate billDate,
        BillStatus status,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal dueAmount,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<BillLineResponse> lines
) {
}
