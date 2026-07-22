package com.deepthoughtnet.clinic.platform.modulith.events.service;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListener;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPublisher;
import com.deepthoughtnet.clinic.platform.modulith.events.db.ModuleBusinessEventEntity;
import com.deepthoughtnet.clinic.platform.modulith.events.db.ModuleBusinessEventListenerExecutionEntity;
import com.deepthoughtnet.clinic.platform.modulith.events.db.ModuleBusinessEventListenerExecutionRepository;
import com.deepthoughtnet.clinic.platform.modulith.events.db.ModuleBusinessEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ModuleBusinessEventPublisherService implements ModuleBusinessEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(ModuleBusinessEventPublisherService.class);

    private final ModuleBusinessEventRepository eventRepository;
    private final ModuleBusinessEventListenerExecutionRepository listenerRepository;
    private final ModuleBusinessEventListenerRegistry registry;
    private final ObjectMapper objectMapper;
    private final ModuleBusinessEventMetrics metrics;

    public ModuleBusinessEventPublisherService(
            ModuleBusinessEventRepository eventRepository,
            ModuleBusinessEventListenerExecutionRepository listenerRepository,
            ModuleBusinessEventListenerRegistry registry,
            ObjectMapper objectMapper,
            ModuleBusinessEventMetrics metrics
    ) {
        this.eventRepository = eventRepository;
        this.listenerRepository = listenerRepository;
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public UUID publish(ModuleBusinessEvent event) {
        validate(event);
        if (eventRepository.existsById(event.eventId())) {
            return event.eventId();
        }

        try {
            String payloadJson = serialize(event);
            List<ModuleBusinessEventListener<? extends ModuleBusinessEvent>> listeners = registry.listenersFor(event.eventType());
            // Persist the business fact and its listener jobs in the same transaction so a rollback cannot
            // leave behind a phantom event or an orphaned execution job.
            ModuleBusinessEventEntity saved = eventRepository.save(ModuleBusinessEventEntity.create(event, payloadJson, listeners.size()));
            for (ModuleBusinessEventListener<? extends ModuleBusinessEvent> listener : listeners) {
                listenerRepository.save(ModuleBusinessEventListenerExecutionEntity.pending(saved, listener.listenerName(), listener.listenerModule()));
            }
            metrics.published(event.eventType());
            log.info(
                    "module_event_published eventId={} eventType={} eventVersion={} tenantId={} sourceModule={} aggregateType={} aggregateId={} correlationId={} causationId={} listenerCount={}",
                    event.eventId(),
                    event.eventType(),
                    event.eventVersion(),
                    event.tenantId(),
                    event.sourceModule(),
                    event.aggregateType(),
                    event.aggregateId(),
                    safe(event.correlationId()),
                    safe(event.causationId()),
                    listeners.size()
            );
            return saved.getId();
        } catch (DataIntegrityViolationException ex) {
            return eventRepository.findById(event.eventId()).map(ModuleBusinessEventEntity::getId).orElse(event.eventId());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to publish module business event " + event.eventType(), ex);
        }
    }

    private String serialize(ModuleBusinessEvent event) throws Exception {
        return objectMapper.writeValueAsString(event);
    }

    private void validate(ModuleBusinessEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event is required");
        }
        if (event.eventId() == null) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (!StringUtils.hasText(event.eventType())) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (event.eventVersion() < 1) {
            throw new IllegalArgumentException("eventVersion is required");
        }
        if (event.occurredAt() == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
        if (event.tenantId() == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (!StringUtils.hasText(event.sourceModule())) {
            throw new IllegalArgumentException("sourceModule is required");
        }
        if (!StringUtils.hasText(event.aggregateType())) {
            throw new IllegalArgumentException("aggregateType is required");
        }
        if (event.aggregateId() == null) {
            throw new IllegalArgumentException("aggregateId is required");
        }
        if (event.payload() == null) {
            throw new IllegalArgumentException("payload is required");
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}
