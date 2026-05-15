package com.deepthoughtnet.clinic.stt.spi;

import java.util.List;

/** Finalized recognition output after stream completion. */
public record SpeechRecognitionResult(String fullText, Double confidence, List<TranscriptChunk> chunks) {
}
