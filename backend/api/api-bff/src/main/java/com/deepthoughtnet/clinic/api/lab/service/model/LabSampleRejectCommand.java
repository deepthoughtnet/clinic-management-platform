package com.deepthoughtnet.clinic.api.lab.service.model;

public record LabSampleRejectCommand(
        String rejectionReason,
        boolean recollectionRequired,
        String notes
) {
}
