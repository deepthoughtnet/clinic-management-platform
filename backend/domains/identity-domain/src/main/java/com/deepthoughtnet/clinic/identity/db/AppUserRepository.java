package com.deepthoughtnet.clinic.identity.db;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUserEntity, UUID> {

    Optional<AppUserEntity> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

    Optional<AppUserEntity> findByTenantIdAndKeycloakSub(UUID tenantId, String keycloakSub);

    // ✅ Used by driver APIs (RequestContext gives appUserId)
    Optional<AppUserEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<AppUserEntity> findByTenantIdAndIdIn(UUID tenantId, List<UUID> ids);

    List<AppUserEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    @org.springframework.data.jpa.repository.Query(
            value = "select exists(select 1 from app_users where tenant_id = :tenantId and driver_id = :driverId)",
            nativeQuery = true
    )
    boolean existsByTenantIdAndDriverId(UUID tenantId, UUID driverId);
}
