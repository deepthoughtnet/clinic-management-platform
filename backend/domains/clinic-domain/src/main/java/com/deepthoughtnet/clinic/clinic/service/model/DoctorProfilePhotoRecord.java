package com.deepthoughtnet.clinic.clinic.service.model;

public record DoctorProfilePhotoRecord(
        String fileName,
        String contentType,
        long sizeBytes,
        byte[] bytes
) {
}
