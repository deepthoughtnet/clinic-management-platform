package com.deepthoughtnet.clinic.notification.service.impl;

import com.deepthoughtnet.clinic.notification.db.NotificationHistoryEntity;
import com.deepthoughtnet.clinic.notification.db.NotificationHistoryRepository;
import com.deepthoughtnet.clinic.notification.model.NotificationEventPayload;
import com.deepthoughtnet.clinic.notification.service.NotificationHistoryFilter;
import com.deepthoughtnet.clinic.notification.service.NotificationHistoryService;
import com.deepthoughtnet.clinic.notification.service.model.NotificationHistoryRecord;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.outbox.OutboxEventCommand;
import com.deepthoughtnet.clinic.platform.outbox.OutboxEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private final OutboxEventPublisher outboxEventPublisher;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public NotificationHistoryServiceImpl(
            NotificationHistoryRepository repository,
            OutboxEventPublisher outboxEventPublisher,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
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
    @Transactional
    public NotificationHistoryRecord queue(
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
        requireTenant(tenantId);
        validate(eventType, channel, recipient, message);
        String deduplicationKey = buildDeduplicationKey(tenantId, eventType, patientId, recipient, sourceType, sourceId);
        NotificationHistoryEntity existing = repository.findByTenantIdAndDeduplicationKey(tenantId, deduplicationKey).orElse(null);
        if (existing != null) {
            return toRecord(existing);
        }

        NotificationHistoryEntity entity = NotificationHistoryEntity.create(
                tenantId,
                patientId,
                eventType.trim().toUpperCase(Locale.ROOT),
                channel.trim().toLowerCase(Locale.ROOT),
                recipient.trim(),
                normalizeNullable(subject),
                message.trim(),
                normalizeNullable(sourceType),
                sourceId,
                deduplicationKey
        );
        NotificationHistoryEntity saved = repository.save(entity);
        UUID outboxId = outboxEventPublisher.publish(new OutboxEventCommand(
                tenantId,
                "NOTIFICATION." + entity.getEventType(),
                ENTITY_TYPE,
                saved.getId(),
                saved.getDeduplicationKey(),
                payloadJson(saved),
                OffsetDateTime.now()
        ));
        if (outboxId != null) {
            saved.attachOutboxEvent(outboxId);
            repository.save(saved);
        }
        audit(tenantId, saved, "notification.created", actorAppUserId, "Queued notification");
        return toRecord(saved);
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
                payloadJson(saved),
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
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String payloadJson(NotificationHistoryEntity entity) {
        try {
            return objectMapper.writeValueAsString(new NotificationEventPayload(
                    entity.getId(),
                    entity.getEventType(),
                    List.of(),
                    entity.getRecipient(),
                    entity.getChannel(),
                    entity.getSubject(),
                    entity.getMessage(),
                    "notification.sent",
                    null,
                    entity.getPatientId(),
                    entity.getSourceType(),
                    entity.getSourceId()
            ));
        } catch (JsonProcessingException ex) {
            return "{\"historyId\":\"" + entity.getId() + "\"}";
        }
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
        return tenantId + ":" + normalizeToken(eventType) + ":" + normalizeToken(patientId == null ? null : patientId.toString()) + ":" + normalizeToken(recipient) + ":" + normalizeToken(sourceType) + ":" + normalizeToken(sourceId == null ? null : sourceId.toString());
    }

    private String normalizeToken(String value) {
        return value == null || value.isBlank() ? "-" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeNullable(String value) {
        return hasText(value) ? value.trim() : null;
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
                payloadJson(entity)
        ));
    }
}
