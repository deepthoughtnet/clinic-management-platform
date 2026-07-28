package com.deepthoughtnet.clinic.discover.landingpage.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LandingPageVersionRepository extends JpaRepository<LandingPageVersionEntity, UUID> {
    List<LandingPageVersionEntity> findByProviderIdOrderByVersionNumberDesc(UUID providerId);
    Optional<LandingPageVersionEntity> findFirstByProviderIdOrderByVersionNumberDesc(UUID providerId);
    Optional<LandingPageVersionEntity> findByProviderIdAndVersionNumber(UUID providerId, int versionNumber);
    Optional<LandingPageVersionEntity> findFirstByProviderIdAndSnapshotHashOrderByVersionNumberDesc(UUID providerId, String snapshotHash);
}
