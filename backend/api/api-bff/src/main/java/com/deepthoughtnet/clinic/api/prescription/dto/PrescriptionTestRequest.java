package com.deepthoughtnet.clinic.api.prescription.dto;

import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionTestCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrescriptionTestRequest(
        @NotBlank @Size(max = 256)
        String testName,
        @Size(max = 1000)
        String instructions,
        Integer sortOrder
) {
    public PrescriptionTestCommand toCommand() {
        return new PrescriptionTestCommand(testName, instructions, sortOrder);
    }
}
