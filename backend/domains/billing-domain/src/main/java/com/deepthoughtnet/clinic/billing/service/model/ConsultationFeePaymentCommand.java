package com.deepthoughtnet.clinic.billing.service.model;

import java.util.UUID;

public record ConsultationFeePaymentCommand(
        UUID appointmentId,
        PaymentMode paymentMode,
        String referenceNumber,
        String notes
) {
}
