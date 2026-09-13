package com.deepthoughtnet.clinic.api.patientportal.careai;

enum PatientPortalCareAiSkillOutcome {
    RUNNING,
    SUCCESS,
    NO_MATCH,
    MULTIPLE_MATCHES,
    NEEDS_INPUT,
    NEEDS_CONFIRMATION,
    NOT_AUTHORIZED,
    NOT_BOOKABLE,
    TIMEOUT,
    TEMPORARILY_UNAVAILABLE,
    CANCELLED,
    STALE,
    FAILED
}
