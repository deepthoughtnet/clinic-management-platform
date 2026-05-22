package com.deepthoughtnet.clinic.inventory.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacyCashierShiftRepository extends JpaRepository<PharmacyCashierShiftEntity, UUID> {
    boolean existsByTenantIdAndCashierUserIdAndStatus(UUID tenantId, UUID cashierUserId, String status);
    Optional<PharmacyCashierShiftEntity> findByTenantIdAndCashierUserIdAndStatus(UUID tenantId, UUID cashierUserId, String status);
    Optional<PharmacyCashierShiftEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    List<PharmacyCashierShiftEntity> findByTenantIdOrderByOpenedAtDesc(UUID tenantId);
    List<PharmacyCashierShiftEntity> findByTenantIdAndCashierUserIdOrderByOpenedAtDesc(UUID tenantId, UUID cashierUserId);
}
