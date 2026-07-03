package com.deepthoughtnet.clinic.api.lab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LabSampleRejectRequest(
        @NotBlank @Size(max = 128) String rejectionReason,
        boolean recollectionRequired,
        @Size(max = 1000) String notes
) {
}
