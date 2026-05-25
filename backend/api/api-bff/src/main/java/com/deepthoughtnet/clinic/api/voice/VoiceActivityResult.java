package com.deepthoughtnet.clinic.api.voice;

public record VoiceActivityResult(
        boolean speechDetected,
        double speechProbability,
        String detail
) {
}
