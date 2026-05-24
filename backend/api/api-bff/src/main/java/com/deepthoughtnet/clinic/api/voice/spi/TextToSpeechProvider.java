package com.deepthoughtnet.clinic.api.voice.spi;

public interface TextToSpeechProvider {
    String providerName();
    boolean isReady();
    VoiceSynthesisResult synthesize(VoiceSynthesisRequest request);
}

