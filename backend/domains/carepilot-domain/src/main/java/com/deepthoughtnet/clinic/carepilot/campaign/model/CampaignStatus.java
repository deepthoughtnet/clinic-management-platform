package com.deepthoughtnet.clinic.carepilot.campaign.model;

/** Lifecycle state of a CarePilot campaign. */
public enum CampaignStatus {
    DRAFT,
    PENDING_APPROVAL,
    CHANGES_REQUESTED,
    APPROVED,
    ACTIVE,
    PAUSED,
    COMPLETED,
    CANCELLED;

    public static CampaignStatus fromLegacy(String value) {
        if (value == null || value.isBlank()) {
            return DRAFT;
        }
        return switch (value.trim().toUpperCase()) {
            case "INACTIVE" -> PAUSED;
            case "ARCHIVED" -> COMPLETED;
            default -> CampaignStatus.valueOf(value.trim().toUpperCase());
        };
    }
}
