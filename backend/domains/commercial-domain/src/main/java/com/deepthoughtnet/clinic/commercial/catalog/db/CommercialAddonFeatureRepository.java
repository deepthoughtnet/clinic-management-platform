package com.deepthoughtnet.clinic.commercial.catalog.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialAddonFeatureRepository extends JpaRepository<CommercialAddonFeatureEntity, CommercialAddonFeatureId> {
    List<CommercialAddonFeatureEntity> findByAddon_Id(UUID addonId);

    void deleteByAddon_Id(UUID addonId);
}
