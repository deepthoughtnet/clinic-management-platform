package com.deepthoughtnet.clinic.carepilot.lead.activity.model;

/** Append-only activity types for lead timeline. */
public enum LeadActivityType {
    CREATED,
    UPDATED,
    STATUS_CHANGED,
    NOTE_ADDED,
    FOLLOW_UP_SCHEDULED,
    FOLLOW_UP_COMPLETED,
    CONVERTED_TO_PATIENT,
    APPOINTMENT_BOOKED,
    CAMPAIGN_LINKED,
    LOST,
    SPAM_MARKED
    ;

    public int lifecyclePriority() {
        return switch (this) {
            case CREATED -> 10;
            case CAMPAIGN_LINKED -> 20;
            case UPDATED -> 30;
            case NOTE_ADDED -> 40;
            case FOLLOW_UP_SCHEDULED -> 50;
            case STATUS_CHANGED, LOST, SPAM_MARKED -> 60;
            case FOLLOW_UP_COMPLETED -> 70;
            case APPOINTMENT_BOOKED -> 80;
            case CONVERTED_TO_PATIENT -> 90;
        };
    }
}
