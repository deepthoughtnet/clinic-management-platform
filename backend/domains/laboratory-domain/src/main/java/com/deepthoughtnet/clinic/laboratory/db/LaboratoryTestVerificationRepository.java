package com.deepthoughtnet.clinic.laboratory.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LaboratoryTestVerificationRepository extends JpaRepository<LaboratoryTestVerificationEntity, UUID> {
    List<LaboratoryTestVerificationEntity> findByTenantIdAndLabOrderIdOrderByVerifiedAtDesc(UUID tenantId, UUID labOrderId);
    Optional<LaboratoryTestVerificationEntity> findTopByTenantIdAndLabOrderItemIdOrderByVerifiedAtDesc(UUID tenantId, UUID labOrderItemId);
}
