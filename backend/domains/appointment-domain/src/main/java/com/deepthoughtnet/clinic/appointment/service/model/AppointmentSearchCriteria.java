package com.deepthoughtnet.clinic.appointment.service.model;

import java.time.LocalDate;
import java.util.UUID;

public record AppointmentSearchCriteria(
        UUID doctorUserId,
        UUID patientId,
        LocalDate appointmentDate,
        AppointmentStatus status,
        AppointmentType type
) {
}
