package com.deepthoughtnet.clinic.tts.spi;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/** In-memory TTS provider for realtime gateway foundation and tests. */
@Component
public class MockTextToSpeechProvider implements TextToSpeechProvider {
    @Override
    public String providerName() {
        return "mock-tts";
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public SpeechSynthesisResult synthesize(SpeechSynthesisRequest request) {
        byte[] payload = ("MOCK_AUDIO:" + request.text()).getBytes(StandardCharsets.UTF_8);
        return new SpeechSynthesisResult(payload, "audio/mock", 5L);
    }
}
