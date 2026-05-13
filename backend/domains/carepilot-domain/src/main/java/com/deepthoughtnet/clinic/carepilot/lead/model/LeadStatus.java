package com.deepthoughtnet.clinic.carepilot.lead.model;

/** Lifecycle states for tenant-scoped leads. */
public enum LeadStatus {
    NEW,
    CONTACTED,
    QUALIFIED,
    FOLLOW_UP_REQUIRED,
    APPOINTMENT_BOOKED,
    CONVERTED,
    LOST,
    SPAM;

    public boolean isTerminal() {
        return this == CONVERTED || this == LOST || this == SPAM;
    }

    public boolean isActivePipeline() {
        return this != LOST && this != SPAM;
    }
}
