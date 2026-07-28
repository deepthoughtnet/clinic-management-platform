package com.deepthoughtnet.clinic.discover.landingpage.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LandingPageRepository extends JpaRepository<LandingPageEntity, UUID> {
    Optional<LandingPageEntity> findByProviderId(UUID providerId);
    Optional<LandingPageEntity> findByCanonicalSlug(String canonicalSlug);
}
