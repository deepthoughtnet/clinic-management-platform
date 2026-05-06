package com.deepthoughtnet.clinic.notification.service;

import com.deepthoughtnet.clinic.notification.db.NotificationOutboxEntity;
import com.deepthoughtnet.clinic.notification.db.NotificationOutboxRepository;
import com.deepthoughtnet.clinic.notification.model.NotificationEventPayload;
import com.deepthoughtnet.clinic.notification.service.NotificationHistoryService;
import com.deepthoughtnet.clinic.notify.NotificationMessage;
import com.deepthoughtnet.clinic.notify.NotificationProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationOutboxRepository repository;
    private final NotificationRecipientResolver recipientResolver;
    private final NotificationHistoryService notificationHistoryService;
    private final NotificationProvider notificationProvider;
    private final ObjectMapper objectMapper;

    public NotificationDispatcher(
            NotificationOutboxRepository repository,
            NotificationRecipientResolver recipientResolver,
            NotificationHistoryService notificationHistoryService,
            NotificationProvider notificationProvider,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.recipientResolver = recipientResolver;
        this.notificationHistoryService = notificationHistoryService;
        this.notificationProvider = notificationProvider;
        this.objectMapper = objectMapper;
    }

    public List<UUID> findDueNotificationIds(NotificationDispatchSettings settings) {
        List<NotificationOutboxEntity> events = repository
                .findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        "PENDING",
                        OffsetDateTime.now(),
                        PageRequest.of(0, settings.batchSize())
                )
                .getContent();
        return events.stream()
                .map(NotificationOutboxEntity::getId)
                .toList();
    }

    @Transactional
    public void dispatchOne(UUID eventId, NotificationDispatchSettings settings) {
        NotificationOutboxEntity event = repository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Notification outbox event not found: " + eventId));
        if (!"PENDING".equals(event.getStatus())) {
            return;
        }

        event.markProcessing();
        try {
            NotificationEventPayload payload = objectMapper.readValue(
                    event.getPayloadJson(),
                    NotificationEventPayload.class
            );
            List<String> recipients = payload.recipient() != null && !payload.recipient().isBlank()
                    ? List.of(payload.recipient())
                    : recipientResolver.resolveEmailsByRoles(event.getTenantId(), payload.recipientRoles() == null ? List.of() : payload.recipientRoles());
            String channel = payload.channel() == null || payload.channel().isBlank() ? settings.channel() : payload.channel();

            for (String recipient : recipients) {
                notificationProvider.send(new NotificationMessage(
                        event.getTenantId(),
                        channel,
                        recipient,
                        payload.subject(),
                        payload.body(),
                        event.getPayloadJson()
                ));
            }

            if (recipients.isEmpty()) {
                if (payload.historyId() != null) {
                    notificationHistoryService.markSkipped(event.getTenantId(), payload.historyId(), "No active recipient available");
                }
                log.info(
                        "Notification outbox event had no active email recipients. eventId={}, tenantId={}, eventType={}",
                        event.getId(),
                        event.getTenantId(),
                        event.getEventType()
                );
            } else if (payload.historyId() != null) {
                notificationHistoryService.markSent(event.getTenantId(), payload.historyId());
            }
            event.markSucceeded();
        } catch (Exception ex) {
            try {
                NotificationEventPayload payload = objectMapper.readValue(event.getPayloadJson(), NotificationEventPayload.class);
                if (payload.historyId() != null) {
                    notificationHistoryService.markFailed(event.getTenantId(), payload.historyId(), safeMessage(ex));
                }
            } catch (Exception ignored) {
                // preserve outbox failure handling even if payload cannot be parsed
            }
            event.markFailed(
                    safeMessage(ex),
                    settings.maxAttempts(),
                    settings.retryBackoff()
            );
            log.warn(
                    "Notification dispatch failed. eventId={}, tenantId={}, eventType={}, error={}",
                    event.getId(),
                    event.getTenantId(),
                    event.getEventType(),
                    safeMessage(ex)
            );
        }
    }

    private String safeMessage(Exception ex) {
        if (ex == null) {
            return "unknown";
        }
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    public record NotificationDispatchSettings(
            String channel,
            int batchSize,
            int maxAttempts,
            Duration retryBackoff
    ) {
        public NotificationDispatchSettings {
            channel = channel == null || channel.isBlank() ? "email" : channel;
            batchSize = batchSize <= 0 ? 25 : Math.min(batchSize, 100);
            maxAttempts = maxAttempts <= 0 ? 3 : Math.min(maxAttempts, 25);
            retryBackoff = retryBackoff == null || retryBackoff.isNegative() || retryBackoff.isZero()
                    ? Duration.ofMinutes(1)
                    : retryBackoff;
        }
    }
}
