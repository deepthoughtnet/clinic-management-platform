package com.deepthoughtnet.clinic.platform.core.errors;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
