package com.deepthoughtnet.clinic.platform.modulith.events.db;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ModuleBusinessEventRepository extends JpaRepository<ModuleBusinessEventEntity, UUID> {
    Optional<ModuleBusinessEventEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("""
            select e from ModuleBusinessEventEntity e
            where e.tenantId = :tenantId and e.status in :statuses
            order by e.createdAt asc
            """)
    List<ModuleBusinessEventEntity> findByTenantIdAndStatusInOrderByCreatedAtAsc(UUID tenantId, List<ModuleBusinessEventStatus> statuses);

    long countByTenantIdAndStatus(UUID tenantId, ModuleBusinessEventStatus status);

    long countByTenantIdAndStatusIn(UUID tenantId, List<ModuleBusinessEventStatus> statuses);

    List<ModuleBusinessEventEntity> findByTenantIdAndOccurredAtBetweenOrderByOccurredAtDesc(UUID tenantId, OffsetDateTime from, OffsetDateTime to);
}
