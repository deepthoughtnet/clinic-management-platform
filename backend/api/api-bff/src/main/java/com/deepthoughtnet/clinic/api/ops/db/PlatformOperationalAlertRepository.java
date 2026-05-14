package com.deepthoughtnet.clinic.api.ops.db;

import com.deepthoughtnet.clinic.api.ops.db.PlatformOperationalAlertEntity.Status;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for tenant-scoped operational alerts. */
public interface PlatformOperationalAlertRepository extends JpaRepository<PlatformOperationalAlertEntity, UUID> {
    List<PlatformOperationalAlertEntity> findTop100ByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    long countByTenantIdAndStatus(UUID tenantId, Status status);
    Optional<PlatformOperationalAlertEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<PlatformOperationalAlertEntity> findFirstByTenantIdAndRuleKeyAndSourceEntityIdAndStatusInOrderByLastSeenAtDesc(
            UUID tenantId,
            String ruleKey,
            String sourceEntityId,
            List<Status> statuses
    );
}
