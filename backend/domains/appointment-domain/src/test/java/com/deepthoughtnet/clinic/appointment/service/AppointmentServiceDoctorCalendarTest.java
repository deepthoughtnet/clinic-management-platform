package com.deepthoughtnet.clinic.appointment.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.appointment.db.AppointmentRepository;
import com.deepthoughtnet.clinic.appointment.db.DoctorAvailabilityEntity;
import com.deepthoughtnet.clinic.appointment.db.DoctorAvailabilityRepository;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceDoctorCalendarTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

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
        lenient().when(tenantUserManagementService.list(TENANT_ID)).thenReturn(List.of(new TenantUserRecord(
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
        )));
    }

    @Test
    void ensureDoctorCalendarExistsCreatesDefaultRowWhenMissing() {
        when(doctorAvailabilityRepository.existsByTenantIdAndDoctorUserId(TENANT_ID, DOCTOR_ID)).thenReturn(false);
        when(doctorAvailabilityRepository.save(any(DoctorAvailabilityEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.ensureDoctorCalendarExists(TENANT_ID, DOCTOR_ID, ACTOR_ID, "test");

        verify(doctorAvailabilityRepository).save(any(DoctorAvailabilityEntity.class));
        verify(auditEventPublisher).record(any());
    }

    @Test
    void ensureDoctorCalendarExistsIsIdempotent() {
        when(doctorAvailabilityRepository.existsByTenantIdAndDoctorUserId(TENANT_ID, DOCTOR_ID)).thenReturn(true);

        service.ensureDoctorCalendarExists(TENANT_ID, DOCTOR_ID, ACTOR_ID, "test");

        verify(doctorAvailabilityRepository, never()).save(any());
        verify(auditEventPublisher, never()).record(any());
    }

    @Test
    void deactivateDoctorCalendarMarksActiveRowsInactive() {
        DoctorAvailabilityEntity row = DoctorAvailabilityEntity.create(TENANT_ID, DOCTOR_ID);
        row.update(java.time.DayOfWeek.MONDAY, java.time.LocalTime.of(9, 0), java.time.LocalTime.of(10, 0), null, null, 15, 1, true);
        when(doctorAvailabilityRepository.findByTenantIdAndDoctorUserIdOrderByDayOfWeekAscStartTimeAsc(eq(TENANT_ID), eq(DOCTOR_ID)))
                .thenReturn(List.of(row));
        when(doctorAvailabilityRepository.save(any(DoctorAvailabilityEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.deactivateDoctorCalendar(TENANT_ID, DOCTOR_ID, ACTOR_ID, "test");

        verify(doctorAvailabilityRepository).save(any(DoctorAvailabilityEntity.class));
        verify(auditEventPublisher).record(any());
    }

    @Test
    void reconcileCreatesMissingCalendarForActiveDoctor() {
        when(doctorAvailabilityRepository.existsByTenantIdAndDoctorUserId(TENANT_ID, DOCTOR_ID)).thenReturn(false);
        when(doctorAvailabilityRepository.save(any(DoctorAvailabilityEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.reconcileDoctorCalendars(TENANT_ID, ACTOR_ID, "test");

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(0);
        verify(doctorAvailabilityRepository).save(any(DoctorAvailabilityEntity.class));
    }

    @Test
    void reconcileSkipsExistingCalendarAndIsIdempotent() {
        when(doctorAvailabilityRepository.existsByTenantIdAndDoctorUserId(TENANT_ID, DOCTOR_ID)).thenReturn(true);

        var first = service.reconcileDoctorCalendars(TENANT_ID, ACTOR_ID, "test");
        var second = service.reconcileDoctorCalendars(TENANT_ID, ACTOR_ID, "test");

        assertThat(first.createdCount()).isEqualTo(0);
        assertThat(first.skippedCount()).isEqualTo(1);
        assertThat(second.createdCount()).isEqualTo(0);
        assertThat(second.skippedCount()).isEqualTo(1);
        verify(doctorAvailabilityRepository, never()).save(any());
    }

    @Test
    void reconcileSkipsInactiveDoctors() {
        UUID inactiveDoctorId = UUID.randomUUID();
        when(tenantUserManagementService.list(TENANT_ID)).thenReturn(List.of(
                new TenantUserRecord(
                        inactiveDoctorId,
                        TENANT_ID,
                        "inactive-sub",
                        "inactive@clinic.local",
                        "Doctor Inactive",
                        "ACTIVE",
                        "DOCTOR",
                        "DISABLED",
                        OffsetDateTime.now(),
                        OffsetDateTime.now(),
                        "SYNCED"
                )
        ));

        var result = service.reconcileDoctorCalendars(TENANT_ID, ACTOR_ID, "test");

        assertThat(result.createdCount()).isEqualTo(0);
        assertThat(result.skippedCount()).isEqualTo(1);
        verify(doctorAvailabilityRepository, never()).save(any());
    }

    @Test
    void reconcilePreservesTenantIsolation() {
        UUID tenant2 = UUID.randomUUID();
        UUID doctor2 = UUID.randomUUID();
        when(tenantUserManagementService.list(tenant2)).thenReturn(List.of(
                new TenantUserRecord(
                        doctor2,
                        tenant2,
                        "doctor-sub-2",
                        "doctor2@clinic.local",
                        "Doctor Two",
                        "ACTIVE",
                        "DOCTOR",
                        "ACTIVE",
                        OffsetDateTime.now(),
                        OffsetDateTime.now(),
                        "SYNCED"
                )
        ));
        when(doctorAvailabilityRepository.existsByTenantIdAndDoctorUserId(tenant2, doctor2)).thenReturn(false);
        List<DoctorAvailabilityEntity> saved = new ArrayList<>();
        when(doctorAvailabilityRepository.save(any(DoctorAvailabilityEntity.class))).thenAnswer(invocation -> {
            DoctorAvailabilityEntity entity = invocation.getArgument(0);
            saved.add(entity);
            return entity;
        });

        service.reconcileDoctorCalendars(tenant2, ACTOR_ID, "test");

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getTenantId()).isEqualTo(tenant2);
        assertThat(saved.get(0).getDoctorUserId()).isEqualTo(doctor2);
    }
}
