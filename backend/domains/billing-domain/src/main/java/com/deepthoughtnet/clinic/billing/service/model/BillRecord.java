package com.deepthoughtnet.clinic.billing.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record BillRecord(
        UUID id,
        UUID tenantId,
        String billNumber,
        UUID patientId,
        String patientNumber,
        String patientName,
        UUID consultationId,
        UUID appointmentId,
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
        List<BillLineRecord> lines
) {
}
