package com.deepthoughtnet.clinic.appointment.service.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentUpsertCommand(
        UUID patientId,
        UUID doctorUserId,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        String reason,
        AppointmentType type,
        AppointmentStatus status
) {
}
