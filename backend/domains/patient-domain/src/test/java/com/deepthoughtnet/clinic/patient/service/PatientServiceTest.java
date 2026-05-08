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
import com.deepthoughtnet.clinic.patient.service.model.PatientSearchCriteria;
import com.deepthoughtnet.clinic.patient.service.model.PatientUpsertCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
        PatientUpsertCommand command = command("Receptionist", "One", "abc123");

        assertThatThrownBy(() -> service.create(tenantId, command, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("mobile must be a valid phone number");
        verify(repository, never()).save(any());
    }

    @Test
    void createAllowsMultipleActivePatientsWithSameMobile() {
        UUID tenantId = UUID.randomUUID();
        var created = service.create(tenantId, command("Receptionist", "One", "9876543210"), UUID.randomUUID());
        assertThat(created.mobile()).isEqualTo("9876543210");
        verify(repository).save(any(PatientEntity.class));
    }

    @Test
    void searchReturnsMultiplePatientsWithSameMobile() {
        UUID tenantId = UUID.randomUUID();
        PatientEntity first = PatientEntity.create(tenantId, "PAT-FIRST");
        first.update("Raj", "Sharma", PatientGender.MALE, null, 42, "9876543210", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
        PatientEntity second = PatientEntity.create(tenantId, "PAT-SECOND");
        second.update("Priya", "Sharma", PatientGender.FEMALE, null, 39, "9876543210", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
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
