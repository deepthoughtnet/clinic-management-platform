package com.deepthoughtnet.clinic.api.lab.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabOrderAttachmentRepository extends JpaRepository<LabOrderAttachmentEntity, UUID> {
    List<LabOrderAttachmentEntity> findByTenantIdAndLabOrderIdOrderByCreatedAtDesc(UUID tenantId, UUID labOrderId);
}
