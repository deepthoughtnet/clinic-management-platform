package com.deepthoughtnet.clinic.api.voice;

public interface VoiceActivityDetector {
    String providerName();

    VoiceActivityResult analyze(byte[] audioBytes, String contentType);
}
