package com.deepthoughtnet.clinic.commercial.catalog.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialAddonLimitIncrementRepository extends JpaRepository<CommercialAddonLimitIncrementEntity, CommercialAddonLimitIncrementId> {
    List<CommercialAddonLimitIncrementEntity> findByAddon_Id(UUID addonId);

    void deleteByAddon_Id(UUID addonId);
}
