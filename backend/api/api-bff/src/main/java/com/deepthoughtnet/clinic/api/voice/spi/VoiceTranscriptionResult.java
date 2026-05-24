package com.deepthoughtnet.clinic.api.voice.spi;

public record VoiceTranscriptionResult(
        String transcript,
        String providerName,
        String providerMessage
) {
}

