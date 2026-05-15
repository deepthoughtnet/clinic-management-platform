package com.deepthoughtnet.clinic.realtime.voice.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for session transcript lines. */
public interface VoiceTranscriptRepository extends JpaRepository<VoiceTranscriptEntity, UUID> {
    List<VoiceTranscriptEntity> findBySessionIdOrderByTranscriptTimestampAsc(UUID sessionId);
}
