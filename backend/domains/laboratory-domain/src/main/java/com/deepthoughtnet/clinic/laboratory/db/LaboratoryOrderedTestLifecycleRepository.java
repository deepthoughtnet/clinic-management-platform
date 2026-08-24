package com.deepthoughtnet.clinic.laboratory.db;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LaboratoryOrderedTestLifecycleRepository extends JpaRepository<LaboratoryOrderedTestLifecycleEntity, UUID> {
    List<LaboratoryOrderedTestLifecycleEntity> findByTenantIdAndLabOrderIdOrderByCreatedAtAsc(UUID tenantId, UUID labOrderId);
    List<LaboratoryOrderedTestLifecycleEntity> findByTenantIdAndLabOrderIdAndLabOrderItemIdIn(UUID tenantId, UUID labOrderId, Collection<UUID> labOrderItemIds);
    Optional<LaboratoryOrderedTestLifecycleEntity> findByTenantIdAndLabOrderItemId(UUID tenantId, UUID labOrderItemId);
    Optional<LaboratoryOrderedTestLifecycleEntity> findByTenantIdAndLabOrderIdAndLabOrderItemId(UUID tenantId, UUID labOrderId, UUID labOrderItemId);
}
