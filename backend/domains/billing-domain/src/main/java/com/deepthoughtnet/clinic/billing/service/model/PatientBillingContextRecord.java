package com.deepthoughtnet.clinic.billing.service.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PatientBillingContextRecord(
        UUID patientId,
        String patientNumber,
        String patientName,
        BigDecimal billDueAmount,
        BigDecimal pendingConsultationFeeAmount,
        BigDecimal totalDueAmount,
        int billCount,
        List<PendingConsultationFeeRecord> pendingConsultationFees
) {
}
