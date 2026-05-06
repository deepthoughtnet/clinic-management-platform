package com.deepthoughtnet.clinic.billing.db;

import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BillRepository extends JpaRepository<BillEntity, UUID> {
    Optional<BillEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<BillEntity> findByTenantIdAndBillNumber(UUID tenantId, String billNumber);

    List<BillEntity> findByTenantIdAndPatientIdOrderByBillDateDescCreatedAtDesc(UUID tenantId, UUID patientId);

    List<BillEntity> findByTenantIdOrderByBillDateDescCreatedAtDesc(UUID tenantId);

    @Query("""
            select b
            from BillEntity b
            where b.tenantId = :tenantId
              and (:patientId is null or b.patientId = :patientId)
              and (:status is null or b.status = :status)
            order by b.billDate desc, b.createdAt desc
            """)
    List<BillEntity> search(UUID tenantId, UUID patientId, BillStatus status);
}
