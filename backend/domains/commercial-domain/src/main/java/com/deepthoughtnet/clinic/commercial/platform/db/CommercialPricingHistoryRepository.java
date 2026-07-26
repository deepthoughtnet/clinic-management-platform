package com.deepthoughtnet.clinic.commercial.platform.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialPricingHistoryRepository extends JpaRepository<CommercialPricingHistoryEntity, UUID> {
    List<CommercialPricingHistoryEntity> findByPublishedVersion_Template_IdOrderByCreatedAtDesc(UUID templateId);
}
