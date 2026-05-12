package com.deepthoughtnet.clinic.carepilot.shared.exception;

/** Exception raised when CarePilot command validation fails. */
public class CarePilotValidationException extends IllegalArgumentException {
    public CarePilotValidationException(String message) {
        super(message);
    }
}
