package com.deepthoughtnet.clinic.inventory.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransactionEntity, UUID> {
    List<InventoryTransactionEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
