package com.deepthoughtnet.clinic.appointment.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPayload;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Immutable appointment-booked payload owned by the appointment module.
 * <p>
 * The platform event layer serializes this payload as opaque JSON; it must remain
 * stable and free of JPA or provider-specific types so the business fact stays
 * decoupled from the event infrastructure.
 */
public record AppointmentBookedEventPayload(
        java.util.UUID appointmentId,
        java.util.UUID patientId,
        java.util.UUID doctorUserId,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        String appointmentStatus,
        String appointmentType
) implements ModuleBusinessEventPayload {
}
