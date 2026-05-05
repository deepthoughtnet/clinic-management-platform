package com.deepthoughtnet.clinic.notification.db;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationOutboxRepository
        extends JpaRepository<NotificationOutboxEntity, UUID>, JpaSpecificationExecutor<NotificationOutboxEntity> {
    boolean existsByDeduplicationKey(String deduplicationKey);

    Optional<NotificationOutboxEntity> findByDeduplicationKey(String deduplicationKey);

    Page<NotificationOutboxEntity> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            String status,
            OffsetDateTime nextAttemptAt,
            Pageable pageable
    );

    Optional<NotificationOutboxEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    long countByTenantIdAndStatus(UUID tenantId, String status);

    long countByTenantIdAndStatusAndProcessedAtGreaterThanEqual(
            UUID tenantId,
            String status,
            OffsetDateTime processedAt
    );

    @Query("""
            select max(n.updatedAt)
            from NotificationOutboxEntity n
            where n.tenantId = :tenantId
              and n.status = 'FAILED'
            """)
    Optional<OffsetDateTime> findLastFailedAt(@Param("tenantId") UUID tenantId);
}
