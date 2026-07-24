package com.deepthoughtnet.clinic.commercial.catalog.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialAddonCapabilityRepository extends JpaRepository<CommercialAddonCapabilityEntity, CommercialAddonCapabilityId> {
    List<CommercialAddonCapabilityEntity> findByAddon_Id(UUID addonId);

    void deleteByAddon_Id(UUID addonId);
}
