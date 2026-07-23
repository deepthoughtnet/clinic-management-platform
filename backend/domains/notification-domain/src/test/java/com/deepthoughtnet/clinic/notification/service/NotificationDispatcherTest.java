package com.deepthoughtnet.clinic.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.notification.db.NotificationOutboxEntity;
import com.deepthoughtnet.clinic.notification.db.NotificationOutboxRepository;
import com.deepthoughtnet.clinic.appointment.service.AppointmentReminderReadService;
import com.deepthoughtnet.clinic.billing.service.PaymentReminderStateReader;
import com.deepthoughtnet.clinic.billing.service.model.PaymentReminderState;
import com.deepthoughtnet.clinic.notification.model.NotificationEventPayload;
import com.deepthoughtnet.clinic.notification.service.NotificationDispatcher.NotificationDispatchSettings;
import com.deepthoughtnet.clinic.notification.service.NotificationHistoryService;
import com.deepthoughtnet.clinic.notify.NotificationMessage;
import com.deepthoughtnet.clinic.notify.NotificationProvider;
import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
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
    private final NotificationHistoryService notificationHistoryService = mock(NotificationHistoryService.class);
    private final AppointmentReminderReadService appointmentReminderReadService = mock(AppointmentReminderReadService.class);
    private final PaymentReminderStateReader paymentReminderStateReader = mock(PaymentReminderStateReader.class);
    private final PatientService patientService = mock(PatientService.class);
    private final NotificationProvider notificationProvider = mock(NotificationProvider.class);
    private final NotificationDispatcher dispatcher = new NotificationDispatcher(
            repository,
            recipientResolver,
            notificationHistoryService,
            appointmentReminderReadService,
            paymentReminderStateReader,
            patientService,
            notificationProvider,
            List.<MessageProvider>of(),
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

    @Test
    void dispatchOneSendsInAppNotificationToStablePatientRecipientWithoutPortalMapping() throws Exception {
        NotificationOutboxEntity event = inAppEvent();
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(patientService.findById(event.getTenantId(), UUID.fromString("22222222-2222-4222-8222-222222222222")))
                .thenReturn(Optional.of(patientRecord(true)));

        dispatcher.dispatchOne(event.getId(), settings());

        verifyNoInteractions(notificationProvider);
        assertThat(event.getStatus()).isEqualTo("SENT");
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getProcessedAt()).isNotNull();
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void dispatchOneSkipsInAppNotificationWhenPatientRecordIsUnavailable() throws Exception {
        NotificationOutboxEntity event = inAppEvent();
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(patientService.findById(event.getTenantId(), UUID.fromString("22222222-2222-4222-8222-222222222222")))
                .thenReturn(Optional.empty());

        dispatcher.dispatchOne(event.getId(), settings());

        assertThat(event.getStatus()).isEqualTo("IGNORED");
        assertThat(event.getIgnoredAt()).isNotNull();
        verify(notificationHistoryService).markSkipped(event.getTenantId(), UUID.fromString("11111111-1111-4111-8111-111111111111"), "Patient record unavailable");
    }

    @Test
    void dispatchOneSkipsStalePaymentReminderAfterBillIsPaid() throws Exception {
        NotificationOutboxEntity event = paymentReminderEvent();
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(paymentReminderStateReader.findCurrentState(event.getTenantId(), UUID.fromString("33333333-3333-4333-8333-333333333333")))
                .thenReturn(new PaymentReminderState(
                        true,
                        false,
                        java.math.BigDecimal.ZERO,
                        "PAID",
                        OffsetDateTime.now(),
                        9L
                ));

        dispatcher.dispatchOne(event.getId(), settings());

        assertThat(event.getStatus()).isEqualTo("IGNORED");
        verify(notificationHistoryService).markSkipped(event.getTenantId(), UUID.fromString("11111111-1111-4111-8111-111111111112"), "Bill already paid");
        verifyNoInteractions(notificationProvider);
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

    private NotificationOutboxEntity inAppEvent() throws Exception {
        NotificationEventPayload payload = new NotificationEventPayload(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                "APPOINTMENT_BOOKED",
                List.of(),
                "22222222-2222-4222-8222-222222222222",
                "in_app",
                "Appointment confirmed",
                "Your appointment with Dr. Amit Verma is confirmed for 24 Jul 2026, 10:00 AM.",
                "notification.sent",
                "{\"recipientType\":\"PATIENT\",\"recipientId\":\"22222222-2222-4222-8222-222222222222\"}",
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                "APPOINTMENT",
                UUID.randomUUID()
        );
        return NotificationOutboxEntity.pending(
                UUID.randomUUID(),
                "NOTIFICATION.APPOINTMENT_BOOKED",
                "NOTIFICATION_HISTORY",
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                "notification:test:" + UUID.randomUUID(),
                objectMapper.writeValueAsString(payload),
                OffsetDateTime.now().minusMinutes(1)
        );
    }

    private NotificationOutboxEntity paymentReminderEvent() throws Exception {
        NotificationEventPayload payload = new NotificationEventPayload(
                UUID.fromString("11111111-1111-4111-8111-111111111112"),
                "PAYMENT_REMINDER",
                List.of(),
                "22222222-2222-4222-8222-222222222222",
                "in_app",
                "Payment reminder",
                "Reminder: ₹75.00 is outstanding on bill BILL-9001.",
                "notification.sent",
                "{\"outstandingAmount\":\"75.00\",\"billNumber\":\"BILL-9001\"}",
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                "BILL",
                UUID.fromString("33333333-3333-4333-8333-333333333333")
        );
        return NotificationOutboxEntity.pending(
                UUID.randomUUID(),
                "NOTIFICATION.PAYMENT_REMINDER",
                "NOTIFICATION_HISTORY",
                UUID.fromString("11111111-1111-4111-8111-111111111112"),
                "notification:test:" + UUID.randomUUID(),
                objectMapper.writeValueAsString(payload),
                OffsetDateTime.now().minusMinutes(1)
        );
    }

    private PatientRecord patientRecord(boolean active) {
        return new PatientRecord(
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                UUID.randomUUID(),
                "PAT-001",
                "Smita",
                "Patil",
                null,
                null,
                null,
                "9999999999",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                active,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private NotificationDispatchSettings settings() {
        return new NotificationDispatchSettings("email", 25, 2, Duration.ofMinutes(1));
    }
}
