package com.deepthoughtnet.clinic.commercial.platform.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialPlanDraftRepository extends JpaRepository<CommercialPlanDraftEntity, UUID> {
    Optional<CommercialPlanDraftEntity> findByTemplate_Id(UUID templateId);
}
