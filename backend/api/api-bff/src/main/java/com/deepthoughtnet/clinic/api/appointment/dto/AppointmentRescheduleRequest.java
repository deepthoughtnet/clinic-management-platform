package com.deepthoughtnet.clinic.api.appointment.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AppointmentRescheduleRequest(
        UUID doctorUserId,
        @NotNull
        LocalDate appointmentDate,
        @NotNull
        LocalTime appointmentTime,
        @Size(max = 512)
        String reason
) {
}
