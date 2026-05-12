package com.deepthoughtnet.clinic.carepilot.campaign.model;

/** Campaign intent classification for future orchestration rules. */
public enum CampaignType {
    APPOINTMENT_REMINDER,
    FOLLOW_UP_REMINDER,
    REFILL_REMINDER,
    VACCINATION_REMINDER,
    BILLING_REMINDER,
    CUSTOM
}
