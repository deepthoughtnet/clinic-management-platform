package com.deepthoughtnet.clinic.discover.reference;

public class InvalidReferenceValueException extends RuntimeException {
    private final String code;
    private final String field;

    public InvalidReferenceValueException(String field, String message) {
        super(message);
        this.code = "INVALID_REFERENCE_VALUE";
        this.field = field;
    }

    public String code() {
        return code;
    }

    public String field() {
        return field;
    }
}
