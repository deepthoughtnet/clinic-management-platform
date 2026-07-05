package com.deepthoughtnet.clinic.api.lab.service.model;

import com.deepthoughtnet.clinic.billing.service.model.PaymentRecord;

public record LabOrderPaymentRecord(
        LabOrderRecord order,
        PaymentRecord payment
) {
}
