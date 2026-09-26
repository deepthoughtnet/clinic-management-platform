package com.deepthoughtnet.clinic.api.patientportal.aivav2.language;

/** Bounded, language-neutral controls emitted by a language adapter. */
public enum DeterministicControl {
    GREETING,
    CONFIRMATION_POSITIVE,
    CONFIRMATION_NEGATIVE,
    SHOW_MORE_SLOTS,
    MORNING,
    AFTERNOON,
    EVENING,
    EXACT_TIME,
    ORDINAL,
    HUMAN_ASSISTANCE,
    PRESENCE_CHECK,
    REPEAT_LAST_RESPONSE,
    HOLD,
    CONTINUE,
    ABANDON_WORKFLOW,
    HELP
}
