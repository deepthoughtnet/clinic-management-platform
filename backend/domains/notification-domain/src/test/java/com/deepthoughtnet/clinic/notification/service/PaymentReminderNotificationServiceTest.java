package com.deepthoughtnet.clinic.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.billing.events.PaymentReminderEvent;
import com.deepthoughtnet.clinic.billing.service.PaymentReminderStateReader;
import com.deepthoughtnet.clinic.billing.service.model.PaymentReminderState;
import com.deepthoughtnet.clinic.notification.service.model.NotificationHistoryRecord;
import com.deepthoughtnet.clinic.notification.service.model.NotificationQueueResult;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentReminderNotificationServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID BILL_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    @Test
    void queuePaymentReminderCreatesInAppAndAdditiveChannelsWhenEligible() {
        PatientService patientService = mock(PatientService.class);
        PaymentReminderStateReader billingStateReader = mock(PaymentReminderStateReader.class);
        NotificationHistoryService notificationHistoryService = mock(NotificationHistoryService.class);
        AppointmentBookedNotificationService service = new AppointmentBookedNotificationService(
                patientService,
                billingStateReader,
                notificationHistoryService,
                new ObjectMapper().findAndRegisterModules()
        );

        PatientRecord patient = patient();
        PaymentReminderState bill = reminderState(true, true, new BigDecimal("75.00"), "PARTIALLY_PAID", OffsetDateTime.now(), 7L);
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patient));
        when(billingStateReader.findCurrentState(eq(TENANT_ID), eq(BILL_ID))).thenReturn(bill);
        when(notificationHistoryService.queueDetailed(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new NotificationQueueResult(notification("PAYMENT_REMINDER", "in_app"), true));

        NotificationQueueResult result = service.queue(PaymentReminderEvent.due(
                TENANT_ID,
                BILL_ID,
                PATIENT_ID,
                "BILL-1001",
                bill.outstandingAmount(),
                "INR",
                null,
                "Asia/Kolkata",
                com.deepthoughtnet.clinic.billing.service.model.BillStatus.PARTIALLY_PAID,
                bill.updatedAt(),
                "OUTSTANDING",
                ACTOR_ID
        ));

        assertThat(result.created()).isTrue();
        verify(notificationHistoryService).queueDetailed(
                eq(TENANT_ID),
                eq(PATIENT_ID),
                eq("PAYMENT_REMINDER"),
                eq("in_app"),
                eq(PATIENT_ID.toString()),
                eq("in_app"),
                eq(PATIENT_ID.toString()),
                eq("Payment reminder"),
                any(),
                eq("BILL"),
                eq(BILL_ID),
                any(),
                eq(ACTOR_ID),
                any()
        );
        verify(notificationHistoryService).queueDetailed(
                eq(TENANT_ID),
                eq(PATIENT_ID),
                eq("PAYMENT_REMINDER"),
                eq("email"),
                eq("smita@example.com"),
                eq("email"),
                eq("smita@example.com"),
                eq("Payment reminder"),
                any(),
                eq("BILL"),
                eq(BILL_ID),
                any(),
                eq(ACTOR_ID),
                any()
        );
    }

    @Test
    void queuePaymentReminderSkipsWhenBillAlreadyPaid() {
        PatientService patientService = mock(PatientService.class);
        PaymentReminderStateReader billingStateReader = mock(PaymentReminderStateReader.class);
        NotificationHistoryService notificationHistoryService = mock(NotificationHistoryService.class);
        AppointmentBookedNotificationService service = new AppointmentBookedNotificationService(
                patientService,
                billingStateReader,
                notificationHistoryService,
                new ObjectMapper().findAndRegisterModules()
        );

        PatientRecord patient = patient();
        PaymentReminderState bill = reminderState(true, false, BigDecimal.ZERO, "PAID", OffsetDateTime.now(), 8L);
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patient));
        when(billingStateReader.findCurrentState(eq(TENANT_ID), eq(BILL_ID))).thenReturn(bill);
        when(notificationHistoryService.recordSkipped(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new NotificationQueueResult(notification("PAYMENT_REMINDER", "in_app"), true));

        NotificationQueueResult result = service.queue(PaymentReminderEvent.due(
                TENANT_ID,
                BILL_ID,
                PATIENT_ID,
                "BILL-1001",
                BigDecimal.ZERO,
                "INR",
                null,
                "Asia/Kolkata",
                com.deepthoughtnet.clinic.billing.service.model.BillStatus.PAID,
                bill.updatedAt(),
                "OUTSTANDING",
                ACTOR_ID
        ));

        assertThat(result.created()).isTrue();
        verify(notificationHistoryService).recordSkipped(
                eq(TENANT_ID),
                eq(PATIENT_ID),
                eq("PAYMENT_REMINDER"),
                eq("in_app"),
                eq(PATIENT_ID.toString()),
                eq("Payment reminder"),
                anyString(),
                eq("BILL"),
                eq(BILL_ID),
                any(),
                eq(ACTOR_ID),
                any(),
                eq("Bill already paid")
        );
    }

    private PatientRecord patient() {
        return new PatientRecord(
                PATIENT_ID,
                TENANT_ID,
                "PAT-001",
                "Smita",
                "Patil",
                PatientGender.FEMALE,
                LocalDate.of(1990, 1, 1),
                36,
                "9999999999",
                "smita@example.com",
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
                true,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private PaymentReminderState reminderState(boolean found, boolean eligible, BigDecimal dueAmount, String status, OffsetDateTime updatedAt, long version) {
        return new PaymentReminderState(found, eligible, dueAmount, status, updatedAt, version);
    }

    private NotificationHistoryRecord notification(String eventType, String channel) {
        return new NotificationHistoryRecord(
                UUID.randomUUID(),
                TENANT_ID,
                PATIENT_ID,
                eventType,
                channel,
                "smita@example.com",
                "Payment reminder",
                "Reminder: ₹75.00 is outstanding on bill BILL-1001.",
                "PENDING",
                null,
                "BILL",
                BILL_ID,
                "dedupe",
                null,
                0,
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }
}
