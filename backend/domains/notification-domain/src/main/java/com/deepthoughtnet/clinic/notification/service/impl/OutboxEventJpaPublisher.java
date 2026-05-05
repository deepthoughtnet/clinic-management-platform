package com.deepthoughtnet.clinic.notification.service.impl;

import com.deepthoughtnet.clinic.notification.db.NotificationOutboxEntity;
import com.deepthoughtnet.clinic.notification.db.NotificationOutboxRepository;
import com.deepthoughtnet.clinic.platform.outbox.OutboxEventCommand;
import com.deepthoughtnet.clinic.platform.outbox.OutboxEventPublisher;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxEventJpaPublisher implements OutboxEventPublisher {

    private final NotificationOutboxRepository repository;

    public OutboxEventJpaPublisher(NotificationOutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public UUID publish(OutboxEventCommand command) {
        validate(command);

        if (repository.existsByDeduplicationKey(command.deduplicationKey())) {
            return repository.findByDeduplicationKey(command.deduplicationKey())
                    .map(NotificationOutboxEntity::getId)
                    .orElse(null);
        }

        try {
            return repository.save(NotificationOutboxEntity.pending(
                    command.tenantId(),
                    command.eventType(),
                    command.aggregateType(),
                    command.aggregateId(),
                    command.deduplicationKey(),
                    command.payloadJson(),
                    command.availableAt()
            )).getId();
        } catch (DataIntegrityViolationException ex) {
            return repository.findByDeduplicationKey(command.deduplicationKey())
                    .map(NotificationOutboxEntity::getId)
                    .orElse(null);
        }
    }

    private void validate(OutboxEventCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        if (command.tenantId() == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (command.eventType() == null || command.eventType().isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (command.aggregateType() == null || command.aggregateType().isBlank()) {
            throw new IllegalArgumentException("aggregateType is required");
        }
        if (command.aggregateId() == null) {
            throw new IllegalArgumentException("aggregateId is required");
        }
        if (command.deduplicationKey() == null || command.deduplicationKey().isBlank()) {
            throw new IllegalArgumentException("deduplicationKey is required");
        }
        if (command.payloadJson() == null || command.payloadJson().isBlank()) {
            throw new IllegalArgumentException("payloadJson is required");
        }
    }
}
