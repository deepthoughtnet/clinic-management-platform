package com.deepthoughtnet.clinic.platform.audit.impl;

import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventHandler;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.audit.AuditEventQueryService;
import com.deepthoughtnet.clinic.platform.audit.AuditEventRecord;
import com.deepthoughtnet.clinic.platform.audit.db.AuditEventEntity;
import com.deepthoughtnet.clinic.platform.audit.db.AuditEventRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditEventJpaService implements AuditEventPublisher, AuditEventQueryService {

    private static final Logger log = LoggerFactory.getLogger(AuditEventJpaService.class);

    private final AuditEventRepository repository;
    private final List<AuditEventHandler> handlers;

    public AuditEventJpaService(AuditEventRepository repository) {
        this(repository, List.of());
    }

    @Autowired
    public AuditEventJpaService(AuditEventRepository repository, List<AuditEventHandler> handlers) {
        this.repository = repository;
        this.handlers = handlers == null ? List.of() : handlers;
    }

    @Override
    @Transactional
    public UUID record(AuditEventCommand command) {
        validate(command);

        AuditEventEntity saved = repository.save(AuditEventEntity.create(
                command.tenantId(),
                command.entityType(),
                command.entityId(),
                command.action(),
                command.actorAppUserId(),
                command.occurredAt(),
                command.summary(),
                command.detailsJson()
        ));

        UUID auditEventId = saved.getId();
        handlers.forEach(handler -> notifyHandler(handler, auditEventId, command));
        return auditEventId;
    }

    private void notifyHandler(AuditEventHandler handler, UUID auditEventId, AuditEventCommand command) {
        try {
            handler.afterAuditRecorded(auditEventId, command);
        } catch (Exception ex) {
            log.warn(
                    "Audit event handler failed. auditEventId={}, action={}, handler={}, error={}",
                    auditEventId,
                    command.action(),
                    handler.getClass().getSimpleName(),
                    ex.getMessage()
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEventRecord> listForEntity(UUID tenantId, String entityType, UUID entityId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("entityType is required");
        }
        if (entityId == null) {
            throw new IllegalArgumentException("entityId is required");
        }

        return repository.findByTenantIdAndEntityTypeAndEntityIdOrderByOccurredAtAsc(
                tenantId,
                entityType,
                entityId
        ).stream().map(this::toRecord).toList();
    }

    private AuditEventRecord toRecord(AuditEventEntity entity) {
        return new AuditEventRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getAction(),
                entity.getActorAppUserId(),
                entity.getOccurredAt(),
                entity.getSummary(),
                entity.getDetailsJson()
        );
    }

    private void validate(AuditEventCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        if (command.tenantId() == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (command.entityType() == null || command.entityType().isBlank()) {
            throw new IllegalArgumentException("entityType is required");
        }
        if (command.entityId() == null) {
            throw new IllegalArgumentException("entityId is required");
        }
        if (command.action() == null || command.action().isBlank()) {
            throw new IllegalArgumentException("action is required");
        }
    }
}
