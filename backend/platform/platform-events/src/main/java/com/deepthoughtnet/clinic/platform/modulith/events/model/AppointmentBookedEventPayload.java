package com.deepthoughtnet.clinic.platform.modulith.events.model;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPayload;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentBookedEventPayload(
        UUID appointmentId,
        UUID patientId,
        UUID doctorUserId,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        String appointmentStatus,
        String appointmentType
) implements ModuleBusinessEventPayload {
}
