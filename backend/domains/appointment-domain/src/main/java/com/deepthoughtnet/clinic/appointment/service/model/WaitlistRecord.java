package com.deepthoughtnet.clinic.appointment.service.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WaitlistRecord(
        UUID id,
        UUID tenantId,
        UUID patientId,
        String patientNumber,
        String patientName,
        UUID doctorUserId,
        String doctorName,
        LocalDate preferredDate,
        LocalTime preferredStartTime,
        LocalTime preferredEndTime,
        String reason,
        String notes,
        WaitlistStatus status,
        UUID bookedAppointmentId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
