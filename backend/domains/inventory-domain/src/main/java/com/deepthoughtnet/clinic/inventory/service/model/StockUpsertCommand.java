package com.deepthoughtnet.clinic.inventory.service.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StockUpsertCommand(
        @NotNull
        UUID medicineId,
        String batchNumber,
        LocalDate expiryDate,
        LocalDate purchaseDate,
        String supplierName,
        @PositiveOrZero
        Integer quantityReceived,
        @PositiveOrZero
        int quantityOnHand,
        Integer lowStockThreshold,
        BigDecimal unitCost,
        BigDecimal purchasePrice,
        BigDecimal sellingPrice,
        boolean active
) {
}
