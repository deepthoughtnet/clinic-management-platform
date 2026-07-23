package com.deepthoughtnet.clinic.appointment.service.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentReminderSnapshot(
        UUID appointmentId,
        UUID tenantId,
        UUID patientId,
        UUID doctorUserId,
        String doctorDisplayName,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        AppointmentStatus status,
        int version,
        OffsetDateTime updatedAt
) {
}
