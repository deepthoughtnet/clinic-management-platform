package com.deepthoughtnet.clinic.api.patientportal.careai;

public enum PatientPortalCareAiIntent {
    BOOK_APPOINTMENT,
    RESCHEDULE_APPOINTMENT,
    CANCEL_APPOINTMENT,
    CHECK_APPOINTMENT,
    FIND_DOCTOR,
    FIND_CLINIC,
    RESET_CONVERSATION,
    GREETING,
    SMALL_TALK,
    UNKNOWN,
    APPOINTMENT_STATUS;

    public static PatientPortalCareAiIntent normalize(PatientPortalCareAiIntent intent) {
        if (intent == null) {
            return null;
        }
        return intent == APPOINTMENT_STATUS ? CHECK_APPOINTMENT : intent;
    }

    public static PatientPortalCareAiIntent parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return normalize(PatientPortalCareAiIntent.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean isWorkflowIntent() {
        return this == BOOK_APPOINTMENT
                || this == RESCHEDULE_APPOINTMENT
                || this == CANCEL_APPOINTMENT
                || this == CHECK_APPOINTMENT
                || this == APPOINTMENT_STATUS;
    }
}
