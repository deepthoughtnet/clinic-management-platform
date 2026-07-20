package com.deepthoughtnet.clinic.carepilot.shared.exception;

/** Exception raised when a campaign lifecycle transition conflicts with the current state. */
public class CampaignConflictException extends RuntimeException {
    public CampaignConflictException(String message) {
        super(message);
    }
}
