package com.deepthoughtnet.clinic.api.voice;

import com.deepthoughtnet.clinic.api.voice.spi.SpeechToTextProvider;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionRequest;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionResult;
import org.springframework.stereotype.Component;

@Component
public class MockVoiceSpeechToTextProvider implements SpeechToTextProvider {
    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public VoiceTranscriptionResult transcribe(VoiceTranscriptionRequest request) {
        return new VoiceTranscriptionResult(
                "Hello, I want to book an appointment.",
                providerName(),
                "Mock STT transcript used because no live STT provider is configured."
        );
    }
}

