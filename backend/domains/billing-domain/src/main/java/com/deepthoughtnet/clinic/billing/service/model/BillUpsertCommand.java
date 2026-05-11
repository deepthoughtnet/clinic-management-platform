package com.deepthoughtnet.clinic.billing.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BillUpsertCommand(
        UUID patientId,
        UUID consultationId,
        UUID appointmentId,
        LocalDate billDate,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal discountAmount,
        String discountReason,
        UUID discountApprovedBy,
        BigDecimal taxAmount,
        String notes,
        List<BillLineCommand> lines
) {
}
