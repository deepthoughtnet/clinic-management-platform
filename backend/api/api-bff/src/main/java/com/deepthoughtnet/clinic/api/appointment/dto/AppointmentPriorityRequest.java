package com.deepthoughtnet.clinic.api.appointment.dto;

import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import jakarta.validation.constraints.NotNull;

public record AppointmentPriorityRequest(
        @NotNull
        AppointmentPriority priority
) {
}
