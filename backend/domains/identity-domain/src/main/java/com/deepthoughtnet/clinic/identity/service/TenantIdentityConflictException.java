package com.deepthoughtnet.clinic.identity.service;

import java.util.List;

public class TenantIdentityConflictException extends RuntimeException {
    private final List<IdentityConflict> conflicts;

    public TenantIdentityConflictException(String message, List<IdentityConflict> conflicts) {
        super(message);
        this.conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }

    public List<IdentityConflict> conflicts() {
        return conflicts;
    }

    public static TenantIdentityConflictException of(List<IdentityConflict> conflicts) {
        return new TenantIdentityConflictException("One or more authentication identifiers are already in use.", conflicts);
    }

    public enum Field {
        USERNAME,
        EMAIL
    }

    public record IdentityConflict(Field field, String code, String message) {
    }
}
