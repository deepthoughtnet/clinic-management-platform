package com.deepthoughtnet.clinic.patient.service.model;

public class PatientPortalAccessRequestConflictException extends RuntimeException {
    public PatientPortalAccessRequestConflictException(String message) {
        super(message);
    }

    public PatientPortalAccessRequestConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
