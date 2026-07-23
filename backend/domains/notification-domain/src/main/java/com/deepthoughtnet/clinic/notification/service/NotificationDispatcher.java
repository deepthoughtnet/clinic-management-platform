package com.deepthoughtnet.clinic.notification.service;

import com.deepthoughtnet.clinic.appointment.service.AppointmentReminderReadService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentReminderSnapshot;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.billing.service.PaymentReminderStateReader;
import com.deepthoughtnet.clinic.billing.service.model.PaymentReminderState;
import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import com.deepthoughtnet.clinic.messaging.spi.MessageRecipient;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import com.deepthoughtnet.clinic.notification.db.NotificationOutboxEntity;
import com.deepthoughtnet.clinic.notification.db.NotificationOutboxRepository;
import com.deepthoughtnet.clinic.notification.model.NotificationEventPayload;
import com.deepthoughtnet.clinic.notify.NotificationMessage;
import com.deepthoughtnet.clinic.notify.NotificationProvider;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final AppointmentReminderReadService appointmentReminderReadService;
    private final PaymentReminderStateReader paymentReminderStateReader;
    private final PatientService patientService;
    private final NotificationProvider notificationProvider;
    private final List<MessageProvider> messageProviders;
    private final ObjectMapper objectMapper;

    public NotificationDispatcher(
            NotificationOutboxRepository repository,
            NotificationRecipientResolver recipientResolver,
            NotificationHistoryService notificationHistoryService,
            AppointmentReminderReadService appointmentReminderReadService,
            PaymentReminderStateReader paymentReminderStateReader,
            PatientService patientService,
            NotificationProvider notificationProvider,
            List<MessageProvider> messageProviders,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.recipientResolver = recipientResolver;
        this.notificationHistoryService = notificationHistoryService;
        this.appointmentReminderReadService = appointmentReminderReadService;
        this.paymentReminderStateReader = paymentReminderStateReader;
        this.patientService = patientService;
        this.notificationProvider = notificationProvider;
        this.messageProviders = messageProviders == null ? List.of() : List.copyOf(messageProviders);
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
            if (isStaleAppointmentReminder(event, payload)) {
                markSkipped(event, payload, "Stale appointment reminder");
                event.markIgnored(null);
                log.info(
                        "Notification outbox reminder skipped as stale. eventId={}, tenantId={}, eventType={}",
                        event.getId(),
                        event.getTenantId(),
                        event.getEventType()
                );
                return;
            }
            if (isStalePaymentReminder(event, payload)) {
                markSkipped(event, payload, "Bill already paid");
                event.markIgnored(null);
                log.info(
                        "Notification outbox payment reminder skipped as stale. eventId={}, tenantId={}, eventType={}",
                        event.getId(),
                        event.getTenantId(),
                        event.getEventType()
                );
                return;
            }

            String channel = payload.channel() == null || payload.channel().isBlank()
                    ? settings.channel()
                    : payload.channel().trim().toLowerCase();
            DispatchOutcome outcome = dispatchByChannel(event, payload, channel);
            if (outcome.ignored()) {
                event.markIgnored(null);
            } else if (outcome.sent()) {
                event.markSucceeded();
            } else if (outcome.pending()) {
                event.markFailed(outcome.errorMessage(), settings.maxAttempts(), settings.retryBackoff());
            }
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

    private DispatchOutcome dispatchByChannel(NotificationOutboxEntity event, NotificationEventPayload payload, String channel) {
        if (payload == null || channel == null || channel.isBlank()) {
            return DispatchOutcome.skippedOutcome("No channel resolved");
        }
        if (isInAppChannel(channel)) {
            return dispatchInApp(event, payload);
        }
        if (isEmailChannel(channel)) {
            return dispatchEmail(event, payload);
        }
        if (isSmsChannel(channel) || isWhatsAppChannel(channel)) {
            return dispatchMessaging(event, payload, channel);
        }
        return DispatchOutcome.skippedOutcome("Unsupported notification channel: " + channel);
    }

    private DispatchOutcome dispatchInApp(NotificationOutboxEntity event, NotificationEventPayload payload) {
        if (payload.patientId() == null) {
            markSkipped(event, payload, "Patient record unavailable");
            log.info("Notification in-app delivery skipped because patient id is missing. eventId={} tenantId={} historyId={}",
                    event.getId(), event.getTenantId(), payload.historyId());
            return DispatchOutcome.skippedOutcome("Patient record unavailable");
        }
        PatientRecord patient = patientService.findById(event.getTenantId(), payload.patientId()).orElse(null);
        if (patient == null || !patient.active()) {
            markSkipped(event, payload, "Patient record unavailable");
            log.info("Notification in-app delivery skipped because patient record is unavailable. eventId={} tenantId={} historyId={}",
                    event.getId(), event.getTenantId(), payload.historyId());
            return DispatchOutcome.skippedOutcome("Patient record unavailable");
        }
        if (payload.historyId() != null) {
            notificationHistoryService.markSent(event.getTenantId(), payload.historyId());
        }
        log.info("Notification in-app delivery sent. eventId={} tenantId={} historyId={} patientId={} channel={}",
                event.getId(), event.getTenantId(), payload.historyId(), payload.patientId(), payload.channel());
        return DispatchOutcome.success();
    }

    private DispatchOutcome dispatchEmail(NotificationOutboxEntity event, NotificationEventPayload payload) {
        if (!isEmailProviderConfigured()) {
            markSkipped(event, payload, "Email provider not configured");
            log.info("Notification email skipped because provider is not configured. eventId={} tenantId={} historyId={}",
                    event.getId(), event.getTenantId(), payload.historyId());
            return DispatchOutcome.skippedOutcome("Email provider not configured");
        }
        String recipient = resolveEmailRecipient(event.getTenantId(), payload);
        if (!hasText(recipient)) {
            markSkipped(event, payload, "Patient email unavailable");
            log.info("Notification email skipped because recipient is unavailable. eventId={} tenantId={} historyId={}",
                    event.getId(), event.getTenantId(), payload.historyId());
            return DispatchOutcome.skippedOutcome("Patient email unavailable");
        }
        try {
            notificationProvider.send(new NotificationMessage(
                    event.getTenantId(),
                    "EMAIL",
                    recipient,
                    payload.subject(),
                    payload.body(),
                    event.getPayloadJson()
            ));
            if (payload.historyId() != null) {
                notificationHistoryService.markSent(event.getTenantId(), payload.historyId());
            }
            log.info("Notification email sent. eventId={} tenantId={} historyId={} recipient={}",
                    event.getId(), event.getTenantId(), payload.historyId(), mask(recipient));
            return DispatchOutcome.success();
        } catch (Exception ex) {
            if (payload.historyId() != null) {
                notificationHistoryService.markFailed(event.getTenantId(), payload.historyId(), safeMessage(ex));
            }
            log.warn("Notification email failed. eventId={} tenantId={} historyId={} error={}",
                    event.getId(), event.getTenantId(), payload.historyId(), safeMessage(ex));
            return DispatchOutcome.pendingOutcome(safeMessage(ex));
        }
    }

    private DispatchOutcome dispatchMessaging(NotificationOutboxEntity event, NotificationEventPayload payload, String channel) {
        MessageChannel messageChannel = toMessageChannel(channel);
        if (messageChannel == null) {
            return DispatchOutcome.skippedOutcome("Unsupported notification channel: " + channel);
        }
        String recipient = resolveMessagingRecipient(event.getTenantId(), payload, messageChannel);
        if (!hasText(recipient)) {
            String reason = isWhatsAppChannel(channel) ? "Patient mobile unavailable" : "Patient mobile unavailable";
            markSkipped(event, payload, reason);
            log.info("Notification {} skipped because recipient is unavailable. eventId={} tenantId={} historyId={}",
                    channel, event.getId(), event.getTenantId(), payload.historyId());
            return DispatchOutcome.skippedOutcome(reason);
        }

        MessageProvider provider = resolveMessageProvider(messageChannel);
        if (provider == null) {
            String reason = isWhatsAppChannel(channel) ? "WhatsApp provider not configured" : "SMS provider not configured";
            markSkipped(event, payload, reason);
            log.info("Notification {} skipped because provider is unavailable. eventId={} tenantId={} historyId={}",
                    channel, event.getId(), event.getTenantId(), payload.historyId());
            return DispatchOutcome.skippedOutcome(reason);
        }

        MessageResult result = provider.send(new MessageRequest(
                event.getTenantId(),
                messageChannel,
                new MessageRecipient(recipient, null),
                payload.subject(),
                payload.body(),
                null,
                correlationId(payload, event),
                null,
                payload.historyId(),
                buildMetadata(payload, event)
        ));

        if (result.success()) {
            if (payload.historyId() != null) {
                notificationHistoryService.markSent(event.getTenantId(), payload.historyId());
            }
            log.info("Notification {} sent. eventId={} tenantId={} historyId={} provider={} recipient={}",
                    channel, event.getId(), event.getTenantId(), payload.historyId(), result.providerName(), mask(recipient));
            return DispatchOutcome.success();
        }
        if (result.status() == MessageDeliveryStatus.NOT_CONFIGURED
                || result.status() == MessageDeliveryStatus.PROVIDER_NOT_AVAILABLE
                || result.status() == MessageDeliveryStatus.SKIPPED) {
            markSkipped(event, payload, result.errorMessage() == null ? channel + " provider not configured" : result.errorMessage());
            log.info("Notification {} skipped. eventId={} tenantId={} historyId={} reason={}",
                    channel, event.getId(), event.getTenantId(), payload.historyId(), result.errorMessage());
            return DispatchOutcome.skippedOutcome(result.errorMessage());
        }

        if (payload.historyId() != null) {
            notificationHistoryService.markFailed(event.getTenantId(), payload.historyId(), safeMessage(result.errorMessage() == null
                    ? new IllegalStateException("Message delivery failed")
                    : new IllegalStateException(result.errorMessage())));
        }
        log.warn("Notification {} failed. eventId={} tenantId={} historyId={} reason={}",
                channel, event.getId(), event.getTenantId(), payload.historyId(), result.errorMessage());
        return DispatchOutcome.pendingOutcome(result.errorMessage());
    }

    private MessageProvider resolveMessageProvider(MessageChannel channel) {
        MessageProvider fallback = null;
        for (MessageProvider provider : messageProviders) {
            if (provider == null) {
                continue;
            }
            if ("carepilot-noop".equalsIgnoreCase(provider.providerName())) {
                fallback = provider;
                continue;
            }
            if (provider.supports(channel)) {
                return provider;
            }
        }
        return fallback;
    }

    private MessageChannel toMessageChannel(String channel) {
        try {
            return MessageChannel.valueOf(channel.trim().toUpperCase());
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isInAppChannel(String channel) {
        return channel != null && channel.equalsIgnoreCase("in_app");
    }

    private boolean isEmailChannel(String channel) {
        return channel != null && channel.equalsIgnoreCase("email");
    }

    private boolean isSmsChannel(String channel) {
        return channel != null && channel.equalsIgnoreCase("sms");
    }

    private boolean isWhatsAppChannel(String channel) {
        return channel != null && channel.equalsIgnoreCase("whatsapp");
    }

    private boolean isEmailProviderConfigured() {
        if (notificationProvider == null) {
            return false;
        }
        return !notificationProvider.getClass().getSimpleName().contains("LoggingNotificationProvider");
    }

    private String resolveEmailRecipient(UUID tenantId, NotificationEventPayload payload) {
        if (payload == null) {
            return null;
        }
        if (hasText(payload.recipient())) {
            return payload.recipient();
        }
        return recipientResolver.resolveEmailsByRoles(tenantId, payload.recipientRoles() == null ? List.of() : payload.recipientRoles())
                .stream()
                .findFirst()
                .orElse(null);
    }

    private String resolveMessagingRecipient(UUID tenantId, NotificationEventPayload payload, MessageChannel channel) {
        if (payload == null) {
            return null;
        }
        if (hasText(payload.recipient())) {
            return payload.recipient();
        }
        if (channel == MessageChannel.EMAIL) {
            return resolveEmailRecipient(tenantId, payload);
        }
        return null;
    }

    private void markSkipped(NotificationOutboxEntity event, NotificationEventPayload payload, String reason) {
        if (payload != null && payload.historyId() != null) {
            notificationHistoryService.markSkipped(event.getTenantId(), payload.historyId(), reason);
        }
    }

    private String correlationId(NotificationEventPayload payload, NotificationOutboxEntity event) {
        String fromDetails = detailAsText(payload, "correlationId");
        if (hasText(fromDetails)) {
            return fromDetails;
        }
        return event == null ? null : event.getId().toString();
    }

    private Map<String, String> buildMetadata(NotificationEventPayload payload, NotificationOutboxEntity event) {
        Map<String, String> metadata = new LinkedHashMap<>();
        putIfText(metadata, "notificationType", payload == null ? null : payload.notificationType());
        putIfText(metadata, "historyId", payload == null || payload.historyId() == null ? null : payload.historyId().toString());
        putIfText(metadata, "sourceType", payload == null ? null : payload.sourceType());
        putIfText(metadata, "sourceId", payload == null || payload.sourceId() == null ? null : payload.sourceId().toString());
        putIfText(metadata, "eventId", event == null ? null : event.getId().toString());
        putIfText(metadata, "correlationId", detailAsText(payload, "correlationId"));
        putIfText(metadata, "causationId", detailAsText(payload, "causationId"));
        return metadata;
    }

    private void putIfText(Map<String, String> metadata, String key, String value) {
        if (metadata != null && hasText(value)) {
            metadata.put(key, value);
        }
    }

    private String detailAsText(NotificationEventPayload payload, String field) {
        if (payload == null || !hasText(payload.detailsJson())) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(payload.detailsJson());
            JsonNode value = node.path(field);
            if (value.isMissingNode() || value.isNull()) {
                return null;
            }
            return value.asText();
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String mask(String value) {
        if (!hasText(value)) {
            return null;
        }
        if (value.contains("@")) {
            int at = value.indexOf('@');
            return value.charAt(0) + "***" + value.substring(at);
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
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

    private boolean isStaleAppointmentReminder(NotificationOutboxEntity event, NotificationEventPayload payload) {
        if (event == null || payload == null || payload.notificationType() == null) {
            return false;
        }
        if (!"APPOINTMENT_REMINDER_DUE".equalsIgnoreCase(payload.notificationType())) {
            return false;
        }
        if (payload.sourceId() == null) {
            return true;
        }
        try {
            JsonNode details = objectMapper.readTree(payload.detailsJson() == null ? "{}" : payload.detailsJson());
            int expectedVersion = details.path("appointmentVersion").asInt(-1);
            String expectedDate = details.path("appointmentDate").asText(null);
            String expectedTime = details.path("appointmentTime").asText(null);
            AppointmentReminderSnapshot snapshot = appointmentReminderReadService.findById(event.getTenantId(), payload.sourceId()).orElse(null);
            if (snapshot == null) {
                return true;
            }
            if (snapshot.status() != AppointmentStatus.BOOKED) {
                return true;
            }
            if (expectedVersion > 0 && snapshot.version() != expectedVersion) {
                return true;
            }
            if (expectedDate != null && !expectedDate.equals(String.valueOf(snapshot.appointmentDate()))) {
                return true;
            }
            if (expectedTime != null && !expectedTime.equals(String.valueOf(snapshot.appointmentTime()))) {
                return true;
            }
            return false;
        } catch (Exception ex) {
            log.warn(
                    "Unable to validate appointment reminder freshness. eventId={} tenantId={} error={}",
                    event.getId(),
                    event.getTenantId(),
                    safeMessage(ex)
            );
            return false;
        }
    }

    private boolean isStalePaymentReminder(NotificationOutboxEntity event, NotificationEventPayload payload) {
        if (event == null || payload == null || payload.notificationType() == null) {
            return false;
        }
        if (!"PAYMENT_REMINDER".equalsIgnoreCase(payload.notificationType())) {
            return false;
        }
        if (payload.sourceId() == null) {
            return true;
        }
        try {
            PaymentReminderState bill = paymentReminderStateReader.findCurrentState(event.getTenantId(), payload.sourceId());
            if (bill == null || !bill.found()) {
                return true;
            }
            if (!bill.reminderEligible()) {
                return true;
            }
            if (bill.outstandingAmount() == null || bill.outstandingAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                return true;
            }
            java.math.BigDecimal expectedAmount = extractAmount(payload);
            if (expectedAmount != null && bill.outstandingAmount().compareTo(expectedAmount) != 0) {
                return true;
            }
            if (payload.detailsJson() != null && !payload.detailsJson().isBlank()) {
                try {
                    JsonNode details = objectMapper.readTree(payload.detailsJson());
                    JsonNode billUpdatedAt = details.path("billUpdatedAt");
                    if (!billUpdatedAt.isMissingNode()
                            && !billUpdatedAt.isNull()
                            && bill.updatedAt() != null
                            && !billUpdatedAt.asText().equals(bill.updatedAt().toString())) {
                        return true;
                    }
                } catch (Exception ex) {
                    log.warn(
                            "Unable to validate payment reminder freshness against updatedAt. eventId={} tenantId={} error={}",
                            event.getId(),
                            event.getTenantId(),
                            safeMessage(ex)
                    );
                }
            }
            return false;
        } catch (Exception ex) {
            log.warn(
                    "Unable to validate payment reminder freshness. eventId={} tenantId={} error={}",
                    event.getId(),
                    event.getTenantId(),
                    safeMessage(ex)
            );
            return false;
        }
    }

    private java.math.BigDecimal extractAmount(NotificationEventPayload payload) {
        if (payload == null || payload.detailsJson() == null || payload.detailsJson().isBlank()) {
            return null;
        }
        try {
            JsonNode details = objectMapper.readTree(payload.detailsJson());
            JsonNode amount = details.path("outstandingAmount");
            if (amount.isMissingNode() || amount.isNull()) {
                return null;
            }
            if (amount.isNumber()) {
                return amount.decimalValue();
            }
            String text = amount.asText(null);
            if (text == null || text.isBlank()) {
                return null;
            }
            return new java.math.BigDecimal(text.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private record DispatchOutcome(boolean sent, boolean ignored, boolean pending, String errorMessage) {
        private static DispatchOutcome success() {
            return new DispatchOutcome(true, false, false, null);
        }

        private static DispatchOutcome skippedOutcome(String errorMessage) {
            return new DispatchOutcome(false, true, false, errorMessage);
        }

        private static DispatchOutcome pendingOutcome(String errorMessage) {
            return new DispatchOutcome(false, false, true, errorMessage);
        }
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
