package com.deepthoughtnet.clinic.commercial.catalog.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CommercialAddonOfferRepository extends JpaRepository<CommercialAddonOfferEntity, UUID>, JpaSpecificationExecutor<CommercialAddonOfferEntity> {
    boolean existsByCodeIgnoreCase(String code);

    Optional<CommercialAddonOfferEntity> findByCodeIgnoreCase(String code);
}
