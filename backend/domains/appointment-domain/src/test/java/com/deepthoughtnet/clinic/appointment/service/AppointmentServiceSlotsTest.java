package com.deepthoughtnet.clinic.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.appointment.db.AppointmentEntity;
import com.deepthoughtnet.clinic.appointment.db.AppointmentRepository;
import com.deepthoughtnet.clinic.appointment.db.DoctorAvailabilityEntity;
import com.deepthoughtnet.clinic.appointment.db.DoctorAvailabilityRepository;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentUpsertCommand;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotStatus;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    private static final LocalDate APPOINTMENT_DATE = LocalDate.of(2026, 5, 11);

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private DoctorAvailabilityRepository doctorAvailabilityRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private TenantUserManagementService tenantUserManagementService;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    private AppointmentService service;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(
                appointmentRepository,
                doctorAvailabilityRepository,
                patientRepository,
                tenantUserManagementService,
                auditEventPublisher,
                new ObjectMapper()
        );
        lenient().when(appointmentRepository.save(any(AppointmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(patientRepository.findByTenantIdAndIdIn(eq(TENANT_ID), any())).thenReturn(List.of(patient(PATIENT_ID)));
        lenient().when(patientRepository.findByTenantIdAndId(eq(TENANT_ID), any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(1);
            return Optional.of(patient(PATIENT_ID)).filter(item -> item.getId().equals(id));
        });
        lenient().when(tenantUserManagementService.list(TENANT_ID)).thenReturn(List.of(doctor()));
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
                DoctorAvailabilitySlotStatus.BOOKED,
                DoctorAvailabilitySlotStatus.UNAVAILABLE
        );
        assertThat(slots.get(1).bookedCount()).isEqualTo(1);
        assertThat(slots.get(1).selectable()).isFalse();
    }

    @Test
    void createScheduledRejectsDoubleBookingWhenSlotIsFull() {
        DoctorAvailabilityEntity availability = availability();
        AppointmentEntity booked = appointment(LocalTime.of(10, 10), AppointmentStatus.WAITING);
        when(doctorAvailabilityRepository.findByTenantIdOrderByDoctorUserIdAscDayOfWeekAscStartTimeAsc(TENANT_ID)).thenReturn(List.of(availability));
        when(appointmentRepository.findByTenantIdAndDoctorUserIdAndAppointmentDateOrderByTokenNumberAscAppointmentTimeAscCreatedAtAsc(TENANT_ID, DOCTOR_ID, APPOINTMENT_DATE))
                .thenReturn(List.of(booked));

        assertThatThrownBy(() -> service.createScheduled(
                TENANT_ID,
                new AppointmentUpsertCommand(PATIENT_ID, DOCTOR_ID, APPOINTMENT_DATE, LocalTime.of(10, 10), "Return visit", AppointmentType.SCHEDULED, null, AppointmentPriority.NORMAL),
                ACTOR_ID,
                false
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fully booked");
    }

    private DoctorAvailabilityEntity availability() {
        DoctorAvailabilityEntity entity = DoctorAvailabilityEntity.create(TENANT_ID, DOCTOR_ID);
        entity.update(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(10, 30), LocalTime.of(10, 20), LocalTime.of(10, 30), 10, 1, true);
        return entity;
    }

    private AppointmentEntity appointment(LocalTime time, AppointmentStatus status) {
        AppointmentEntity entity = AppointmentEntity.create(TENANT_ID, PATIENT_ID, DOCTOR_ID);
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
