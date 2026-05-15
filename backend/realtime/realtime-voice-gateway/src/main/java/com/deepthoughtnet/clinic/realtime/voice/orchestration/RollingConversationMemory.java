package com.deepthoughtnet.clinic.realtime.voice.orchestration;

import com.deepthoughtnet.clinic.realtime.voice.transcript.SpeakerType;
import com.deepthoughtnet.clinic.realtime.voice.transcript.VoiceTranscriptRecord;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Short-term in-memory conversation memory hook.
 */
@Component
public class RollingConversationMemory {
    private static final int MAX_LINES = 24;
    private final ConcurrentHashMap<UUID, Deque<VoiceTranscriptRecord>> memory = new ConcurrentHashMap<>();

    public void append(UUID sessionId, VoiceTranscriptRecord line) {
        Deque<VoiceTranscriptRecord> deque = memory.computeIfAbsent(sessionId, ignored -> new ArrayDeque<>());
        deque.addLast(line);
        while (deque.size() > MAX_LINES) {
            deque.removeFirst();
        }
    }

    public String buildPromptContext(UUID sessionId) {
        return memory.getOrDefault(sessionId, new ArrayDeque<>()).stream()
                .map(r -> r.speakerType().name() + ": " + r.text())
                .reduce("", (a, b) -> a.isBlank() ? b : a + "\n" + b);
    }

    public int recentMisunderstandingCount(UUID sessionId) {
        List<VoiceTranscriptRecord> lines = memory.getOrDefault(sessionId, new ArrayDeque<>()).stream().toList();
        int misunderstandings = 0;
        for (VoiceTranscriptRecord line : lines) {
            if (line.speakerType() != SpeakerType.AI) {
                continue;
            }
            String text = line.text().toLowerCase(java.util.Locale.ROOT);
            if (text.contains("sorry") && text.contains("understand")) {
                misunderstandings++;
            }
        }
        return misunderstandings;
    }

    public void clear(UUID sessionId) {
        memory.remove(sessionId);
    }
}
