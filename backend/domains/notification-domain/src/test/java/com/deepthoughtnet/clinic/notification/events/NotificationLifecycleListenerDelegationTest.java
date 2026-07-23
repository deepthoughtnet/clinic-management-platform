package com.deepthoughtnet.clinic.notification.events;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.deepthoughtnet.clinic.billing.events.BillGeneratedEvent;
import com.deepthoughtnet.clinic.billing.events.PaymentReceivedEvent;
import com.deepthoughtnet.clinic.consultation.events.FollowUpDueEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.model.LabReportPublishedEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.model.LabReportPublishedEventPayload;
import com.deepthoughtnet.clinic.platform.modulith.events.model.VaccinationDueEvent;
import com.deepthoughtnet.clinic.prescription.events.PrescriptionReadyEvent;
import com.deepthoughtnet.clinic.notification.service.AppointmentBookedNotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationLifecycleListenerDelegationTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID CONSULTATION_ID = UUID.randomUUID();
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    @Test
    void prescriptionReadyListenerDelegatesToNotificationService() {
        AppointmentBookedNotificationService service = mock(AppointmentBookedNotificationService.class);
        new PrescriptionReadyNotificationListener(service).handle(PrescriptionReadyEvent.ready(
                TENANT_ID,
                UUID.randomUUID(),
                CONSULTATION_ID,
                PATIENT_ID,
                DOCTOR_ID,
                "Dr. Test",
                "Clinic",
                "RX-1",
                LocalDate.of(2026, 7, 24),
                "Asia/Kolkata",
                OffsetDateTime.now(),
                1,
                ACTOR_ID
        ));
        verify(service).queue(org.mockito.ArgumentMatchers.any(PrescriptionReadyEvent.class));
    }

    @Test
    void labReportReadyListenerDelegatesToNotificationService() {
        AppointmentBookedNotificationService service = mock(AppointmentBookedNotificationService.class);
        new LabReportReadyNotificationListener(service).handle(new LabReportPublishedEvent(
                UUID.randomUUID(),
                "LAB_REPORT_PUBLISHED",
                1,
                OffsetDateTime.now(),
                TENANT_ID,
                "LAB",
                "LAB_ORDER",
                UUID.randomUUID(),
                "corr",
                "corr",
                ACTOR_ID,
                new LabReportPublishedEventPayload(
                        UUID.randomUUID(),
                        PATIENT_ID,
                        CONSULTATION_ID,
                        "LAB-1",
                        "Clinic",
                        "Asia/Kolkata",
                        OffsetDateTime.now(),
                        "report.pdf",
                        "PUBLISHED"
                )
        ));
        verify(service).queue(org.mockito.ArgumentMatchers.any(LabReportPublishedEvent.class));
    }

    @Test
    void billGeneratedListenerDelegatesToNotificationService() {
        AppointmentBookedNotificationService service = mock(AppointmentBookedNotificationService.class);
        new BillGeneratedNotificationListener(service).handle(BillGeneratedEvent.generated(
                TENANT_ID,
                UUID.randomUUID(),
                PATIENT_ID,
                "BILL-1",
                new BigDecimal("1200.00"),
                "INR",
                LocalDate.of(2026, 7, 24),
                "Clinic",
                "Asia/Kolkata",
                OffsetDateTime.now(),
                1,
                ACTOR_ID
        ));
        verify(service).queue(org.mockito.ArgumentMatchers.any(BillGeneratedEvent.class));
    }

    @Test
    void paymentReceivedListenerDelegatesToNotificationService() {
        AppointmentBookedNotificationService service = mock(AppointmentBookedNotificationService.class);
        new PaymentReceivedNotificationListener(service).handle(PaymentReceivedEvent.received(
                TENANT_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                PATIENT_ID,
                "BILL-1",
                "RCPT-1",
                new BigDecimal("1200.00"),
                "INR",
                "CASH",
                "Clinic",
                "Asia/Kolkata",
                OffsetDateTime.now(),
                ACTOR_ID
        ));
        verify(service).queue(org.mockito.ArgumentMatchers.any(PaymentReceivedEvent.class));
    }

    @Test
    void followUpDueListenerDelegatesToNotificationService() {
        AppointmentBookedNotificationService service = mock(AppointmentBookedNotificationService.class);
        new FollowUpDueNotificationListener(service).handle(FollowUpDueEvent.due(
                TENANT_ID,
                CONSULTATION_ID,
                PATIENT_ID,
                DOCTOR_ID,
                "Dr. Test",
                "Clinic",
                LocalDate.of(2026, 7, 24),
                "Asia/Kolkata",
                "24h",
                ACTOR_ID
        ));
        verify(service).queue(org.mockito.ArgumentMatchers.any(FollowUpDueEvent.class));
    }

    @Test
    void vaccinationDueListenerDelegatesToNotificationService() {
        AppointmentBookedNotificationService service = mock(AppointmentBookedNotificationService.class);
        new VaccinationDueNotificationListener(service).handle(VaccinationDueEvent.due(
                TENANT_ID,
                UUID.randomUUID(),
                PATIENT_ID,
                "Hepatitis B",
                "Dose 2",
                LocalDate.of(2026, 7, 24),
                "Asia/Kolkata",
                "Clinic",
                "24h",
                ACTOR_ID
        ));
        verify(service).queue(org.mockito.ArgumentMatchers.any(VaccinationDueEvent.class));
    }
}
