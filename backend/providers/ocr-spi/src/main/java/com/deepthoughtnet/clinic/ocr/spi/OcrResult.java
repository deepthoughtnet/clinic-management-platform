package com.deepthoughtnet.clinic.ocr.spi;

public record OcrResult(
        String provider,
        String text
) {
    /**
     * OCR providers represent "no extractable text" as an empty result.  A
     * nullable text value leaks provider-specific behaviour into every
     * consumer and is particularly unsafe for String-oriented extraction
     * pipelines.
     */
    public OcrResult {
        text = text == null ? "" : text;
    }
}
