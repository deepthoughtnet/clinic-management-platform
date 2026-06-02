package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.math.BigDecimal;

public record PatientPortalBillLineResponse(
        String itemType,
        String itemName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        BigDecimal lineDiscountAmount,
        Integer sortOrder
) {
}
