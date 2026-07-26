package com.deepthoughtnet.clinic.commercial.platform.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialPlanPricingRepository extends JpaRepository<CommercialPlanPricingEntity, UUID> {
    Optional<CommercialPlanPricingEntity> findByPublishedVersion_Id(UUID publishedVersionId);
}
