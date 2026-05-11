package com.deepthoughtnet.clinic.billing.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillRefundRepository extends JpaRepository<BillRefundEntity, UUID> {
    List<BillRefundEntity> findByTenantIdAndBillIdOrderByRefundedAtDescCreatedAtDesc(UUID tenantId, UUID billId);
}
