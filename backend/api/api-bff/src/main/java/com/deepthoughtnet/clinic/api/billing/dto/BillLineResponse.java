package com.deepthoughtnet.clinic.api.billing.dto;

import com.deepthoughtnet.clinic.billing.service.model.BillItemType;
import java.math.BigDecimal;

public record BillLineResponse(
        String id,
        BillItemType itemType,
        String itemName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        String referenceId,
        BigDecimal lineDiscountAmount,
        String batchNumber,
        String dispensationReferenceId,
        Integer sortOrder
) {
}
