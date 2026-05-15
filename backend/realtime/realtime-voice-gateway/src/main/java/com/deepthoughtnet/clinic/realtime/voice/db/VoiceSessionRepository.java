package com.deepthoughtnet.clinic.realtime.voice.db;

import com.deepthoughtnet.clinic.realtime.voice.session.VoiceSessionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for tenant-scoped voice sessions. */
public interface VoiceSessionRepository extends JpaRepository<VoiceSessionEntity, UUID> {
    List<VoiceSessionEntity> findTop200ByTenantIdOrderByStartedAtDesc(UUID tenantId);
    Optional<VoiceSessionEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    long countByTenantIdAndSessionStatus(UUID tenantId, VoiceSessionStatus status);
}
