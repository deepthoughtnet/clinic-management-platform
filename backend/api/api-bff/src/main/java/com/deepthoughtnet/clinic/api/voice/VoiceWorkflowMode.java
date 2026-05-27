package com.deepthoughtnet.clinic.api.voice;

import java.util.Locale;

enum VoiceWorkflowMode {
    GENERIC("generic"),
    APPOINTMENT_BOOKING("appointment-booking");

    private final String configValue;

    VoiceWorkflowMode(String configValue) {
        this.configValue = configValue;
    }

    String configValue() {
        return configValue;
    }

    static VoiceWorkflowMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return GENERIC;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if ("appointment".equals(normalized) || "appointment-booking".equals(normalized)) {
            return APPOINTMENT_BOOKING;
        }
        return GENERIC;
    }
}
