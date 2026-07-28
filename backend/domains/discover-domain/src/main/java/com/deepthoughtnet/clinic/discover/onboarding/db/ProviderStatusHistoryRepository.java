package com.deepthoughtnet.clinic.discover.onboarding.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderStatusHistoryRepository extends JpaRepository<ProviderStatusHistoryEntity, UUID> {
    List<ProviderStatusHistoryEntity> findByProviderIdOrderByCreatedAtAsc(UUID providerId);
}
