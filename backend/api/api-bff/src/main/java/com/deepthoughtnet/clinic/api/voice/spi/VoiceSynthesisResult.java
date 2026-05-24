package com.deepthoughtnet.clinic.api.voice.spi;

public record VoiceSynthesisResult(
        byte[] audioBytes,
        String contentType,
        String providerName,
        String providerMessage
) {
}

