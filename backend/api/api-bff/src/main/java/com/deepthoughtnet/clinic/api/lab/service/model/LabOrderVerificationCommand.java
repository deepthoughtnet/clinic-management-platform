package com.deepthoughtnet.clinic.api.lab.service.model;

public record LabOrderVerificationCommand(
        String decision,
        String reason,
        String comments
) {
}
