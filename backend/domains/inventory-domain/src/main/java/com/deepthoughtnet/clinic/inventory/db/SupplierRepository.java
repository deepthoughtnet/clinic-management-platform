package com.deepthoughtnet.clinic.inventory.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<SupplierEntity, UUID> {
    List<SupplierEntity> findByTenantIdOrderBySupplierNameAsc(UUID tenantId);
    Optional<SupplierEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<SupplierEntity> findByTenantIdAndSupplierNameIgnoreCase(UUID tenantId, String supplierName);
    Optional<SupplierEntity> findByTenantIdAndGstNumberIgnoreCase(UUID tenantId, String gstNumber);
    Optional<SupplierEntity> findByTenantIdAndPhone(UUID tenantId, String phone);
    Optional<SupplierEntity> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);
    boolean existsByTenantIdAndSupplierNameIgnoreCaseAndIdNot(UUID tenantId, String supplierName, UUID id);
    boolean existsByTenantIdAndGstNumberIgnoreCaseAndIdNot(UUID tenantId, String gstNumber, UUID id);
    boolean existsByTenantIdAndPhoneAndIdNot(UUID tenantId, String phone, UUID id);
    boolean existsByTenantIdAndEmailIgnoreCaseAndIdNot(UUID tenantId, String email, UUID id);
}
