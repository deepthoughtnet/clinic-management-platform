package com.deepthoughtnet.clinic.discover.onboarding.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderChangeRequestRepository extends JpaRepository<ProviderChangeRequestEntity, UUID> {
    List<ProviderChangeRequestEntity> findByProviderIdOrderByRequestedAtDesc(UUID providerId);
    Optional<ProviderChangeRequestEntity> findFirstByProviderIdAndResolvedAtIsNullOrderByRequestedAtDesc(UUID providerId);
}
