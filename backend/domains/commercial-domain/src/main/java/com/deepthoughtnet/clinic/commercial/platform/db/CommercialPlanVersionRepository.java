package com.deepthoughtnet.clinic.commercial.platform.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.PublicationStatus;

public interface CommercialPlanVersionRepository extends JpaRepository<CommercialPlanVersionEntity, UUID> {
    List<CommercialPlanVersionEntity> findByTemplate_IdOrderByVersionNumberDesc(UUID templateId);
    Optional<CommercialPlanVersionEntity> findByTemplate_IdAndVersionNumber(UUID templateId, int versionNumber);
    Optional<CommercialPlanVersionEntity> findTopByTemplate_IdOrderByVersionNumberDesc(UUID templateId);
    long countByStatus(PublicationStatus status);
}
