package com.deepthoughtnet.clinic.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.appointment.events.AppointmentBookedEvent;
import com.deepthoughtnet.clinic.appointment.events.AppointmentCancelledEvent;
import com.deepthoughtnet.clinic.appointment.events.AppointmentReminderDueEvent;
import com.deepthoughtnet.clinic.appointment.events.AppointmentRescheduledEvent;
import com.deepthoughtnet.clinic.billing.events.BillGeneratedEvent;
import com.deepthoughtnet.clinic.billing.events.PaymentReceivedEvent;
import com.deepthoughtnet.clinic.billing.service.PaymentReminderStateReader;
import com.deepthoughtnet.clinic.consultation.events.FollowUpDueEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.model.LabReportPublishedEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.model.LabReportPublishedEventPayload;
import com.deepthoughtnet.clinic.platform.modulith.events.model.VaccinationDueEvent;
import com.deepthoughtnet.clinic.prescription.events.PrescriptionReadyEvent;
import com.deepthoughtnet.clinic.notification.service.model.NotificationHistoryRecord;
import com.deepthoughtnet.clinic.notification.service.model.NotificationQueueResult;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class AppointmentBookedNotificationServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID APPOINTMENT_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID CONSULTATION_ID = UUID.randomUUID();
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final LocalDate APPOINTMENT_DATE = LocalDate.of(2026, 7, 24);
    private static final LocalTime APPOINTMENT_TIME = LocalTime.of(10, 0);
    private static final String APPOINTMENT_TIMEZONE = "Asia/Kolkata";

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void queueBuildsBusinessFriendlyBookedNotificationAndKeepsDeliveryRecipientSeparate() {
        PatientService patientService = mock(PatientService.class);
        NotificationHistoryService notificationHistoryService = mock(NotificationHistoryService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AppointmentBookedNotificationService service = new AppointmentBookedNotificationService(
                patientService,
                mock(PaymentReminderStateReader.class),
                notificationHistoryService,
                objectMapper
        );

        MDC.put("correlationId", "corr-appointment");
        AppointmentBookedEvent event = bookedEvent("Dr. Clinic");
        PatientRecord patient = patientRecord("9999999999", "asha@example.com");
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patient));
        AtomicReference<String> detailsJson = new AtomicReference<>();
        when(notificationHistoryService.queueDetailed(
                eq(TENANT_ID),
                eq(PATIENT_ID),
                eq("APPOINTMENT_BOOKED"),
                eq("in_app"),
                eq(PATIENT_ID.toString()),
                eq("in_app"),
                eq(PATIENT_ID.toString()),
                eq("Appointment confirmed"),
                any(),
                eq("APPOINTMENT"),
                eq(APPOINTMENT_ID),
                any(),
                eq(ACTOR_ID),
                any()
        )).thenAnswer(invocation -> {
            detailsJson.set(invocation.getArgument(11));
            return notificationResult("Appointment confirmed", "Your appointment with Dr. Clinic is confirmed for 24 Jul 2026, 10:00 AM.");
        });

        NotificationQueueResult result = service.queue(event);

        assertThat(result.created()).isTrue();
        assertThat(detailsJson.get()).contains("\"eventId\":\"" + event.eventId() + "\"");
        assertThat(detailsJson.get()).contains("\"correlationId\":\"corr-appointment\"");
        assertThat(detailsJson.get()).contains("\"appointmentTimezone\":\"Asia/Kolkata\"");
        assertThat(detailsJson.get()).contains("\"doctorDisplayName\":\"Dr. Clinic\"");
        assertThat(detailsJson.get()).contains("\"recipientType\":\"PATIENT\"");
        assertThat(detailsJson.get()).contains("\"recipientId\":\"" + PATIENT_ID + "\"");
    }

    @Test
    void queueBookedNormalizesDoctorPrefixOnceAndFormatsDateWithoutSeconds() {
        assertBookedMessage("Amit Verma", "Dr. Amit Verma");
        assertBookedMessage("Dr Amit Verma", "Dr. Amit Verma");
        assertBookedMessage("Dr. Amit Verma", "Dr. Amit Verma");
    }

    @Test
    void queueRescheduledUsesOldAndNewDateTimesWithNormalizedDoctorPrefix() {
        var captured = captureLifecycleMessage(
                bookedService -> bookedService.queueRescheduled(AppointmentRescheduledEvent.rescheduled(
                        TENANT_ID,
                        APPOINTMENT_ID,
                        PATIENT_ID,
                        DOCTOR_ID,
                        "Dr Amit Verma",
                        "City Clinic",
                        LocalDate.of(2026, 7, 23),
                        LocalTime.of(10, 0),
                        LocalDate.of(2026, 7, 23),
                        LocalTime.of(12, 30),
                        APPOINTMENT_TIMEZONE,
                        2,
                        ACTOR_ID
                )))
                ;

        assertThat(captured).isEqualTo("Your appointment with Dr. Amit Verma has been rescheduled from 23 Jul 2026, 10:00 AM to 23 Jul 2026, 12:30 PM.");
        assertThat(captured).doesNotContain(":00:00");
    }

    @Test
    void queueCancelledUsesScheduledDateTimeAndSuppressesInternalReason() {
        var captured = captureLifecycleMessage(
                bookedService -> bookedService.queueCancelled(AppointmentCancelledEvent.cancelled(
                        TENANT_ID,
                        APPOINTMENT_ID,
                        PATIENT_ID,
                        DOCTOR_ID,
                        "Amit Verma",
                        null,
                        LocalDate.of(2026, 7, 24),
                        LocalTime.of(10, 0),
                        APPOINTMENT_TIMEZONE,
                        3,
                        ACTOR_ID
                )))
                ;

        assertThat(captured).isEqualTo("Your appointment with Dr. Amit Verma scheduled for 24 Jul 2026, 10:00 AM has been cancelled.");
        assertThat(captured).doesNotContain("reason");
        assertThat(captured).doesNotContain(":00:00");
    }

    @Test
    void queueReminderDueUsesNormalizedDoctorPrefixAndAppointmentDateTime() {
        var captured = captureLifecycleMessage(
                bookedService -> bookedService.queueReminderDue(AppointmentReminderDueEvent.due(
                        TENANT_ID,
                        APPOINTMENT_ID,
                        PATIENT_ID,
                        DOCTOR_ID,
                        "Dr. Amit Verma",
                        "City Clinic",
                        LocalDate.of(2026, 7, 24),
                        LocalTime.of(10, 0),
                        APPOINTMENT_TIMEZONE,
                        "24h",
                        4,
                        ACTOR_ID
                )))
                ;

        assertThat(captured).isEqualTo("Reminder: You have an appointment with Dr. Amit Verma on 24 Jul 2026, 10:00 AM.");
    }

    @Test
    void queuePrescriptionReadyUsesSafeCopyWithoutMedicineDetails() {
        var captured = captureLifecycleMessage(service -> service.queue(PrescriptionReadyEvent.ready(
                TENANT_ID,
                UUID.randomUUID(),
                CONSULTATION_ID,
                PATIENT_ID,
                DOCTOR_ID,
                "Dr Amit Verma",
                "City Clinic",
                "RX-1001",
                LocalDate.of(2026, 7, 24),
                APPOINTMENT_TIMEZONE,
                OffsetDateTime.parse("2026-07-23T10:15:00Z"),
                1,
                ACTOR_ID
        )));

        assertThat(captured).isEqualTo("Your prescription from Dr. Amit Verma is ready. Visit date: 24 Jul 2026.");
    }

    @Test
    void queueLabReportReadyUsesBusinessFriendlyCopy() {
        var captured = captureLifecycleMessage(service -> service.queue(new LabReportPublishedEvent(
                UUID.randomUUID(),
                "LAB_REPORT_PUBLISHED",
                1,
                OffsetDateTime.parse("2026-07-23T10:15:00Z"),
                TENANT_ID,
                "LAB",
                "LAB_ORDER",
                UUID.randomUUID(),
                "corr-lab",
                "corr-lab",
                ACTOR_ID,
                new LabReportPublishedEventPayload(
                        UUID.randomUUID(),
                        PATIENT_ID,
                        CONSULTATION_ID,
                        "LAB-1001",
                        "City Clinic",
                        APPOINTMENT_TIMEZONE,
                        OffsetDateTime.parse("2026-07-23T10:15:00Z"),
                        "lab-report.pdf",
                        "PUBLISHED"
                )
        )));

        assertThat(captured).isEqualTo("Your lab report for order LAB-1001 is ready.");
    }

    @Test
    void queueBillGeneratedUsesAmountAndDueDate() {
        var captured = captureLifecycleMessage(service -> service.queue(BillGeneratedEvent.generated(
                TENANT_ID,
                UUID.randomUUID(),
                PATIENT_ID,
                "BILL-1001",
                new java.math.BigDecimal("2500.00"),
                "INR",
                LocalDate.of(2026, 7, 24),
                "City Clinic",
                APPOINTMENT_TIMEZONE,
                OffsetDateTime.parse("2026-07-23T10:15:00Z"),
                1,
                ACTOR_ID
        )));

        assertThat(captured).contains("₹2500.00");
        assertThat(captured).contains("Payment is due by 24 Jul 2026.");
    }

    @Test
    void queuePaymentReceivedUsesReceiptAwareCopy() {
        var captured = captureLifecycleMessage(service -> service.queue(PaymentReceivedEvent.received(
                TENANT_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                PATIENT_ID,
                "BILL-1001",
                "RCPT-2001",
                new java.math.BigDecimal("2500.00"),
                "INR",
                "UPI",
                "City Clinic",
                APPOINTMENT_TIMEZONE,
                OffsetDateTime.parse("2026-07-23T10:15:00Z"),
                ACTOR_ID
        )));

        assertThat(captured).contains("We received your payment of ₹2500.00. Thank you.");
        assertThat(captured).contains("Receipt RCPT-2001 has been generated.");
    }

    @Test
    void queueFollowUpDueUsesReminderCopy() {
        var captured = captureLifecycleMessage(service -> service.queue(FollowUpDueEvent.due(
                TENANT_ID,
                CONSULTATION_ID,
                PATIENT_ID,
                DOCTOR_ID,
                "Dr Amit Verma",
                "City Clinic",
                LocalDate.of(2026, 7, 24),
                APPOINTMENT_TIMEZONE,
                "24h",
                ACTOR_ID
        )));

        assertThat(captured).isEqualTo("Reminder: Your follow-up with Dr. Amit Verma is due on 24 Jul 2026.");
    }

    @Test
    void queueVaccinationDueUsesDoseAwareCopy() {
        var captured = captureLifecycleMessage(service -> service.queue(VaccinationDueEvent.due(
                TENANT_ID,
                UUID.randomUUID(),
                PATIENT_ID,
                "Hepatitis B",
                "Dose 2",
                LocalDate.of(2026, 7, 24),
                APPOINTMENT_TIMEZONE,
                "City Clinic",
                "24h",
                ACTOR_ID
        )));

        assertThat(captured).isEqualTo("Hepatitis B - Dose 2 is due on 24 Jul 2026.");
    }

    @Test
    void queueHandlesMissingExternalRecipientWithoutFailing() {
        PatientService patientService = mock(PatientService.class);
        NotificationHistoryService notificationHistoryService = mock(NotificationHistoryService.class);
        AppointmentBookedNotificationService service = new AppointmentBookedNotificationService(
                patientService,
                mock(PaymentReminderStateReader.class),
                notificationHistoryService,
                new ObjectMapper().findAndRegisterModules()
        );

        AppointmentBookedEvent event = bookedEvent("Dr. Clinic");
        PatientRecord patient = patientRecord("9999999999", null);
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patient));
        when(notificationHistoryService.queueDetailed(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(notificationResult("Appointment confirmed", "Your appointment with Dr. Clinic is confirmed for 24 Jul 2026, 10:00 AM."));

        NotificationQueueResult result = service.queue(event);

        assertThat(result.created()).isTrue();
        org.mockito.Mockito.verify(notificationHistoryService).queueDetailed(
                eq(TENANT_ID),
                eq(PATIENT_ID),
                eq("APPOINTMENT_BOOKED"),
                eq("in_app"),
                eq(PATIENT_ID.toString()),
                eq("in_app"),
                eq(PATIENT_ID.toString()),
                eq("Appointment confirmed"),
                any(),
                eq("APPOINTMENT"),
                eq(APPOINTMENT_ID),
                any(),
                eq(ACTOR_ID),
                any()
        );
    }

    private AppointmentBookedEvent bookedEvent(String doctorDisplayName) {
        return AppointmentBookedEvent.booked(
                TENANT_ID,
                APPOINTMENT_ID,
                PATIENT_ID,
                DOCTOR_ID,
                doctorDisplayName,
                APPOINTMENT_DATE,
                APPOINTMENT_TIME,
                APPOINTMENT_TIMEZONE,
                "BOOKED",
                "SCHEDULED",
                ACTOR_ID
        );
    }

    private PatientRecord patientRecord(String mobile, String email) {
        return new PatientRecord(
                PATIENT_ID,
                TENANT_ID,
                "PAT-001",
                "Asha",
                "Rao",
                null,
                null,
                null,
                mobile,
                email,
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

    private NotificationQueueResult notificationResult(String subject, String message) {
        return new NotificationQueueResult(new NotificationHistoryRecord(
                UUID.randomUUID(),
                TENANT_ID,
                PATIENT_ID,
                "APPOINTMENT_BOOKED",
                "in_app",
                PATIENT_ID.toString(),
                subject,
                message,
                "PENDING",
                null,
                "APPOINTMENT",
                APPOINTMENT_ID,
                "dedup",
                null,
                0,
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        ), true);
    }

    private void assertBookedMessage(String doctorName, String normalizedDoctorName) {
        String message = captureLifecycleMessage(bookedService -> bookedService.queueBooked(bookedEvent(doctorName)));
        assertThat(message).contains(normalizedDoctorName);
        assertThat(message).contains("24 Jul 2026, 10:00 AM");
        assertThat(message).doesNotContain(":00:00");
    }

    private String captureLifecycleMessage(java.util.function.Function<AppointmentBookedNotificationService, NotificationQueueResult> invocation) {
        PatientService patientService = mock(PatientService.class);
        NotificationHistoryService notificationHistoryService = mock(NotificationHistoryService.class);
        AppointmentBookedNotificationService service = new AppointmentBookedNotificationService(
                patientService,
                mock(PaymentReminderStateReader.class),
                notificationHistoryService,
                new ObjectMapper().findAndRegisterModules()
        );

        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patientRecord("9999999999", "asha@example.com")));
        AtomicReference<String> messageRef = new AtomicReference<>();
        when(notificationHistoryService.queueDetailed(
                eq(TENANT_ID),
                eq(PATIENT_ID),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenAnswer(invocationContext -> {
            String subject = invocationContext.getArgument(7);
            String message = invocationContext.getArgument(8);
            messageRef.set(message);
            return notificationResult(subject, message);
        });

        invocation.apply(service);
        return messageRef.get();
    }
}
