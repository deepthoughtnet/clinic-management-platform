package com.deepthoughtnet.clinic.api.publicsite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileRecord;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import com.deepthoughtnet.clinic.identity.service.model.TenantModulesRecord;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicCatalogFacadeTest {

    @Test
    void listsOnlyActivePublicSafeDoctorsAndClinics() {
        PlatformTenantManagementService tenantService = mock(PlatformTenantManagementService.class);
        ClinicProfileService clinicProfileService = mock(ClinicProfileService.class);
        TenantUserManagementService tenantUserManagementService = mock(TenantUserManagementService.class);
        DoctorProfileService doctorProfileService = mock(DoctorProfileService.class);
        PublicCatalogFacade facade = new PublicCatalogFacade(tenantService, clinicProfileService, tenantUserManagementService, doctorProfileService);

        UUID activeTenantId = UUID.randomUUID();
        UUID suspendedTenantId = UUID.randomUUID();
        UUID activeDoctorId = UUID.randomUUID();
        UUID inactiveDoctorId = UUID.randomUUID();

        when(tenantService.list()).thenReturn(List.of(
                tenant(activeTenantId, "sunrise", "ACTIVE"),
                tenant(suspendedTenantId, "hidden", "SUSPENDED")
        ));
        when(clinicProfileService.findByTenantId(activeTenantId)).thenReturn(Optional.of(clinic(activeTenantId, "Sunrise Clinic", true, "Pune")));
        when(clinicProfileService.findByTenantId(suspendedTenantId)).thenReturn(Optional.of(clinic(suspendedTenantId, "Hidden Clinic", true, "Mumbai")));

        when(tenantUserManagementService.list(activeTenantId)).thenReturn(List.of(
                doctorUser(activeDoctorId, activeTenantId, "Dr. Asha Menon", "ACTIVE", "ACTIVE"),
                doctorUser(inactiveDoctorId, activeTenantId, "Dr. Hidden", "ACTIVE", "DISABLED"),
                nonDoctorUser(activeTenantId)
        ));
        when(tenantUserManagementService.list(suspendedTenantId)).thenReturn(List.of(
                doctorUser(UUID.randomUUID(), suspendedTenantId, "Dr. Suspended", "ACTIVE", "ACTIVE")
        ));

        when(doctorProfileService.findByDoctorUserId(activeTenantId, activeDoctorId)).thenReturn(Optional.of(
                doctorProfile(activeTenantId, activeDoctorId, "Dermatology, Skin Care", true, new BigDecimal("700"))
        ));
        when(doctorProfileService.findByDoctorUserId(activeTenantId, inactiveDoctorId)).thenReturn(Optional.of(
                doctorProfile(activeTenantId, inactiveDoctorId, "Cardiology", true, new BigDecimal("1200"))
        ));

        var doctors = facade.listDoctors("asha", "pune", null, null);
        var clinics = facade.listClinics(null, null, "Dermatology", null);
        var search = facade.search("skin", "pune", null);

        assertThat(doctors).hasSize(1);
        assertThat(doctors.get(0).doctorDisplayName()).isEqualTo("Dr. Asha Menon");
        assertThat(doctors.get(0).clinicDisplayName()).isEqualTo("Sunrise Clinic");
        assertThat(doctors.get(0).consultationFee()).isNull();
        assertThat(doctors.get(0).languages()).isEmpty();
        assertThat(doctors.get(0).nextAvailableSlotSummary()).isNull();

        assertThat(clinics).hasSize(1);
        assertThat(clinics.get(0).specialities()).containsExactly("Dermatology", "Skin Care");
        assertThat(search.specialities()).contains("Dermatology", "Skin Care");
        assertThat(search.doctors()).hasSize(1);
        assertThat(search.clinics()).hasSize(1);
    }

    @Test
    void publicDtosDoNotExposeInternalOrPatientFields() {
        assertThat(componentNames(com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicSummaryResponse.class))
                .doesNotContain("id", "tenantId", "patientId", "email", "phone", "registrationNumber", "notes");
        assertThat(componentNames(com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse.class))
                .doesNotContain("id", "tenantId", "patientId", "email", "mobile", "registrationNumber", "consultationRoom", "notes");
    }

    private List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private PlatformTenantRecord tenant(UUID id, String code, String status) {
        return new PlatformTenantRecord(
                id,
                code,
                code,
                "TRIAL",
                status,
                new TenantModulesRecord(true, false, false, false, false, false, false, false, false, false),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private ClinicProfileRecord clinic(UUID tenantId, String name, boolean active, String city) {
        return new ClinicProfileRecord(
                UUID.randomUUID(),
                tenantId,
                name,
                name,
                null,
                null,
                "Main Road",
                null,
                city,
                "Maharashtra",
                "India",
                "411001",
                null,
                null,
                null,
                active,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private TenantUserRecord doctorUser(UUID appUserId, UUID tenantId, String name, String userStatus, String membershipStatus) {
        return new TenantUserRecord(
                appUserId,
                tenantId,
                null,
                null,
                name,
                userStatus,
                "DOCTOR",
                membershipStatus,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "EXISTING"
        );
    }

    private TenantUserRecord nonDoctorUser(UUID tenantId) {
        return new TenantUserRecord(
                UUID.randomUUID(),
                tenantId,
                null,
                null,
                "Reception",
                "ACTIVE",
                "RECEPTIONIST",
                "ACTIVE",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "EXISTING"
        );
    }

    private DoctorProfileRecord doctorProfile(UUID tenantId, UUID doctorUserId, String specialization, boolean active, BigDecimal fee) {
        return new DoctorProfileRecord(
                UUID.randomUUID(),
                tenantId,
                doctorUserId,
                null,
                specialization,
                null,
                null,
                null,
                fee,
                8,
                40,
                active,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }
}
