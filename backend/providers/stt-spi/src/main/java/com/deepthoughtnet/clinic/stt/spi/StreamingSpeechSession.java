package com.deepthoughtnet.clinic.stt.spi;

/**
 * Streaming STT session contract for incremental transcript chunks.
 */
public interface StreamingSpeechSession extends AutoCloseable {
    TranscriptChunk pushAudio(byte[] pcmChunk);
    SpeechRecognitionResult finish();
    @Override
    void close();
}
