package com.deepthoughtnet.clinic.notificationcenter.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffNotificationRepository extends JpaRepository<StaffNotificationEntity, UUID> {
    Optional<StaffNotificationEntity> findByTenantIdAndSourceEventId(UUID tenantId, UUID sourceEventId);

    Optional<StaffNotificationEntity> findByTenantIdAndId(UUID tenantId, UUID id);
}
