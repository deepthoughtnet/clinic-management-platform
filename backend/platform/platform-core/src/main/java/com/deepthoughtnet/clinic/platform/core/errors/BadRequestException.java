package com.deepthoughtnet.clinic.platform.core.errors;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
