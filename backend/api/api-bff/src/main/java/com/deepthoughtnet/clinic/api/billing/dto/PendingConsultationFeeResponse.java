package com.deepthoughtnet.clinic.api.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

public record PendingConsultationFeeResponse(
        String appointmentId,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        String doctorUserId,
        String doctorName,
        BigDecimal consultationFee,
        BigDecimal dueAmount,
        String paymentBypassReason,
        OffsetDateTime paymentBypassedAt
) {
}
