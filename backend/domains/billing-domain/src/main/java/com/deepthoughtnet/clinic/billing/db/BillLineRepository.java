package com.deepthoughtnet.clinic.billing.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillLineRepository extends JpaRepository<BillLineEntity, UUID> {
    List<BillLineEntity> findByTenantIdAndBillIdOrderBySortOrderAsc(UUID tenantId, UUID billId);

    void deleteByTenantIdAndBillId(UUID tenantId, UUID billId);
}
