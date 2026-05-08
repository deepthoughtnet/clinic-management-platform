package com.deepthoughtnet.clinic.api.appointment.dto;

import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;

public record AppointmentPriorityRequest(
        AppointmentPriority priority
) {
}
