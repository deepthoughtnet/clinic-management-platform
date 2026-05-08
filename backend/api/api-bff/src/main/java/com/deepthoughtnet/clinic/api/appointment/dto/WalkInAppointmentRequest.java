package com.deepthoughtnet.clinic.api.appointment.dto;

import java.time.LocalDate;
import java.util.UUID;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;

public record WalkInAppointmentRequest(
        UUID patientId,
        UUID doctorUserId,
        LocalDate appointmentDate,
        String reason,
        AppointmentPriority priority
) {
}
