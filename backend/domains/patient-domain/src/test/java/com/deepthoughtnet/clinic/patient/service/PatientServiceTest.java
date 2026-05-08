package com.deepthoughtnet.clinic.patient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.patient.service.model.PatientUpsertCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PatientServiceTest {
    private PatientRepository repository;
    private PatientService service;

    @BeforeEach
    void setUp() {
        repository = mock(PatientRepository.class);
        service = new PatientService(repository, mock(AuditEventPublisher.class), new ObjectMapper());
        when(repository.existsByTenantIdAndPatientNumber(any(), any())).thenReturn(false);
        when(repository.save(any(PatientEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
        PatientUpsertCommand command = command("Receptionist", "One", "abc123");

        assertThatThrownBy(() -> service.create(tenantId, command, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("mobile must be a valid phone number");
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsDuplicateActiveMobileInTenant() {
        UUID tenantId = UUID.randomUUID();
        PatientEntity existing = PatientEntity.create(tenantId, "PAT-EXISTING");
        existing.update("Existing", "Patient", PatientGender.UNKNOWN, null, null, "9876543210", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
        when(repository.findFirstByTenantIdAndMobileIgnoreCaseAndActiveTrue(tenantId, "9876543210")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(tenantId, command("Receptionist", "One", "9876543210"), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Active patient with the same mobile already exists");
        verify(repository, never()).save(any());
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
}
