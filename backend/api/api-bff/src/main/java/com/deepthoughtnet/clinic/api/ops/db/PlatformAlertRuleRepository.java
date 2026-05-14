package com.deepthoughtnet.clinic.api.ops.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for alert rule definitions (global + tenant-scoped overrides). */
public interface PlatformAlertRuleRepository extends JpaRepository<PlatformAlertRuleEntity, UUID> {
    List<PlatformAlertRuleEntity> findByTenantIdIsNullAndEnabledTrue();
    List<PlatformAlertRuleEntity> findByTenantIdAndEnabledTrue(UUID tenantId);
    Optional<PlatformAlertRuleEntity> findByTenantIdAndRuleKey(UUID tenantId, String ruleKey);
}
