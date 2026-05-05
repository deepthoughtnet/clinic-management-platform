package com.deepthoughtnet.clinic.identity.exception;

public class TenantModuleDisabledException extends RuntimeException {
    public TenantModuleDisabledException(String moduleKey) {
        super("This module is not enabled for your tenant.");
    }
}
