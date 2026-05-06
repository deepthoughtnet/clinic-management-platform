package com.deepthoughtnet.clinic.inventory.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StockRecord(
        UUID id,
        UUID tenantId,
        UUID medicineId,
        String medicineName,
        String medicineType,
        String batchNumber,
        LocalDate expiryDate,
        int quantityOnHand,
        Integer lowStockThreshold,
        BigDecimal unitCost,
        BigDecimal sellingPrice,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
