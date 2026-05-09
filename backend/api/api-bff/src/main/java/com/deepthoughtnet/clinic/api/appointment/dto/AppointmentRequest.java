package com.deepthoughtnet.clinic.api.appointment.dto;

import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AppointmentRequest(
        @NotNull
        UUID patientId,
        @NotNull
        UUID doctorUserId,
        @NotNull
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        @Size(max = 512)
        String reason,
        @NotNull
        AppointmentType type,
        AppointmentStatus status,
        AppointmentPriority priority
) {
}
