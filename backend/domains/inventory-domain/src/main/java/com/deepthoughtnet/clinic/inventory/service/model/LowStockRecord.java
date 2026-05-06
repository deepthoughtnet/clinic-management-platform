package com.deepthoughtnet.clinic.inventory.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LowStockRecord(
        UUID stockId,
        UUID medicineId,
        String medicineName,
        String medicineType,
        String batchNumber,
        LocalDate expiryDate,
        int quantityOnHand,
        Integer lowStockThreshold,
        BigDecimal sellingPrice
) {
}
