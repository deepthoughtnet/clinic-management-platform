package com.deepthoughtnet.clinic.billing.service.model;

import java.util.UUID;

public record BillingSearchCriteria(
        UUID patientId,
        BillStatus status
) {
}
