package com.deepthoughtnet.clinic.appointment.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPayload;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Immutable appointment-rescheduled payload owned by the appointment module.
 * <p>
 * The payload carries only notification-safe facts needed by downstream modules
 * so the appointment write transaction remains the source of truth.
 */
public record AppointmentRescheduledEventPayload(
        UUID appointmentId,
        UUID patientId,
        UUID doctorUserId,
        String doctorDisplayName,
        String clinicDisplayName,
        LocalDate previousAppointmentDate,
        LocalTime previousAppointmentTime,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        String appointmentTimezone,
        int appointmentVersion
) implements ModuleBusinessEventPayload {
}
