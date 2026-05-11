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
import com.deepthoughtnet.clinic.appointment.db.AppointmentWaitlistRepository;
import com.deepthoughtnet.clinic.appointment.db.DoctorAvailabilityRepository;
import com.deepthoughtnet.clinic.appointment.db.DoctorUnavailabilityRepository;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.appointment.service.model.WalkInAppointmentCommand;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceQueueAndTokenTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID PATIENT_ONE_ID = UUID.randomUUID();
    private static final UUID PATIENT_TWO_ID = UUID.randomUUID();

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
        lenient().when(patientRepository.findByTenantIdAndIdIn(eq(TENANT_ID), any())).thenReturn(List.of(
                patient(PATIENT_ONE_ID, "PAT-001", "Raj", "Sharma", "9876543210"),
                patient(PATIENT_TWO_ID, "PAT-002", "Priya", "Sharma", "9876543211")
        ));
        lenient().when(patientRepository.findByTenantIdAndId(eq(TENANT_ID), any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(1);
            return List.of(
                    patient(PATIENT_ONE_ID, "PAT-001", "Raj", "Sharma", "9876543210"),
                    patient(PATIENT_TWO_ID, "PAT-002", "Priya", "Sharma", "9876543211")
            ).stream().filter(item -> item.getId().equals(id)).findFirst();
        });
        lenient().when(tenantUserManagementService.list(TENANT_ID)).thenReturn(List.of(doctor()));
    }

    @Test
    void createWalkInGeneratesSequentialDailyTokens() {
        LocalDate firstDate = LocalDate.now().plusDays(1);
        LocalDate secondDate = firstDate.plusDays(1);
        when(appointmentRepository.findMaxTokenNumber(TENANT_ID, DOCTOR_ID, firstDate)).thenReturn(0, 1);
        when(appointmentRepository.findMaxTokenNumber(TENANT_ID, DOCTOR_ID, secondDate)).thenReturn(0);

        var first = service.createWalkIn(TENANT_ID, new WalkInAppointmentCommand(PATIENT_ONE_ID, DOCTOR_ID, firstDate, "Walk in", AppointmentPriority.NORMAL), ACTOR_ID, false);
        var second = service.createWalkIn(TENANT_ID, new WalkInAppointmentCommand(PATIENT_TWO_ID, DOCTOR_ID, firstDate, "Walk in", AppointmentPriority.NORMAL), ACTOR_ID, false);
        var nextDay = service.createWalkIn(TENANT_ID, new WalkInAppointmentCommand(PATIENT_ONE_ID, DOCTOR_ID, secondDate, "Walk in", AppointmentPriority.NORMAL), ACTOR_ID, false);

        assertThat(first.tokenNumber()).isEqualTo(1);
        assertThat(second.tokenNumber()).isEqualTo(2);
        assertThat(nextDay.tokenNumber()).isEqualTo(1);
    }

    @Test
    void listQueueTodaySortsWaitingByPriorityBeforeLowerPriorityRecords() {
        AppointmentEntity urgent = appointment(PATIENT_ONE_ID, 3, AppointmentStatus.WAITING, AppointmentPriority.URGENT);
        AppointmentEntity normal = appointment(PATIENT_TWO_ID, 1, AppointmentStatus.WAITING, AppointmentPriority.NORMAL);
        AppointmentEntity inConsultation = appointment(PATIENT_ONE_ID, 2, AppointmentStatus.IN_CONSULTATION, AppointmentPriority.MANUAL_PRIORITY);
        AppointmentEntity completed = appointment(PATIENT_TWO_ID, 4, AppointmentStatus.COMPLETED, AppointmentPriority.NORMAL);
        when(appointmentRepository.findByTenantIdAndDoctorUserIdAndAppointmentDateOrderByTokenNumberAscAppointmentTimeAscCreatedAtAsc(TENANT_ID, DOCTOR_ID, LocalDate.now()))
                .thenReturn(List.of(completed, normal, inConsultation, urgent));

        var rows = service.listQueueToday(TENANT_ID, DOCTOR_ID);

        assertThat(rows).extracting("status").containsExactly(
                AppointmentStatus.WAITING,
                AppointmentStatus.WAITING,
                AppointmentStatus.IN_CONSULTATION,
                AppointmentStatus.COMPLETED
        );
        assertThat(rows).extracting("priority").containsExactly(
                AppointmentPriority.URGENT,
                AppointmentPriority.NORMAL,
                AppointmentPriority.MANUAL_PRIORITY,
                AppointmentPriority.NORMAL
        );
    }

    @Test
    void listTodayExcludesCancelledAppointments() {
        AppointmentEntity cancelled = appointment(PATIENT_ONE_ID, 1, AppointmentStatus.CANCELLED, AppointmentPriority.NORMAL);
        AppointmentEntity waiting = appointment(PATIENT_TWO_ID, 2, AppointmentStatus.WAITING, AppointmentPriority.NORMAL);
        when(appointmentRepository.findByTenantIdAndAppointmentDateOrderByAppointmentTimeAscCreatedAtAsc(TENANT_ID, LocalDate.now()))
                .thenReturn(List.of(cancelled, waiting));

        var today = service.listToday(TENANT_ID);

        assertThat(today).extracting("status").containsExactly(AppointmentStatus.WAITING);
    }

    @Test
    void reorderQueueTodayAllowsOnlyReorderableItemsAndReassignsTokens() {
        AppointmentEntity first = appointment(PATIENT_ONE_ID, 1, AppointmentStatus.BOOKED, AppointmentPriority.NORMAL);
        AppointmentEntity second = appointment(PATIENT_TWO_ID, 2, AppointmentStatus.WAITING, AppointmentPriority.NORMAL);
        AppointmentEntity completed = appointment(PATIENT_ONE_ID, 3, AppointmentStatus.COMPLETED, AppointmentPriority.NORMAL);
        when(appointmentRepository.findByTenantIdAndDoctorUserIdAndAppointmentDateOrderByTokenNumberAscAppointmentTimeAscCreatedAtAsc(TENANT_ID, DOCTOR_ID, LocalDate.now()))
                .thenReturn(List.of(first, second, completed));

        var reordered = service.reorderQueueToday(TENANT_ID, DOCTOR_ID, List.of(second.getId(), first.getId()), ACTOR_ID);
        assertThat(reordered.stream().filter(row -> row.status() == AppointmentStatus.BOOKED || row.status() == AppointmentStatus.WAITING).map(row -> row.id()).toList())
                .containsExactly(second.getId(), first.getId());

        assertThatThrownBy(() -> service.reorderQueueToday(TENANT_ID, DOCTOR_ID, List.of(first.getId()), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("all and only");
        verify(appointmentRepository).save(second);
    }

    private AppointmentEntity appointment(UUID patientId, Integer token, AppointmentStatus status, AppointmentPriority priority) {
        AppointmentEntity entity = AppointmentEntity.create(TENANT_ID, patientId, DOCTOR_ID);
        entity.update(LocalDate.now(), null, token, "OPD visit", AppointmentType.SCHEDULED, status, priority);
        return entity;
    }

    private PatientEntity patient(UUID id, String number, String firstName, String lastName, String mobile) {
        PatientEntity entity = PatientEntity.create(TENANT_ID, number);
        entity.update(firstName, lastName, com.deepthoughtnet.clinic.patient.service.model.PatientGender.UNKNOWN, null, 30, mobile, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
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
