package com.deepthoughtnet.clinic.api.prescriptiontemplate.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionTemplateRepository extends JpaRepository<PrescriptionTemplateEntity, UUID> {
    Optional<PrescriptionTemplateEntity> findFirstByTenantIdAndActiveTrueOrderByTemplateVersionDesc(UUID tenantId);
    List<PrescriptionTemplateEntity> findByTenantIdAndActiveTrue(UUID tenantId);
    List<PrescriptionTemplateEntity> findByTenantIdOrderByTemplateVersionDesc(UUID tenantId);
}
