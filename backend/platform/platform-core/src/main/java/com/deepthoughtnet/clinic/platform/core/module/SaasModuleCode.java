package com.deepthoughtnet.clinic.platform.core.module;

public enum SaasModuleCode {
    APPOINTMENTS,
    CONSULTATION,
    PRESCRIPTION,
    BILLING,
    VACCINATION,
    INVENTORY,
    AI_COPILOT,
    CAREPILOT;

    public static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }
}
