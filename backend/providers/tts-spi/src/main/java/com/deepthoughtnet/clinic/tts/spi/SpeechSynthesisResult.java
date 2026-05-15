package com.deepthoughtnet.clinic.tts.spi;

/** Synthesis result abstraction with raw audio payload placeholder. */
public record SpeechSynthesisResult(byte[] audioBytes, String contentType, long latencyMs) {
}
