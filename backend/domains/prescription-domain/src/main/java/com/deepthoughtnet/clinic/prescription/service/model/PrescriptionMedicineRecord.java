package com.deepthoughtnet.clinic.prescription.service.model;

public record PrescriptionMedicineRecord(
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
