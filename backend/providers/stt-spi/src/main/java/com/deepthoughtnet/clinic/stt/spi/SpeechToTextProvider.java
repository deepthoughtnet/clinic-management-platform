package com.deepthoughtnet.clinic.stt.spi;

import java.util.UUID;

/**
 * Provider abstraction for streaming speech-to-text engines.
 */
public interface SpeechToTextProvider {
    String providerName();
    boolean isReady();
    StreamingSpeechSession openSession(UUID tenantId, String locale);
}
