package com.deepthoughtnet.clinic.identity.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantOnboardingRepository extends JpaRepository<TenantOnboardingEntity, UUID> {
    Optional<TenantOnboardingEntity> findByTenantId(UUID tenantId);
}
