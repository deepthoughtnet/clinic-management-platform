package com.deepthoughtnet.clinic.stt.spi;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * In-memory STT provider used for realtime gateway foundation testing.
 */
@Component
public class MockSpeechToTextProvider implements SpeechToTextProvider {
    @Override
    public String providerName() {
        return "mock-stt";
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public StreamingSpeechSession openSession(UUID tenantId, String locale) {
        return new MockStreamingSpeechSession();
    }

    private static final class MockStreamingSpeechSession implements StreamingSpeechSession {
        private final List<TranscriptChunk> chunks = new ArrayList<>();

        @Override
        public TranscriptChunk pushAudio(byte[] pcmChunk) {
            TranscriptChunk chunk = new TranscriptChunk("audio-chunk-" + chunks.size(), false, 0.95d, OffsetDateTime.now());
            chunks.add(chunk);
            return chunk;
        }

        @Override
        public SpeechRecognitionResult finish() {
            return new SpeechRecognitionResult(String.join(" ", chunks.stream().map(TranscriptChunk::text).toList()), 0.95d, List.copyOf(chunks));
        }

        @Override
        public void close() {
        }
    }
}
