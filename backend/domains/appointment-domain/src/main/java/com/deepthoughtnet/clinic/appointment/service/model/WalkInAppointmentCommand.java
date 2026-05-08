package com.deepthoughtnet.clinic.appointment.service.model;

import java.time.LocalDate;
import java.util.UUID;

public record WalkInAppointmentCommand(
        UUID patientId,
        UUID doctorUserId,
        LocalDate appointmentDate,
        String reason,
        AppointmentPriority priority
) {
}
