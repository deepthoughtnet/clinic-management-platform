package com.deepthoughtnet.clinic.voice.spi;

/** Transcript payload from provider (optional in foundation mode). */
public record VoiceCallTranscript(
        String text,
        String summary,
        String sentiment,
        String outcome,
        boolean requiresFollowUp
) {}
