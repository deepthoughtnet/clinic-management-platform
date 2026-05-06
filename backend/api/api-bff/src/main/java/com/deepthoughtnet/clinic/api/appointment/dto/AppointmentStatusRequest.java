package com.deepthoughtnet.clinic.api.appointment.dto;

import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;

public record AppointmentStatusRequest(AppointmentStatus status) {
}
