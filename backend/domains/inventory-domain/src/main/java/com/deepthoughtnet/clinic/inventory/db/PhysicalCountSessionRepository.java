package com.deepthoughtnet.clinic.inventory.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhysicalCountSessionRepository extends JpaRepository<PhysicalCountSessionEntity, UUID> {
    List<PhysicalCountSessionEntity> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId);
    Optional<PhysicalCountSessionEntity> findByTenantIdAndId(UUID tenantId, UUID id);
}
