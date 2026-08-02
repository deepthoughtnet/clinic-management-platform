package com.deepthoughtnet.clinic.discover.publicprofile.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DiscoverPublicProviderProfileRepository extends JpaRepository<DiscoverPublicProviderProfileEntity, UUID>, JpaSpecificationExecutor<DiscoverPublicProviderProfileEntity> {
    Optional<DiscoverPublicProviderProfileEntity> findByProviderId(UUID providerId);
    Optional<DiscoverPublicProviderProfileEntity> findByCanonicalSlug(String canonicalSlug);
    Optional<DiscoverPublicProviderProfileEntity> findFirstBySourceSystemIgnoreCaseAndSourceEntityReference(String sourceSystem, String sourceEntityReference);
    boolean existsByCanonicalSlug(String canonicalSlug);
}
