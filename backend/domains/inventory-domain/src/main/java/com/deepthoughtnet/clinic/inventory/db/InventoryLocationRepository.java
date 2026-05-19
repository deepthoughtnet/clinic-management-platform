package com.deepthoughtnet.clinic.inventory.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryLocationRepository extends JpaRepository<InventoryLocationEntity, UUID> {
    List<InventoryLocationEntity> findByTenantIdOrderByDefaultLocationDescLocationNameAsc(UUID tenantId);
    Optional<InventoryLocationEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<InventoryLocationEntity> findByTenantIdAndDefaultLocationTrue(UUID tenantId);
    Optional<InventoryLocationEntity> findByTenantIdAndLocationNameIgnoreCase(UUID tenantId, String locationName);
    boolean existsByTenantIdAndLocationNameIgnoreCaseAndIdNot(UUID tenantId, String locationName, UUID id);
}
