package com.deepthoughtnet.clinic.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.appointment.db.AppointmentEntity;
import com.deepthoughtnet.clinic.appointment.db.AppointmentRepository;
import com.deepthoughtnet.clinic.appointment.db.AppointmentWaitlistEntity;
import com.deepthoughtnet.clinic.appointment.db.AppointmentWaitlistRepository;
import com.deepthoughtnet.clinic.appointment.db.DoctorAvailabilityEntity;
import com.deepthoughtnet.clinic.appointment.db.DoctorAvailabilityRepository;
import com.deepthoughtnet.clinic.appointment.db.DoctorUnavailabilityEntity;
import com.deepthoughtnet.clinic.appointment.db.DoctorUnavailabilityRepository;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRescheduleCommand;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentUpsertCommand;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotStatus;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorUnavailabilityType;
import com.deepthoughtnet.clinic.appointment.service.model.WaitlistCreateCommand;
import com.deepthoughtnet.clinic.appointment.service.model.WaitlistStatus;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceSlotsTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final LocalDate APPOINTMENT_DATE = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private DoctorAvailabilityRepository doctorAvailabilityRepository;
    @Mock
    private DoctorUnavailabilityRepository doctorUnavailabilityRepository;
    @Mock
    private AppointmentWaitlistRepository appointmentWaitlistRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private TenantUserManagementService tenantUserManagementService;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    private AppointmentService service;
    private final AtomicReference<AppointmentWaitlistEntity> savedWaitlist = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        service = new AppointmentService(
                appointmentRepository,
                doctorAvailabilityRepository,
                doctorUnavailabilityRepository,
                appointmentWaitlistRepository,
                patientRepository,
                tenantUserManagementService,
                auditEventPublisher,
                new ObjectMapper()
        );
        lenient().when(appointmentRepository.save(any(AppointmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(patientRepository.findByTenantIdAndIdIn(eq(TENANT_ID), any())).thenReturn(List.of(patient(PATIENT_ID)));
        lenient().when(patientRepository.findByTenantIdAndId(eq(TENANT_ID), any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(1);
            return Optional.of(patient(id));
        });
        lenient().when(tenantUserManagementService.list(TENANT_ID)).thenReturn(List.of(doctor()));
        lenient().when(doctorUnavailabilityRepository.findByTenantIdAndDoctorUserIdAndActiveTrueAndStartAtLessThanAndEndAtGreaterThan(eq(TENANT_ID), eq(DOCTOR_ID), any(), any()))
                .thenReturn(List.of());
        lenient().when(appointmentWaitlistRepository.save(any(AppointmentWaitlistEntity.class))).thenAnswer(invocation -> {
            AppointmentWaitlistEntity entity = invocation.getArgument(0);
            savedWaitlist.set(entity);
            return entity;
        });
    }

    @Test
    void listSlotsMarksBookedAndUnavailableSlots() {
        DoctorAvailabilityEntity availability = availability();
        AppointmentEntity booked = appointment(LocalTime.of(10, 10), AppointmentStatus.WAITING);
        when(doctorAvailabilityRepository.findByTenantIdOrderByDoctorUserIdAscDayOfWeekAscStartTimeAsc(TENANT_ID)).thenReturn(List.of(availability));
        when(appointmentRepository.findByTenantIdAndDoctorUserIdAndAppointmentDateOrderByTokenNumberAscAppointmentTimeAscCreatedAtAsc(TENANT_ID, DOCTOR_ID, APPOINTMENT_DATE))
                .thenReturn(List.of(booked));

        var slots = service.listSlots(TENANT_ID, DOCTOR_ID, APPOINTMENT_DATE);

        assertThat(slots).hasSize(3);
        assertThat(slots).extracting("status").containsExactly(
                DoctorAvailabilitySlotStatus.AVAILABLE,
                DoctorAvailabilitySlotStatus.FULL,
                DoctorAvailabilitySlotStatus.BREAK
        );
        assertThat(slots.get(1).bookedCount()).isEqualTo(1);
        assertThat(slots.get(1).selectable()).isFalse();
    }

    @Test
    void sessionCapacityTwoAllowsTwoInSameSlotAndRejectsThird() {
        DoctorAvailabilityEntity availability = availability(LocalTime.of(10, 0), LocalTime.of(10, 30), 10, 2);
        AppointmentEntity bookedOne = appointment(UUID.randomUUID(), LocalTime.of(10, 10), AppointmentStatus.WAITING);
        AppointmentEntity bookedTwo = appointment(UUID.randomUUID(), LocalTime.of(10, 10), AppointmentStatus.BOOKED);
        when(doctorAvailabilityRepository.findByTenantIdOrderByDoctorUserIdAscDayOfWeekAscStartTimeAsc(TENANT_ID)).thenReturn(List.of(availability));
        when(appointmentRepository.findByTenantIdAndDoctorUserIdAndAppointmentDateOrderByTokenNumberAscAppointmentTimeAscCreatedAtAsc(TENANT_ID, DOCTOR_ID, APPOINTMENT_DATE))
                .thenReturn(List.of(bookedOne), List.of(bookedOne), List.of(bookedOne, bookedTwo));

        service.createScheduled(
                TENANT_ID,
                new AppointmentUpsertCommand(UUID.randomUUID(), DOCTOR_ID, APPOINTMENT_DATE, LocalTime.of(10, 10), "First", AppointmentType.SCHEDULED, null, AppointmentPriority.NORMAL),
                ACTOR_ID,
                false
        );
        service.createScheduled(
                TENANT_ID,
                new AppointmentUpsertCommand(UUID.randomUUID(), DOCTOR_ID, APPOINTMENT_DATE, LocalTime.of(10, 10), "Second", AppointmentType.SCHEDULED, null, AppointmentPriority.NORMAL),
                ACTOR_ID,
                false
        );

        assertThatThrownBy(() -> service.createScheduled(
                TENANT_ID,
                new AppointmentUpsertCommand(UUID.randomUUID(), DOCTOR_ID, APPOINTMENT_DATE, LocalTime.of(10, 10), "Third", AppointmentType.SCHEDULED, null, AppointmentPriority.NORMAL),
                ACTOR_ID,
                false
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fully booked");
    }

    @Test
    void listSlotsReturnsPartiallyBookedAndFullStatusesWithCounts() {
        DoctorAvailabilityEntity availability = availability(LocalTime.of(9, 0), LocalTime.of(10, 0), 30, 2);
        AppointmentEntity booked = appointment(UUID.randomUUID(), LocalTime.of(9, 0), AppointmentStatus.BOOKED);
        AppointmentEntity fullA = appointment(UUID.randomUUID(), LocalTime.of(9, 30), AppointmentStatus.BOOKED);
        AppointmentEntity fullB = appointment(UUID.randomUUID(), LocalTime.of(9, 30), AppointmentStatus.WAITING);
        when(doctorAvailabilityRepository.findByTenantIdOrderByDoctorUserIdAscDayOfWeekAscStartTimeAsc(TENANT_ID)).thenReturn(List.of(availability));
        when(appointmentRepository.findByTenantIdAndDoctorUserIdAndAppointmentDateOrderByTokenNumberAscAppointmentTimeAscCreatedAtAsc(TENANT_ID, DOCTOR_ID, APPOINTMENT_DATE))
                .thenReturn(List.of(booked, fullA, fullB));

        var slots = service.listSlots(TENANT_ID, DOCTOR_ID, APPOINTMENT_DATE);
        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).status()).isEqualTo(DoctorAvailabilitySlotStatus.PARTIALLY_BOOKED);
        assertThat(slots.get(0).bookedCount()).isEqualTo(1);
        assertThat(slots.get(0).maxPatientsPerSlot()).isEqualTo(2);
        assertThat(slots.get(1).status()).isEqualTo(DoctorAvailabilitySlotStatus.FULL);
        assertThat(slots.get(1).bookedCount()).isEqualTo(2);
        assertThat(slots.get(1).maxPatientsPerSlot()).isEqualTo(2);
    }

    @Test
    void differentSessionsCanHaveDifferentDurationAndCapacity() {
        DoctorAvailabilityEntity morning = availability(LocalTime.of(9, 0), LocalTime.of(10, 0), 30, 2);
        DoctorAvailabilityEntity evening = availability(LocalTime.of(16, 0), LocalTime.of(16, 45), 15, 4);
        when(doctorAvailabilityRepository.findByTenantIdOrderByDoctorUserIdAscDayOfWeekAscStartTimeAsc(TENANT_ID))
                .thenReturn(List.of(morning, evening));
        when(appointmentRepository.findByTenantIdAndDoctorUserIdAndAppointmentDateOrderByTokenNumberAscAppointmentTimeAscCreatedAtAsc(TENANT_ID, DOCTOR_ID, APPOINTMENT_DATE))
                .thenReturn(List.of());

        var slots = service.listSlots(TENANT_ID, DOCTOR_ID, APPOINTMENT_DATE);
        assertThat(slots).extracting("slotTime").containsExactly(
                LocalTime.of(9, 0), LocalTime.of(9, 30),
                LocalTime.of(16, 0), LocalTime.of(16, 15), LocalTime.of(16, 30)
        );
        assertThat(slots.stream().filter(s -> s.slotTime().isBefore(LocalTime.NOON)).allMatch(s -> s.maxPatientsPerSlot() == 2)).isTrue();
        assertThat(slots.stream().filter(s -> s.slotTime().isAfter(LocalTime.NOON)).allMatch(s -> s.maxPatientsPerSlot() == 4)).isTrue();
    }

    @Test
    void createScheduledRejectsSlotInsideLeaveBlock() {
        DoctorAvailabilityEntity availability = availability();
        DoctorUnavailabilityEntity leave = DoctorUnavailabilityEntity.create(TENANT_ID, DOCTOR_ID);
        leave.update(
                APPOINTMENT_DATE.atTime(10, 0).atOffset(OffsetDateTime.now().getOffset()),
                APPOINTMENT_DATE.atTime(10, 20).atOffset(OffsetDateTime.now().getOffset()),
                DoctorUnavailabilityType.LEAVE,
                "Personal leave",
                true
        );
        when(doctorAvailabilityRepository.findByTenantIdOrderByDoctorUserIdAscDayOfWeekAscStartTimeAsc(TENANT_ID)).thenReturn(List.of(availability));
        when(appointmentRepository.findByTenantIdAndDoctorUserIdAndAppointmentDateOrderByTokenNumberAscAppointmentTimeAscCreatedAtAsc(TENANT_ID, DOCTOR_ID, APPOINTMENT_DATE))
                .thenReturn(List.of());
        when(doctorUnavailabilityRepository.findByTenantIdAndDoctorUserIdAndActiveTrueAndStartAtLessThanAndEndAtGreaterThan(eq(TENANT_ID), eq(DOCTOR_ID), any(), any()))
                .thenReturn(List.of(leave));

        assertThatThrownBy(() -> service.createScheduled(
                TENANT_ID,
                new AppointmentUpsertCommand(PATIENT_ID, DOCTOR_ID, APPOINTMENT_DATE, LocalTime.of(10, 10), "New visit", AppointmentType.SCHEDULED, null, AppointmentPriority.NORMAL),
                ACTOR_ID,
                false
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void rescheduleRejectsOccupiedSlot() {
        DoctorAvailabilityEntity availability = availability();
        AppointmentEntity source = appointment(LocalTime.of(10, 0), AppointmentStatus.BOOKED);
        AppointmentEntity targetBooked = appointment(LocalTime.of(10, 10), AppointmentStatus.BOOKED);
        when(doctorAvailabilityRepository.findByTenantIdOrderByDoctorUserIdAscDayOfWeekAscStartTimeAsc(TENANT_ID)).thenReturn(List.of(availability));
        when(appointmentRepository.findByTenantIdAndId(TENANT_ID, source.getId())).thenReturn(Optional.of(source));
        when(appointmentRepository.findByTenantIdAndDoctorUserIdAndAppointmentDateOrderByTokenNumberAscAppointmentTimeAscCreatedAtAsc(TENANT_ID, DOCTOR_ID, APPOINTMENT_DATE))
                .thenReturn(List.of(targetBooked));

        assertThatThrownBy(() -> service.reschedule(
                TENANT_ID,
                source.getId(),
                new AppointmentRescheduleCommand(DOCTOR_ID, APPOINTMENT_DATE, LocalTime.of(10, 10), "Need later slot"),
                ACTOR_ID,
                false
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fully booked");
    }

    @Test
    void waitlistCreateAndStatusUpdateWorks() {
        var created = service.createWaitlist(
                TENANT_ID,
                new WaitlistCreateCommand(PATIENT_ID, DOCTOR_ID, APPOINTMENT_DATE, LocalTime.of(12, 0), LocalTime.of(12, 20), "No slot", "Call patient"),
                ACTOR_ID
        );
        assertThat(created.status()).isEqualTo(WaitlistStatus.WAITING);

        when(appointmentWaitlistRepository.findByTenantIdAndId(eq(TENANT_ID), eq(created.id())))
                .thenAnswer(invocation -> Optional.ofNullable(savedWaitlist.get()));
        var updated = service.updateWaitlistStatus(TENANT_ID, created.id(), WaitlistStatus.CONTACTED, ACTOR_ID);
        assertThat(updated.status()).isEqualTo(WaitlistStatus.CONTACTED);
    }

    @Test
    void createScheduledAllowsManualTimeWhenNoAvailabilityScheduleExists() {
        when(doctorAvailabilityRepository.findByTenantIdOrderByDoctorUserIdAscDayOfWeekAscStartTimeAsc(TENANT_ID)).thenReturn(List.of());

        service.createScheduled(
                TENANT_ID,
                new AppointmentUpsertCommand(PATIENT_ID, DOCTOR_ID, APPOINTMENT_DATE, LocalTime.of(11, 30), "New visit", AppointmentType.SCHEDULED, null, AppointmentPriority.NORMAL),
                ACTOR_ID,
                false
        );

        verify(appointmentRepository).save(any(AppointmentEntity.class));
    }

    @Test
    void createScheduledRejectsBlankAppointmentTime() {
        assertThatThrownBy(() -> service.createScheduled(
                TENANT_ID,
                new AppointmentUpsertCommand(PATIENT_ID, DOCTOR_ID, APPOINTMENT_DATE, null, "New visit", AppointmentType.SCHEDULED, null, AppointmentPriority.NORMAL),
                ACTOR_ID,
                false
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("appointmentTime is required");
    }

    @Test
    void createScheduledRejectsPastDateTime() {
        LocalDate pastDate = LocalDate.now().minusDays(1);
        assertThatThrownBy(() -> service.createScheduled(
                TENANT_ID,
                new AppointmentUpsertCommand(PATIENT_ID, DOCTOR_ID, pastDate, LocalTime.of(10, 0), "New visit", AppointmentType.SCHEDULED, null, AppointmentPriority.NORMAL),
                ACTOR_ID,
                false
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be in the past");
    }

    @Test
    void createScheduledRejectsDuplicateActiveSameDoctorPatientAndSlot() {
        when(appointmentRepository.existsByTenantIdAndDoctorUserIdAndPatientIdAndAppointmentDateAndAppointmentTimeAndStatusNotIn(
                eq(TENANT_ID), eq(DOCTOR_ID), eq(PATIENT_ID), eq(APPOINTMENT_DATE), eq(LocalTime.of(11, 30)), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createScheduled(
                TENANT_ID,
                new AppointmentUpsertCommand(PATIENT_ID, DOCTOR_ID, APPOINTMENT_DATE, LocalTime.of(11, 30), "New visit", AppointmentType.SCHEDULED, null, AppointmentPriority.NORMAL),
                ACTOR_ID,
                false
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    private DoctorAvailabilityEntity availability() {
        return availability(LocalTime.of(10, 0), LocalTime.of(10, 30), 10, 1);
    }

    private DoctorAvailabilityEntity availability(LocalTime start, LocalTime end, int durationMinutes, int maxPatientsPerSlot) {
        DoctorAvailabilityEntity entity = DoctorAvailabilityEntity.create(TENANT_ID, DOCTOR_ID);
        entity.update(DayOfWeek.MONDAY, start, end, LocalTime.of(10, 20), LocalTime.of(10, 30), durationMinutes, maxPatientsPerSlot, true);
        return entity;
    }

    private AppointmentEntity appointment(LocalTime time, AppointmentStatus status) {
        return appointment(PATIENT_ID, time, status);
    }

    private AppointmentEntity appointment(UUID patientId, LocalTime time, AppointmentStatus status) {
        AppointmentEntity entity = AppointmentEntity.create(TENANT_ID, patientId, DOCTOR_ID);
        entity.update(APPOINTMENT_DATE, time, 1, "Follow-up", AppointmentType.SCHEDULED, status, AppointmentPriority.NORMAL);
        return entity;
    }

    private PatientEntity patient(UUID id) {
        PatientEntity entity = PatientEntity.create(TENANT_ID, "PAT-001");
        entity.update("Raj", "Sharma", com.deepthoughtnet.clinic.patient.service.model.PatientGender.MALE, null, 35, "9876543210", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
        try {
            java.lang.reflect.Field idField = PatientEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return entity;
    }

    private TenantUserRecord doctor() {
        return new TenantUserRecord(
                DOCTOR_ID,
                TENANT_ID,
                "doctor-sub",
                "doctor@clinic.local",
                "Doctor One",
                "ACTIVE",
                "DOCTOR",
                "ACTIVE",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "SYNCED"
        );
    }
}
