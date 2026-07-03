package com.deepthoughtnet.clinic.platform.core.module;

public enum SaasModuleCode {
    APPOINTMENTS,
    PATIENTS,
    CONSULTATION,
    PRESCRIPTION,
    BILLING,
    VACCINATION,
    INVENTORY,
    PHARMACY_POS,
    LABORATORY,
    REPORTS,
    AI_COPILOT,
    CAREPILOT;

    public static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }
}
