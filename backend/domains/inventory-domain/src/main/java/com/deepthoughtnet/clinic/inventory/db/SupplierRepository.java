package com.deepthoughtnet.clinic.inventory.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<SupplierEntity, UUID> {
    List<SupplierEntity> findByTenantIdOrderBySupplierNameAsc(UUID tenantId);
    Optional<SupplierEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<SupplierEntity> findByTenantIdAndSupplierNameIgnoreCase(UUID tenantId, String supplierName);
    boolean existsByTenantIdAndSupplierNameIgnoreCaseAndIdNot(UUID tenantId, String supplierName, UUID id);
}
