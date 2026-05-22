package com.deepthoughtnet.clinic.inventory.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacySalePrescriptionRepository extends JpaRepository<PharmacySalePrescriptionEntity, UUID> {
    Optional<PharmacySalePrescriptionEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    boolean existsByTenantIdAndStorageKey(UUID tenantId, String storageKey);
}
