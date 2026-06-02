package com.deepthoughtnet.clinic.api.patientportal.dto;

public record PatientPortalPrescriptionMedicineResponse(
        String medicineName,
        String medicineType,
        String strength,
        String dosage,
        String frequency,
        String duration,
        String timing,
        String instructions,
        Integer sortOrder
) {
}
