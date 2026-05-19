package com.deepthoughtnet.clinic.inventory.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceiptEntity, UUID> {
    List<GoodsReceiptEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<GoodsReceiptEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<GoodsReceiptEntity> findByTenantIdAndReceiptNumberIgnoreCase(UUID tenantId, String receiptNumber);
    boolean existsByTenantIdAndReceiptNumberIgnoreCaseAndIdNot(UUID tenantId, String receiptNumber, UUID id);
}
