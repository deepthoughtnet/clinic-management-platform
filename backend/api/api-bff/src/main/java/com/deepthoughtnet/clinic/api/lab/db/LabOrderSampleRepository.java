package com.deepthoughtnet.clinic.api.lab.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabOrderSampleRepository extends JpaRepository<LabOrderSampleEntity, UUID> {
    List<LabOrderSampleEntity> findByTenantIdAndLabOrderIdOrderByCollectedAtAscCreatedAtAsc(UUID tenantId, UUID labOrderId);
    Optional<LabOrderSampleEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<LabOrderSampleEntity> findByTenantIdAndAccessionNumber(UUID tenantId, String accessionNumber);
    Optional<LabOrderSampleEntity> findFirstByTenantIdAndAccessionNumberStartingWithOrderByAccessionNumberDesc(UUID tenantId, String accessionPrefix);
    Optional<LabOrderSampleEntity> findByTenantIdAndBarcodeValue(UUID tenantId, String barcodeValue);
}
