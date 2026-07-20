package com.deepthoughtnet.clinic.identity.service;

public class TenantProvisioningException extends RuntimeException {
    private final String stage;

    public TenantProvisioningException(String stage, String message, Throwable cause) {
        super(message, cause);
        this.stage = stage;
    }

    public String stage() {
        return stage;
    }
}
