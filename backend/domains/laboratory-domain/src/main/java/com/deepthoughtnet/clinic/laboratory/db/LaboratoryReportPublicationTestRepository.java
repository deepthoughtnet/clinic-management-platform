package com.deepthoughtnet.clinic.laboratory.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LaboratoryReportPublicationTestRepository extends JpaRepository<LaboratoryReportPublicationTestEntity, UUID> {
    List<LaboratoryReportPublicationTestEntity> findByTenantIdAndPublicationArtifactIdOrderByIncludedAtAsc(UUID tenantId, UUID publicationArtifactId);
    Optional<LaboratoryReportPublicationTestEntity> findTopByTenantIdAndLabOrderItemIdOrderByIncludedAtDesc(UUID tenantId, UUID labOrderItemId);
}
