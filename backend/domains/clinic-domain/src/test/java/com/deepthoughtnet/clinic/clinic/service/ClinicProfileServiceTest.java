package com.deepthoughtnet.clinic.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.clinic.db.ClinicProfileEntity;
import com.deepthoughtnet.clinic.clinic.db.ClinicProfileRepository;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileUpsertCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

class ClinicProfileServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorAppUserId = UUID.randomUUID();
    private ClinicProfileRepository repository;
    private AuditEventPublisher auditEventPublisher;
    private ClinicProfileService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ClinicProfileRepository.class);
        auditEventPublisher = Mockito.mock(AuditEventPublisher.class);
        service = new ClinicProfileService(repository, auditEventPublisher, new ObjectMapper());
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(repository.findBySlugIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.save(any(ClinicProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @ParameterizedTest
    @MethodSource("blankMandatoryFields")
    void blankMandatoryFieldIsRejected(String fieldName, Supplier<ClinicProfileUpsertCommand> commandSupplier, String expectedMessage) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.upsert(
                tenantId,
                commandSupplier.get(),
                actorAppUserId
        ));

        assertThat(ex).hasMessage(expectedMessage);
    }

    @ParameterizedTest
    @MethodSource("whitespaceMandatoryFields")
    void whitespaceOnlyMandatoryFieldIsRejected(String fieldName, Supplier<ClinicProfileUpsertCommand> commandSupplier, String expectedMessage) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.upsert(
                tenantId,
                commandSupplier.get(),
                actorAppUserId
        ));

        assertThat(ex).hasMessage(expectedMessage);
    }

    @Test
    void invalidPhoneIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.upsert(
                tenantId,
                new ClinicProfileUpsertCommand(
                        "Jeevanam Healthcare Clinic",
                        "Jeevanam Healthcare",
                        "abc123",
                        "clinic@example.com",
                        "123 Main Road",
                        null,
                        "Pune",
                        "Maharashtra",
                        "India",
                        "411001",
                        "REG-123",
                        null,
                        null,
                        true,
                        false,
                        null
                ),
                actorAppUserId
        ));

        assertThat(ex).hasMessage("Enter a valid 10-digit Indian mobile number.");
    }

    @Test
    void invalidEmailIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.upsert(
                tenantId,
                new ClinicProfileUpsertCommand(
                        "Jeevanam Healthcare Clinic",
                        "Jeevanam Healthcare",
                        "9876543210",
                        "clinic@",
                        "123 Main Road",
                        null,
                        "Pune",
                        "Maharashtra",
                        "India",
                        "411001",
                        "REG-123",
                        null,
                        null,
                        true,
                        false,
                        null
                ),
                actorAppUserId
        ));

        assertThat(ex).hasMessage("Enter a valid email address.");
    }

    @Test
    void invalidIndianPostalCodeIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.upsert(
                tenantId,
                new ClinicProfileUpsertCommand(
                        "Jeevanam Healthcare Clinic",
                        "Jeevanam Healthcare",
                        "9876543210",
                        "clinic@example.com",
                        "123 Main Road",
                        null,
                        "Pune",
                        "Maharashtra",
                        "India",
                        "41A001",
                        "REG-123",
                        null,
                        null,
                        true,
                        false,
                        null
                ),
                actorAppUserId
        ));

        assertThat(ex).hasMessage("Enter a valid 6-digit PIN code.");
    }

    @Test
    void invalidGstinIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.upsert(
                tenantId,
                new ClinicProfileUpsertCommand(
                        "Jeevanam Healthcare Clinic",
                        "Jeevanam Healthcare",
                        "9876543210",
                        "clinic@example.com",
                        "123 Main Road",
                        null,
                        "Pune",
                        "Maharashtra",
                        "India",
                        "411001",
                        "REG-123",
                        "1234",
                        null,
                        true,
                        false,
                        null
                ),
                actorAppUserId
        ));

        assertThat(ex).hasMessage("Enter a valid GSTIN.");
    }

    @Test
    void invalidManualSlugIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.upsert(
                tenantId,
                new ClinicProfileUpsertCommand(
                        "Jeevanam Healthcare Clinic",
                        "Jeevanam Healthcare",
                        "9876543210",
                        "clinic@example.com",
                        "123 Main Road",
                        null,
                        "Pune",
                        "Maharashtra",
                        "India",
                        "411001",
                        "REG-123",
                        null,
                        null,
                        true,
                        false,
                        "bad slug!"
                ),
                actorAppUserId
        ));

        assertThat(ex).hasMessage("Enter a valid public slug.");
    }

    @Test
    void duplicateManualSlugIsRejected() {
        ClinicProfileEntity existing = ClinicProfileEntity.create(UUID.randomUUID());
        existing.update("Other Clinic", "Other Clinic", "9876543210", "other@example.com", "Line 1", null, "Pune", "Maharashtra", "India", "411001", "REG-2", null, null, true, false, "shared-clinic");
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(repository.findBySlugIgnoreCase("shared-clinic")).thenReturn(Optional.of(existing));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.upsert(
                tenantId,
                new ClinicProfileUpsertCommand(
                        "Jeevanam Healthcare Clinic",
                        "Jeevanam Healthcare",
                        "9876543210",
                        "clinic@example.com",
                        "123 Main Road",
                        null,
                        "Pune",
                        "Maharashtra",
                        "India",
                        "411001",
                        "REG-123",
                        null,
                        null,
                        true,
                        false,
                        "shared-clinic"
                ),
                actorAppUserId
        ));

        assertThat(ex).hasMessage("This public slug is already in use. Choose another one.");
    }

    @Test
    void optionalFieldsMayBeBlankAndSlugMayAutoGenerate() {
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(repository.findBySlugIgnoreCase("jeevanam-healthcare")).thenReturn(Optional.empty());

        var result = service.upsert(
                tenantId,
                new ClinicProfileUpsertCommand(
                        "Jeevanam Healthcare Clinic",
                        "Jeevanam Healthcare",
                        "9876543210",
                        "clinic@example.com",
                        "123 Main Road",
                        "",
                        "Pune",
                        "Maharashtra",
                        "India",
                        "411001",
                        "REG-123",
                        "",
                        null,
                        true,
                        false,
                        ""
                ),
                actorAppUserId
        );

        assertThat(result.slug()).isEqualTo("jeevanam-healthcare");
        assertThat(result.addressLine2()).isNull();
        assertThat(result.gstNumber()).isNull();
        verify(auditEventPublisher).record(any());
    }

    @Test
    void successfulValidUpdatePersistsAndAudits() {
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(repository.findBySlugIgnoreCase("green-valley-healthcare")).thenReturn(Optional.empty());

        var result = service.upsert(
                tenantId,
                new ClinicProfileUpsertCommand(
                        "Green Valley Healthcare",
                        "Green Valley Healthcare",
                        "9876543210",
                        "clinic@example.com",
                        "12 Green Valley Road",
                        "Suite 4",
                        "Pune",
                        "Maharashtra",
                        "India",
                        "411001",
                        "REG-123",
                        "27ABCDE1234F1Z5",
                        UUID.randomUUID(),
                        true,
                        true,
                        "green-valley-healthcare"
                ),
                actorAppUserId
        );

        assertThat(result.clinicName()).isEqualTo("Green Valley Healthcare");
        assertThat(result.slug()).isEqualTo("green-valley-healthcare");
        verify(auditEventPublisher).record(any());
    }

    private static Stream<Arguments> blankMandatoryFields() {
        return Stream.of(
                Arguments.of("clinicName", (Supplier<ClinicProfileUpsertCommand>) () -> validCommand("", "Jeevanam Healthcare", "9876543210", "clinic@example.com", "123 Main Road", "Pune", "Maharashtra", "India", "411001", "REG-123"), "Clinic name is required."),
                Arguments.of("displayName", (Supplier<ClinicProfileUpsertCommand>) () -> validCommand("Jeevanam Healthcare Clinic", "", "9876543210", "clinic@example.com", "123 Main Road", "Pune", "Maharashtra", "India", "411001", "REG-123"), "Display name is required."),
                Arguments.of("phone", (Supplier<ClinicProfileUpsertCommand>) () -> validCommand("Jeevanam Healthcare Clinic", "Jeevanam Healthcare", "", "clinic@example.com", "123 Main Road", "Pune", "Maharashtra", "India", "411001", "REG-123"), "Phone is required."),
                Arguments.of("email", (Supplier<ClinicProfileUpsertCommand>) () -> validCommand("Jeevanam Healthcare Clinic", "Jeevanam Healthcare", "9876543210", "", "123 Main Road", "Pune", "Maharashtra", "India", "411001", "REG-123"), "Email is required."),
                Arguments.of("addressLine1", (Supplier<ClinicProfileUpsertCommand>) () -> validCommand("Jeevanam Healthcare Clinic", "Jeevanam Healthcare", "9876543210", "clinic@example.com", "", "Pune", "Maharashtra", "India", "411001", "REG-123"), "Address line 1 is required."),
                Arguments.of("city", (Supplier<ClinicProfileUpsertCommand>) () -> validCommand("Jeevanam Healthcare Clinic", "Jeevanam Healthcare", "9876543210", "clinic@example.com", "123 Main Road", "", "Maharashtra", "India", "411001", "REG-123"), "City is required."),
                Arguments.of("state", (Supplier<ClinicProfileUpsertCommand>) () -> validCommand("Jeevanam Healthcare Clinic", "Jeevanam Healthcare", "9876543210", "clinic@example.com", "123 Main Road", "Pune", "", "India", "411001", "REG-123"), "State is required."),
                Arguments.of("country", (Supplier<ClinicProfileUpsertCommand>) () -> validCommand("Jeevanam Healthcare Clinic", "Jeevanam Healthcare", "9876543210", "clinic@example.com", "123 Main Road", "Pune", "Maharashtra", "", "411001", "REG-123"), "Country is required."),
                Arguments.of("postalCode", (Supplier<ClinicProfileUpsertCommand>) () -> validCommand("Jeevanam Healthcare Clinic", "Jeevanam Healthcare", "9876543210", "clinic@example.com", "123 Main Road", "Pune", "Maharashtra", "India", "", "REG-123"), "Postal code is required."),
                Arguments.of("registrationNumber", (Supplier<ClinicProfileUpsertCommand>) () -> validCommand("Jeevanam Healthcare Clinic", "Jeevanam Healthcare", "9876543210", "clinic@example.com", "123 Main Road", "Pune", "Maharashtra", "India", "411001", ""), "Registration number is required.")
        );
    }

    private static Stream<Arguments> whitespaceMandatoryFields() {
        return Stream.of(
                Arguments.of("clinicName", (Supplier<ClinicProfileUpsertCommand>) () -> validCommand("  ", "Jeevanam Healthcare", "9876543210", "clinic@example.com", "123 Main Road", "Pune", "Maharashtra", "India", "411001", "REG-123"), "Clinic name is required."),
                Arguments.of("displayName", (Supplier<ClinicProfileUpsertCommand>) () -> validCommand("Jeevanam Healthcare Clinic", "   ", "9876543210", "clinic@example.com", "123 Main Road", "Pune", "Maharashtra", "India", "411001", "REG-123"), "Display name is required."),
                Arguments.of("phone", (Supplier<ClinicProfileUpsertCommand>) () -> validCommand("Jeevanam Healthcare Clinic", "Jeevanam Healthcare", "   ", "clinic@example.com", "123 Main Road", "Pune", "Maharashtra", "India", "411001", "REG-123"), "Phone is required."),
                Arguments.of("email", (Supplier<ClinicProfileUpsertCommand>) () -> validCommand("Jeevanam Healthcare Clinic", "Jeevanam Healthcare", "9876543210", "   ", "123 Main Road", "Pune", "Maharashtra", "India", "411001", "REG-123"), "Email is required."),
                Arguments.of("addressLine1", (Supplier<ClinicProfileUpsertCommand>) () -> validCommand("Jeevanam Healthcare Clinic", "Jeevanam Healthcare", "9876543210", "clinic@example.com", "  ", "Pune", "Maharashtra", "India", "411001", "REG-123"), "Address line 1 is required."),
                Arguments.of("city", (Supplier<ClinicProfileUpsertCommand>) () -> validCommand("Jeevanam Healthcare Clinic", "Jeevanam Healthcare", "9876543210", "clinic@example.com", "123 Main Road", "   ", "Maharashtra", "India", "411001", "REG-123"), "City is required."),
                Arguments.of("state", (Supplier<ClinicProfileUpsertCommand>) () -> validCommand("Jeevanam Healthcare Clinic", "Jeevanam Healthcare", "9876543210", "clinic@example.com", "123 Main Road", "Pune", "   ", "India", "411001", "REG-123"), "State is required."),
                Arguments.of("country", (Supplier<ClinicProfileUpsertCommand>) () -> validCommand("Jeevanam Healthcare Clinic", "Jeevanam Healthcare", "9876543210", "clinic@example.com", "123 Main Road", "Pune", "Maharashtra", "   ", "411001", "REG-123"), "Country is required."),
                Arguments.of("postalCode", (Supplier<ClinicProfileUpsertCommand>) () -> validCommand("Jeevanam Healthcare Clinic", "Jeevanam Healthcare", "9876543210", "clinic@example.com", "123 Main Road", "Pune", "Maharashtra", "India", "   ", "REG-123"), "Postal code is required."),
                Arguments.of("registrationNumber", (Supplier<ClinicProfileUpsertCommand>) () -> validCommand("Jeevanam Healthcare Clinic", "Jeevanam Healthcare", "9876543210", "clinic@example.com", "123 Main Road", "Pune", "Maharashtra", "India", "411001", "   "), "Registration number is required.")
        );
    }

    private static ClinicProfileUpsertCommand validCommand(
            String clinicName,
            String displayName,
            String phone,
            String email,
            String addressLine1,
            String city,
            String state,
            String country,
            String postalCode,
            String registrationNumber
    ) {
        return new ClinicProfileUpsertCommand(
                clinicName,
                displayName,
                phone,
                email,
                addressLine1,
                null,
                city,
                state,
                country,
                postalCode,
                registrationNumber,
                null,
                null,
                true,
                false,
                null
        );
    }
}
