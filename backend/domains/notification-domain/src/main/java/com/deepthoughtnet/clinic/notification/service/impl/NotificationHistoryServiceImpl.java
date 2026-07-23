package com.deepthoughtnet.clinic.notification.service.impl;

import com.deepthoughtnet.clinic.notification.db.NotificationHistoryEntity;
import com.deepthoughtnet.clinic.notification.db.NotificationHistoryRepository;
import com.deepthoughtnet.clinic.notification.db.NotificationOutboxEntity;
import com.deepthoughtnet.clinic.notification.db.NotificationOutboxRepository;
import com.deepthoughtnet.clinic.notification.model.NotificationEventPayload;
import com.deepthoughtnet.clinic.notification.service.NotificationHistoryFilter;
import com.deepthoughtnet.clinic.notification.service.NotificationHistoryService;
import com.deepthoughtnet.clinic.notification.service.model.NotificationHistoryGroupRecord;
import com.deepthoughtnet.clinic.notification.service.model.NotificationHistoryRecord;
import com.deepthoughtnet.clinic.notification.service.model.NotificationQueueResult;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.outbox.OutboxEventCommand;
import com.deepthoughtnet.clinic.platform.outbox.OutboxEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationHistoryServiceImpl implements NotificationHistoryService {
    private static final String ENTITY_TYPE = "NOTIFICATION_HISTORY";

    private final NotificationHistoryRepository repository;
    private final NotificationOutboxRepository outboxRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public NotificationHistoryServiceImpl(
            NotificationHistoryRepository repository,
            NotificationOutboxRepository outboxRepository,
            OutboxEventPublisher outboxEventPublisher,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.outboxEventPublisher = outboxEventPublisher;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationHistoryRecord> list(UUID tenantId, NotificationHistoryFilter filter) {
        Pageable pageable = PageRequest.of(
                Math.max(0, filter == null ? 0 : filter.page()),
                normalizeSize(filter == null ? 20 : filter.size()),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return repository.findAll(toSpecification(tenantId, filter), pageable).map(this::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationHistoryRecord> findById(UUID tenantId, UUID id) {
        requireTenant(tenantId);
        requireId(id, "id");
        return repository.findByTenantIdAndId(tenantId, id).map(this::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationHistoryRecord> listByPatient(UUID tenantId, UUID patientId) {
        requireTenant(tenantId);
        requireId(patientId, "patientId");
        return repository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId)
                .stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationHistoryGroupRecord> listGrouped(UUID tenantId, NotificationHistoryFilter filter) {
        requireTenant(tenantId);
        List<NotificationHistoryRecord> records = repository.findAll((root, query, cb) -> cb.equal(root.get("tenantId"), tenantId), Sort.by(Sort.Direction.DESC, "createdAt", "id"))
                .stream()
                .map(this::toRecord)
                .toList();
        Map<String, NotificationHistoryGroupBuilder> groups = new LinkedHashMap<>();
        for (NotificationHistoryRecord record : records) {
            String logicalKey = logicalNotificationId(record);
            groups.computeIfAbsent(logicalKey, NotificationHistoryGroupBuilder::new).add(record);
        }
        return groups.values().stream()
                .map(NotificationHistoryGroupBuilder::build)
                .filter(group -> matchesGroupedFilter(group, filter))
                .sorted(Comparator
                        .comparing(NotificationHistoryGroupRecord::lastActivityAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(NotificationHistoryGroupRecord::queuedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(NotificationHistoryGroupRecord::logicalNotificationId))
                .toList();
    }

    @Override
    @Transactional
    public NotificationQueueResult queueDetailed(
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
        return queueDetailed(
                tenantId,
                patientId,
                eventType,
                channel,
                recipient,
                channel,
                recipient,
                subject,
                message,
                sourceType,
                sourceId,
                null,
                actorAppUserId
        );
    }

    @Override
    @Transactional
    public NotificationQueueResult queueDetailed(
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
    ) {
        return queueDetailed(
                tenantId,
                patientId,
                eventType,
                historyChannel,
                historyRecipient,
                deliveryChannel,
                deliveryRecipient,
                subject,
                message,
                sourceType,
                sourceId,
                detailsJson,
                actorAppUserId,
                null
        );
    }

    @Override
    @Transactional
    public NotificationQueueResult queueDetailed(
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
    ) {
        requireTenant(tenantId);
        validate(eventType, historyChannel, historyRecipient, message);
        return queueLifecycle(
                tenantId,
                patientId,
                eventType,
                historyChannel,
                historyRecipient,
                deliveryChannel,
                deliveryRecipient,
                subject,
                message,
                sourceType,
                sourceId,
                detailsJson,
                actorAppUserId,
                deduplicationKey,
                true,
                null
        );
    }

    @Override
    @Transactional
    public NotificationQueueResult recordSkipped(
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
    ) {
        requireTenant(tenantId);
        validate(eventType, historyChannel, historyRecipient, message);
        return queueLifecycle(
                tenantId,
                patientId,
                eventType,
                historyChannel,
                historyRecipient,
                historyChannel,
                historyRecipient,
                subject,
                message,
                sourceType,
                sourceId,
                detailsJson,
                actorAppUserId,
                deduplicationKey,
                false,
                reason
        );
    }

    @Override
    @Transactional
    public NotificationHistoryRecord retry(UUID tenantId, UUID id, UUID actorAppUserId) {
        NotificationHistoryEntity entity = findEntity(tenantId, id);
        if (!"FAILED".equals(entity.getStatus()) && !"SKIPPED".equals(entity.getStatus())) {
            throw new IllegalArgumentException("Only failed or skipped notifications can be retried");
        }
        entity.retryNow();
        NotificationHistoryEntity saved = repository.save(entity);
        UUID outboxId = outboxEventPublisher.publish(new OutboxEventCommand(
                tenantId,
                "NOTIFICATION." + saved.getEventType(),
                ENTITY_TYPE,
                saved.getId(),
                saved.getDeduplicationKey() + ":retry:" + saved.getAttemptCount(),
                payloadJsonForRetry(saved),
                OffsetDateTime.now()
        ));
        if (outboxId != null) {
            saved.attachOutboxEvent(outboxId);
            repository.save(saved);
        }
        audit(tenantId, saved, "notification.retried", actorAppUserId, "Retried notification");
        return toRecord(saved);
    }

    @Override
    @Transactional
    public NotificationHistoryRecord markSent(UUID tenantId, UUID id) {
        NotificationHistoryEntity entity = findEntity(tenantId, id);
        entity.markSent();
        return toRecord(repository.save(entity));
    }

    @Override
    @Transactional
    public NotificationHistoryRecord markFailed(UUID tenantId, UUID id, String reason) {
        NotificationHistoryEntity entity = findEntity(tenantId, id);
        entity.markFailed(reason);
        return toRecord(repository.save(entity));
    }

    @Override
    @Transactional
    public NotificationHistoryRecord markSkipped(UUID tenantId, UUID id, String reason) {
        NotificationHistoryEntity entity = findEntity(tenantId, id);
        entity.markSkipped(reason);
        return toRecord(repository.save(entity));
    }

    @Override
    @Transactional
    public NotificationHistoryRecord markRead(UUID tenantId, UUID id) {
        NotificationHistoryEntity entity = findEntity(tenantId, id);
        entity.markRead();
        return toRecord(repository.save(entity));
    }

    @Override
    @Transactional
    public NotificationHistoryRecord markUnread(UUID tenantId, UUID id) {
        NotificationHistoryEntity entity = findEntity(tenantId, id);
        entity.markUnread();
        return toRecord(repository.save(entity));
    }

    private NotificationHistoryEntity findEntity(UUID tenantId, UUID id) {
        requireTenant(tenantId);
        requireId(id, "id");
        return repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
    }

    private Specification<NotificationHistoryEntity> toSpecification(UUID tenantId, NotificationHistoryFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (filter != null) {
                if (hasText(filter.status())) {
                    predicates.add(cb.equal(root.get("status"), filter.status().trim().toUpperCase(Locale.ROOT)));
                }
                if (hasText(filter.eventType())) {
                    predicates.add(cb.equal(root.get("eventType"), filter.eventType().trim().toUpperCase(Locale.ROOT)));
                }
                if (hasText(filter.channel())) {
                    predicates.add(cb.equal(root.get("channel"), filter.channel().trim().toLowerCase(Locale.ROOT)));
                }
                if (filter.patientId() != null) {
                    predicates.add(cb.equal(root.get("patientId"), filter.patientId()));
                }
                if (filter.from() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
                }
                if (filter.to() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.to()));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private NotificationHistoryRecord toRecord(NotificationHistoryEntity entity) {
        return new NotificationHistoryRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getPatientId(),
                entity.getEventType(),
                entity.getChannel(),
                entity.getRecipient(),
                entity.getSubject(),
                entity.getMessage(),
                entity.getStatus(),
                entity.getFailureReason(),
                entity.getSourceType(),
                entity.getSourceId(),
                entity.getDeduplicationKey(),
                entity.getOutboxEventId(),
                entity.getAttemptCount(),
                entity.getSentAt(),
                entity.getReadAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private boolean matchesGroupedFilter(NotificationHistoryGroupRecord group, NotificationHistoryFilter filter) {
        if (filter == null) {
            return true;
        }
        if (hasText(filter.status()) && !group.overallStatus().equalsIgnoreCase(filter.status().trim())) {
            return false;
        }
        if (hasText(filter.eventType()) && !group.eventType().equalsIgnoreCase(filter.eventType().trim())) {
            return false;
        }
        if (filter.patientId() != null && !filter.patientId().equals(group.patientId())) {
            return false;
        }
        if (filter.from() != null && group.queuedAt() != null && group.queuedAt().isBefore(filter.from())) {
            return false;
        }
        if (filter.to() != null && group.queuedAt() != null && group.queuedAt().isAfter(filter.to())) {
            return false;
        }
        if (hasText(filter.channel())) {
            String requested = filter.channel().trim().toUpperCase(Locale.ROOT);
            boolean channelMatched = group.deliveries().stream()
                    .anyMatch(record -> record.channel().equalsIgnoreCase(requested));
            if (!channelMatched) {
                return false;
            }
        }
        return true;
    }

    private static String logicalNotificationId(NotificationHistoryRecord record) {
        String deduplicationKey = record.deduplicationKey();
        if (!hasText(deduplicationKey)) {
            return record.id().toString();
        }
        String[] tokens = deduplicationKey.split(":");
        if (tokens.length >= 8 && looksLikeUuid(tokens[1]) && looksLikeUuid(tokens[2]) && looksLikeUuid(tokens[3])) {
            return String.join(":", tokens[0], tokens[1], tokens[2], tokens[3]);
        }
        return deduplicationKey;
    }

    private static boolean looksLikeUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String payloadJson(
            NotificationHistoryEntity entity,
            String deliveryChannel,
            String deliveryRecipient,
            String detailsJson
    ) {
        try {
            return objectMapper.writeValueAsString(new NotificationEventPayload(
                    entity.getId(),
                    entity.getEventType(),
                    List.of(),
                    normalizeNullable(deliveryRecipient),
                    normalizeNullable(deliveryChannel) == null ? entity.getChannel() : deliveryChannel.trim().toLowerCase(Locale.ROOT),
                    entity.getSubject(),
                    entity.getMessage(),
                    "notification.sent",
                    detailsJson,
                    entity.getPatientId(),
                    entity.getSourceType(),
                    entity.getSourceId()
            ));
        } catch (JsonProcessingException ex) {
            return "{\"historyId\":\"" + entity.getId() + "\"}";
        }
    }

    private String payloadJsonForRetry(NotificationHistoryEntity entity) {
        if (entity.getOutboxEventId() != null) {
            return outboxRepository.findByTenantIdAndId(entity.getTenantId(), entity.getOutboxEventId())
                    .map(NotificationOutboxEntity::getPayloadJson)
                    .orElseGet(() -> payloadJson(entity, entity.getChannel(), entity.getRecipient(), null));
        }
        return payloadJson(entity, entity.getChannel(), entity.getRecipient(), null);
    }

    private void validate(String eventType, String channel, String recipient, String message) {
        if (!hasText(eventType)) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (!hasText(channel)) {
            throw new IllegalArgumentException("channel is required");
        }
        if (!hasText(recipient)) {
            throw new IllegalArgumentException("recipient is required");
        }
        if (!hasText(message)) {
            throw new IllegalArgumentException("message is required");
        }
    }

    private String buildDeduplicationKey(UUID tenantId, String eventType, UUID patientId, String recipient, String sourceType, UUID sourceId) {
        return buildDeduplicationKey(tenantId, eventType, null, patientId, recipient, sourceType, sourceId);
    }

    private String buildDeduplicationKey(UUID tenantId, String eventType, String channel, UUID patientId, String recipient, String sourceType, UUID sourceId) {
        return tenantId
                + ":"
                + normalizeToken(eventType)
                + ":"
                + normalizeToken(channel)
                + ":"
                + normalizeToken(patientId == null ? null : patientId.toString())
                + ":"
                + normalizeToken(recipient)
                + ":"
                + normalizeToken(sourceType)
                + ":"
                + normalizeToken(sourceId == null ? null : sourceId.toString());
    }

    private NotificationQueueResult queueLifecycle(
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
            String deduplicationKey,
            boolean queueOutbox,
            String skipReason
    ) {
        String normalizedEventType = eventType.trim().toUpperCase(Locale.ROOT);
        String normalizedHistoryChannel = historyChannel.trim().toLowerCase(Locale.ROOT);
        String normalizedHistoryRecipient = historyRecipient.trim();
        String effectiveDeduplicationKey = hasText(deduplicationKey)
                ? deduplicationKey.trim()
                : buildDeduplicationKey(tenantId, normalizedEventType, normalizedHistoryChannel, patientId, normalizedHistoryRecipient, sourceType, sourceId);
        NotificationHistoryEntity existing = repository.findByTenantIdAndDeduplicationKey(tenantId, effectiveDeduplicationKey).orElse(null);
        if (existing != null) {
            return new NotificationQueueResult(toRecord(existing), false);
        }

        NotificationHistoryEntity entity = NotificationHistoryEntity.create(
                tenantId,
                patientId,
                normalizedEventType,
                normalizedHistoryChannel,
                normalizedHistoryRecipient,
                normalizeNullable(subject),
                message.trim(),
                normalizeNullable(sourceType),
                sourceId,
                effectiveDeduplicationKey
        );

        if (!queueOutbox) {
            entity.markSkipped(hasText(skipReason) ? skipReason.trim() : "Skipped");
            NotificationHistoryEntity saved = repository.save(entity);
            audit(tenantId, saved, "notification.skipped", actorAppUserId, "Skipped notification");
            return new NotificationQueueResult(toRecord(saved), true);
        }

        NotificationHistoryEntity saved = repository.save(entity);
        UUID outboxId = outboxEventPublisher.publish(new OutboxEventCommand(
                tenantId,
                "NOTIFICATION." + entity.getEventType(),
                ENTITY_TYPE,
                saved.getId(),
                saved.getDeduplicationKey(),
                payloadJson(saved, deliveryChannel, deliveryRecipient, detailsJson),
                OffsetDateTime.now()
        ));
        if (outboxId != null) {
            saved.attachOutboxEvent(outboxId);
            repository.save(saved);
        }
        audit(tenantId, saved, "notification.created", actorAppUserId, "Queued notification");
        return new NotificationQueueResult(toRecord(saved), true);
    }

    private static String normalizeToken(String value) {
        return value == null || value.isBlank() ? "-" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalizeNullable(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static final class NotificationHistoryGroupBuilder {
        private final String logicalNotificationId;
        private final List<NotificationHistoryRecord> deliveries = new ArrayList<>();
        private UUID tenantId;
        private UUID patientId;
        private String eventType;
        private String subject;
        private String message;
        private OffsetDateTime queuedAt;
        private OffsetDateTime lastActivityAt;
        private OffsetDateTime readAt;

        private NotificationHistoryGroupBuilder(String logicalNotificationId) {
            this.logicalNotificationId = logicalNotificationId;
        }

        private void add(NotificationHistoryRecord record) {
            deliveries.add(record);
            if (tenantId == null) {
                tenantId = record.tenantId();
            }
            if (patientId == null) {
                patientId = record.patientId();
            }
            if (eventType == null) {
                eventType = record.eventType();
            }
            if (subject == null && hasText(record.subject())) {
                subject = record.subject();
            }
            if (message == null && hasText(record.message())) {
                message = record.message();
            }
            queuedAt = min(queuedAt, record.createdAt());
            lastActivityAt = max(lastActivityAt, record.sentAt(), record.updatedAt(), record.createdAt());
            readAt = max(readAt, record.readAt());
        }

        private NotificationHistoryGroupRecord build() {
            Comparator<NotificationHistoryRecord> deliveryComparator = Comparator
                    .comparingInt((NotificationHistoryRecord record) -> channelOrder(record.channel()))
                    .thenComparing(Comparator.comparing(NotificationHistoryRecord::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .thenComparing(NotificationHistoryRecord::id);
            List<NotificationHistoryRecord> orderedDeliveries = deliveries.stream()
                    .sorted(deliveryComparator)
                    .toList();
            return new NotificationHistoryGroupRecord(
                    logicalNotificationId,
                    tenantId,
                    patientId,
                    eventType,
                    subject,
                    message,
                    overallStatus(orderedDeliveries),
                    readAt == null ? "UNREAD" : "READ",
                    queuedAt,
                    lastActivityAt,
                    orderedDeliveries
            );
        }

        private OffsetDateTime min(OffsetDateTime current, OffsetDateTime candidate) {
            if (candidate == null) {
                return current;
            }
            if (current == null || candidate.isBefore(current)) {
                return candidate;
            }
            return current;
        }

        private OffsetDateTime max(OffsetDateTime current, OffsetDateTime... candidates) {
            OffsetDateTime result = current;
            for (OffsetDateTime candidate : candidates) {
                if (candidate == null) {
                    continue;
                }
                if (result == null || candidate.isAfter(result)) {
                    result = candidate;
                }
            }
            return result;
        }
    }

    private static String overallStatus(List<NotificationHistoryRecord> deliveries) {
        boolean hasSent = deliveries.stream().anyMatch(record -> "SENT".equalsIgnoreCase(record.status()));
        boolean hasFailed = deliveries.stream().anyMatch(record -> "FAILED".equalsIgnoreCase(record.status()));
        boolean hasPending = deliveries.stream().anyMatch(record -> "PENDING".equalsIgnoreCase(record.status()));
        boolean allSkipped = !deliveries.isEmpty() && deliveries.stream().allMatch(record -> "SKIPPED".equalsIgnoreCase(record.status()));

        if (hasSent && hasFailed) {
            return "PARTIAL";
        }
        if (hasSent && hasPending) {
            return "PENDING";
        }
        if (hasSent) {
            return "DELIVERED";
        }
        if (hasPending) {
            return "PENDING";
        }
        if (hasFailed) {
            return "FAILED";
        }
        if (allSkipped) {
            return "NOT_DELIVERED";
        }
        return "NOT_DELIVERED";
    }

    private static int channelOrder(String channel) {
        if (channel == null) {
            return Integer.MAX_VALUE;
        }
        return switch (channel.toLowerCase(Locale.ROOT)) {
            case "in_app" -> 0;
            case "email" -> 1;
            case "sms" -> 2;
            case "whatsapp" -> 3;
            default -> 10;
        };
    }

    private void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
    }

    private void requireId(UUID id, String field) {
        if (id == null) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private int normalizeSize(int size) {
        return size <= 0 ? 20 : Math.min(size, 100);
    }

    private void audit(UUID tenantId, NotificationHistoryEntity entity, String action, UUID actorAppUserId, String message) {
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                ENTITY_TYPE,
                entity.getId(),
                action,
                actorAppUserId,
                OffsetDateTime.now(),
                message,
                payloadJson(entity, entity.getChannel(), entity.getRecipient(), null)
        ));
    }
}
