package com.deepthoughtnet.clinic.stt.spi;

import java.time.OffsetDateTime;

/** Incremental transcript fragment from a streaming STT engine. */
public record TranscriptChunk(String text, boolean finalChunk, Double confidence, OffsetDateTime timestamp) {
}
