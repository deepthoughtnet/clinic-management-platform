package com.deepthoughtnet.clinic.appointment.service.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentRecord(
        UUID id,
        UUID tenantId,
        UUID patientId,
        String patientNumber,
        String patientName,
        UUID doctorUserId,
        String doctorName,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        Integer tokenNumber,
        String reason,
        AppointmentType type,
        AppointmentPriority priority,
        AppointmentStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
