package com.deepthoughtnet.clinic.notification.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NotificationHistoryRepository
        extends JpaRepository<NotificationHistoryEntity, UUID>, JpaSpecificationExecutor<NotificationHistoryEntity> {

    Optional<NotificationHistoryEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<NotificationHistoryEntity> findByTenantIdAndDeduplicationKey(UUID tenantId, String deduplicationKey);

    boolean existsByTenantIdAndDeduplicationKey(UUID tenantId, String deduplicationKey);

    List<NotificationHistoryEntity> findByTenantIdAndPatientIdOrderByCreatedAtDesc(UUID tenantId, UUID patientId);
}
