package com.deepthoughtnet.clinic.platform.providerintegration.service;

public final class ProviderConnectionConflictException extends RuntimeException {
    private final String code;

    public ProviderConnectionConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
