package com.deepthoughtnet.clinic.api.appointment.dto;

import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

public record AppointmentResponse(
        String id,
        String tenantId,
        String patientId,
        String patientNumber,
        String patientName,
        String patientMobile,
        String doctorUserId,
        String doctorName,
        String consultationId,
        String consultationFeeStatus,
        BigDecimal consultationFeeAmount,
        BigDecimal consultationFeePaidAmount,
        BigDecimal consultationFeeDueAmount,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        Integer tokenNumber,
        String displayReference,
        String reason,
        AppointmentType type,
        AppointmentPriority priority,
        AppointmentStatus status,
        String paymentBypassReason,
        String paymentBypassNotes,
        String paymentBypassedBy,
        OffsetDateTime paymentBypassedAt,
        String clinicalIntakeStatus,
        String clinicalIntakeChiefComplaint,
        String clinicalIntakeRecordedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
