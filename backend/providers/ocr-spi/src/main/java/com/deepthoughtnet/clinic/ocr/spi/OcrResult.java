package com.deepthoughtnet.clinic.ocr.spi;

public record OcrResult(
        String provider,
        String text
) {
}
