package com.deepthoughtnet.clinic.inventory.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record StockUpsertCommand(
        @NotNull
        UUID medicineId,
        String batchNumber,
        LocalDate expiryDate,
        @PositiveOrZero
        int quantityOnHand,
        Integer lowStockThreshold,
        BigDecimal unitCost,
        BigDecimal sellingPrice,
        boolean active
) {
}
