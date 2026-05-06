package com.deepthoughtnet.clinic.prescription.service.model;

public record PrescriptionTestRecord(
        String testName,
        String instructions,
        Integer sortOrder
) {
}
