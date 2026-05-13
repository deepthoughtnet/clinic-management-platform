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
}
