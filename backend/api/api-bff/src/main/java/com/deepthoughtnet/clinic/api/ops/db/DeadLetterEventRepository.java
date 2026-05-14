package com.deepthoughtnet.clinic.api.ops.db;

import com.deepthoughtnet.clinic.api.ops.db.DeadLetterEventEntity.SourceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for dead-letter events. */
public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEventEntity, UUID> {
    List<DeadLetterEventEntity> findTop200ByTenantIdOrderByDeadLetteredAtDesc(UUID tenantId);
    Optional<DeadLetterEventEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    boolean existsByTenantIdAndSourceTypeAndSourceExecutionId(UUID tenantId, SourceType sourceType, UUID sourceExecutionId);
}
