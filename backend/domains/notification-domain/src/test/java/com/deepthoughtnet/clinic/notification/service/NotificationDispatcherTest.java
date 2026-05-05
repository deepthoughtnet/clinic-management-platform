package com.deepthoughtnet.clinic.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.notification.db.NotificationOutboxEntity;
import com.deepthoughtnet.clinic.notification.db.NotificationOutboxRepository;
import com.deepthoughtnet.clinic.notification.model.NotificationEventPayload;
import com.deepthoughtnet.clinic.notification.service.NotificationDispatcher.NotificationDispatchSettings;
import com.deepthoughtnet.clinic.notify.NotificationMessage;
import com.deepthoughtnet.clinic.notify.NotificationProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;

class NotificationDispatcherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
    private final NotificationRecipientResolver recipientResolver = mock(NotificationRecipientResolver.class);
    private final NotificationProvider notificationProvider = mock(NotificationProvider.class);
    private final NotificationDispatcher dispatcher = new NotificationDispatcher(
            repository,
            recipientResolver,
            notificationProvider,
            objectMapper
    );

    @Test
    void findsDuePendingNotificationIds() throws Exception {
        NotificationOutboxEntity event = pendingEvent();
        when(repository.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(event)));

        List<UUID> ids = dispatcher.findDueNotificationIds(settings());

        assertThat(ids).containsExactly(event.getId());
    }

    @Test
    void dispatchOneSendsNotificationAndMarksEventSent() throws Exception {
        NotificationOutboxEntity event = pendingEvent();
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(recipientResolver.resolveEmailsByRoles(event.getTenantId(), List.of("CLINIC_APPROVER")))
                .thenReturn(List.of("approver@example.com"));

        dispatcher.dispatchOne(event.getId(), settings());

        ArgumentCaptor<NotificationMessage> message = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationProvider).send(message.capture());
        assertThat(message.getValue().recipient()).isEqualTo("approver@example.com");
        assertThat(message.getValue().subject()).isEqualTo("Clinic ready for approval");
        assertThat(event.getStatus()).isEqualTo("SENT");
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getProcessedAt()).isNotNull();
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void dispatchOneRecordsFailureAndRetryMetadata() throws Exception {
        NotificationOutboxEntity event = pendingEvent();
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(recipientResolver.resolveEmailsByRoles(event.getTenantId(), List.of("CLINIC_APPROVER")))
                .thenReturn(List.of("approver@example.com"));
        doThrow(new IllegalStateException("SMTP unavailable")).when(notificationProvider).send(any());

        dispatcher.dispatchOne(event.getId(), settings());

        assertThat(event.getStatus()).isEqualTo("PENDING");
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getProcessedAt()).isNull();
        assertThat(event.getLastError()).isEqualTo("SMTP unavailable");
        assertThat(event.getNextAttemptAt()).isAfter(OffsetDateTime.now().minusSeconds(1));
    }

    private NotificationOutboxEntity pendingEvent() throws Exception {
        NotificationEventPayload payload = new NotificationEventPayload(
                "CLINIC_READY_FOR_APPROVAL",
                List.of("CLINIC_APPROVER"),
                "Clinic ready for approval",
                "An clinic is ready for approval.",
                "CLINIC_SUBMITTED_FOR_APPROVAL",
                "{}"
        );
        return NotificationOutboxEntity.pending(
                UUID.randomUUID(),
                "CLINIC_READY_FOR_APPROVAL",
                "CLINIC",
                UUID.randomUUID(),
                "notification:test:" + UUID.randomUUID(),
                objectMapper.writeValueAsString(payload),
                OffsetDateTime.now().minusMinutes(1)
        );
    }

    private NotificationDispatchSettings settings() {
        return new NotificationDispatchSettings("email", 25, 2, Duration.ofMinutes(1));
    }
}
