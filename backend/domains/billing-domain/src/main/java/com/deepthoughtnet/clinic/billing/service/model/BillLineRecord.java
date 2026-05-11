package com.deepthoughtnet.clinic.billing.service.model;

import java.math.BigDecimal;
import java.util.UUID;

public record BillLineRecord(
        UUID id,
        BillItemType itemType,
        String itemName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        UUID referenceId,
        Integer sortOrder,
        BigDecimal lineDiscountAmount,
        String batchNumber,
        UUID dispensationReferenceId
) {
}
