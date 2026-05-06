package com.deepthoughtnet.clinic.billing.service.model;

public record ReceiptPdf(
        String filename,
        byte[] content
) {
}
