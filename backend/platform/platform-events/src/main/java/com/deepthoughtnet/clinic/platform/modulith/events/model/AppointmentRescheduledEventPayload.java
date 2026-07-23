package com.deepthoughtnet.clinic.platform.modulith.events.model;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPayload;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

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
