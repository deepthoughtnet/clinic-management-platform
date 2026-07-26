package com.deepthoughtnet.clinic.commercial.entitlement.db;

import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.SnapshotStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialEffectiveEntitlementSnapshotRepository extends JpaRepository<CommercialEffectiveEntitlementSnapshotEntity, UUID> {
    Optional<CommercialEffectiveEntitlementSnapshotEntity> findTopByTenantIdAndSnapshotStatusOrderByGeneratedAtDesc(UUID tenantId, SnapshotStatus snapshotStatus);

    List<CommercialEffectiveEntitlementSnapshotEntity> findByTenantIdOrderByGeneratedAtDesc(UUID tenantId);

    long countBySnapshotStatus(SnapshotStatus snapshotStatus);

    long countByTenantIdAndSnapshotStatus(UUID tenantId, SnapshotStatus snapshotStatus);
}
