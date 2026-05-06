package com.deepthoughtnet.clinic.api.prescription.dto;

import com.deepthoughtnet.clinic.prescription.service.model.MedicineType;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionMedicineCommand;
import com.deepthoughtnet.clinic.prescription.service.model.Timing;

public record PrescriptionMedicineRequest(
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
    public PrescriptionMedicineCommand toCommand() {
        return new PrescriptionMedicineCommand(medicineName, medicineType, strength, dosage, frequency, duration, timing, instructions, sortOrder);
    }
}
