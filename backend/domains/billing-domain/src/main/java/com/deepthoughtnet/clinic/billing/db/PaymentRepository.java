package com.deepthoughtnet.clinic.billing.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<PaymentEntity> findByTenantIdAndBillIdOrderByCreatedAtDesc(UUID tenantId, UUID billId);
}
