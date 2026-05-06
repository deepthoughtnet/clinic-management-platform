package com.deepthoughtnet.clinic.billing.service.model;

public record BillPdf(
        String filename,
        byte[] content
) {
}
