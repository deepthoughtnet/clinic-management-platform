package com.deepthoughtnet.clinic.laboratory.db;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LaboratorySpecimenTestLinkRepository extends JpaRepository<LaboratorySpecimenTestLinkEntity, UUID> {
    List<LaboratorySpecimenTestLinkEntity> findByTenantIdAndLabOrderIdOrderByLinkedAtAsc(UUID tenantId, UUID labOrderId);
    List<LaboratorySpecimenTestLinkEntity> findByTenantIdAndLabOrderItemIdInAndActiveTrue(UUID tenantId, Collection<UUID> labOrderItemIds);
    Optional<LaboratorySpecimenTestLinkEntity> findByTenantIdAndLabOrderSampleIdAndLabOrderItemId(UUID tenantId, UUID labOrderSampleId, UUID labOrderItemId);
    List<LaboratorySpecimenTestLinkEntity> findByTenantIdAndLabOrderSampleIdAndActiveTrue(UUID tenantId, UUID labOrderSampleId);
}
