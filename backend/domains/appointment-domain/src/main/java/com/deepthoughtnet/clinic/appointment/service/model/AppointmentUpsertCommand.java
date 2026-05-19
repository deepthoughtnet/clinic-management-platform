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
        AppointmentStatus status,
        AppointmentPriority priority,
        boolean allowAdHocBooking
) {
    public AppointmentUpsertCommand(
            UUID patientId,
            UUID doctorUserId,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            String reason,
            AppointmentType type,
            AppointmentStatus status,
            AppointmentPriority priority
    ) {
        this(patientId, doctorUserId, appointmentDate, appointmentTime, reason, type, status, priority, false);
    }
}
