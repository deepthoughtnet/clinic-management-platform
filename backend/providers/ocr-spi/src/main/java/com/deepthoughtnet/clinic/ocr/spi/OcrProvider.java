package com.deepthoughtnet.clinic.ocr.spi;

public interface OcrProvider {

    String providerName();

    default void validateReady() {
    }

    OcrResult extractText(OcrDocument document);
}
