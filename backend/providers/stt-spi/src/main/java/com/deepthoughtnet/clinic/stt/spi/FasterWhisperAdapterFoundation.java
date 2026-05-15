package com.deepthoughtnet.clinic.stt.spi;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Placeholder foundation for future faster-whisper/whisper.cpp integrations.
 */
@Component
public class FasterWhisperAdapterFoundation implements SpeechToTextProvider {
    @Override
    public String providerName() {
        return "faster-whisper-foundation";
    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public StreamingSpeechSession openSession(UUID tenantId, String locale) {
        throw new UnsupportedOperationException("Faster-whisper adapter is a v1 foundation only.");
    }
}
