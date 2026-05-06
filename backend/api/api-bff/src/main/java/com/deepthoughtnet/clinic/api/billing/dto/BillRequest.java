package com.deepthoughtnet.clinic.api.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BillRequest(
        UUID patientId,
        UUID consultationId,
        UUID appointmentId,
        LocalDate billDate,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        String notes,
        List<BillLineRequest> lines
) {
}
