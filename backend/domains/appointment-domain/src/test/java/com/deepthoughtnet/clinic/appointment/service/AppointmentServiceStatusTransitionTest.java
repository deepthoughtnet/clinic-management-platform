package com.deepthoughtnet.clinic.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.appointment.db.AppointmentEntity;
import com.deepthoughtnet.clinic.appointment.db.AppointmentRepository;
import com.deepthoughtnet.clinic.appointment.db.DoctorAvailabilityRepository;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatusUpdateCommand;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
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
class AppointmentServiceStatusTransitionTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
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
        lenient().when(appointmentRepository.save(any(AppointmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(patientRepository.findByTenantIdAndIdIn(any(), any())).thenReturn(List.of());
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
    void allowsRequiredQueueLifecycleTransitions() {
        assertThat(updateStatus(AppointmentStatus.BOOKED, AppointmentStatus.WAITING)).isEqualTo(AppointmentStatus.WAITING);
        assertThat(updateStatus(AppointmentStatus.WAITING, AppointmentStatus.IN_CONSULTATION)).isEqualTo(AppointmentStatus.IN_CONSULTATION);
        assertThat(updateStatus(AppointmentStatus.IN_CONSULTATION, AppointmentStatus.COMPLETED)).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void allowsCancellationAndNoShowOnlyBeforeConsultationStarts() {
        assertThat(updateStatus(AppointmentStatus.BOOKED, AppointmentStatus.CANCELLED)).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(updateStatus(AppointmentStatus.WAITING, AppointmentStatus.NO_SHOW)).isEqualTo(AppointmentStatus.NO_SHOW);

        assertInvalidTransition(AppointmentStatus.IN_CONSULTATION, AppointmentStatus.CANCELLED);
        assertInvalidTransition(AppointmentStatus.IN_CONSULTATION, AppointmentStatus.NO_SHOW);
    }

    @Test
    void rejectsSkippedAndTerminalTransitions() {
        assertInvalidTransition(AppointmentStatus.BOOKED, AppointmentStatus.IN_CONSULTATION);
        assertInvalidTransition(AppointmentStatus.WAITING, AppointmentStatus.COMPLETED);
        assertInvalidTransition(AppointmentStatus.COMPLETED, AppointmentStatus.WAITING);
        assertInvalidTransition(AppointmentStatus.CANCELLED, AppointmentStatus.WAITING);
        assertInvalidTransition(AppointmentStatus.NO_SHOW, AppointmentStatus.WAITING);
    }

    private AppointmentStatus updateStatus(AppointmentStatus current, AppointmentStatus target) {
        AppointmentEntity entity = appointment(current);
        when(appointmentRepository.findByTenantIdAndId(TENANT_ID, entity.getId())).thenReturn(Optional.of(entity));
        return service.updateStatus(TENANT_ID, entity.getId(), new AppointmentStatusUpdateCommand(target), ACTOR_ID).status();
    }

    private void assertInvalidTransition(AppointmentStatus current, AppointmentStatus target) {
        AppointmentEntity entity = appointment(current);
        when(appointmentRepository.findByTenantIdAndId(TENANT_ID, entity.getId())).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.updateStatus(TENANT_ID, entity.getId(), new AppointmentStatusUpdateCommand(target), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid appointment status transition");
    }

    private AppointmentEntity appointment(AppointmentStatus status) {
        AppointmentEntity entity = AppointmentEntity.create(TENANT_ID, PATIENT_ID, DOCTOR_ID);
        entity.update(LocalDate.now(), null, 1, "OPD visit", AppointmentType.SCHEDULED, status);
        return entity;
    }
}
