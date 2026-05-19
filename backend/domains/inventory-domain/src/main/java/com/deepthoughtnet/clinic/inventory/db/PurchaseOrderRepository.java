package com.deepthoughtnet.clinic.inventory.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrderEntity, UUID> {
    List<PurchaseOrderEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<PurchaseOrderEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<PurchaseOrderEntity> findByTenantIdAndPoNumberIgnoreCase(UUID tenantId, String poNumber);
    boolean existsByTenantIdAndPoNumberIgnoreCaseAndIdNot(UUID tenantId, String poNumber, UUID id);
}
