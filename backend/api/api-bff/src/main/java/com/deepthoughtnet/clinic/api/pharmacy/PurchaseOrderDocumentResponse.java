package com.deepthoughtnet.clinic.api.pharmacy;

import java.time.OffsetDateTime;

record PurchaseOrderDocumentResponse(
        String filename,
        byte[] content,
        OffsetDateTime generatedAt
) {
}
