package com.deepthoughtnet.clinic.discover.publicprofile.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscoverPublicProviderProfileSlugRepository extends JpaRepository<DiscoverPublicProviderProfileSlugEntity, UUID> {
    Optional<DiscoverPublicProviderProfileSlugEntity> findFirstBySlug(String slug);
    List<DiscoverPublicProviderProfileSlugEntity> findByProviderIdOrderByCreatedAtAsc(UUID providerId);
    Optional<DiscoverPublicProviderProfileSlugEntity> findFirstByProviderIdAndActiveTrueOrderByUpdatedAtDesc(UUID providerId);
    boolean existsBySlug(String slug);
}
