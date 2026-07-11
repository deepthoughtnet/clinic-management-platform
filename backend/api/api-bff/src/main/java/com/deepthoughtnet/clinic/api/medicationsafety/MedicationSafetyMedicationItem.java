package com.deepthoughtnet.clinic.api.medicationsafety;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MedicationSafetyMedicationItem(
        String prescriptionItemId,
        String medicineId,
        String medicineName,
        String normalizedMedicineName,
        List<String> activeIngredients,
        String therapeuticClass,
        String strength,
        String strengthUnit,
        String dose,
        String doseUnit,
        String frequency,
        String duration,
        String timing,
        String indication,
        boolean prn,
        String source,
        String status,
        String verificationStatus,
        BigDecimal confidence,
        String sourceDocumentId,
        String sourceDocumentTitle,
        LocalDate sourceDocumentDate
) {
}
