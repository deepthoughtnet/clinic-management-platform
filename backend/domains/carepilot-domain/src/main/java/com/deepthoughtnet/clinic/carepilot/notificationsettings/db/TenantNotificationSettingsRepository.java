package com.deepthoughtnet.clinic.carepilot.notificationsettings.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantNotificationSettingsRepository extends JpaRepository<TenantNotificationSettingsEntity, UUID> {
    Optional<TenantNotificationSettingsEntity> findByTenantId(UUID tenantId);
}
