package com.deepthoughtnet.clinic.inventory.db;

import java.util.List;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacySaleReturnRepository extends JpaRepository<PharmacySaleReturnEntity, UUID> {
    List<PharmacySaleReturnEntity> findByTenantIdAndSaleIdOrderByCreatedAtAsc(UUID tenantId, UUID saleId);
    List<PharmacySaleReturnEntity> findByTenantIdAndSaleIdInOrderByCreatedAtDesc(UUID tenantId, Collection<UUID> saleIds);
    List<PharmacySaleReturnEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<PharmacySaleReturnEntity> findByTenantIdAndReturnNumber(UUID tenantId, String returnNumber);
}
