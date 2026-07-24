package com.deepthoughtnet.clinic.notificationcenter.service;

import com.deepthoughtnet.clinic.notificationcenter.db.StaffNotificationEntity;
import com.deepthoughtnet.clinic.notificationcenter.db.StaffNotificationRecipientEntity;
import com.deepthoughtnet.clinic.notificationcenter.db.StaffNotificationRecipientRepository;
import com.deepthoughtnet.clinic.notificationcenter.db.StaffNotificationRepository;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterAudienceResolver.ResolvedAudienceRecipient;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationAction;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationPublisher;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationRequest;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class StaffNotificationProjectionService implements StaffNotificationPublisher {
    private static final Logger log = LoggerFactory.getLogger(StaffNotificationProjectionService.class);

    private final StaffNotificationRepository notificationRepository;
    private final StaffNotificationRecipientRepository recipientRepository;
    private final NotificationCenterAudienceResolver audienceResolver;

    public StaffNotificationProjectionService(
            StaffNotificationRepository notificationRepository,
            StaffNotificationRecipientRepository recipientRepository,
            NotificationCenterAudienceResolver audienceResolver
    ) {
        this.notificationRepository = notificationRepository;
        this.recipientRepository = recipientRepository;
        this.audienceResolver = audienceResolver;
    }

    @Override
    @Transactional
    public UUID publish(StaffNotificationRequest request) {
        if (request == null || request.tenantId() == null || request.sourceEventId() == null) {
            throw new IllegalArgumentException("Notification request is incomplete");
        }

        StaffNotificationEntity notification = notificationRepository.findByTenantIdAndSourceEventId(request.tenantId(), request.sourceEventId())
                .orElseGet(() -> createNotification(request));

        List<ResolvedAudienceRecipient> recipients = audienceResolver.resolve(request.tenantId(), request.audiences());
        if (recipients.isEmpty()) {
            log.warn(
                    "staff_notification_projection_no_recipients tenantId={} eventId={} sourceEventType={}",
                    request.tenantId(),
                    request.sourceEventId(),
                    request.sourceEventType()
            );
            return notification.getId();
        }

        for (ResolvedAudienceRecipient recipient : recipients) {
            if (recipient == null || recipient.appUserId() == null) {
                continue;
            }
            if (recipientRepository.findByTenantIdAndNotification_IdAndAppUserId(request.tenantId(), notification.getId(), recipient.appUserId()).isPresent()) {
                continue;
            }
            try {
                recipientRepository.save(StaffNotificationRecipientEntity.create(
                        request.tenantId(),
                        notification,
                        recipient.appUserId(),
                        recipient.displayName(),
                        recipient.role(),
                        recipient.matchedAudience(),
                        request.title(),
                        request.preview(),
                        request.category(),
                        request.priority(),
                        request.businessReference(),
                        actionLabel(request.action()),
                        actionRoute(request.action()),
                        actionTargetId(request.action()),
                        request.sourceEventId(),
                        request.sourceEventType(),
                        request.sourceModule(),
                        request.aggregateType(),
                        request.aggregateId(),
                        request.correlationId(),
                        request.causationId(),
                        request.occurredAt()
                ));
            } catch (DataIntegrityViolationException ex) {
                log.info(
                        "staff_notification_projection_duplicate tenantId={} notificationId={} appUserId={} sourceEventId={}",
                        request.tenantId(),
                        notification.getId(),
                        recipient.appUserId(),
                        request.sourceEventId()
                );
            }
        }

        return notification.getId();
    }

    private StaffNotificationEntity createNotification(StaffNotificationRequest request) {
        try {
            return notificationRepository.save(StaffNotificationEntity.create(
                    request.tenantId(),
                    request.sourceEventId(),
                    request.sourceEventType(),
                    request.sourceModule(),
                    request.aggregateType(),
                    request.aggregateId(),
                    request.category(),
                    request.priority(),
                    request.title(),
                    request.preview(),
                    request.businessReference(),
                    actionLabel(request.action()),
                    actionRoute(request.action()),
                    actionTargetId(request.action()),
                    request.correlationId(),
                    request.causationId(),
                    request.occurredAt()
            ));
        } catch (DataIntegrityViolationException ex) {
            return notificationRepository.findByTenantIdAndSourceEventId(request.tenantId(), request.sourceEventId())
                    .orElseThrow(() -> ex);
        }
    }

    private String actionLabel(StaffNotificationAction action) {
        return action == null ? null : action.label();
    }

    private String actionRoute(StaffNotificationAction action) {
        return action == null ? null : action.routeKey();
    }

    private UUID actionTargetId(StaffNotificationAction action) {
        if (action == null || action.targetId() == null || action.targetId().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(action.targetId());
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
