package com.deepthoughtnet.clinic.api.lab.dto;

import jakarta.validation.constraints.NotBlank;

public record LabOrderReportDeliveryActionRequest(
        @NotBlank String action,
        String channel,
        String notes
) {
}
