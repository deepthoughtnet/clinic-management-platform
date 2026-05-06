package com.deepthoughtnet.clinic.api.prescription.dto;

import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionTestCommand;

public record PrescriptionTestRequest(
        String testName,
        String instructions,
        Integer sortOrder
) {
    public PrescriptionTestCommand toCommand() {
        return new PrescriptionTestCommand(testName, instructions, sortOrder);
    }
}
