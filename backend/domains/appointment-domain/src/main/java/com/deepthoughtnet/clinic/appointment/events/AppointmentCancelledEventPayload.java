package com.deepthoughtnet.clinic.appointment.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPayload;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Immutable appointment-cancelled payload owned by the appointment module.
 */
public record AppointmentCancelledEventPayload(
        UUID appointmentId,
        UUID patientId,
        UUID doctorUserId,
        String doctorDisplayName,
        String clinicDisplayName,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        String appointmentTimezone,
        int appointmentVersion
) implements ModuleBusinessEventPayload {
}
