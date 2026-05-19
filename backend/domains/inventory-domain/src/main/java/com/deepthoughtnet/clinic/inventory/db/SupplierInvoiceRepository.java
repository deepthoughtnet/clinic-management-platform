package com.deepthoughtnet.clinic.inventory.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoiceEntity, UUID> {
    List<SupplierInvoiceEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<SupplierInvoiceEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<SupplierInvoiceEntity> findByTenantIdAndInvoiceNumberIgnoreCase(UUID tenantId, String invoiceNumber);
    boolean existsByTenantIdAndInvoiceNumberIgnoreCaseAndIdNot(UUID tenantId, String invoiceNumber, UUID id);
}
