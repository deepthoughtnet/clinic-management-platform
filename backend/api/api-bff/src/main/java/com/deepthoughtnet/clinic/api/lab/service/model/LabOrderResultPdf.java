package com.deepthoughtnet.clinic.api.lab.service.model;

public record LabOrderResultPdf(
        String filename,
        byte[] content
) {
}
