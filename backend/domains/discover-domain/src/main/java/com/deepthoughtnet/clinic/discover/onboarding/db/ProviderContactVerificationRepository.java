package com.deepthoughtnet.clinic.discover.onboarding.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderContactVerificationRepository extends JpaRepository<ProviderContactVerificationEntity, UUID> {
    Optional<ProviderContactVerificationEntity> findByProviderId(UUID providerId);
}
