package com.deepthoughtnet.clinic.api.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentSearchCriteria;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria;
import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.notification.service.NotificationHistoryService;
import com.deepthoughtnet.clinic.notification.service.model.NotificationHistoryRecord;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.service.PrescriptionTemplateService;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class NotificationActionServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();

    private NotificationHistoryService notificationHistoryService;
    private BillingService billingService;
    private AppointmentService appointmentService;
    private PatientRepository patientRepository;
    private NotificationActionService service;

    @BeforeEach
    void setUp() {
        notificationHistoryService = mock(NotificationHistoryService.class);
        billingService = mock(BillingService.class);
        appointmentService = mock(AppointmentService.class);
        patientRepository = mock(PatientRepository.class);
        service = new NotificationActionService(
                notificationHistoryService,
                mock(PrescriptionService.class),
                billingService,
                appointmentService,
                mock(ConsultationService.class),
                mock(VaccinationService.class),
                mock(PlatformTenantManagementService.class),
                patientRepository,
                mock(com.deepthoughtnet.clinic.notify.NotificationProvider.class),
                mock(PrescriptionTemplateService.class)
        );

        when(notificationHistoryService.queue(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mock(NotificationHistoryRecord.class));
        PatientEntity patient = mock(PatientEntity.class);
        when(patient.getId()).thenReturn(patientId);
        when(patient.getFirstName()).thenReturn("Asha");
        when(patient.getLastName()).thenReturn("Rao");
        when(patient.getEmail()).thenReturn("asha@example.com");
        when(patient.getMobile()).thenReturn("9999999999");
        when(patientRepository.findByTenantIdAndId(eq(tenantId), eq(patientId))).thenReturn(Optional.of(patient));
    }

    @Test
    void queuePaymentRemindersQueuesOutstandingBills() {
        when(billingService.list(eq(tenantId), any(BillingSearchCriteria.class))).thenReturn(List.of(
                new BillRecord(
                        UUID.randomUUID(),
                        tenantId,
                        "BILL-1",
                        patientId,
                        "PAT-1",
                        "Asha Rao",
                        null,
                        null,
                        LocalDate.now(),
                        BillStatus.PARTIALLY_PAID,
                        new BigDecimal("100.00"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("100.00"),
                        new BigDecimal("25.00"),
                        new BigDecimal("75.00"),
                        null,
                        OffsetDateTime.now(),
                        OffsetDateTime.now(),
                        List.of()
                )
        ));

        int queued = service.queuePaymentReminders(tenantId, actorId);

        assertThat(queued).isEqualTo(1);
        Mockito.verify(notificationHistoryService).queue(
                eq(tenantId),
                eq(patientId),
                eq("PAYMENT_REMINDER"),
                eq("email"),
                eq("asha@example.com"),
                eq("Payment reminder"),
                contains("BILL-1"),
                eq("BILL"),
                any(),
                eq(actorId)
        );
    }

    @Test
    void queueMissedAppointmentRemindersQueuesPastUnresolvedAppointments() {
        AppointmentRecord appointment = new AppointmentRecord(
                UUID.randomUUID(),
                tenantId,
                patientId,
                "PAT-1",
                "Asha Rao",
                "9999999999",
                UUID.randomUUID(),
                "Dr. Clinic",
                null,
                LocalDate.now().minusDays(1),
                LocalTime.of(9, 0),
                1,
                "Follow up",
                AppointmentType.FOLLOW_UP,
                AppointmentPriority.FOLLOW_UP,
                AppointmentStatus.BOOKED,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(appointmentService.search(eq(tenantId), any(AppointmentSearchCriteria.class))).thenReturn(List.of(appointment));
        when(appointmentService.findById(eq(tenantId), eq(appointment.id()))).thenReturn(appointment);

        int queued = service.queueMissedAppointmentReminders(tenantId, LocalDate.now(), actorId);

        assertThat(queued).isEqualTo(1);
        Mockito.verify(notificationHistoryService).queue(
                eq(tenantId),
                eq(patientId),
                eq("MISSED_APPOINTMENT_REMINDER"),
                eq("email"),
                eq("asha@example.com"),
                eq("Missed appointment reminder"),
                contains("reschedule"),
                eq("APPOINTMENT"),
                any(),
                eq(actorId)
        );
    }
}
