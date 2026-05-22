package com.deepthoughtnet.clinic.inventory.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacySaleRepository extends JpaRepository<PharmacySaleEntity, UUID> {
    Optional<PharmacySaleEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<PharmacySaleEntity> findByTenantIdAndSaleNumber(UUID tenantId, String saleNumber);
    List<PharmacySaleEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
