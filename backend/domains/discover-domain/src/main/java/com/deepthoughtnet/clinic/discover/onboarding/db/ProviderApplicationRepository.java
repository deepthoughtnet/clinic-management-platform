package com.deepthoughtnet.clinic.discover.onboarding.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderApplicationRepository extends JpaRepository<ProviderApplicationEntity, UUID> {
    Optional<ProviderApplicationEntity> findByTokenHash(String tokenHash);
}
