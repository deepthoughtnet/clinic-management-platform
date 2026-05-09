package com.deepthoughtnet.clinic.appointment.service.model;

public record AppointmentStatusUpdateCommand(AppointmentStatus status, String comment) {
}
