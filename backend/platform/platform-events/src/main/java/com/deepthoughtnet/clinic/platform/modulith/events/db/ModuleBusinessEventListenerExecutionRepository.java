package com.deepthoughtnet.clinic.platform.modulith.events.db;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ModuleBusinessEventListenerExecutionRepository extends JpaRepository<ModuleBusinessEventListenerExecutionEntity, UUID> {
    Optional<ModuleBusinessEventListenerExecutionEntity> findByTenantIdAndEventIdAndListenerName(UUID tenantId, UUID eventId, String listenerName);

    List<ModuleBusinessEventListenerExecutionEntity> findByTenantIdAndEventIdOrderByCreatedAtAsc(UUID tenantId, UUID eventId);

    @Query("""
            select j from ModuleBusinessEventListenerExecutionEntity j
            where j.status in ('PENDING', 'RETRY_SCHEDULED')
              and j.nextAttemptAt is not null
              and j.nextAttemptAt <= :now
            order by j.createdAt asc
            """)
    List<ModuleBusinessEventListenerExecutionEntity> findRunnable(OffsetDateTime now);

    @Query("""
            select j from ModuleBusinessEventListenerExecutionEntity j
            where j.tenantId = :tenantId
              and j.status = 'PROCESSING'
              and j.updatedAt <= :cutoff
            order by j.updatedAt asc
            """)
    List<ModuleBusinessEventListenerExecutionEntity> findStaleProcessing(UUID tenantId, OffsetDateTime cutoff);

    @Query("""
            select j from ModuleBusinessEventListenerExecutionEntity j
            where j.status = 'PROCESSING'
              and j.updatedAt <= :cutoff
            order by j.updatedAt asc
            """)
    List<ModuleBusinessEventListenerExecutionEntity> findStaleProcessingAll(OffsetDateTime cutoff);

    long countByStatus(String status);

    long countByTenantIdAndStatus(UUID tenantId, String status);

    long countByTenantIdAndEventIdAndStatus(UUID tenantId, UUID eventId, String status);
}
