package com.deepthoughtnet.clinic.discover.publicprofile.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscoverPublicProviderProfileVersionRepository extends JpaRepository<DiscoverPublicProviderProfileVersionEntity, UUID> {
    List<DiscoverPublicProviderProfileVersionEntity> findByProviderIdOrderByVersionNumberDesc(UUID providerId);
    Optional<DiscoverPublicProviderProfileVersionEntity> findFirstByProviderIdOrderByVersionNumberDesc(UUID providerId);
    Optional<DiscoverPublicProviderProfileVersionEntity> findByProviderIdAndVersionNumber(UUID providerId, int versionNumber);
    Optional<DiscoverPublicProviderProfileVersionEntity> findFirstByProviderIdAndSnapshotHashOrderByVersionNumberDesc(UUID providerId, String snapshotHash);
}
