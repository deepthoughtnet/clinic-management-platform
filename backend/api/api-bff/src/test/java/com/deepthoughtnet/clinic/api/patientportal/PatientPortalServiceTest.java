package com.deepthoughtnet.clinic.api.patientportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRescheduleCommand;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatusUpdateCommand;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentUpsertCommand;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotRecord;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotStatus;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentBookingRequest;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillItemType;
import com.deepthoughtnet.clinic.billing.service.model.BillLineRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.billing.service.model.DiscountType;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileRecord;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.TenantNotificationSettingsService;
import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.argThat;

class PatientPortalServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OTHER_TENANT_ID = UUID.randomUUID();
    private static final UUID APP_USER_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();

    private AppUserRepository appUserRepository;
    private PatientRepository patientRepository;
    private ClinicProfileService clinicProfileService;
    private TenantUserManagementService tenantUserManagementService;
    private DoctorProfileService doctorProfileService;
    private PatientService patientService;
    private TenantNotificationSettingsService notificationSettingsService;
    private AppointmentService appointmentService;
    private PrescriptionService prescriptionService;
    private BillingService billingService;
    private PatientPortalService service;

    @BeforeEach
    void setUp() {
        appUserRepository = mock(AppUserRepository.class);
        patientRepository = mock(PatientRepository.class);
        clinicProfileService = mock(ClinicProfileService.class);
        tenantUserManagementService = mock(TenantUserManagementService.class);
        doctorProfileService = mock(DoctorProfileService.class);
        patientService = mock(PatientService.class);
        notificationSettingsService = mock(TenantNotificationSettingsService.class);
        appointmentService = mock(AppointmentService.class);
        prescriptionService = mock(PrescriptionService.class);
        billingService = mock(BillingService.class);
        service = new PatientPortalService(
                appUserRepository,
                patientRepository,
                clinicProfileService,
                tenantUserManagementService,
                doctorProfileService,
                patientService,
                notificationSettingsService,
                appointmentService,
                prescriptionService,
                billingService
        );
        when(notificationSettingsService.findByTenantId(any())).thenReturn(Optional.empty());
        RequestContextHolder.set(new RequestContext(new TenantId(TENANT_ID), APP_USER_ID, "patient-sub", Set.of("PATIENT"), "PATIENT", "corr-1"));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void patientSeesOnlyOwnData() {
        AppUserEntity appUser = AppUserEntity.create(TENANT_ID, "patient-sub", "patient@example.com", "Portal Patient");
        appUser.setPatientId(PATIENT_ID);
        PatientEntity patient = patientEntity(TENANT_ID, PATIENT_ID, "PAT-001");

        when(appUserRepository.findByTenantIdAndId(TENANT_ID, APP_USER_ID)).thenReturn(Optional.of(appUser));
        when(patientRepository.findByTenantIdAndId(TENANT_ID, PATIENT_ID)).thenReturn(Optional.of(patient));
        when(clinicProfileService.findByTenantId(TENANT_ID)).thenReturn(Optional.of(clinicProfile()));
        when(appointmentService.listByPatient(TENANT_ID, PATIENT_ID)).thenReturn(List.of(appointmentRecord(PATIENT_ID)));
        when(prescriptionService.listByPatient(TENANT_ID, PATIENT_ID)).thenReturn(List.of(prescriptionRecord(PATIENT_ID)));
        when(billingService.listByPatient(TENANT_ID, PATIENT_ID)).thenReturn(List.of(billRecord(PATIENT_ID)));
        when(billingService.listReceipts(TENANT_ID, billRecord(PATIENT_ID).id())).thenReturn(List.of());

        assertThat(service.me().patientNumber()).isEqualTo("PAT-001");
        assertThat(service.me().clinicName()).isEqualTo("Sunrise Clinic");
        assertThat(service.appointments()).singleElement().satisfies(appointment -> {
            assertThat(appointment.doctorName()).isEqualTo("Dr. Mehta");
            assertThat(appointment.status()).isEqualTo("BOOKED");
            assertThat(appointment.clinicName()).isEqualTo("Sunrise Clinic");
        });
        assertThat(service.prescriptions()).singleElement().satisfies(prescription -> {
            assertThat(prescription.prescriptionNumber()).isEqualTo("RX-001");
            assertThat(prescription.status()).isEqualTo("FINALIZED");
            assertThat(prescription.clinicName()).isEqualTo("Sunrise Clinic");
        });
        assertThat(service.bills()).singleElement().satisfies(bill -> {
            assertThat(bill.billNumber()).isEqualTo("BILL-001");
            assertThat(bill.billType()).isEqualTo("Medicine");
            assertThat(bill.dueAmount()).isEqualByComparingTo("250.00");
        });

        assertThat(service.dashboard()).satisfies(dashboard -> {
            assertThat(dashboard.patientDisplayName()).isEqualTo("Riya Sharma");
            assertThat(dashboard.clinicName()).isEqualTo("Sunrise Clinic");
            assertThat(dashboard.unpaidDueAmount()).isEqualByComparingTo("250.00");
            assertThat(dashboard.nextAppointment()).isNotNull();
        });

        verify(appointmentService, times(2)).listByPatient(TENANT_ID, PATIENT_ID);
        verify(prescriptionService, times(2)).listByPatient(TENANT_ID, PATIENT_ID);
        verify(billingService, times(2)).listByPatient(TENANT_ID, PATIENT_ID);
    }

    @Test
    void tenantACannotAccessTenantBPatientMapping() {
        when(appUserRepository.findByTenantIdAndId(TENANT_ID, APP_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(service::appointments)
                .isInstanceOf(com.deepthoughtnet.clinic.platform.core.errors.UnauthorizedException.class)
                .hasMessageContaining("not found");

        verify(appointmentService, never()).listByPatient(OTHER_TENANT_ID, PATIENT_ID);
        verify(patientRepository, never()).findByTenantIdAndId(OTHER_TENANT_ID, PATIENT_ID);
    }

    @Test
    void accessIsRejectedWhenAccountHasNoPatientOwnershipLink() {
        AppUserEntity appUser = AppUserEntity.create(TENANT_ID, "patient-sub", "patient@example.com", "Portal Patient");
        when(appUserRepository.findByTenantIdAndId(TENANT_ID, APP_USER_ID)).thenReturn(Optional.of(appUser));

        assertThatThrownBy(service::me)
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void patientCanBookOwnAppointmentAndSeeItInAppointments() {
        AppUserEntity appUser = AppUserEntity.create(TENANT_ID, "patient-sub", "patient@example.com", "Portal Patient");
        appUser.setPatientId(PATIENT_ID);
        PatientEntity patient = patientEntity(TENANT_ID, PATIENT_ID, "PAT-001");
        UUID doctorUserId = UUID.randomUUID();
        LocalDate appointmentDate = LocalDate.now().plusDays(2);
        var appointment = appointmentRecord(PATIENT_ID, doctorUserId, appointmentDate, java.time.LocalTime.of(10, 30));

        when(appUserRepository.findByTenantIdAndId(TENANT_ID, APP_USER_ID)).thenReturn(Optional.of(appUser));
        when(patientRepository.findByTenantIdAndId(TENANT_ID, PATIENT_ID)).thenReturn(Optional.of(patient));
        when(clinicProfileService.findByTenantId(TENANT_ID)).thenReturn(Optional.of(clinicProfile()));
        when(tenantUserManagementService.list(TENANT_ID)).thenReturn(List.of(doctorUser(doctorUserId, "Dr. Mehta", TENANT_ID)));
        when(doctorProfileService.findByDoctorUserId(TENANT_ID, doctorUserId)).thenReturn(Optional.of(doctorProfile(doctorUserId)));
        when(appointmentService.listSlots(eq(TENANT_ID), eq(doctorUserId), eq(appointmentDate), any()))
                .thenReturn(List.of(slotRecord(doctorUserId, appointmentDate, java.time.LocalTime.of(10, 30), DoctorAvailabilitySlotStatus.AVAILABLE, true)));
        when(appointmentService.createScheduled(eq(TENANT_ID), any(AppointmentUpsertCommand.class), eq(APP_USER_ID), eq(false), any()))
                .thenReturn(appointment);
        when(appointmentService.listByPatient(TENANT_ID, PATIENT_ID)).thenReturn(List.of(appointment));

        var confirmation = service.bookAppointment(new PatientPortalAppointmentBookingRequest(
                doctorUserId.toString(),
                appointmentDate,
                java.time.LocalTime.of(10, 30),
                "Seasonal fever"
        ));

        ArgumentCaptor<AppointmentUpsertCommand> commandCaptor = ArgumentCaptor.forClass(AppointmentUpsertCommand.class);
        verify(appointmentService).createScheduled(eq(TENANT_ID), commandCaptor.capture(), eq(APP_USER_ID), eq(false), any());
        assertThat(commandCaptor.getValue().patientId()).isEqualTo(PATIENT_ID);
        assertThat(commandCaptor.getValue().doctorUserId()).isEqualTo(doctorUserId);
        assertThat(commandCaptor.getValue().type()).isEqualTo(AppointmentType.SCHEDULED);
        assertThat(commandCaptor.getValue().priority()).isEqualTo(AppointmentPriority.NORMAL);
        assertThat(confirmation.doctorName()).isEqualTo("Dr. Mehta");
        assertThat(confirmation.status()).isEqualTo("BOOKED");
        assertThat(service.appointments()).singleElement().satisfies(item -> {
            assertThat(item.doctorName()).isEqualTo("Dr. Mehta");
            assertThat(item.appointmentDate()).isEqualTo(appointmentDate);
        });
    }

    @Test
    void patientCannotBookDoctorFromAnotherTenant() {
        AppUserEntity appUser = AppUserEntity.create(TENANT_ID, "patient-sub", "patient@example.com", "Portal Patient");
        appUser.setPatientId(PATIENT_ID);
        when(appUserRepository.findByTenantIdAndId(TENANT_ID, APP_USER_ID)).thenReturn(Optional.of(appUser));
        when(patientRepository.findByTenantIdAndId(TENANT_ID, PATIENT_ID)).thenReturn(Optional.of(patientEntity(TENANT_ID, PATIENT_ID, "PAT-001")));
        when(tenantUserManagementService.list(TENANT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.bookAppointment(new PatientPortalAppointmentBookingRequest(
                UUID.randomUUID().toString(),
                LocalDate.now().plusDays(1),
                java.time.LocalTime.of(11, 0),
                "Check-up"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Doctor not found");

        verify(appointmentService, never()).createScheduled(eq(TENANT_ID), any(), eq(APP_USER_ID), eq(false));
    }

    @Test
    void patientCannotBookUnavailableSlot() {
        AppUserEntity appUser = AppUserEntity.create(TENANT_ID, "patient-sub", "patient@example.com", "Portal Patient");
        appUser.setPatientId(PATIENT_ID);
        PatientEntity patient = patientEntity(TENANT_ID, PATIENT_ID, "PAT-001");
        UUID doctorUserId = UUID.randomUUID();
        LocalDate appointmentDate = LocalDate.now().plusDays(1);

        when(appUserRepository.findByTenantIdAndId(TENANT_ID, APP_USER_ID)).thenReturn(Optional.of(appUser));
        when(patientRepository.findByTenantIdAndId(TENANT_ID, PATIENT_ID)).thenReturn(Optional.of(patient));
        when(tenantUserManagementService.list(TENANT_ID)).thenReturn(List.of(doctorUser(doctorUserId, "Dr. Mehta", TENANT_ID)));
        when(doctorProfileService.findByDoctorUserId(TENANT_ID, doctorUserId)).thenReturn(Optional.of(doctorProfile(doctorUserId)));
        when(appointmentService.listSlots(eq(TENANT_ID), eq(doctorUserId), eq(appointmentDate), any()))
                .thenReturn(List.of(slotRecord(doctorUserId, appointmentDate, java.time.LocalTime.of(9, 0), DoctorAvailabilitySlotStatus.FULL, false)));

        assertThatThrownBy(() -> service.bookAppointment(new PatientPortalAppointmentBookingRequest(
                doctorUserId.toString(),
                appointmentDate,
                java.time.LocalTime.of(9, 0),
                "Follow-up"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Selected slot is no longer available");
    }

    @Test
    void patientCannotBookPastSlot() {
        AppUserEntity appUser = AppUserEntity.create(TENANT_ID, "patient-sub", "patient@example.com", "Portal Patient");
        appUser.setPatientId(PATIENT_ID);
        when(appUserRepository.findByTenantIdAndId(TENANT_ID, APP_USER_ID)).thenReturn(Optional.of(appUser));
        when(patientRepository.findByTenantIdAndId(TENANT_ID, PATIENT_ID)).thenReturn(Optional.of(patientEntity(TENANT_ID, PATIENT_ID, "PAT-001")));

        assertThatThrownBy(() -> service.bookAppointment(new PatientPortalAppointmentBookingRequest(
                UUID.randomUUID().toString(),
                LocalDate.now().minusDays(1),
                java.time.LocalTime.of(10, 0),
                "Review"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("current or future appointment date");
    }

    @Test
    void patientCanRescheduleOnlyOwnedUpcomingAppointment() {
        AppUserEntity appUser = AppUserEntity.create(TENANT_ID, "patient-sub", "patient@example.com", "Portal Patient");
        appUser.setPatientId(PATIENT_ID);
        PatientEntity patient = patientEntity(TENANT_ID, PATIENT_ID, "PAT-001");
        UUID doctorUserId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        LocalDate currentDate = LocalDate.now().plusDays(2);
        LocalDate newDate = LocalDate.now().plusDays(5);
        AppointmentRecord current = new AppointmentRecord(
                appointmentId,
                TENANT_ID,
                PATIENT_ID,
                "PAT-001",
                "Riya Sharma",
                "9999999999",
                doctorUserId,
                "Dr. Mehta",
                null,
                currentDate,
                java.time.LocalTime.of(10, 0),
                17,
                "Review visit",
                AppointmentType.SCHEDULED,
                AppointmentPriority.NORMAL,
                AppointmentStatus.BOOKED,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        AppointmentRecord rescheduled = new AppointmentRecord(
                appointmentId,
                TENANT_ID,
                PATIENT_ID,
                "PAT-001",
                "Riya Sharma",
                "9999999999",
                doctorUserId,
                "Dr. Mehta",
                null,
                newDate,
                java.time.LocalTime.of(11, 30),
                17,
                "Review visit",
                AppointmentType.SCHEDULED,
                AppointmentPriority.NORMAL,
                AppointmentStatus.BOOKED,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(appUserRepository.findByTenantIdAndId(TENANT_ID, APP_USER_ID)).thenReturn(Optional.of(appUser));
        when(patientRepository.findByTenantIdAndId(TENANT_ID, PATIENT_ID)).thenReturn(Optional.of(patient));
        when(clinicProfileService.findByTenantId(TENANT_ID)).thenReturn(Optional.of(clinicProfile()));
        when(appointmentService.listByPatient(TENANT_ID, PATIENT_ID)).thenReturn(List.of(current));
        when(appointmentService.reschedule(eq(TENANT_ID), eq(appointmentId), any(AppointmentRescheduleCommand.class), eq(APP_USER_ID), eq(false), any()))
                .thenReturn(rescheduled);

        var confirmation = service.rescheduleAppointment(appointmentId, newDate, java.time.LocalTime.of(11, 30), "Review visit");

        verify(appointmentService).reschedule(
                eq(TENANT_ID),
                eq(appointmentId),
                argThat(command -> command.doctorUserId().equals(doctorUserId)
                        && command.appointmentDate().equals(newDate)
                        && command.appointmentTime().equals(java.time.LocalTime.of(11, 30))),
                eq(APP_USER_ID),
                eq(false),
                any()
        );
        assertThat(confirmation.message()).contains("rescheduled successfully");
    }

    @Test
    void patientCannotRescheduleAnotherPatientsAppointment() {
        AppUserEntity appUser = AppUserEntity.create(TENANT_ID, "patient-sub", "patient@example.com", "Portal Patient");
        appUser.setPatientId(PATIENT_ID);
        when(appUserRepository.findByTenantIdAndId(TENANT_ID, APP_USER_ID)).thenReturn(Optional.of(appUser));
        when(patientRepository.findByTenantIdAndId(TENANT_ID, PATIENT_ID)).thenReturn(Optional.of(patientEntity(TENANT_ID, PATIENT_ID, "PAT-001")));
        when(appointmentService.listByPatient(TENANT_ID, PATIENT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.rescheduleAppointment(UUID.randomUUID(), LocalDate.now().plusDays(1), java.time.LocalTime.of(10, 0), "Review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Upcoming appointment not found");

        verify(appointmentService, never()).reschedule(eq(TENANT_ID), any(), any(), eq(APP_USER_ID), eq(false));
    }

    @Test
    void patientCanCancelOwnedUpcomingAppointment() {
        AppUserEntity appUser = AppUserEntity.create(TENANT_ID, "patient-sub", "patient@example.com", "Portal Patient");
        appUser.setPatientId(PATIENT_ID);
        PatientEntity patient = patientEntity(TENANT_ID, PATIENT_ID, "PAT-001");
        UUID appointmentId = UUID.randomUUID();
        AppointmentRecord current = new AppointmentRecord(
                appointmentId,
                TENANT_ID,
                PATIENT_ID,
                "PAT-001",
                "Riya Sharma",
                "9999999999",
                UUID.randomUUID(),
                "Dr. Mehta",
                null,
                LocalDate.now().plusDays(1),
                java.time.LocalTime.of(9, 0),
                17,
                "Review visit",
                AppointmentType.SCHEDULED,
                AppointmentPriority.NORMAL,
                AppointmentStatus.BOOKED,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        AppointmentRecord cancelled = new AppointmentRecord(
                appointmentId,
                TENANT_ID,
                PATIENT_ID,
                "PAT-001",
                "Riya Sharma",
                "9999999999",
                current.doctorUserId(),
                "Dr. Mehta",
                null,
                current.appointmentDate(),
                current.appointmentTime(),
                17,
                "Cancelled by patient",
                AppointmentType.SCHEDULED,
                AppointmentPriority.NORMAL,
                AppointmentStatus.CANCELLED,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(appUserRepository.findByTenantIdAndId(TENANT_ID, APP_USER_ID)).thenReturn(Optional.of(appUser));
        when(patientRepository.findByTenantIdAndId(TENANT_ID, PATIENT_ID)).thenReturn(Optional.of(patient));
        when(clinicProfileService.findByTenantId(TENANT_ID)).thenReturn(Optional.of(clinicProfile()));
        when(appointmentService.listByPatient(TENANT_ID, PATIENT_ID)).thenReturn(List.of(current));
        when(appointmentService.updateStatus(eq(TENANT_ID), eq(appointmentId), any(AppointmentStatusUpdateCommand.class), eq(APP_USER_ID)))
                .thenReturn(cancelled);

        var confirmation = service.cancelAppointment(appointmentId, "Cancelled by patient");

        verify(appointmentService).updateStatus(
                eq(TENANT_ID),
                eq(appointmentId),
                argThat(command -> command.status() == AppointmentStatus.CANCELLED),
                eq(APP_USER_ID)
        );
        assertThat(confirmation.status()).isEqualTo("CANCELLED");
    }

    private PatientEntity patientEntity(UUID tenantId, UUID patientId, String patientNumber) {
        PatientEntity patient = PatientEntity.create(tenantId, patientNumber);
        patient.update(
                "Riya",
                "Sharma",
                com.deepthoughtnet.clinic.patient.service.model.PatientGender.FEMALE,
                LocalDate.of(1992, 3, 4),
                32,
                "9999999999",
                "riya@example.com",
                "Line 1",
                null,
                "Mumbai",
                "MH",
                "India",
                "400001",
                "Emergency",
                "8888888888",
                "O+",
                "Peanuts",
                "Asthma",
                "Inhaler",
                "Appendectomy",
                "internal notes should stay hidden",
                true
        );
        try {
            var idField = PatientEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(patient, patientId);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
        return patient;
    }

    private AppointmentRecord appointmentRecord(UUID patientId) {
        return appointmentRecord(patientId, UUID.randomUUID(), LocalDate.of(2026, 6, 10), java.time.LocalTime.of(10, 30));
    }

    private AppointmentRecord appointmentRecord(UUID patientId, UUID doctorUserId, LocalDate appointmentDate, java.time.LocalTime appointmentTime) {
        return new AppointmentRecord(
                UUID.randomUUID(),
                TENANT_ID,
                patientId,
                "PAT-001",
                "Riya Sharma",
                "9999999999",
                doctorUserId,
                "Dr. Mehta",
                null,
                appointmentDate,
                appointmentTime,
                17,
                "Review visit",
                AppointmentType.SCHEDULED,
                AppointmentPriority.NORMAL,
                AppointmentStatus.BOOKED,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private PrescriptionRecord prescriptionRecord(UUID patientId) {
        return new PrescriptionRecord(
                UUID.randomUUID(),
                TENANT_ID,
                patientId,
                "PAT-001",
                "Riya Sharma",
                UUID.randomUUID(),
                "Dr. Mehta",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "RX-001",
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                "Mild viral fever",
                "Hydrate well",
                LocalDate.of(2026, 6, 15),
                PrescriptionStatus.FINALIZED,
                OffsetDateTime.now(),
                UUID.randomUUID(),
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                List.of(),
                List.of()
        );
    }

    private BillRecord billRecord(UUID patientId) {
        return new BillRecord(
                UUID.fromString("00000000-0000-0000-0000-000000000111"),
                TENANT_ID,
                "BILL-001",
                patientId,
                "PAT-001",
                "Riya Sharma",
                null,
                null,
                LocalDate.of(2026, 6, 2),
                BillStatus.ISSUED,
                new BigDecimal("1000.00"),
                DiscountType.NONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "internal discount reason",
                null,
                new BigDecimal("50.00"),
                new BigDecimal("1050.00"),
                new BigDecimal("800.00"),
                BigDecimal.ZERO,
                new BigDecimal("800.00"),
                new BigDecimal("250.00"),
                null,
                "internal bill notes",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                List.of(
                        new BillLineRecord(
                                UUID.randomUUID(),
                                BillItemType.MEDICINE,
                                "Amoxicillin",
                                2,
                                new BigDecimal("500.00"),
                                new BigDecimal("1000.00"),
                                null,
                                1,
                                BigDecimal.ZERO,
                                null,
                                null
                        )
                )
        );
    }

    private ClinicProfileRecord clinicProfile() {
        return new ClinicProfileRecord(
                UUID.randomUUID(),
                TENANT_ID,
                "Sunrise Health",
                "Sunrise Clinic",
                null,
                null,
                null,
                null,
                "Mumbai",
                "MH",
                "India",
                "400001",
                null,
                null,
                null,
                true,
                false,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private TenantUserRecord doctorUser(UUID doctorUserId, String displayName, UUID tenantId) {
        return new TenantUserRecord(
                doctorUserId,
                tenantId,
                "doctor-sub",
                "doctor@example.com",
                displayName,
                "ACTIVE",
                "DOCTOR",
                "ACTIVE",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "READY"
        );
    }

    private DoctorProfileRecord doctorProfile(UUID doctorUserId) {
        return new DoctorProfileRecord(
                UUID.randomUUID(),
                TENANT_ID,
                doctorUserId,
                "9999999998",
                "General Medicine",
                "MBBS",
                "REG-100",
                "Room 4",
                null,
                9,
                42,
                true,
                false,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private DoctorAvailabilitySlotRecord slotRecord(
            UUID doctorUserId,
            LocalDate appointmentDate,
            java.time.LocalTime slotTime,
            DoctorAvailabilitySlotStatus status,
            boolean selectable
    ) {
        return new DoctorAvailabilitySlotRecord(
                doctorUserId,
                "Dr. Mehta",
                appointmentDate,
                slotTime,
                slotTime.plusMinutes(15),
                status,
                selectable ? 0 : 1,
                1,
                selectable,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
