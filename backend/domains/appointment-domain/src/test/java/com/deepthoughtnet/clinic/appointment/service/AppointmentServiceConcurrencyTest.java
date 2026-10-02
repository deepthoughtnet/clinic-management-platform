package com.deepthoughtnet.clinic.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.appointment.db.AppointmentEntity;
import com.deepthoughtnet.clinic.appointment.db.AppointmentRepository;
import com.deepthoughtnet.clinic.appointment.db.DoctorAvailabilityEntity;
import com.deepthoughtnet.clinic.appointment.db.DoctorAvailabilityRepository;
import com.deepthoughtnet.clinic.appointment.db.DoctorUnavailabilityRepository;
import com.deepthoughtnet.clinic.appointment.db.AppointmentWaitlistRepository;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentUpsertCommand;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootTest(
        classes = AppointmentServiceConcurrencyTest.TestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:appointment_concurrency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
        }
)
class AppointmentServiceConcurrencyTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID PATIENT_A = UUID.randomUUID();
    private static final UUID PATIENT_B = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.now(ZoneId.of("Asia/Kolkata"))
            .with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    private static final LocalTime SLOT = LocalTime.of(10, 0);

    @Autowired
    private AppointmentService service;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private DoctorAvailabilityRepository doctorAvailabilityRepository;

    @MockBean
    private DoctorUnavailabilityRepository doctorUnavailabilityRepository;
    @MockBean
    private AppointmentWaitlistRepository appointmentWaitlistRepository;
    @MockBean
    private PatientRepository patientRepository;
    @MockBean
    private TenantUserManagementService tenantUserManagementService;
    @MockBean
    private AuditEventPublisher auditEventPublisher;
    @MockBean
    private ModuleBusinessEventPublisher moduleBusinessEventPublisher;

    @BeforeEach
    void setUp() {
        appointmentRepository.deleteAll();
        doctorAvailabilityRepository.deleteAll();
        when(doctorUnavailabilityRepository.findByTenantIdAndDoctorUserIdAndActiveTrueAndStartAtLessThanAndEndAtGreaterThan(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(tenantUserManagementService.list(TENANT_ID)).thenReturn(List.of(new TenantUserRecord(
                DOCTOR_ID,
                TENANT_ID,
                "doctor-sub",
                "doctor@example.com",
                "Doctor",
                "ACTIVE",
                "DOCTOR",
                "ACTIVE",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "SYNCED"
        )));
        PatientEntity patientA = patient(PATIENT_A);
        PatientEntity patientB = patient(PATIENT_B);
        when(patientRepository.findByTenantIdAndId(any(), any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(1);
            if (id == null) {
                return Optional.empty();
            }
            return Optional.of(id.equals(PATIENT_B) ? patientB : patientA);
        });
        when(patientRepository.findByTenantIdAndIdIn(any(), any())).thenReturn(List.of(patientA, patientB));
        DoctorAvailabilityEntity availability = DoctorAvailabilityEntity.create(TENANT_ID, DOCTOR_ID);
        availability.update(DayOfWeek.MONDAY, SLOT, SLOT.plusMinutes(30), null, null, 30, 1, true);
        doctorAvailabilityRepository.saveAndFlush(availability);
    }

    @Test
    void capacityOneAllowsOnlyOneConcurrentDifferentPatientBooking() throws Exception {
        List<BookingAttempt> attempts = runConcurrentBookings(PATIENT_A, PATIENT_B);

        assertThat(attempts).extracting(BookingAttempt::succeeded).containsExactlyInAnyOrder(true, false);
        assertThat(attempts).filteredOn(attempt -> !attempt.succeeded())
                .singleElement()
                .extracting(BookingAttempt::failure)
                .satisfies(failure -> assertThat(failure).hasMessageContaining("This slot is full."));
        assertThat(appointmentRepository.count()).isEqualTo(1);
    }

    @Test
    void capacityTwoAllowsTwoAndRejectsThirdConcurrentBooking() throws Exception {
        doctorAvailabilityRepository.deleteAll();
        DoctorAvailabilityEntity availability = DoctorAvailabilityEntity.create(TENANT_ID, DOCTOR_ID);
        availability.update(DayOfWeek.MONDAY, SLOT, SLOT.plusMinutes(30), null, null, 30, 2, true);
        doctorAvailabilityRepository.saveAndFlush(availability);

        UUID patientC = UUID.randomUUID();
        PatientEntity patient = patient(patientC);
        PatientEntity patientA = patient(PATIENT_A);
        PatientEntity patientB = patient(PATIENT_B);
        when(patientRepository.findByTenantIdAndId(any(), any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(1);
            if (id == null) {
                return Optional.empty();
            }
            return Optional.of(id.equals(PATIENT_B) ? patientB : id.equals(patientC) ? patient : patientA);
        });
        when(patientRepository.findByTenantIdAndIdIn(any(), any())).thenReturn(List.of(patientA, patientB, patient));

        List<BookingAttempt> attempts = runConcurrentBookings(PATIENT_A, PATIENT_B, patientC);

        assertThat(attempts).extracting(BookingAttempt::succeeded).containsExactlyInAnyOrder(true, true, false);
        assertThat(attempts).filteredOn(attempt -> !attempt.succeeded())
                .singleElement()
                .extracting(BookingAttempt::failure)
                .satisfies(failure -> assertThat(failure).hasMessageContaining("This slot is full."));
        assertThat(appointmentRepository.count()).isEqualTo(2);
    }

    @Test
    void samePatientConcurrentBookingStillCreatesOnlyOneActiveAppointment() throws Exception {
        List<BookingAttempt> attempts = runConcurrentBookings(PATIENT_A, PATIENT_A);

        assertThat(attempts).extracting(BookingAttempt::succeeded).containsExactlyInAnyOrder(true, false);
        assertThat(attempts).filteredOn(attempt -> !attempt.succeeded())
                .singleElement()
                .extracting(BookingAttempt::failure)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .containsAnyOf("This slot is full.", "An active appointment already exists"));
        assertThat(appointmentRepository.count()).isEqualTo(1);
    }

    @Test
    void cancelledAndNoShowReleaseCapacityButCompletedStillConsumesIt() {
        persistAppointment(PATIENT_A, AppointmentStatus.CANCELLED, 1);
        persistAppointment(PATIENT_B, AppointmentStatus.NO_SHOW, 2);

        UUID patientC = UUID.randomUUID();
        PatientEntity patient = patient(patientC);
        when(patientRepository.findByTenantIdAndId(any(), any())).thenAnswer(invocation -> Optional.of(patient));
        when(patientRepository.findByTenantIdAndIdIn(any(), any())).thenReturn(List.of(patient));

        service.createScheduled(
                TENANT_ID,
                new AppointmentUpsertCommand(patientC, DOCTOR_ID, DATE, SLOT, "Released slot", AppointmentType.SCHEDULED, null, AppointmentPriority.NORMAL),
                UUID.randomUUID(),
                false,
                ZoneId.of("Asia/Kolkata")
        );

        persistAppointment(UUID.randomUUID(), AppointmentStatus.COMPLETED, 4);
        UUID patientD = UUID.randomUUID();
        when(patientRepository.findByTenantIdAndId(any(), any())).thenAnswer(invocation -> Optional.of(patient(patientD)));
        assertThatThrownBy(() -> service.createScheduled(
                TENANT_ID,
                new AppointmentUpsertCommand(patientD, DOCTOR_ID, DATE, SLOT, "Completed slot", AppointmentType.SCHEDULED, null, AppointmentPriority.NORMAL),
                UUID.randomUUID(),
                false,
                ZoneId.of("Asia/Kolkata")
        )).hasMessageContaining("This slot is full.");
    }

    @Test
    void authorizedOverbookingWithReasonRemainsAllowed() {
        persistAppointment(PATIENT_A, AppointmentStatus.BOOKED, 1);
        UUID patientC = UUID.randomUUID();
        PatientEntity patient = patient(patientC);
        when(patientRepository.findByTenantIdAndId(any(), any())).thenAnswer(invocation -> Optional.of(patient));
        when(patientRepository.findByTenantIdAndIdIn(any(), any())).thenReturn(List.of(patient));

        service.createScheduled(
                TENANT_ID,
                new AppointmentUpsertCommand(patientC, DOCTOR_ID, DATE, SLOT, "Approved overbooking", AppointmentType.SCHEDULED, null, AppointmentPriority.NORMAL),
                UUID.randomUUID(),
                true,
                ZoneId.of("Asia/Kolkata")
        );

        assertThat(appointmentRepository.count()).isEqualTo(2);
    }

    private List<BookingAttempt> runConcurrentBookings(UUID... patients) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(patients.length);
        try {
            List<Future<BookingAttempt>> futures = java.util.Arrays.stream(patients)
                    .map(patientId -> executor.submit(() -> {
                        start.await(10, TimeUnit.SECONDS);
                        try {
                            service.createScheduled(
                                    TENANT_ID,
                                    new AppointmentUpsertCommand(patientId, DOCTOR_ID, DATE, SLOT, "Concurrent test", AppointmentType.SCHEDULED, null, AppointmentPriority.NORMAL),
                                    UUID.randomUUID(),
                                    false,
                                    ZoneId.of("Asia/Kolkata")
                            );
                            return new BookingAttempt(true, null);
                        } catch (RuntimeException ex) {
                            return new BookingAttempt(false, ex);
                        }
                    }))
                    .toList();
            start.countDown();
            return futures.stream().map(future -> {
                try {
                    return get(future);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Concurrent booking test interrupted", ex);
                } catch (ExecutionException ex) {
                    throw new IllegalStateException("Concurrent booking test failed", ex);
                }
            }).toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private BookingAttempt get(Future<BookingAttempt> future) throws InterruptedException, ExecutionException {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException ex) {
            throw new IllegalStateException("Concurrent booking did not complete", ex);
        }
    }

    private PatientEntity patient(UUID id) {
        PatientEntity patient = org.mockito.Mockito.mock(PatientEntity.class);
        when(patient.getId()).thenReturn(id);
        when(patient.getPatientNumber()).thenReturn("PAT-" + id.toString().substring(0, 8));
        when(patient.getFirstName()).thenReturn("Test");
        when(patient.getLastName()).thenReturn("Patient");
        return patient;
    }

    private void persistAppointment(UUID patientId, AppointmentStatus status, int token) {
        AppointmentEntity appointment = AppointmentEntity.create(TENANT_ID, patientId, DOCTOR_ID);
        appointment.update(DATE, SLOT, token, "existing", AppointmentType.SCHEDULED, status, AppointmentPriority.NORMAL);
        appointmentRepository.saveAndFlush(appointment);
    }

    record BookingAttempt(boolean succeeded, RuntimeException failure) {
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {
            AppointmentEntity.class,
            DoctorAvailabilityEntity.class
    })
    @EnableJpaRepositories(basePackageClasses = {
            AppointmentRepository.class,
            DoctorAvailabilityRepository.class
    })
    @Import(AppointmentService.class)
    static class TestApplication {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
