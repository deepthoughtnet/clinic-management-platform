package com.deepthoughtnet.clinic.platform.core.context;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed tenant identifier used across the platform.
 */
public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "tenantId");
    }

    public static TenantId of(UUID value) {
        return new TenantId(value);
    }

    public static TenantId parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return new TenantId(UUID.fromString(raw.trim()));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
