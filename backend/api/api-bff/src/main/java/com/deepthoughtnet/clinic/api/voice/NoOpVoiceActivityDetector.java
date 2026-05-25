package com.deepthoughtnet.clinic.api.voice;

import org.springframework.stereotype.Component;

@Component
public class NoOpVoiceActivityDetector implements VoiceActivityDetector {
    @Override
    public String providerName() {
        return "noop";
    }

    @Override
    public VoiceActivityResult analyze(byte[] audioBytes, String contentType) {
        return new VoiceActivityResult(false, 0.0d, "Backend VAD is not enabled for live gating in this phase.");
    }
}
