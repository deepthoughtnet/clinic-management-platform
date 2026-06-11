package com.deepthoughtnet.clinic.billing.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PendingConsultationFeeRecord(
        UUID appointmentId,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        UUID doctorUserId,
        String doctorName,
        BigDecimal consultationFee,
        BigDecimal dueAmount,
        String paymentBypassReason,
        OffsetDateTime paymentBypassedAt
) {
}
