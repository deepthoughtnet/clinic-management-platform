package com.deepthoughtnet.clinic.laboratory.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LaboratoryReportPublicationArtifactRepository extends JpaRepository<LaboratoryReportPublicationArtifactEntity, UUID> {
    List<LaboratoryReportPublicationArtifactEntity> findByTenantIdAndLabOrderIdOrderByArtifactNumberDesc(UUID tenantId, UUID labOrderId);
    List<LaboratoryReportPublicationArtifactEntity> findByTenantIdAndLabOrderIdOrderByArtifactNumberAsc(UUID tenantId, UUID labOrderId);
    Optional<LaboratoryReportPublicationArtifactEntity> findTopByTenantIdAndLabOrderIdOrderByArtifactNumberDesc(UUID tenantId, UUID labOrderId);
    Optional<LaboratoryReportPublicationArtifactEntity> findByVerificationToken(String verificationToken);
    Optional<LaboratoryReportPublicationArtifactEntity> findByTenantIdAndVerificationToken(UUID tenantId, String verificationToken);
}
