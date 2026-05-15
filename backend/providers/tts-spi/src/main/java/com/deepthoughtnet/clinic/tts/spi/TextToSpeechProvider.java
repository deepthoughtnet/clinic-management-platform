package com.deepthoughtnet.clinic.tts.spi;

/**
 * Provider abstraction for text-to-speech synthesis.
 */
public interface TextToSpeechProvider {
    String providerName();
    boolean isReady();
    SpeechSynthesisResult synthesize(SpeechSynthesisRequest request);
}
