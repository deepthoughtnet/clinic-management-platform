package com.deepthoughtnet.clinic.inventory.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacySalePaymentRepository extends JpaRepository<PharmacySalePaymentEntity, UUID> {
    List<PharmacySalePaymentEntity> findByTenantIdAndSaleIdOrderByCreatedAtAsc(UUID tenantId, UUID saleId);
    List<PharmacySalePaymentEntity> findByTenantIdAndCashierShiftIdOrderByCreatedAtAsc(UUID tenantId, UUID cashierShiftId);
    List<PharmacySalePaymentEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<PharmacySalePaymentEntity> findByTenantIdAndReceiptNumber(UUID tenantId, String receiptNumber);
}
