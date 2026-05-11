package com.deepthoughtnet.clinic.api.billing.dto;

import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.billing.service.model.DiscountType;
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
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal discountAmount,
        String discountReason,
        String discountApprovedBy,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal refundedAmount,
        BigDecimal netPaidAmount,
        BigDecimal dueAmount,
        OffsetDateTime invoiceEmailedAt,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<BillLineResponse> lines
) {
}
