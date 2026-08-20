package com.deepthoughtnet.clinic.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.deepthoughtnet.clinic.clinic.db.DoctorProfileEntity;
import com.deepthoughtnet.clinic.clinic.db.DoctorProfileRepository;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileUpsertCommand;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DoctorProfileServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID doctorUserId = UUID.randomUUID();
    private DoctorProfileRepository repository;
    private DoctorProfileService service;
    private ObjectStorageService storageService;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(DoctorProfileRepository.class);
        storageService = Mockito.mock(ObjectStorageService.class);
        when(storageService.buildDocumentStorageKey(any(), any())).thenReturn("tenants/test/documents/2026/07/photo.webp");
        service = new DoctorProfileService(repository, storageService);
        when(repository.save(any(DoctorProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.saveAndFlush(any(DoctorProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsProfileWhenMissing() {
        when(repository.findByTenantIdAndDoctorUserId(tenantId, doctorUserId)).thenReturn(Optional.empty());
        var result = service.upsert(tenantId, doctorUserId, new DoctorProfileUpsertCommand(
                "9999999999",
                "Dermatology",
                java.util.List.of("Dermatology", "Skin"),
                "MBBS",
                "REG-1",
                "Room 2",
                null,
                new java.math.BigDecimal("500.00"),
                new java.math.BigDecimal("300.00"),
                new java.math.BigDecimal("800.00"),
                12,
                null,
                LocalDate.of(1985, 3, 15),
                true,
                true,
                "dr-demo",
                null
        ));
        assertThat(result.specialization()).isEqualTo("Dermatology");
        assertThat(result.specializations()).containsExactly("Dermatology", "Skin");
        assertThat(result.opdFee()).isEqualByComparingTo("500.00");
        assertThat(result.active()).isTrue();
        assertThat(result.publicListingEnabled()).isTrue();
        assertThat(result.slug()).isEqualTo("dr-demo");
    }

    @Test
    void updatesExistingProfile() {
        DoctorProfileEntity existing = DoctorProfileEntity.create(tenantId, doctorUserId);
        existing.update("1111", "ENT", java.util.List.of("ENT"), "MD", "REG-2", "Room 1", null, null, null, null, null, null, LocalDate.of(1984, 2, 1), true, false, null);
        when(repository.findByTenantIdAndDoctorUserId(tenantId, doctorUserId)).thenReturn(Optional.of(existing));
        var result = service.upsert(tenantId, doctorUserId, new DoctorProfileUpsertCommand(
                "8888888888",
                "Cardiology",
                java.util.List.of("Cardiology", "Heart"),
                "DM",
                "REG-3",
                "Room 3",
                null,
                new java.math.BigDecimal("750.00"),
                new java.math.BigDecimal("650.00"),
                new java.math.BigDecimal("1000.00"),
                12,
                null,
                LocalDate.of(1984, 2, 1),
                false,
                true,
                "dr-cardio",
                null
        ));
        assertThat(result.mobile()).isEqualTo("8888888888");
        assertThat(result.specialization()).isEqualTo("Cardiology");
        assertThat(result.specializations()).containsExactly("Cardiology", "Heart");
        assertThat(result.opdFee()).isEqualByComparingTo("750.00");
        assertThat(result.followUpFee()).isEqualByComparingTo("650.00");
        assertThat(result.emergencyFee()).isEqualByComparingTo("1000.00");
        assertThat(result.active()).isFalse();
        assertThat(result.publicListingEnabled()).isTrue();
        assertThat(result.slug()).isEqualTo("dr-cardio");
    }

    @Test
    void existingDoctorCanKeepSameRegistrationNumber() {
        DoctorProfileEntity existing = DoctorProfileEntity.create(tenantId, doctorUserId);
        existing.update("1111", "ENT", java.util.List.of("ENT"), "MD", "REG-2", "Room 1", null, null, null, null, null, null, LocalDate.of(1984, 2, 1), true, false, null);
        when(repository.findByTenantIdAndDoctorUserId(tenantId, doctorUserId)).thenReturn(Optional.of(existing));
        when(repository.findFirstByTenantIdAndActiveTrueAndRegistrationNumberIgnoreCase(tenantId, "REG-2")).thenReturn(Optional.of(existing));

        var result = service.upsert(tenantId, doctorUserId, new DoctorProfileUpsertCommand(
                "8888888888",
                "Cardiology",
                java.util.List.of("Cardiology"),
                "DM",
                "REG-2",
                "Room 3",
                null,
                new java.math.BigDecimal("750.00"),
                new java.math.BigDecimal("650.00"),
                new java.math.BigDecimal("1000.00"),
                12,
                null,
                LocalDate.of(1984, 2, 1),
                true,
                true,
                "dr-cardio",
                null
        ));

        assertThat(result.registrationNumber()).isEqualTo("REG-2");
    }

    @Test
    void duplicateRegistrationNumberInSameTenantIsRejected() {
        UUID otherDoctorId = UUID.randomUUID();
        DoctorProfileEntity duplicate = DoctorProfileEntity.create(tenantId, otherDoctorId);
        duplicate.update(null, "ENT", java.util.List.of("ENT"), null, "REG-1", null, null, null, null, null, null, null, LocalDate.of(1980, 1, 1), true, false, null);
        when(repository.findByTenantIdAndDoctorUserId(tenantId, doctorUserId)).thenReturn(Optional.empty());
        when(repository.findFirstByTenantIdAndActiveTrueAndRegistrationNumberIgnoreCase(tenantId, "REG-1")).thenReturn(Optional.of(duplicate));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.upsert(
                tenantId,
                doctorUserId,
                new DoctorProfileUpsertCommand(
                        "7777777777",
                        "Dermatology",
                        java.util.List.of("Dermatology"),
                        "MBBS",
                        "REG-1",
                        "Room 1",
                        null,
                        new java.math.BigDecimal("500.00"),
                        new java.math.BigDecimal("300.00"),
                        new java.math.BigDecimal("800.00"),
                        12,
                        null,
                        LocalDate.of(1985, 3, 15),
                        true,
                        true,
                        null
                )
        ));

        assertThat(ex).hasMessage("Doctor registration number already exists for this clinic.");
    }

    @Test
    void missingDateOfBirthIsRejectedForNewProfiles() {
        when(repository.findByTenantIdAndDoctorUserId(tenantId, doctorUserId)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.upsert(
                        tenantId,
                        doctorUserId,
                        new DoctorProfileUpsertCommand(
                        "9999999999",
                        "Dermatology",
                        java.util.List.of("Dermatology"),
                        "MBBS",
                        "REG-1",
                        "Room 2",
                        null,
                        new java.math.BigDecimal("500.00"),
                        new java.math.BigDecimal("300.00"),
                        new java.math.BigDecimal("800.00"),
                        12,
                        null,
                        null,
                        true,
                        true,
                        "dr-demo",
                        null
                )
        ));

        assertThat(ex).hasMessage("Date of birth is required.");
    }

    @Test
    void updatePhotoPersistsStorageMetadataAndReturnsPhotoUrl() {
        DoctorProfileEntity existing = DoctorProfileEntity.create(tenantId, doctorUserId);
        when(repository.findByTenantIdAndDoctorUserId(tenantId, doctorUserId)).thenReturn(Optional.of(existing));
        when(storageService.statObjectSize(any())).thenReturn(2048L);

        var result = service.updatePhoto(
                tenantId,
                doctorUserId,
                "profile.webp",
                "image/webp",
                new byte[] {1, 2, 3}
        );

        assertThat(result.photoUrl()).startsWith("/api/doctors/" + doctorUserId + "/photo?v=");
        assertThat(result.photoFileName()).isEqualTo("profile.webp");
        assertThat(result.photoMimeType()).isEqualTo("image/webp");
        assertThat(result.photoSizeBytes()).isEqualTo(2048L);
        verify(storageService).putObject(any(), any(), any());
        verify(storageService).statObjectSize(any());
    }

    @Test
    void findByDoctorUserIdRepairsMissingPhotoSize() {
        DoctorProfileEntity existing = DoctorProfileEntity.create(tenantId, doctorUserId);
        existing.updatePhoto("tenants/test/documents/2026/07/photo.webp", "image/webp", null, "profile.webp");
        when(repository.findByTenantIdAndDoctorUserId(tenantId, doctorUserId)).thenReturn(Optional.of(existing));
        when(storageService.statObjectSize("tenants/test/documents/2026/07/photo.webp")).thenReturn(4096L);

        var result = service.findByDoctorUserIdWithPhotoRepair(tenantId, doctorUserId).orElseThrow();

        assertThat(result.photoUrl()).startsWith("/api/doctors/" + doctorUserId + "/photo?v=");
        assertThat(result.photoSizeBytes()).isEqualTo(4096L);
        verify(repository).saveAndFlush(existing);
    }
}
