package com.deepthoughtnet.clinic.carepilot.shared.util;

import com.deepthoughtnet.clinic.carepilot.shared.exception.CarePilotValidationException;
import java.util.UUID;

/** Shared validation helpers for CarePilot tenant-scoped services. */
public final class CarePilotValidators {
    private CarePilotValidators() {}

    public static void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new CarePilotValidationException("tenantId is required");
        }
    }

    public static void requireId(UUID value, String field) {
        if (value == null) {
            throw new CarePilotValidationException(field + " is required");
        }
    }

    public static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new CarePilotValidationException(field + " is required");
        }
    }
}
