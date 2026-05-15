package com.deepthoughtnet.clinic.tts.spi;

import java.util.UUID;

/** Synthesis request contract with tenant and locale context. */
public record SpeechSynthesisRequest(UUID tenantId, String text, String locale, String voiceName) {
}
