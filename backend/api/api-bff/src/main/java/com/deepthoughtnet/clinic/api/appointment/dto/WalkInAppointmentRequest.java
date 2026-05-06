package com.deepthoughtnet.clinic.api.appointment.dto;

import java.time.LocalDate;
import java.util.UUID;

public record WalkInAppointmentRequest(
        UUID patientId,
        UUID doctorUserId,
        LocalDate appointmentDate,
        String reason
) {
}
