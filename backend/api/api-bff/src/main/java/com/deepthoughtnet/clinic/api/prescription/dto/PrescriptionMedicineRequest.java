package com.deepthoughtnet.clinic.api.prescription.dto;

import com.deepthoughtnet.clinic.prescription.service.model.MedicineType;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionMedicineCommand;
import com.deepthoughtnet.clinic.prescription.service.model.Timing;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrescriptionMedicineRequest(
        @NotBlank @Size(max = 256)
        String medicineName,
        MedicineType medicineType,
        @Size(max = 128)
        String strength,
        @NotBlank @Size(max = 128)
        String dosage,
        @NotBlank @Size(max = 128)
        String frequency,
        @NotBlank @Size(max = 128)
        String duration,
        Timing timing,
        @Size(max = 1000)
        String instructions,
        @Min(1) @Max(100)
        Integer sortOrder
) {
    public PrescriptionMedicineCommand toCommand() {
        return new PrescriptionMedicineCommand(medicineName, medicineType, strength, dosage, frequency, duration, timing, instructions, sortOrder);
    }
}
