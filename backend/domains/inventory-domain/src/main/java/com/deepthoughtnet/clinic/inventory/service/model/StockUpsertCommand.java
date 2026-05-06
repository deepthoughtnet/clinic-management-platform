package com.deepthoughtnet.clinic.inventory.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StockUpsertCommand(
        UUID medicineId,
        String batchNumber,
        LocalDate expiryDate,
        int quantityOnHand,
        Integer lowStockThreshold,
        BigDecimal unitCost,
        BigDecimal sellingPrice,
        boolean active
) {
}
