package com.deepthoughtnet.clinic.commercial.platform.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialPlanAddonPricingRepository extends JpaRepository<CommercialPlanAddonPricingEntity, UUID> {
    List<CommercialPlanAddonPricingEntity> findByPricing_IdOrderById(UUID pricingId);
}
