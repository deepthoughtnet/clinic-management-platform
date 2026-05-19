package com.deepthoughtnet.clinic.billing.db;

import com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillRepository extends JpaRepository<BillEntity, UUID>, BillRepositoryCustom {
    Optional<BillEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<BillEntity> findByTenantIdAndBillNumber(UUID tenantId, String billNumber);

    List<BillEntity> findByTenantIdAndPatientIdOrderByBillDateDescCreatedAtDesc(UUID tenantId, UUID patientId);

    List<BillEntity> findByTenantIdOrderByBillDateDescCreatedAtDesc(UUID tenantId);

    List<BillEntity> search(UUID tenantId, BillingSearchCriteria criteria);

    List<BillEntity> findByTenantIdAndAppointmentIdOrderByCreatedAtDesc(UUID tenantId, UUID appointmentId);
}
