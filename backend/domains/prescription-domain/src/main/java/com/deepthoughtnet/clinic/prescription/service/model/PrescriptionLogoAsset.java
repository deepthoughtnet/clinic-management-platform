package com.deepthoughtnet.clinic.prescription.service.model;

public record PrescriptionLogoAsset(
        byte[] bytes,
        String contentType,
        String fileName
) {
}
