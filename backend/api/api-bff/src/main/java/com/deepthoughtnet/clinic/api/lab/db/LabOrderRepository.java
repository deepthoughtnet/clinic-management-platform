package com.deepthoughtnet.clinic.api.lab.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabOrderRepository extends JpaRepository<LabOrderEntity, UUID> {
    Optional<LabOrderEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<LabOrderEntity> findByTenantIdAndOrderNumber(UUID tenantId, String orderNumber);
    List<LabOrderEntity> findByTenantIdOrderByOrderedAtDescCreatedAtDesc(UUID tenantId);
    List<LabOrderEntity> findByTenantIdAndConsultationIdOrderByCreatedAtDesc(UUID tenantId, UUID consultationId);
    List<LabOrderEntity> findByTenantIdAndPatientIdOrderByCreatedAtDesc(UUID tenantId, UUID patientId);
}
