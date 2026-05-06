package com.deepthoughtnet.clinic.prescription.service.model;

public record PrescriptionMedicineCommand(
        String medicineName,
        MedicineType medicineType,
        String strength,
        String dosage,
        String frequency,
        String duration,
        Timing timing,
        String instructions,
        Integer sortOrder
) {
}
