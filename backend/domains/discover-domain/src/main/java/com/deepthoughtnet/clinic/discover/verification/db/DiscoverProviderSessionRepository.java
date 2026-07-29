package com.deepthoughtnet.clinic.discover.verification.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscoverProviderSessionRepository extends JpaRepository<DiscoverProviderSessionEntity, UUID> {
    Optional<DiscoverProviderSessionEntity> findBySessionTokenHash(String sessionTokenHash);
    List<DiscoverProviderSessionEntity> findByProviderAccountIdOrderByCreatedAtDesc(UUID providerAccountId);
}
