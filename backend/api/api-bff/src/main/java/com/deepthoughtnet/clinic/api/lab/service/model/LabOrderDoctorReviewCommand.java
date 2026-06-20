package com.deepthoughtnet.clinic.api.lab.service.model;

public record LabOrderDoctorReviewCommand(
        String decision,
        String reason,
        String doctorComments
) {
}
