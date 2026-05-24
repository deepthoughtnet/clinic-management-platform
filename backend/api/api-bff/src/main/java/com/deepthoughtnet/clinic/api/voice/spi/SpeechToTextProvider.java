package com.deepthoughtnet.clinic.api.voice.spi;

public interface SpeechToTextProvider {
    String providerName();
    boolean isReady();
    VoiceTranscriptionResult transcribe(VoiceTranscriptionRequest request);
}

