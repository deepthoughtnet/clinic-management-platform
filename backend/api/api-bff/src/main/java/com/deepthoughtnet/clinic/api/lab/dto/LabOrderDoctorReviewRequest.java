package com.deepthoughtnet.clinic.api.lab.dto;

import jakarta.validation.constraints.Size;

public record LabOrderDoctorReviewRequest(
        @Size(max = 4000) String comments
) {
}
