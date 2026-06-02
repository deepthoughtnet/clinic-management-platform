package com.deepthoughtnet.clinic.api.patientportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.billing.service.model.DiscountType;
import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PatientPortalServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OTHER_TENANT_ID = UUID.randomUUID();
    private static final UUID APP_USER_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();

    private AppUserRepository appUserRepository;
    private PatientRepository patientRepository;
    private AppointmentService appointmentService;
    private PrescriptionService prescriptionService;
    private BillingService billingService;
    private PatientPortalService service;

    @BeforeEach
    void setUp() {
        appUserRepository = mock(AppUserRepository.class);
        patientRepository = mock(PatientRepository.class);
        appointmentService = mock(AppointmentService.class);
        prescriptionService = mock(PrescriptionService.class);
        billingService = mock(BillingService.class);
        service = new PatientPortalService(
                appUserRepository,
                patientRepository,
                appointmentService,
                prescriptionService,
                billingService
        );
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
        when(appointmentService.listByPatient(TENANT_ID, PATIENT_ID)).thenReturn(List.of(appointmentRecord(PATIENT_ID)));
        when(prescriptionService.listByPatient(TENANT_ID, PATIENT_ID)).thenReturn(List.of(prescriptionRecord(PATIENT_ID)));
        when(billingService.listByPatient(TENANT_ID, PATIENT_ID)).thenReturn(List.of(billRecord(PATIENT_ID)));

        assertThat(service.me().patientId()).isEqualTo(PATIENT_ID.toString());
        assertThat(service.appointments()).singleElement().satisfies(appointment -> {
            assertThat(appointment.doctorName()).isEqualTo("Dr. Mehta");
            assertThat(appointment.status()).isEqualTo("BOOKED");
        });
        assertThat(service.prescriptions()).singleElement().satisfies(prescription -> {
            assertThat(prescription.prescriptionNumber()).isEqualTo("RX-001");
            assertThat(prescription.status()).isEqualTo("FINALIZED");
        });
        assertThat(service.bills()).singleElement().satisfies(bill -> {
            assertThat(bill.billNumber()).isEqualTo("BILL-001");
            assertThat(bill.dueAmount()).isEqualByComparingTo("250.00");
        });

        verify(appointmentService).listByPatient(TENANT_ID, PATIENT_ID);
        verify(prescriptionService).listByPatient(TENANT_ID, PATIENT_ID);
        verify(billingService).listByPatient(TENANT_ID, PATIENT_ID);
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
        return new AppointmentRecord(
                UUID.randomUUID(),
                TENANT_ID,
                patientId,
                "PAT-001",
                "Riya Sharma",
                "9999999999",
                UUID.randomUUID(),
                "Dr. Mehta",
                null,
                LocalDate.of(2026, 6, 10),
                java.time.LocalTime.of(10, 30),
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
                UUID.randomUUID(),
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
                List.of()
        );
    }
}
