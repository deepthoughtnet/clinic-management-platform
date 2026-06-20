package com.deepthoughtnet.clinic.api.lab.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LabOrderDoctorReviewRequest(
        @NotNull ReviewDecision decision,
        @Size(max = 60) String reason,
        @Size(max = 250) String comments
) {
    public enum ReviewDecision {
        APPROVE,
        SEND_BACK
    }
}
