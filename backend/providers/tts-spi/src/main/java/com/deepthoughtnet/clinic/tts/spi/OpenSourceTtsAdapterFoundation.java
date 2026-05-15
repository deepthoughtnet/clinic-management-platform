package com.deepthoughtnet.clinic.tts.spi;

import org.springframework.stereotype.Component;

/**
 * Placeholder foundation for future Piper/Coqui local TTS engines.
 */
@Component
public class OpenSourceTtsAdapterFoundation implements TextToSpeechProvider {
    @Override
    public String providerName() {
        return "opensource-tts-foundation";
    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public SpeechSynthesisResult synthesize(SpeechSynthesisRequest request) {
        throw new UnsupportedOperationException("Open-source TTS adapter is a v1 foundation only.");
    }
}
