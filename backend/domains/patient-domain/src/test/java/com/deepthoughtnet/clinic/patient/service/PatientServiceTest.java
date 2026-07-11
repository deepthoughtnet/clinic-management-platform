package com.deepthoughtnet.clinic.patient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.patient.service.model.PatientSearchCriteria;
import com.deepthoughtnet.clinic.patient.service.model.PatientUpsertCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventAction;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;
import com.deepthoughtnet.clinic.patient.service.model.PatientConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PatientServiceTest {
    private static final ZoneId UTC = ZoneId.of("UTC");

    private PatientRepository repository;
    private AuditEventPublisher auditEventPublisher;
    private PatientService service;

    @BeforeEach
    void setUp() {
        repository = mock(PatientRepository.class);
        auditEventPublisher = mock(AuditEventPublisher.class);
        service = new PatientService(repository, auditEventPublisher, new ObjectMapper().findAndRegisterModules());
        when(repository.existsByTenantIdAndPatientNumber(any(), any())).thenReturn(false);
        when(repository.save(any(PatientEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Sort.class)))
                .thenAnswer(invocation -> List.of());
    }

    @Test
    void createRequiresMobile() {
        UUID tenantId = UUID.randomUUID();
        PatientUpsertCommand command = command("Receptionist", "One", "");

        assertThatThrownBy(() -> service.create(tenantId, command, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("mobile is required");
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsInvalidMobileFormat() {
        UUID tenantId = UUID.randomUUID();
        PatientUpsertCommand command = command("Receptionist", "One", "12345");

        assertThatThrownBy(() -> service.create(tenantId, command, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("mobile must be a valid 10-digit mobile number");
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsDuplicateMobileInSameTenant() {
        UUID tenantId = UUID.randomUUID();
        PatientEntity existing = PatientEntity.create(tenantId, "PAT-EXIST");
        existing.update(
                "Existing",
                "Patient",
                PatientGender.UNKNOWN,
                null,
                null,
                "9876543210",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true
        );
        when(repository.findFirstByTenantIdAndMobileIgnoreCase(tenantId, "9876543210")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(tenantId, command("Receptionist", "One", "9876543210"), UUID.randomUUID()))
                .isInstanceOf(PatientConflictException.class)
                .hasMessageContaining("Patient already exists");
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsInactiveMobileDuplicateInSameTenant() {
        UUID tenantId = UUID.randomUUID();
        PatientEntity existing = PatientEntity.create(tenantId, "PAT-EXIST");
        existing.update(
                "Existing",
                "Patient",
                PatientGender.UNKNOWN,
                null,
                null,
                "9876543210",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false
        );
        when(repository.findFirstByTenantIdAndMobileIgnoreCase(tenantId, "9876543210")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(tenantId, command("Receptionist", "One", "9876543210"), UUID.randomUUID()))
                .isInstanceOf(PatientConflictException.class)
                .hasMessageContaining("inactive");
        verify(repository, never()).save(any());
    }

    @Test
    void createAllowsSameMobileInDifferentTenant() {
        UUID tenantId = UUID.randomUUID();
        var created = service.create(tenantId, command("Receptionist", "One", "9876543210"), UUID.randomUUID());

        assertThat(created.mobile()).isEqualTo("9876543210");
        verify(repository).save(any(PatientEntity.class));
    }

    @Test
    void searchReturnsMultiplePatientsWithSameMobile() {
        UUID tenantId = UUID.randomUUID();
        PatientEntity first = PatientEntity.create(tenantId, "PAT-FIRST");
        first.update(
                "Raj",
                "Sharma",
                PatientGender.MALE,
                null,
                42,
                "9876543210",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true
        );
        PatientEntity second = PatientEntity.create(tenantId, "PAT-SECOND");
        second.update(
                "Priya",
                "Sharma",
                PatientGender.FEMALE,
                null,
                39,
                "9876543210",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true
        );
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(first, second));

        var rows = service.search(tenantId, new PatientSearchCriteria(null, "9876543210", null, true));

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting("firstName").containsExactly("Raj", "Priya");
    }

    @Test
    void createAllowsBlankLastNameForQuickRegistration() {
        UUID tenantId = UUID.randomUUID();
        PatientUpsertCommand command = command("Walkin", "", "9876543210");

        var saved = service.create(tenantId, command, UUID.randomUUID());

        assertThat(saved.firstName()).isEqualTo("Walkin");
        assertThat(saved.lastName()).isEmpty();
        assertThat(saved.mobile()).isEqualTo("9876543210");
    }

    @Test
    void receptionistCanUpdateSameDayPatientAndLogsFieldLevelAudit() {
        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        PatientEntity patient = patient(tenantId, patientId, "PAT-1001", OffsetDateTime.now(UTC));
        patient.update(
                "Asha",
                "Rao",
                PatientGender.FEMALE,
                null,
                31,
                "9999999999",
                "old@example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Old notes",
                true
        );
        when(repository.findByTenantIdAndId(tenantId, patientId)).thenReturn(Optional.of(patient));

        PatientUpsertCommand command = new PatientUpsertCommand(
                "Asha",
                "Rai",
                PatientGender.FEMALE,
                null,
                31,
                "8888888888",
                "new@example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "New notes",
                true
        );

        var saved = service.update(tenantId, patientId, command, actorId, "RECEPTIONIST", UTC, "frontdesk@clinic.test");

        assertThat(saved.lastName()).isEqualTo("Rai");
        assertThat(saved.mobile()).isEqualTo("8888888888");
        assertThat(saved.email()).isEqualTo("new@example.com");
        assertThat(saved.notes()).isEqualTo("New notes");

        ArgumentCaptor<AuditEventCommand> auditCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(auditEventPublisher, atLeastOnce()).record(auditCaptor.capture());
        assertThat(auditCaptor.getAllValues())
                .extracting(AuditEventCommand::action)
                .containsOnly(AuditEventAction.PATIENT_PROFILE_UPDATED);
        assertThat(auditCaptor.getAllValues())
                .extracting(AuditEventCommand::detailsJson)
                .allSatisfy(json -> {
                    assertThat(json).contains("\"changedField\"");
                    assertThat(json).contains("\"actorRole\":\"RECEPTIONIST\"");
                    assertThat(json).contains("\"actorEmail\":\"frontdesk@clinic.test\"");
                });
    }

    @Test
    void receptionistCannotUpdateOlderPatient() {
        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        PatientEntity patient = patient(tenantId, patientId, "PAT-2001", OffsetDateTime.now(UTC).minusDays(1));
        patient.update(
                "Asha",
                "Rao",
                PatientGender.FEMALE,
                null,
                31,
                "9999999999",
                "old@example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Old notes",
                true
        );
        when(repository.findByTenantIdAndId(tenantId, patientId)).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> service.update(
                tenantId,
                patientId,
                command("Asha", "Rai", "8888888888"),
                UUID.randomUUID(),
                "RECEPTIONIST",
                UTC,
                "frontdesk@clinic.test"
        ))
                .isInstanceOf(ForbiddenException.class)
                .satisfies(ex -> {
                    ForbiddenException forbiddenException = (ForbiddenException) ex;
                    assertThat(forbiddenException.getMessage()).isEqualTo("Patient details can be edited by Clinic Admin after registration day.");
                });
        verify(repository, never()).save(any());
        verify(auditEventPublisher, never()).record(any());
    }

    @Test
    void clinicAdminCanUpdateOlderPatient() {
        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        PatientEntity patient = patient(tenantId, patientId, "PAT-3001", OffsetDateTime.now(UTC).minusDays(1));
        patient.update(
                "Asha",
                "Rao",
                PatientGender.FEMALE,
                null,
                31,
                "9999999999",
                "old@example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Old notes",
                true
        );
        when(repository.findByTenantIdAndId(tenantId, patientId)).thenReturn(Optional.of(patient));

        var saved = service.update(
                tenantId,
                patientId,
                command("Asha", "Rai", "8888888888"),
                actorId,
                "CLINIC_ADMIN",
                UTC,
                "admin@clinic.test"
        );

        assertThat(saved.lastName()).isEqualTo("Rai");
        verify(repository).save(any(PatientEntity.class));
        verify(auditEventPublisher, atLeastOnce()).record(any());
    }

    @Test
    void clinicAdminCanPersistAllergiesOnUpdate() {
        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        PatientEntity patient = patient(tenantId, patientId, "PAT-3002", OffsetDateTime.now(UTC).minusDays(1));
        patient.update(
                "Asha",
                "Rao",
                PatientGender.FEMALE,
                null,
                31,
                "9999999999",
                "old@example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Old notes",
                true
        );
        when(repository.findByTenantIdAndId(tenantId, patientId)).thenReturn(Optional.of(patient));

        PatientUpsertCommand command = new PatientUpsertCommand(
                "Asha",
                "Rao",
                PatientGender.FEMALE,
                null,
                31,
                "9999999999",
                "old@example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Paracetamol",
                null,
                null,
                "Uses inhaler",
                "Old notes",
                true
        );

        var saved = service.update(tenantId, patientId, command, actorId, "CLINIC_ADMIN", UTC, "admin@clinic.test");

        assertThat(saved.allergies()).isEqualTo("Paracetamol");
        assertThat(saved.surgicalHistory()).isEqualTo("Uses inhaler");
        assertThat(saved.notes()).isEqualTo("Old notes");
        verify(repository).save(any(PatientEntity.class));
    }

    @Test
    void receptionistCannotDeactivateOlderPatient() {
        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        PatientEntity patient = patient(tenantId, patientId, "PAT-4001", OffsetDateTime.now(UTC).minusDays(1));
        patient.update(
                "Asha",
                "Rao",
                PatientGender.FEMALE,
                null,
                31,
                "9999999999",
                "old@example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Old notes",
                true
        );
        when(repository.findByTenantIdAndId(tenantId, patientId)).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> service.deactivate(
                tenantId,
                patientId,
                UUID.randomUUID(),
                "RECEPTIONIST",
                UTC,
                "frontdesk@clinic.test"
        ))
                .isInstanceOf(ForbiddenException.class)
                .satisfies(ex -> {
                    ForbiddenException forbiddenException = (ForbiddenException) ex;
                    assertThat(forbiddenException.getMessage()).isEqualTo("Patient details can be edited by Clinic Admin after registration day.");
                });
        verify(repository, never()).save(any());
        verify(auditEventPublisher, never()).record(any());
    }

    private PatientUpsertCommand command(String firstName, String lastName, String mobile) {
        return new PatientUpsertCommand(
                firstName,
                lastName,
                PatientGender.UNKNOWN,
                null,
                null,
                mobile,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true
        );
    }

    private PatientEntity patient(UUID tenantId, UUID id, String patientNumber, OffsetDateTime createdAt) {
        PatientEntity entity = PatientEntity.create(tenantId, patientNumber);
        setField(entity, "id", id);
        setField(entity, "createdAt", createdAt);
        setField(entity, "updatedAt", createdAt);
        return entity;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to set field " + fieldName, ex);
        }
    }

    private Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ex) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
