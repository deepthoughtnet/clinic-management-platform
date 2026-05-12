package com.deepthoughtnet.clinic.api.billing.dto;

import java.time.OffsetDateTime;

public record InvoiceEmailSendResponse(
        boolean sent,
        String message,
        String recipientEmail,
        OffsetDateTime sentAt
) {
}
