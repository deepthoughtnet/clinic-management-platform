package com.deepthoughtnet.clinic.discover.onboarding.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderServiceRepository extends JpaRepository<ProviderServiceEntity, UUID> {
    List<ProviderServiceEntity> findByProviderIdOrderByLabelAsc(UUID providerId);
    void deleteByProviderId(UUID providerId);
}
