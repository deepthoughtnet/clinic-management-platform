package com.deepthoughtnet.clinic.notification.service;

import com.deepthoughtnet.clinic.notification.db.NotificationOutboxEntity;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface NotificationCenterService {
    Page<NotificationOutboxEntity> list(UUID tenantId, NotificationFilter filter);

    NotificationOutboxEntity get(UUID tenantId, UUID id);

    NotificationOutboxEntity retry(UUID tenantId, UUID id);

    NotificationOutboxEntity markIgnored(UUID tenantId, UUID id, UUID ignoredByAppUserId);

    NotificationSummary summarize(UUID tenantId);
}
