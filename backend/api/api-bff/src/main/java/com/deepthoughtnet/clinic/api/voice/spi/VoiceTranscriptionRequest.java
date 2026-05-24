package com.deepthoughtnet.clinic.api.voice.spi;

import java.util.UUID;

public record VoiceTranscriptionRequest(
        UUID tenantId,
        byte[] audioBytes,
        String contentType,
        String filename,
        String language
) {
}

