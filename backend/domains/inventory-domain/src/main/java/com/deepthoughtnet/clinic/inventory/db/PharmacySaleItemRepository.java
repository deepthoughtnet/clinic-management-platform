package com.deepthoughtnet.clinic.inventory.db;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacySaleItemRepository extends JpaRepository<PharmacySaleItemEntity, UUID> {
    List<PharmacySaleItemEntity> findByTenantIdAndSaleIdOrderByCreatedAtAsc(UUID tenantId, UUID saleId);
    List<PharmacySaleItemEntity> findByTenantIdAndSaleIdIn(UUID tenantId, Collection<UUID> saleIds);
    List<PharmacySaleItemEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
