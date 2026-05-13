package com.deepthoughtnet.clinic.carepilot.execution.service;

import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryAttemptEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryAttemptRepository;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryEventEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignDeliveryEventRepository;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionEntity;
import com.deepthoughtnet.clinic.carepilot.execution.db.CampaignExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Handles provider webhook lifecycle updates and persists tenant-safe delivery events.
 */
@Service
public class CampaignDeliveryWebhookService {
    private static final Logger log = LoggerFactory.getLogger(CampaignDeliveryWebhookService.class);
    private static final int MAX_RAW_PAYLOAD_LENGTH = 4000;

    private final CampaignExecutionRepository executionRepository;
    private final CampaignDeliveryAttemptRepository attemptRepository;
    private final CampaignDeliveryEventRepository eventRepository;

    public CampaignDeliveryWebhookService(
            CampaignExecutionRepository executionRepository,
            CampaignDeliveryAttemptRepository attemptRepository,
            CampaignDeliveryEventRepository eventRepository
    ) {
        this.executionRepository = executionRepository;
        this.attemptRepository = attemptRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * Applies one provider delivery event, updates matching executions, and appends immutable audit entries.
     */
    @Transactional
    public DeliveryWebhookUpdateResult applyProviderDeliveryEvent(ProviderDeliveryEventCommand command) {
        validate(command);
        OffsetDateTime eventAt = command.eventTimestamp() == null ? OffsetDateTime.now() : command.eventTimestamp();
        String redacted = redactPayload(command.rawPayload());
        if (eventRepository.existsByProviderNameAndProviderMessageIdAndInternalStatusAndEventTypeAndEventTimestamp(
                command.providerName(), command.providerMessageId(), command.internalStatus(), command.eventType(), eventAt
        )) {
            return new DeliveryWebhookUpdateResult(0, 1, true);
        }

        List<CampaignExecutionEntity> matches = executionRepository.findByProviderNameAndProviderMessageIdOrderByUpdatedAtDesc(
                command.providerName(),
                command.providerMessageId()
        );
        if (matches.isEmpty()) {
            eventRepository.save(CampaignDeliveryEventEntity.create(
                    null,
                    null,
                    null,
                    command.providerName(),
                    command.providerMessageId(),
                    command.channelType(),
                    command.externalStatus(),
                    command.internalStatus(),
                    command.eventType(),
                    eventAt,
                    redacted
            ));
            log.warn("CarePilot webhook event unmatched. provider={} channel={} messageId={} eventType={} status={}",
                    command.providerName(), command.channelType(), command.providerMessageId(), command.eventType(), command.internalStatus());
            return new DeliveryWebhookUpdateResult(0, 1, false);
        }

        int updated = 0;
        int events = 0;
        for (CampaignExecutionEntity execution : matches) {
            CampaignDeliveryAttemptEntity latestAttempt = attemptRepository
                    .findFirstByTenantIdAndExecutionIdOrderByAttemptNumberDesc(execution.getTenantId(), execution.getId())
                    .orElse(null);

            String failureReason = isFailureLike(command.internalStatus())
                    ? "Provider delivery status: " + command.internalStatus().name()
                    : null;
            execution.markDeliveryLifecycleStatus(command.internalStatus(), failureReason, eventAt);
            executionRepository.save(execution);
            updated++;

            eventRepository.save(CampaignDeliveryEventEntity.create(
                    execution.getTenantId(),
                    execution.getId(),
                    latestAttempt == null ? null : latestAttempt.getId(),
                    command.providerName(),
                    command.providerMessageId(),
                    command.channelType(),
                    command.externalStatus(),
                    command.internalStatus(),
                    command.eventType(),
                    eventAt,
                    redacted
            ));
            events++;
        }

        return new DeliveryWebhookUpdateResult(updated, events, false);
    }

    private boolean isFailureLike(MessageDeliveryStatus status) {
        return status == MessageDeliveryStatus.FAILED
                || status == MessageDeliveryStatus.BOUNCED
                || status == MessageDeliveryStatus.UNDELIVERED;
    }

    private String redactPayload(String rawPayload) {
        if (!StringUtils.hasText(rawPayload)) {
            return null;
        }
        String sanitized = rawPayload
                .replaceAll("(?i)(\"access[_-]?token\"\\s*:\\s*\")[^\"]+(\"\\s*)", "$1[REDACTED]$2")
                .replaceAll("(?i)(\"authorization\"\\s*:\\s*\")[^\"]+(\"\\s*)", "$1[REDACTED]$2")
                .replaceAll("(?i)(\"api[_-]?key\"\\s*:\\s*\")[^\"]+(\"\\s*)", "$1[REDACTED]$2");
        return sanitized.length() > MAX_RAW_PAYLOAD_LENGTH ? sanitized.substring(0, MAX_RAW_PAYLOAD_LENGTH) : sanitized;
    }

    private void validate(ProviderDeliveryEventCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        if (!StringUtils.hasText(command.providerName())) {
            throw new IllegalArgumentException("providerName is required");
        }
        if (!StringUtils.hasText(command.providerMessageId())) {
            throw new IllegalArgumentException("providerMessageId is required");
        }
        if (command.channelType() == null) {
            throw new IllegalArgumentException("channelType is required");
        }
        if (command.internalStatus() == null) {
            throw new IllegalArgumentException("internalStatus is required");
        }
        if (!StringUtils.hasText(command.eventType())) {
            throw new IllegalArgumentException("eventType is required");
        }
    }

    /**
     * Provider-agnostic event command used by webhook adapters.
     */
    public record ProviderDeliveryEventCommand(
            String providerName,
            String providerMessageId,
            ChannelType channelType,
            String externalStatus,
            MessageDeliveryStatus internalStatus,
            String eventType,
            OffsetDateTime eventTimestamp,
            String rawPayload
    ) {}

    /**
     * Lightweight result for webhook observability and tests.
     */
    public record DeliveryWebhookUpdateResult(
            int updatedExecutions,
            int persistedEvents,
            boolean duplicate
    ) {}
}
