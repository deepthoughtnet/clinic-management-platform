package com.deepthoughtnet.clinic.notification.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.notification.db.NotificationHistoryEntity;
import com.deepthoughtnet.clinic.notification.db.NotificationHistoryRepository;
import com.deepthoughtnet.clinic.notification.db.NotificationOutboxRepository;
import com.deepthoughtnet.clinic.notification.db.NotificationOutboxEntity;
import com.deepthoughtnet.clinic.notification.model.NotificationEventPayload;
import com.deepthoughtnet.clinic.notification.service.NotificationHistoryFilter;
import com.deepthoughtnet.clinic.notification.service.model.NotificationHistoryGroupRecord;
import com.deepthoughtnet.clinic.notification.service.model.NotificationHistoryRecord;
import com.deepthoughtnet.clinic.notification.service.model.NotificationQueueResult;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.outbox.OutboxEventCommand;
import com.deepthoughtnet.clinic.platform.outbox.OutboxEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

class NotificationHistoryServiceImplTest {

    @Test
    void queueDetailedReturnsCreatedFalseWhenDuplicateAlreadyExists() {
        NotificationHistoryRepository repository = mock(NotificationHistoryRepository.class);
        NotificationOutboxRepository outboxRepository = mock(NotificationOutboxRepository.class);
        OutboxEventPublisher outboxEventPublisher = mock(OutboxEventPublisher.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        NotificationHistoryServiceImpl service = new NotificationHistoryServiceImpl(
                repository,
                outboxRepository,
                outboxEventPublisher,
                auditEventPublisher,
                new ObjectMapper()
        );
        when(repository.save(any(NotificationHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        NotificationHistoryEntity existing = NotificationHistoryEntity.create(
                tenantId,
                patientId,
                "APPOINTMENT_REMINDER",
                "email",
                "patient@example.com",
                "Appointment reminder",
                "Appointment scheduled",
                "APPOINTMENT",
                sourceId,
                "existing-dedup"
        );
        when(repository.findByTenantIdAndDeduplicationKey(any(), any())).thenReturn(Optional.of(existing));

        NotificationQueueResult result = service.queueDetailed(
                tenantId,
                patientId,
                "APPOINTMENT_REMINDER",
                "email",
                "patient@example.com",
                "Appointment reminder",
                "Appointment scheduled",
                "APPOINTMENT",
                sourceId,
                UUID.randomUUID()
        );

        assertThat(result.created()).isFalse();
        assertThat(result.notification().id()).isEqualTo(existing.getId());
        verify(repository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any());
    }

    @Test
    void queueDetailedWithSeparateDeliveryRecipientUsesBusinessRecipientForDedupeAndDeliveryRecipientForPayload() throws Exception {
        NotificationHistoryRepository repository = mock(NotificationHistoryRepository.class);
        NotificationOutboxRepository outboxRepository = mock(NotificationOutboxRepository.class);
        OutboxEventPublisher outboxEventPublisher = mock(OutboxEventPublisher.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        ObjectMapper objectMapper = new ObjectMapper();
        NotificationHistoryServiceImpl service = new NotificationHistoryServiceImpl(
                repository,
                outboxRepository,
                outboxEventPublisher,
                auditEventPublisher,
                objectMapper
        );
        when(repository.save(any(NotificationHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        when(repository.findByTenantIdAndDeduplicationKey(any(), any())).thenReturn(Optional.empty());
        when(outboxEventPublisher.publish(any())).thenReturn(UUID.randomUUID());

        NotificationQueueResult result = service.queueDetailed(
                tenantId,
                patientId,
                "APPOINTMENT_BOOKED",
                "in_app",
                "patient:PAT-1 • Asha Rao",
                "email",
                "asha@example.com",
                "Appointment confirmed",
                "Your appointment with Dr. Amit Verma is confirmed for 22 Jul 2026, 11:00 AM.",
                "APPOINTMENT",
                sourceId,
                "{\"eventId\":\"event-1\",\"correlationId\":\"corr-1\"}",
                UUID.randomUUID()
        );

        assertThat(result.created()).isTrue();
        assertThat(result.notification().recipient()).isEqualTo("patient:PAT-1 • Asha Rao");

        ArgumentCaptor<OutboxEventCommand> commandCaptor = ArgumentCaptor.forClass(OutboxEventCommand.class);
        verify(outboxEventPublisher).publish(commandCaptor.capture());
        NotificationEventPayload payload = objectMapper.readValue(commandCaptor.getValue().payloadJson(), NotificationEventPayload.class);
        assertThat(payload.recipient()).isEqualTo("asha@example.com");
        assertThat(payload.channel()).isEqualTo("email");
        assertThat(payload.detailsJson()).contains("\"eventId\":\"event-1\"");
        assertThat(payload.detailsJson()).contains("\"correlationId\":\"corr-1\"");
    }

    @Test
    void retryReusesOriginalDeliveryPayloadWhenHistoryRecipientIsBusinessFriendly() throws Exception {
        NotificationHistoryRepository repository = mock(NotificationHistoryRepository.class);
        NotificationOutboxRepository outboxRepository = mock(NotificationOutboxRepository.class);
        OutboxEventPublisher outboxEventPublisher = mock(OutboxEventPublisher.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        ObjectMapper objectMapper = new ObjectMapper();
        NotificationHistoryServiceImpl service = new NotificationHistoryServiceImpl(
                repository,
                outboxRepository,
                outboxEventPublisher,
                auditEventPublisher,
                objectMapper
        );
        when(repository.save(any(NotificationHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();
        String payloadJson = objectMapper.writeValueAsString(new NotificationEventPayload(
                UUID.randomUUID(),
                "APPOINTMENT_BOOKED",
                List.of(),
                "asha@example.com",
                "email",
                "Appointment confirmed",
                "Your appointment is confirmed.",
                "notification.sent",
                "{\"eventId\":\"event-1\"}",
                patientId,
                "APPOINTMENT",
                sourceId
        ));

        NotificationHistoryEntity history = NotificationHistoryEntity.create(
                tenantId,
                patientId,
                "APPOINTMENT_BOOKED",
                "in_app",
                "patient:PAT-001 • Asha Rao",
                "Appointment confirmed",
                "Your appointment is confirmed.",
                "APPOINTMENT",
                sourceId,
                tenantId + ":appointment_booked:patient-pat-001-asha-rao:appointment:" + sourceId
        );
        history.markFailed("email failed");
        history.attachOutboxEvent(outboxId);
        NotificationOutboxEntity outbox = NotificationOutboxEntity.pending(
                tenantId,
                "NOTIFICATION.APPOINTMENT_BOOKED",
                "NOTIFICATION_HISTORY",
                history.getId(),
                history.getDeduplicationKey(),
                payloadJson,
                OffsetDateTime.now().minusMinutes(1)
        );

        when(repository.findByTenantIdAndId(eq(tenantId), eq(history.getId()))).thenReturn(Optional.of(history));
        when(outboxRepository.findByTenantIdAndId(eq(tenantId), eq(outboxId))).thenReturn(Optional.of(outbox));
        when(outboxEventPublisher.publish(any())).thenReturn(UUID.randomUUID());

        service.retry(tenantId, history.getId(), actorId);

        ArgumentCaptor<OutboxEventCommand> commandCaptor = ArgumentCaptor.forClass(OutboxEventCommand.class);
        verify(outboxEventPublisher).publish(commandCaptor.capture());
        NotificationEventPayload retryPayload = objectMapper.readValue(commandCaptor.getValue().payloadJson(), NotificationEventPayload.class);
        assertThat(retryPayload.recipient()).isEqualTo("asha@example.com");
        assertThat(retryPayload.channel()).isEqualTo("email");
        assertThat(retryPayload.detailsJson()).contains("\"eventId\":\"event-1\"");
    }

    @Test
    void listGroupedCollapsesMultiChannelRowsIntoOneLogicalNotification() {
        NotificationHistoryRepository repository = mock(NotificationHistoryRepository.class);
        NotificationOutboxRepository outboxRepository = mock(NotificationOutboxRepository.class);
        OutboxEventPublisher outboxEventPublisher = mock(OutboxEventPublisher.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        NotificationHistoryServiceImpl service = new NotificationHistoryServiceImpl(
                repository,
                outboxRepository,
                outboxEventPublisher,
                auditEventPublisher,
                new ObjectMapper()
        );

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        NotificationHistoryEntity inApp = appointmentDelivery(tenantId, patientId, sourceId, eventId, "in_app", "patient", "SENT", null);
        NotificationHistoryEntity email = appointmentDelivery(tenantId, patientId, sourceId, eventId, "email", "patient@example.com", "SKIPPED", "Patient email unavailable");
        NotificationHistoryEntity sms = appointmentDelivery(tenantId, patientId, sourceId, eventId, "sms", "9999999999", "SKIPPED", "SMS notifications disabled");
        NotificationHistoryEntity whatsapp = appointmentDelivery(tenantId, patientId, sourceId, eventId, "whatsapp", "9999999999", "SKIPPED", "WhatsApp notifications disabled");
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<NotificationHistoryEntity>>any(), any(Sort.class)))
                .thenReturn(List.of(inApp, email, sms, whatsapp));

        List<NotificationHistoryGroupRecord> grouped = service.listGrouped(tenantId, new NotificationHistoryFilter(null, null, null, null, null, null, 0, 100));

        assertThat(grouped).hasSize(1);
        NotificationHistoryGroupRecord group = grouped.get(0);
        assertThat(group.logicalNotificationId()).isEqualTo("APPOINTMENT_BOOKED:" + tenantId + ":" + eventId + ":" + patientId);
        assertThat(group.overallStatus()).isEqualTo("DELIVERED");
        assertThat(group.deliveries()).extracting(NotificationHistoryRecord::channel).containsExactly("in_app", "email", "sms", "whatsapp");
    }

    @Test
    void listGroupedKeepsSeparateLegitimateOccurrencesAndAppliesChannelFilter() {
        NotificationHistoryRepository repository = mock(NotificationHistoryRepository.class);
        NotificationOutboxRepository outboxRepository = mock(NotificationOutboxRepository.class);
        OutboxEventPublisher outboxEventPublisher = mock(OutboxEventPublisher.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        NotificationHistoryServiceImpl service = new NotificationHistoryServiceImpl(
                repository,
                outboxRepository,
                outboxEventPublisher,
                auditEventPublisher,
                new ObjectMapper()
        );

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID firstEventId = UUID.randomUUID();
        UUID secondEventId = UUID.randomUUID();
        NotificationHistoryEntity first = appointmentDelivery(tenantId, patientId, sourceId, firstEventId, "in_app", "patient", "SENT", null);
        NotificationHistoryEntity second = appointmentDelivery(tenantId, patientId, sourceId, secondEventId, "in_app", "patient", "SENT", null);
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<NotificationHistoryEntity>>any(), any(Sort.class)))
                .thenReturn(List.of(second, first));

        List<NotificationHistoryGroupRecord> grouped = service.listGrouped(tenantId, new NotificationHistoryFilter(null, null, "in_app", null, null, null, 0, 100));

        assertThat(grouped).hasSize(2);
        assertThat(grouped).extracting(NotificationHistoryGroupRecord::logicalNotificationId)
                .containsExactly(
                        "APPOINTMENT_BOOKED:" + tenantId + ":" + secondEventId + ":" + patientId,
                        "APPOINTMENT_BOOKED:" + tenantId + ":" + firstEventId + ":" + patientId
                );
    }

    @Test
    void listGroupedComputesOverallStatusFromChannelOutcomes() {
        NotificationHistoryRepository repository = mock(NotificationHistoryRepository.class);
        NotificationOutboxRepository outboxRepository = mock(NotificationOutboxRepository.class);
        OutboxEventPublisher outboxEventPublisher = mock(OutboxEventPublisher.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        NotificationHistoryServiceImpl service = new NotificationHistoryServiceImpl(
                repository,
                outboxRepository,
                outboxEventPublisher,
                auditEventPublisher,
                new ObjectMapper()
        );

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID deliveredEventId = UUID.randomUUID();
        UUID partialEventId = UUID.randomUUID();
        UUID pendingEventId = UUID.randomUUID();
        UUID failedEventId = UUID.randomUUID();
        UUID skippedEventId = UUID.randomUUID();
        NotificationHistoryEntity delivered = appointmentDelivery(tenantId, patientId, sourceId, deliveredEventId, "in_app", "patient", "SENT", null);
        NotificationHistoryEntity partialInApp = appointmentDelivery(tenantId, patientId, sourceId, partialEventId, "in_app", "patient", "SENT", null);
        NotificationHistoryEntity partialEmail = appointmentDelivery(tenantId, patientId, sourceId, partialEventId, "email", "patient@example.com", "FAILED", "SMTP timeout");
        NotificationHistoryEntity pending = appointmentDelivery(tenantId, patientId, sourceId, pendingEventId, "in_app", "patient", "PENDING", null);
        NotificationHistoryEntity failed = appointmentDelivery(tenantId, patientId, sourceId, failedEventId, "in_app", "patient", "FAILED", "provider down");
        NotificationHistoryEntity skipped = appointmentDelivery(tenantId, patientId, sourceId, skippedEventId, "in_app", "patient", "SKIPPED", "Patient record unavailable");
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<NotificationHistoryEntity>>any(), any(Sort.class)))
                .thenReturn(List.of(delivered, partialInApp, partialEmail, pending, failed, skipped));

        List<NotificationHistoryGroupRecord> grouped = service.listGrouped(tenantId, new NotificationHistoryFilter(null, null, null, null, null, null, 0, 100));

        assertThat(grouped)
                .extracting(NotificationHistoryGroupRecord::overallStatus)
                .contains("DELIVERED", "PARTIAL", "PENDING", "FAILED", "NOT_DELIVERED");
    }

    private NotificationHistoryEntity appointmentDelivery(
            UUID tenantId,
            UUID patientId,
            UUID sourceId,
            UUID eventId,
            String channel,
            String recipient,
            String status,
            String reason
    ) {
        NotificationHistoryEntity entity = NotificationHistoryEntity.create(
                tenantId,
                patientId,
                "APPOINTMENT_BOOKED",
                channel,
                recipient,
                "Appointment confirmed",
                "Your appointment is confirmed.",
                "APPOINTMENT",
                sourceId,
                "APPOINTMENT_BOOKED:" + tenantId + ":" + eventId + ":" + patientId + ":" + channel + ":" + recipient + ":APPOINTMENT:" + sourceId
        );
        if ("SENT".equals(status)) {
            entity.markSent();
        } else if ("FAILED".equals(status)) {
            entity.markFailed(reason);
        } else if ("PENDING".equals(status)) {
            entity.retryNow();
        } else {
            entity.markSkipped(reason);
        }
        return entity;
    }
}
