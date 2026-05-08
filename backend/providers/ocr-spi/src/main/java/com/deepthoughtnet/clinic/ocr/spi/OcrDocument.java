package com.deepthoughtnet.clinic.ocr.spi;

public record OcrDocument(
        String originalFilename,
        String mediaType,
        byte[] bytes
) {
}
