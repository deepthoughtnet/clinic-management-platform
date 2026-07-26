package com.deepthoughtnet.clinic.commercial.platform.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialPlanMeteredRateRepository extends JpaRepository<CommercialPlanMeteredRateEntity, UUID> {
    List<CommercialPlanMeteredRateEntity> findByPricing_IdOrderById(UUID pricingId);
}
