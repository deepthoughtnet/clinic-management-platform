package com.deepthoughtnet.clinic.prescription.service.model;

public record PrescriptionPdf(
        String filename,
        byte[] content
) {
}
