package com.deepthoughtnet.clinic.api.appointment.dto;

import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AppointmentStatusRequest(
        @NotNull AppointmentStatus status,
        @Size(max = 512) String comment
) {
}
