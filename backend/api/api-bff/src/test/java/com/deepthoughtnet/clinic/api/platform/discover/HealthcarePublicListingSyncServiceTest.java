package com.deepthoughtnet.clinic.api.platform.discover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfilePhotoRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderPublicationRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.deepthoughtnet.clinic.appointment.service.DoctorAvailabilityQueryService;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileSnapshot;

@ExtendWith(MockitoExtension.class)
class HealthcarePublicListingSyncServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("407dbc68-107d-4f64-83c8-6499e50e5c78");
    private static final UUID CLINIC_ID = UUID.fromString("fb6977b3-683b-40a3-95b8-05ffbad1dac0");
    private static final UUID DOCTOR_USER_ID = UUID.fromString("ff4d7d2a-401a-4993-9814-afe2863275b6");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-08T04:10:51.000Z");

    @Mock
    private ClinicProfileService clinicProfileService;
    @Mock
    private DoctorProfileService doctorProfileService;
    @Mock
    private TenantUserManagementService tenantUserManagementService;
    @Mock
    private DoctorAvailabilityQueryService doctorAvailabilityQueryService;
    @Mock
    private ClinicalDocumentService clinicalDocumentService;
    @Mock
    private ProviderPublicProfileService publicProfileService;

    private HealthcarePublicListingSyncService service;

    @BeforeEach
    void setUp() {
        service = new HealthcarePublicListingSyncService(
                clinicProfileService,
                doctorProfileService,
                tenantUserManagementService,
                doctorAvailabilityQueryService,
                clinicalDocumentService,
                publicProfileService
        );
    }

    @Test
    void syncTenantSkipsPhotoDownloadWhenDoctorHasNoPhotoMetadata() {
        ClinicProfileRecord clinic = clinicRecord();
        DoctorProfileRecord doctor = doctorRecord(null, null, null, null);

        when(clinicProfileService.findByTenantId(TENANT_ID)).thenReturn(Optional.of(clinic));
        when(doctorProfileService.findByTenantIdAndActive(TENANT_ID)).thenReturn(List.of(doctor));
        when(tenantUserManagementService.list(TENANT_ID)).thenReturn(List.of(doctorUser()));
        when(doctorAvailabilityQueryService.hasActiveAvailability(TENANT_ID, DOCTOR_USER_ID)).thenReturn(false);

        HealthcarePublicListingSyncService.HealthcarePublicListingSyncSummary summary =
                service.syncTenant(TENANT_ID, null, "startup.reconcile");

        assertThat(summary.failed()).isZero();
        assertThat(summary.updated()).isEqualTo(2);
        verify(doctorProfileService, never()).downloadPhoto(any(), any());
        verify(publicProfileService, never()).upsertPublishedMedia(any(), any(), any(), any(), any());

        ArgumentCaptor<PublicProviderProfileSnapshot> snapshotCaptor = ArgumentCaptor.forClass(PublicProviderProfileSnapshot.class);
        verify(publicProfileService, times(2)).upsertLifecycleProfile(snapshotCaptor.capture(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), anyLong());
        PublicProviderProfileSnapshot doctorSnapshot = snapshotCaptor.getAllValues().get(1);
        assertThat(doctorSnapshot.providerType()).isEqualTo(ProviderType.INDIVIDUAL_DOCTOR);
        assertThat(doctorSnapshot.doctorPhotoDocumentId()).isNull();
    }

    @Test
    void syncTenantProjectsDoctorPhotoWhenPhotoMetadataExists() {
        ClinicProfileRecord clinic = clinicRecord();
        UUID photoDocumentId = UUID.randomUUID();
        DoctorProfileRecord doctor = doctorRecord(
                "/api/doctors/" + DOCTOR_USER_ID + "/photo?v=1754626251000",
                "photo.webp",
                "image/webp",
                2048L
        );

        when(clinicProfileService.findByTenantId(TENANT_ID)).thenReturn(Optional.of(clinic));
        when(doctorProfileService.findByTenantIdAndActive(TENANT_ID)).thenReturn(List.of(doctor));
        when(tenantUserManagementService.list(TENANT_ID)).thenReturn(List.of(doctorUser()));
        when(doctorAvailabilityQueryService.hasActiveAvailability(TENANT_ID, DOCTOR_USER_ID)).thenReturn(false);
        when(doctorProfileService.downloadPhoto(TENANT_ID, DOCTOR_USER_ID))
                .thenReturn(new DoctorProfilePhotoRecord("photo.webp", "image/webp", 2048L, new byte[] {1, 2, 3}));
        when(publicProfileService.upsertPublishedMedia(any(), any(), any(), any(), any())).thenReturn(photoDocumentId);

        HealthcarePublicListingSyncService.HealthcarePublicListingSyncSummary summary =
                service.syncTenant(TENANT_ID, null, "startup.reconcile");

        assertThat(summary.failed()).isZero();
        assertThat(summary.updated()).isEqualTo(2);
        verify(doctorProfileService).downloadPhoto(TENANT_ID, DOCTOR_USER_ID);
        verify(publicProfileService).upsertPublishedMedia(eq(DOCTOR_USER_ID), any(), any(), any(), any());

        ArgumentCaptor<PublicProviderProfileSnapshot> snapshotCaptor = ArgumentCaptor.forClass(PublicProviderProfileSnapshot.class);
        verify(publicProfileService, times(2)).upsertLifecycleProfile(snapshotCaptor.capture(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), anyLong());
        PublicProviderProfileSnapshot doctorSnapshot = snapshotCaptor.getAllValues().get(1);
        assertThat(doctorSnapshot.providerType()).isEqualTo(ProviderType.INDIVIDUAL_DOCTOR);
        assertThat(doctorSnapshot.doctorPhotoDocumentId()).isEqualTo(photoDocumentId);
    }

    @Test
    void syncTenantContinuesWhenOptionalPhotoRetrievalFails() {
        ClinicProfileRecord clinic = clinicRecord();
        DoctorProfileRecord doctor = doctorRecord(
                "/api/doctors/" + DOCTOR_USER_ID + "/photo?v=1754626251000",
                "photo.webp",
                "image/webp",
                2048L
        );

        when(clinicProfileService.findByTenantId(TENANT_ID)).thenReturn(Optional.of(clinic));
        when(doctorProfileService.findByTenantIdAndActive(TENANT_ID)).thenReturn(List.of(doctor));
        when(tenantUserManagementService.list(TENANT_ID)).thenReturn(List.of(doctorUser()));
        when(doctorAvailabilityQueryService.hasActiveAvailability(TENANT_ID, DOCTOR_USER_ID)).thenReturn(false);
        when(doctorProfileService.downloadPhoto(TENANT_ID, DOCTOR_USER_ID))
                .thenThrow(new IllegalStateException("storage unavailable"));

        HealthcarePublicListingSyncService.HealthcarePublicListingSyncSummary summary =
                service.syncTenant(TENANT_ID, null, "startup.reconcile");

        assertThat(summary.failed()).isZero();
        assertThat(summary.updated()).isEqualTo(2);
        verify(doctorProfileService).downloadPhoto(TENANT_ID, DOCTOR_USER_ID);
        verify(publicProfileService, never()).upsertPublishedMedia(any(), any(), any(), any(), any());
    }

    private ClinicProfileRecord clinicRecord() {
        return new ClinicProfileRecord(
                CLINIC_ID,
                TENANT_ID,
                "Green Valley Family Clinic",
                "Green Valley Family Clinic",
                null,
                null,
                "123 Main Road",
                null,
                "Pune",
                "Maharashtra",
                "India",
                "411001",
                "REG-1",
                null,
                null,
                true,
                true,
                "green-valley-family-clinic",
                NOW,
                NOW
        );
    }

    private TenantUserRecord doctorUser() {
        return new TenantUserRecord(
                DOCTOR_USER_ID,
                TENANT_ID,
                "sub-doctor",
                "amit.verma@greenvalley.test",
                "Amit Verma",
                "ACTIVE",
                "DOCTOR",
                "ACTIVE",
                NOW,
                NOW,
                "COMPLETE"
        );
    }

    private DoctorProfileRecord doctorRecord(String photoUrl, String photoFileName, String photoMimeType, Long photoSizeBytes) {
        return new DoctorProfileRecord(
                UUID.randomUUID(),
                TENANT_ID,
                DOCTOR_USER_ID,
                "9999999999",
                "General Medicine",
                List.of("General Medicine"),
                "MBBS",
                "REG-DOCTOR-1",
                "Room 1",
                new BigDecimal("500.00"),
                new BigDecimal("500.00"),
                null,
                null,
                12,
                null,
                true,
                true,
                "amit-verma",
                photoUrl,
                photoFileName,
                photoMimeType,
                photoSizeBytes,
                NOW,
                NOW
        );
    }
}
