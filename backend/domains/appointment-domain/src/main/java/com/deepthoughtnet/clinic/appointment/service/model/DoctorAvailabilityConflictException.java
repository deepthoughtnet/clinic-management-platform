package com.deepthoughtnet.clinic.appointment.service.model;

public class DoctorAvailabilityConflictException extends RuntimeException {
    public DoctorAvailabilityConflictException(String message) {
        super(message);
    }
}
