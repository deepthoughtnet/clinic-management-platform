package com.deepthoughtnet.clinic.identity.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscriptionEntity, UUID> {
    List<TenantSubscriptionEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<TenantSubscriptionEntity> findTopByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
