package com.deepthoughtnet.clinic.realtime.voice.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for ordered session event timeline. */
public interface VoiceSessionEventRepository extends JpaRepository<VoiceSessionEventEntity, UUID> {
    List<VoiceSessionEventEntity> findBySessionIdOrderBySequenceNumberAsc(UUID sessionId);
    long countBySessionId(UUID sessionId);
}
