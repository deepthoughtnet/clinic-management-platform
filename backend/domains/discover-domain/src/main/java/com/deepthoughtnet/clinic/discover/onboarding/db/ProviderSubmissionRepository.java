package com.deepthoughtnet.clinic.discover.onboarding.db;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderSubmissionRepository extends JpaRepository<ProviderSubmissionEntity, UUID> {
    int countByProviderId(UUID providerId);
    java.util.List<ProviderSubmissionEntity> findByProviderIdOrderByVersionNumberDesc(UUID providerId);
    java.util.Optional<ProviderSubmissionEntity> findFirstByProviderIdOrderByVersionNumberDesc(UUID providerId);
    java.util.Optional<ProviderSubmissionEntity> findFirstByProviderIdAndSnapshotHashOrderByVersionNumberDesc(UUID providerId, String snapshotHash);
}
