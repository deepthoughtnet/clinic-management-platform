package com.deepthoughtnet.clinic.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.appointment.events.AppointmentBookedEvent;
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
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void queueBuildsBusinessFriendlyNotificationAndKeepsDeliveryRecipientSeparate() {
        PatientService patientService = mock(PatientService.class);
        NotificationHistoryService notificationHistoryService = mock(NotificationHistoryService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AppointmentBookedNotificationService service = new AppointmentBookedNotificationService(
                patientService,
                notificationHistoryService,
                objectMapper
        );

        MDC.put("correlationId", "corr-appointment");
        AppointmentBookedEvent event = AppointmentBookedEvent.booked(
                TENANT_ID,
                APPOINTMENT_ID,
                PATIENT_ID,
                DOCTOR_ID,
                "Dr. Clinic",
                LocalDate.of(2026, 7, 22),
                LocalTime.of(11, 0),
                "Asia/Kolkata",
                "BOOKED",
                "SCHEDULED",
                ACTOR_ID
        );
        PatientRecord patient = new PatientRecord(
                PATIENT_ID,
                TENANT_ID,
                "PAT-001",
                "Asha",
                "Rao",
                null,
                null,
                null,
                "9999999999",
                "asha@example.com",
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
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patient));
        AtomicReference<String> detailsJson = new AtomicReference<>();
        when(notificationHistoryService.queueDetailed(
                eq(TENANT_ID),
                eq(PATIENT_ID),
                eq("APPOINTMENT_BOOKED"),
                eq("in_app"),
                eq("patient:PAT-001 • Asha Rao"),
                eq("email"),
                eq("asha@example.com"),
                eq("Appointment confirmed"),
                any(),
                eq("APPOINTMENT"),
                eq(APPOINTMENT_ID),
                any(),
                eq(ACTOR_ID)
        )).thenAnswer(invocation -> {
            detailsJson.set(invocation.getArgument(11));
            return new NotificationQueueResult(new NotificationHistoryRecord(
                    UUID.randomUUID(),
                    TENANT_ID,
                    PATIENT_ID,
                    "APPOINTMENT_BOOKED",
                    "in_app",
                    "patient:PAT-001 • Asha Rao",
                    "Appointment confirmed",
                    "Your appointment with Dr. Clinic is confirmed.",
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
        });

        NotificationQueueResult result = service.queue(event);

        assertThat(result.created()).isTrue();
        assertThat(detailsJson.get()).contains("\"eventId\":\"" + event.eventId() + "\"");
        assertThat(detailsJson.get()).contains("\"correlationId\":\"corr-appointment\"");
        assertThat(detailsJson.get()).contains("\"appointmentTimezone\":\"Asia/Kolkata\"");
        assertThat(detailsJson.get()).contains("\"doctorDisplayName\":\"Dr. Clinic\"");
    }

    @Test
    void queueHandlesMissingExternalRecipientWithoutFailing() {
        PatientService patientService = mock(PatientService.class);
        NotificationHistoryService notificationHistoryService = mock(NotificationHistoryService.class);
        AppointmentBookedNotificationService service = new AppointmentBookedNotificationService(
                patientService,
                notificationHistoryService,
                new ObjectMapper().findAndRegisterModules()
        );

        AppointmentBookedEvent event = AppointmentBookedEvent.booked(
                TENANT_ID,
                APPOINTMENT_ID,
                PATIENT_ID,
                DOCTOR_ID,
                "Dr. Clinic",
                LocalDate.of(2026, 7, 22),
                LocalTime.of(11, 0),
                "Asia/Kolkata",
                "BOOKED",
                "SCHEDULED",
                ACTOR_ID
        );
        PatientRecord patient = new PatientRecord(
                PATIENT_ID,
                TENANT_ID,
                "PAT-001",
                "Asha",
                "Rao",
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
                true,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(patientService.findById(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patient));
        when(notificationHistoryService.queueDetailed(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new NotificationQueueResult(new NotificationHistoryRecord(
                        UUID.randomUUID(),
                        TENANT_ID,
                        PATIENT_ID,
                        "APPOINTMENT_BOOKED",
                        "in_app",
                        "patient:PAT-001 • Asha Rao",
                        "Appointment confirmed",
                        "Your appointment with Dr. Clinic is confirmed.",
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
                ), true));

        NotificationQueueResult result = service.queue(event);

        assertThat(result.created()).isTrue();
        org.mockito.Mockito.verify(notificationHistoryService).queueDetailed(
                eq(TENANT_ID),
                eq(PATIENT_ID),
                eq("APPOINTMENT_BOOKED"),
                eq("in_app"),
                eq("patient:PAT-001 • Asha Rao"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                eq("Appointment confirmed"),
                any(),
                eq("APPOINTMENT"),
                eq(APPOINTMENT_ID),
                any(),
                eq(ACTOR_ID)
        );
    }
}
