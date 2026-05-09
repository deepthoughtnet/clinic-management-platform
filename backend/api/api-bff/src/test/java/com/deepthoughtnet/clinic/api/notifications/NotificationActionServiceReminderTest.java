package com.deepthoughtnet.clinic.api.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import com.deepthoughtnet.clinic.notification.service.NotificationHistoryService;
import com.deepthoughtnet.clinic.notification.service.model.NotificationHistoryRecord;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.service.PrescriptionTemplateService;
import com.deepthoughtnet.clinic.notify.NotificationProvider;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class NotificationActionServiceReminderTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    @Test
    void queuesOnlyBookedAppointmentsInReminderWindow() {
        NotificationHistoryService notificationHistoryService = Mockito.mock(NotificationHistoryService.class);
        PrescriptionService prescriptionService = Mockito.mock(PrescriptionService.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        AppointmentService appointmentService = Mockito.mock(AppointmentService.class);
        ConsultationService consultationService = Mockito.mock(ConsultationService.class);
        VaccinationService vaccinationService = Mockito.mock(VaccinationService.class);
        PlatformTenantManagementService tenantManagementService = Mockito.mock(PlatformTenantManagementService.class);
        PatientRepository patientRepository = Mockito.mock(PatientRepository.class);
        NotificationProvider notificationProvider = Mockito.mock(NotificationProvider.class);
        PrescriptionTemplateService prescriptionTemplateService = Mockito.mock(PrescriptionTemplateService.class);

        NotificationActionService service = new NotificationActionService(
                notificationHistoryService,
                prescriptionService,
                billingService,
                appointmentService,
                consultationService,
                vaccinationService,
                tenantManagementService,
                patientRepository,
                notificationProvider,
                prescriptionTemplateService
        );

        OffsetDateTime now = OffsetDateTime.now();
        AppointmentRecord due = appointment(UUID.randomUUID(), now.plusHours(2).plusMinutes(5).toLocalDate(), now.plusHours(2).plusMinutes(5).toLocalTime(), AppointmentStatus.BOOKED);
        AppointmentRecord skippedStatus = appointment(UUID.randomUUID(), now.plusHours(2).plusMinutes(5).toLocalDate(), now.plusHours(2).plusMinutes(5).toLocalTime(), AppointmentStatus.WAITING);
        AppointmentRecord outsideWindow = appointment(UUID.randomUUID(), now.plusHours(4).toLocalDate(), now.plusHours(4).toLocalTime(), AppointmentStatus.BOOKED);

        when(appointmentService.search(eq(TENANT_ID), any())).thenReturn(List.of(due, skippedStatus, outsideWindow));
        when(appointmentService.findById(eq(TENANT_ID), eq(due.id()))).thenReturn(due);
        when(patientRepository.findByTenantIdAndId(eq(TENANT_ID), eq(PATIENT_ID))).thenReturn(Optional.of(patient()));
        when(notificationHistoryService.queue(eq(TENANT_ID), eq(PATIENT_ID), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), eq(due.id()), eq(ACTOR_ID)))
                .thenReturn(new NotificationHistoryRecord(UUID.randomUUID(), TENANT_ID, PATIENT_ID, "APPOINTMENT_REMINDER", "email", "patient@clinic.local", "Appointment reminder", "msg", "PENDING", null, "APPOINTMENT", due.id(), "dedup", null, 0, null, now, now));

        int queued = service.queueAppointmentReminders(TENANT_ID, null, ACTOR_ID);

        assertThat(queued).isEqualTo(1);
        Mockito.verify(notificationHistoryService, Mockito.times(1)).queue(eq(TENANT_ID), eq(PATIENT_ID), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), eq(due.id()), eq(ACTOR_ID));
    }

    private AppointmentRecord appointment(UUID id, LocalDate date, LocalTime time, AppointmentStatus status) {
        return new AppointmentRecord(
                id,
                TENANT_ID,
                PATIENT_ID,
                "PAT-001",
                "Raj Sharma",
                "9876543210",
                DOCTOR_ID,
                "Doctor One",
                null,
                date,
                time,
                1,
                "Follow-up",
                AppointmentType.SCHEDULED,
                AppointmentPriority.NORMAL,
                status,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private PatientEntity patient() {
        PatientEntity entity = PatientEntity.create(TENANT_ID, "PAT-001");
        entity.update("Raj", "Sharma", com.deepthoughtnet.clinic.patient.service.model.PatientGender.MALE, null, 35, "9876543210", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
        try {
            java.lang.reflect.Field idField = PatientEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, PATIENT_ID);
            java.lang.reflect.Field emailField = PatientEntity.class.getDeclaredField("email");
            emailField.setAccessible(true);
            emailField.set(entity, "patient@clinic.local");
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return entity;
    }
}
