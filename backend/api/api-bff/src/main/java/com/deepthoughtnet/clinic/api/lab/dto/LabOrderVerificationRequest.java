package com.deepthoughtnet.clinic.api.lab.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LabOrderVerificationRequest(
        @NotNull VerificationDecision decision,
        @Size(max = 128) String reason,
        @Size(max = 1000) String comments
) {
    public enum VerificationDecision {
        APPROVE,
        SEND_BACK
    }
}
