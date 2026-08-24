package com.deepthoughtnet.clinic.api.lab.dto;

import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LabOrderVerificationRequest(
        @NotNull VerificationDecision decision,
        List<UUID> orderedTestIds,
        Boolean recollectionRequired,
        @Size(max = 128) String reason,
        @Size(max = 1000) String comments
) {
    public enum VerificationDecision {
        APPROVE,
        SEND_BACK
    }
}
