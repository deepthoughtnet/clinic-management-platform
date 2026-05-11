package com.deepthoughtnet.clinic.billing.service.model;

import java.time.LocalDate;
import java.util.UUID;

public record BillingSearchCriteria(
        UUID patientId,
        BillStatus status,
        LocalDate fromDate,
        LocalDate toDate,
        PaymentMode paymentMode
) {
}
