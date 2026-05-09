package com.deepthoughtnet.clinic.api.appointment.dto;

import java.time.LocalDate;
import java.util.UUID;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WalkInAppointmentRequest(
        @NotNull
        UUID patientId,
        @NotNull
        UUID doctorUserId,
        @NotNull
        LocalDate appointmentDate,
        @Size(max = 512)
        String reason,
        AppointmentPriority priority
) {
}
