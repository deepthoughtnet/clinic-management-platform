package com.deepthoughtnet.clinic.platform.audit.db;

import java.util.List;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {
    List<AuditEventEntity> findByTenantIdAndEntityTypeAndEntityIdOrderByOccurredAtAsc(
            UUID tenantId,
            String entityType,
            UUID entityId
    );

    List<AuditEventEntity> findTop5ByTenantIdOrderByOccurredAtDesc(UUID tenantId);

    List<AuditEventEntity> findTop5ByTenantIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
            UUID tenantId,
            OffsetDateTime occurredAt
    );
}
