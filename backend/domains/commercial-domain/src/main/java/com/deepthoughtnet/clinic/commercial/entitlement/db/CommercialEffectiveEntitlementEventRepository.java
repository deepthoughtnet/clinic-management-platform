package com.deepthoughtnet.clinic.commercial.entitlement.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialEffectiveEntitlementEventRepository extends JpaRepository<CommercialEffectiveEntitlementEventEntity, UUID> {
    List<CommercialEffectiveEntitlementEventEntity> findByTenantIdOrderByOccurredAtDesc(UUID tenantId);
}
