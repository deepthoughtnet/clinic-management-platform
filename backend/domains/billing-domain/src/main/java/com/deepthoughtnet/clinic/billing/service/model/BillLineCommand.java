package com.deepthoughtnet.clinic.billing.service.model;

import java.math.BigDecimal;
import java.util.UUID;

public record BillLineCommand(
        BillItemType itemType,
        String itemName,
        Integer quantity,
        BigDecimal unitPrice,
        UUID referenceId,
        Integer sortOrder
) {
}
