package com.deepthoughtnet.clinic.api.billing.dto;

import com.deepthoughtnet.clinic.billing.service.model.BillItemType;
import java.math.BigDecimal;
import java.util.UUID;

public record BillLineRequest(
        BillItemType itemType,
        String itemName,
        Integer quantity,
        BigDecimal unitPrice,
        UUID referenceId,
        Integer sortOrder
) {
}
