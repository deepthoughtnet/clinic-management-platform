package com.deepthoughtnet.clinic.api.ai.reasoning.dto;

public record ClinicalSafetyNote(
        String message,
        String severity,
        String rationale,
        String action,
        String sourceType,
        String sourceTitle,
        String verificationStatus,
        String actionType
) {
}
