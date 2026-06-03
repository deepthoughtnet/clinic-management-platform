package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.math.BigDecimal;

public record PatientPortalBillLineResponse(
        String itemName,
        Integer quantity,
        BigDecimal totalPrice,
        String summary
) {
}
