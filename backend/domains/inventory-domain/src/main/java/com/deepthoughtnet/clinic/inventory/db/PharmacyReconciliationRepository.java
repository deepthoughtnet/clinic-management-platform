package com.deepthoughtnet.clinic.inventory.db;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacyReconciliationRepository extends JpaRepository<PharmacyReconciliationEntity, UUID> {
    List<PharmacyReconciliationEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<PharmacyReconciliationEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    List<PharmacyReconciliationEntity> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);
}
