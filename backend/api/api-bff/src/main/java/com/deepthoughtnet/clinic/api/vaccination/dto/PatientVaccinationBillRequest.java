package com.deepthoughtnet.clinic.api.vaccination.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PatientVaccinationBillRequest(
        UUID billId,
        boolean createNewBill,
        BigDecimal billItemUnitPrice
) {
}
