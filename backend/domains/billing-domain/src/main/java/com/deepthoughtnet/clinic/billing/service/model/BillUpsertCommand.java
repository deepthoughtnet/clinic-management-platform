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
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        String notes,
        List<BillLineCommand> lines
) {
}
