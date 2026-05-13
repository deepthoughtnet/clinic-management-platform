package com.deepthoughtnet.clinic.carepilot.campaign.model;

/** Campaign intent classification for future orchestration rules. */
public enum CampaignType {
    APPOINTMENT_REMINDER,
    MISSED_APPOINTMENT_FOLLOW_UP,
    FOLLOW_UP_REMINDER,
    LEAD_FOLLOW_UP_REMINDER,
    WEBINAR_CONFIRMATION,
    WEBINAR_REMINDER,
    WEBINAR_FOLLOW_UP,
    REFILL_REMINDER,
    VACCINATION_REMINDER,
    BILLING_REMINDER,
    WELLNESS_MESSAGE,
    CUSTOM
}
