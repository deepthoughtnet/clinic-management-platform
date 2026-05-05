package com.deepthoughtnet.clinic.identity.db;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TenantMembershipRepository extends JpaRepository<TenantMembershipEntity, UUID> {
    Optional<TenantMembershipEntity> findByTenantIdAndAppUserId(UUID tenantId, UUID appUserId);

    List<TenantMembershipEntity> findByTenantId(UUID tenantId);

    @Query("""
            select m
            from TenantMembershipEntity m
            join AppUserEntity u on u.id = m.appUserId and u.tenantId = m.tenantId
            where u.keycloakSub = :keycloakSub
              and upper(u.status) = 'ACTIVE'
              and upper(m.status) = 'ACTIVE'
            order by m.createdAt asc
            """)
    List<TenantMembershipEntity> findActiveByKeycloakSub(String keycloakSub);
}
