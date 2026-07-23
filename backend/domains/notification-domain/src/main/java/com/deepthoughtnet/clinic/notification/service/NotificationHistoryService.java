package com.deepthoughtnet.clinic.notification.service;

import com.deepthoughtnet.clinic.notification.service.model.NotificationHistoryRecord;
import com.deepthoughtnet.clinic.notification.service.model.NotificationHistoryGroupRecord;
import com.deepthoughtnet.clinic.notification.service.model.NotificationQueueResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;

public interface NotificationHistoryService {
    Page<NotificationHistoryRecord> list(UUID tenantId, NotificationHistoryFilter filter);

    List<NotificationHistoryGroupRecord> listGrouped(UUID tenantId, NotificationHistoryFilter filter);

    Optional<NotificationHistoryRecord> findById(UUID tenantId, UUID id);

    List<NotificationHistoryRecord> listByPatient(UUID tenantId, UUID patientId);

    default NotificationHistoryRecord queue(
            UUID tenantId,
            UUID patientId,
            String eventType,
            String channel,
            String recipient,
            String subject,
            String message,
            String sourceType,
            UUID sourceId,
            UUID actorAppUserId
    ) {
        return queueDetailed(tenantId, patientId, eventType, channel, recipient, channel, recipient, subject, message, sourceType, sourceId, null, actorAppUserId)
                .notification();
    }

    NotificationQueueResult queueDetailed(
            UUID tenantId,
            UUID patientId,
            String eventType,
            String channel,
            String recipient,
            String subject,
            String message,
            String sourceType,
            UUID sourceId,
            UUID actorAppUserId
    );

    NotificationQueueResult queueDetailed(
            UUID tenantId,
            UUID patientId,
            String eventType,
            String historyChannel,
            String historyRecipient,
            String deliveryChannel,
            String deliveryRecipient,
            String subject,
            String message,
            String sourceType,
            UUID sourceId,
            String detailsJson,
            UUID actorAppUserId
    );

    NotificationQueueResult queueDetailed(
            UUID tenantId,
            UUID patientId,
            String eventType,
            String historyChannel,
            String historyRecipient,
            String deliveryChannel,
            String deliveryRecipient,
            String subject,
            String message,
            String sourceType,
            UUID sourceId,
            String detailsJson,
            UUID actorAppUserId,
            String deduplicationKey
    );

    NotificationQueueResult recordSkipped(
            UUID tenantId,
            UUID patientId,
            String eventType,
            String historyChannel,
            String historyRecipient,
            String subject,
            String message,
            String sourceType,
            UUID sourceId,
            String detailsJson,
            UUID actorAppUserId,
            String deduplicationKey,
            String reason
    );

    NotificationHistoryRecord retry(UUID tenantId, UUID id, UUID actorAppUserId);

    NotificationHistoryRecord markSent(UUID tenantId, UUID id);

    NotificationHistoryRecord markFailed(UUID tenantId, UUID id, String reason);

    NotificationHistoryRecord markSkipped(UUID tenantId, UUID id, String reason);

    NotificationHistoryRecord markRead(UUID tenantId, UUID id);

    NotificationHistoryRecord markUnread(UUID tenantId, UUID id);
}
