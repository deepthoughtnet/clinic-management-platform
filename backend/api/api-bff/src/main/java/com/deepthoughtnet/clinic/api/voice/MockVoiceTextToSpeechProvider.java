package com.deepthoughtnet.clinic.api.voice;

import com.deepthoughtnet.clinic.api.voice.spi.TextToSpeechProvider;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisRequest;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisResult;
import org.springframework.stereotype.Component;

@Component
public class MockVoiceTextToSpeechProvider implements TextToSpeechProvider {
    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public VoiceSynthesisResult synthesize(VoiceSynthesisRequest request) {
        return new VoiceSynthesisResult(
                null,
                null,
                providerName(),
                "TTS provider not configured. Text response is available, but no playable audio was generated."
        );
    }
}

