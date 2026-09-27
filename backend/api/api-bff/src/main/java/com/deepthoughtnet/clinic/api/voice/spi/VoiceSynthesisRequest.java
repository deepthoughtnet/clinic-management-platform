package com.deepthoughtnet.clinic.api.voice.spi;

import java.util.UUID;

public record VoiceSynthesisRequest(
        UUID tenantId,
        String text,
        String language,
        String outputFormat
) {
    public VoiceSynthesisRequest(UUID tenantId, String text, String language) {
        this(tenantId, text, language, null);
    }
}
