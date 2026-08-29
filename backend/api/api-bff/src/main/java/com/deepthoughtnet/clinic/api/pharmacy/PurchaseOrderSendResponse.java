package com.deepthoughtnet.clinic.api.pharmacy;

import java.time.OffsetDateTime;

record PurchaseOrderSendResponse(
        boolean sent,
        String message,
        String recipientEmail,
        OffsetDateTime sentAt
) {
}
