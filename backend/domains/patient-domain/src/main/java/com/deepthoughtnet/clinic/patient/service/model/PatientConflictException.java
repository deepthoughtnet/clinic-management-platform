package com.deepthoughtnet.clinic.patient.service.model;

public class PatientConflictException extends RuntimeException {
    public PatientConflictException(String message) {
        super(message);
    }

    public PatientConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
