package com.deepthoughtnet.clinic.api.pharmacy;

import java.util.UUID;

public record MedicineImportRowResult(
        int rowNumber,
        String medicineName,
        String status,
        String message,
        UUID medicineId,
        UUID stockId
) {
}
