package com.deepthoughtnet.clinic.platform.core.errors;

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) { super(message); }
}
