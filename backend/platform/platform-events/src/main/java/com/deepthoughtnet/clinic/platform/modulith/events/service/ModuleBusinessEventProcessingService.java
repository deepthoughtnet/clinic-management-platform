package com.deepthoughtnet.clinic.platform.modulith.events.service;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListener;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListenerStatus;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventProcessingException;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventStatus;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventProperties;
import com.deepthoughtnet.clinic.platform.modulith.events.db.ModuleBusinessEventEntity;
import com.deepthoughtnet.clinic.platform.modulith.events.db.ModuleBusinessEventListenerExecutionEntity;
import com.deepthoughtnet.clinic.platform.modulith.events.db.ModuleBusinessEventListenerExecutionRepository;
import com.deepthoughtnet.clinic.platform.modulith.events.db.ModuleBusinessEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ModuleBusinessEventProcessingService {
    private static final Logger log = LoggerFactory.getLogger(ModuleBusinessEventProcessingService.class);

    private final ModuleBusinessEventRepository eventRepository;
    private final ModuleBusinessEventListenerExecutionRepository listenerRepository;
    private final ModuleBusinessEventListenerRegistry registry;
    private final ObjectMapper objectMapper;
    private final ModuleBusinessEventProperties properties;
    private final ModuleBusinessEventMetrics metrics;
    private final TransactionTemplate claimTransactionTemplate;
    private final TransactionTemplate transactionTemplate;

    public ModuleBusinessEventProcessingService(
            ModuleBusinessEventRepository eventRepository,
            ModuleBusinessEventListenerExecutionRepository listenerRepository,
            ModuleBusinessEventListenerRegistry registry,
            ObjectMapper objectMapper,
            ModuleBusinessEventProperties properties,
            ModuleBusinessEventMetrics metrics,
            PlatformTransactionManager transactionManager
    ) {
        this.eventRepository = eventRepository;
        this.listenerRepository = listenerRepository;
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.metrics = metrics;
        this.claimTransactionTemplate = new TransactionTemplate(transactionManager);
        this.claimTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public List<ModuleBusinessEventEntity> listPendingEvents(UUID tenantId) {
        return eventRepository.findByTenantIdAndStatusInOrderByCreatedAtAsc(
                tenantId,
                List.of(ModuleBusinessEventStatus.PENDING, ModuleBusinessEventStatus.RETRY_SCHEDULED, ModuleBusinessEventStatus.PROCESSING)
        );
    }

    public List<ModuleBusinessEventListenerExecutionEntity> listListenerExecutions(UUID tenantId, UUID eventId) {
        return listenerRepository.findByTenantIdAndEventIdOrderByCreatedAtAsc(tenantId, eventId);
    }

    public List<ModuleBusinessEventEntity> listEvents(UUID tenantId, OffsetDateTime from, OffsetDateTime to) {
        return eventRepository.findByTenantIdAndOccurredAtBetweenOrderByOccurredAtDesc(tenantId, from, to);
    }

    public int dispatchRunnable(int limit) {
        transactionTemplate.executeWithoutResult(tx -> reclaimStaleProcessing());
        List<ModuleBusinessEventListenerExecutionEntity> runnable = listenerRepository.findRunnable(OffsetDateTime.now());
        int processed = 0;
        int max = Math.max(1, limit);
        for (ModuleBusinessEventListenerExecutionEntity row : runnable) {
            if (processed >= max) {
                break;
            }
            processOne(row.getId());
            processed++;
        }
        metrics.gaugePending(listenerRepository.countByStatus(ModuleBusinessEventListenerStatus.PENDING.name())
                + listenerRepository.countByStatus(ModuleBusinessEventListenerStatus.RETRY_SCHEDULED.name()));
        metrics.gaugeDeadLettered(listenerRepository.countByStatus(ModuleBusinessEventListenerStatus.DEAD_LETTERED.name()));
        return processed;
    }

    public void processOne(UUID listenerExecutionId) {
        Boolean claimed = claimTransactionTemplate.execute(status -> claim(listenerExecutionId));
        if (!Boolean.TRUE.equals(claimed)) {
            return;
        }
        transactionTemplate.executeWithoutResult(tx -> processClaimed(listenerExecutionId));
    }

    public int recoverStaleProcessing() {
        return transactionTemplate.execute(status -> {
            List<ModuleBusinessEventListenerExecutionEntity> stale = listenerRepository.findStaleProcessingAll(staleCutoff());
            for (ModuleBusinessEventListenerExecutionEntity row : stale) {
                row.reclaimForRetry();
                listenerRepository.save(row);
                recalculate(row.getEventId());
            }
            return stale.size();
        });
    }

    private boolean claim(UUID listenerExecutionId) {
        ModuleBusinessEventListenerExecutionEntity row = listenerRepository.findById(listenerExecutionId)
                .orElseThrow(() -> new IllegalArgumentException("Listener execution not found"));
        if (ModuleBusinessEventListenerStatus.SUCCEEDED.name().equals(row.getStatus())
                || ModuleBusinessEventListenerStatus.FAILED.name().equals(row.getStatus())
                || ModuleBusinessEventListenerStatus.DEAD_LETTERED.name().equals(row.getStatus())) {
            return false;
        }
        row.markProcessing();
        try {
            // Flush the claim before invoking the listener so a second node cannot observe the same row as
            // runnable and execute it concurrently. If another node won the claim, this flush fails fast and
            // the duplicate execution is skipped without producing a listener side effect.
            listenerRepository.saveAndFlush(row);
        } catch (OptimisticLockingFailureException ex) {
            log.debug(
                    "module_event_listener_claim_skipped eventId={} eventType={} listenerName={} correlationId={} reason={}",
                    row.getEventId(),
                    row.getEventType(),
                    row.getListenerName(),
                    "unknown",
                    safe(ex.getMessage())
            );
            return false;
        }
        return true;
    }

    private void processClaimed(UUID listenerExecutionId) {
        ModuleBusinessEventListenerExecutionEntity row = listenerRepository.findById(listenerExecutionId)
                .orElseThrow(() -> new IllegalArgumentException("Listener execution not found"));
        if (!ModuleBusinessEventListenerStatus.PROCESSING.name().equals(row.getStatus())) {
            return;
        }
        ModuleBusinessEventEntity event = eventRepository.findById(row.getEventId())
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        ModuleBusinessEventListener<? extends ModuleBusinessEvent> listener = registry.findByName(row.getListenerName());
        if (listener == null) {
            row.markFailed("Listener not registered", false, properties.getMaxAttempts(), Duration.ZERO);
            listenerRepository.save(row);
            recalculate(event.getId());
            return;
        }

        long started = System.nanoTime();
        TimerSampleHolder timer = new TimerSampleHolder(metrics.startTimer());
        try {
            MDC.put("eventId", event.getId().toString());
            MDC.put("eventType", event.getEventType());
            MDC.put("eventVersion", String.valueOf(event.getEventVersion()));
            MDC.put("tenantId", event.getTenantId() == null ? "" : event.getTenantId().toString());
            MDC.put("sourceModule", event.getSourceModule());
            MDC.put("aggregateType", event.getAggregateType());
            MDC.put("aggregateId", event.getAggregateId() == null ? "" : event.getAggregateId().toString());
            MDC.put("correlationId", event.getCorrelationId());
            MDC.put("causationId", event.getCausationId());
            MDC.put("listenerName", row.getListenerName());
            MDC.put("attempt", String.valueOf(row.getAttemptCount()));

            @SuppressWarnings("unchecked")
            ModuleBusinessEventListener<ModuleBusinessEvent> typedListener = (ModuleBusinessEventListener<ModuleBusinessEvent>) listener;
            ModuleBusinessEvent payload = objectMapper.readValue(event.getPayloadJson(), (Class<ModuleBusinessEvent>) listener.eventClass());
            typedListener.handle(payload);

            row.markSucceeded();
            listenerRepository.save(row);
            recalculate(event.getId());
            metrics.listenerAttempt(row.getListenerName(), event.getEventType());
            metrics.stopTimer(timer.sample(), event.getEventType(), row.getListenerName(), "SUCCEEDED");
            log.info(
                    "module_event_listener_processed eventId={} eventType={} eventVersion={} tenantId={} sourceModule={} aggregateType={} aggregateId={} correlationId={} causationId={} listenerName={} attempt={} outcome=SUCCEEDED durationMs={}",
                    event.getId(),
                    event.getEventType(),
                    event.getEventVersion(),
                    event.getTenantId(),
                    event.getSourceModule(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getCorrelationId(),
                    event.getCausationId(),
                    row.getListenerName(),
                    row.getAttemptCount(),
                    durationMs(started)
            );
        } catch (Exception ex) {
            boolean retryable = isRetryable(ex);
            row.markFailed(ex.getMessage(), retryable, properties.getMaxAttempts(), retryBackoff());
            listenerRepository.save(row);
            recalculate(event.getId());
            metrics.listenerAttempt(row.getListenerName(), event.getEventType());
            metrics.listenerFailure(row.getListenerName(), event.getEventType(), row.getStatus());
            if (ModuleBusinessEventListenerStatus.RETRY_SCHEDULED.name().equals(row.getStatus())) {
                metrics.listenerRetry(row.getListenerName(), event.getEventType());
            }
            metrics.stopTimer(timer.sample(), event.getEventType(), row.getListenerName(), row.getStatus());
            log.warn(
                    "module_event_listener_failed eventId={} eventType={} eventVersion={} tenantId={} sourceModule={} aggregateType={} aggregateId={} correlationId={} causationId={} listenerName={} attempt={} outcome={} durationMs={} error={}",
                    event.getId(),
                    event.getEventType(),
                    event.getEventVersion(),
                    event.getTenantId(),
                    event.getSourceModule(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getCorrelationId(),
                    event.getCausationId(),
                    row.getListenerName(),
                    row.getAttemptCount(),
                    row.getStatus(),
                    durationMs(started),
                    safe(ex.getMessage())
            );
        } finally {
            MDC.remove("eventId");
            MDC.remove("eventType");
            MDC.remove("eventVersion");
            MDC.remove("tenantId");
            MDC.remove("sourceModule");
            MDC.remove("aggregateType");
            MDC.remove("aggregateId");
            MDC.remove("correlationId");
            MDC.remove("causationId");
            MDC.remove("listenerName");
            MDC.remove("attempt");
        }
    }

    private void recalculate(UUID eventId) {
        ModuleBusinessEventEntity event = eventRepository.findById(eventId).orElse(null);
        if (event == null) {
            return;
        }
        List<ModuleBusinessEventListenerExecutionEntity> rows = listenerRepository.findByTenantIdAndEventIdOrderByCreatedAtAsc(event.getTenantId(), eventId);
        int succeeded = 0;
        int failed = 0;
        int retry = 0;
        int dead = 0;
        int processing = 0;
        int pending = 0;
        for (ModuleBusinessEventListenerExecutionEntity row : rows) {
            String status = row.getStatus();
            if (ModuleBusinessEventListenerStatus.SUCCEEDED.name().equals(status)) {
                succeeded++;
            } else if (ModuleBusinessEventListenerStatus.FAILED.name().equals(status)) {
                failed++;
            } else if (ModuleBusinessEventListenerStatus.RETRY_SCHEDULED.name().equals(status)) {
                retry++;
            } else if (ModuleBusinessEventListenerStatus.DEAD_LETTERED.name().equals(status)) {
                dead++;
            } else if (ModuleBusinessEventListenerStatus.PROCESSING.name().equals(status)) {
                processing++;
            } else {
                pending++;
            }
        }
        event.recalculateStatus(succeeded, failed, retry, dead, processing, pending);
        eventRepository.save(event);
    }

    private void reclaimStaleProcessing() {
        // Reclaiming stale claims is intentionally separate from the runnable dispatch path so a restarted
        // node can release abandoned PROCESSING rows without impacting healthy in-flight work.
        List<ModuleBusinessEventListenerExecutionEntity> stale = listenerRepository.findStaleProcessingAll(staleCutoff());
        for (ModuleBusinessEventListenerExecutionEntity row : stale) {
            row.reclaimForRetry();
            listenerRepository.save(row);
            recalculate(row.getEventId());
        }
    }

    private OffsetDateTime staleCutoff() {
        return OffsetDateTime.now().minus(parseDuration(properties.getStaleProcessingTimeout()));
    }

    private Duration retryBackoff() {
        return parseDuration(properties.getRetryBackoff());
    }

    private Duration parseDuration(String text) {
        try {
            return Duration.parse(text);
        } catch (Exception ex) {
            return Duration.ofMinutes(1);
        }
    }

    private boolean isRetryable(Exception ex) {
        if (ex instanceof ModuleBusinessEventProcessingException mbe) {
            return mbe.isRetryable();
        }
        if (ex instanceof IllegalArgumentException) {
            return false;
        }
        return true;
    }

    private long durationMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000L);
    }

    private String safe(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n\\t]+", " ").trim();
    }

    private record TimerSampleHolder(io.micrometer.core.instrument.Timer.Sample sample) {
    }
}
