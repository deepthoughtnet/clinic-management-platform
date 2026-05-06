package com.deepthoughtnet.clinic.api.appointment.dto;

import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentRequest(
        UUID patientId,
        UUID doctorUserId,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        String reason,
        AppointmentType type,
        AppointmentStatus status
) {
}
