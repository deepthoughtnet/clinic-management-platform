package com.deepthoughtnet.clinic.prescription.service.model;

public record PrescriptionTestCommand(
        String testName,
        String instructions,
        Integer sortOrder
) {
}
