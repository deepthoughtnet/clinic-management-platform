package com.deepthoughtnet.clinic.discover.providerownership;

public class ProviderOwnershipConflictException extends RuntimeException {
    private final String code;

    public ProviderOwnershipConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
