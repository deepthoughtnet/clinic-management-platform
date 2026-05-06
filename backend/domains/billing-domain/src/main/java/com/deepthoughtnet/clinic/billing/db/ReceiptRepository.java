package com.deepthoughtnet.clinic.billing.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptRepository extends JpaRepository<ReceiptEntity, UUID> {
    Optional<ReceiptEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<ReceiptEntity> findByTenantIdAndReceiptNumber(UUID tenantId, String receiptNumber);

    List<ReceiptEntity> findByTenantIdAndBillIdOrderByCreatedAtDesc(UUID tenantId, UUID billId);
}
