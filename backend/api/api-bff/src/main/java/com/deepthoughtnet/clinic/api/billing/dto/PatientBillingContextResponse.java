package com.deepthoughtnet.clinic.api.billing.dto;

import java.math.BigDecimal;
import java.util.List;

public record PatientBillingContextResponse(
        String patientId,
        String patientNumber,
        String patientName,
        BigDecimal billDueAmount,
        BigDecimal pendingConsultationFeeAmount,
        BigDecimal totalDueAmount,
        int billCount,
        List<PendingConsultationFeeResponse> pendingConsultationFees
) {
}
