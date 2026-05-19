package com.deepthoughtnet.clinic.inventory.service.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StockUpsertCommand(
        @NotNull
        UUID medicineId,
        UUID locationId,
        @Size(max = 128)
        String barcode,
        @Size(max = 128)
        String qrCode,
        @Size(max = 128)
        String externalCode,
        String batchNumber,
        String purchaseReferenceNumber,
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
