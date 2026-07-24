package com.deepthoughtnet.clinic.notificationcenter.db;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

public interface StaffNotificationRecipientRepository extends JpaRepository<StaffNotificationRecipientEntity, UUID>, JpaSpecificationExecutor<StaffNotificationRecipientEntity> {

    Page<StaffNotificationRecipientEntity> findByTenantIdAndAppUserIdOrderByCreatedAtDesc(UUID tenantId, UUID appUserId, Pageable pageable);

    Page<StaffNotificationRecipientEntity> findByTenantIdAndAppUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID tenantId, UUID appUserId, Pageable pageable);

    List<StaffNotificationRecipientEntity> findTop10ByTenantIdAndAppUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID tenantId, UUID appUserId);

    Optional<StaffNotificationRecipientEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<StaffNotificationRecipientEntity> findByTenantIdAndIdAndAppUserId(UUID tenantId, UUID id, UUID appUserId);

    Optional<StaffNotificationRecipientEntity> findByTenantIdAndNotification_IdAndAppUserId(UUID tenantId, UUID notificationId, UUID appUserId);

    long countByTenantIdAndAppUserIdAndReadAtIsNull(UUID tenantId, UUID appUserId);
}
