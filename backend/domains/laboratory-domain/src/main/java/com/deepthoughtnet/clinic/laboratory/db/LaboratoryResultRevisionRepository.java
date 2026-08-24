package com.deepthoughtnet.clinic.laboratory.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LaboratoryResultRevisionRepository extends JpaRepository<LaboratoryResultRevisionEntity, UUID> {
    List<LaboratoryResultRevisionEntity> findByTenantIdAndLabOrderIdOrderByLabOrderItemIdAscRevisionNumberDesc(UUID tenantId, UUID labOrderId);
    List<LaboratoryResultRevisionEntity> findByTenantIdAndLabOrderItemIdOrderByRevisionNumberDesc(UUID tenantId, UUID labOrderItemId);
    Optional<LaboratoryResultRevisionEntity> findTopByTenantIdAndLabOrderItemIdOrderByRevisionNumberDesc(UUID tenantId, UUID labOrderItemId);
}
