package com.deepthoughtnet.clinic.api.publicsite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilityRecord;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotRecord;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotStatus;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotTimeState;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileRecord;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import com.deepthoughtnet.clinic.identity.service.model.TenantModulesRecord;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import java.lang.reflect.RecordComponent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicCatalogFacadeTest {

    @Test
    void hiddenDoctorAndInactiveTenantAreNotVisibleWhileActivePublicDoctorIsVisible() {
        PlatformTenantManagementService tenantService = mock(PlatformTenantManagementService.class);
        ClinicProfileService clinicProfileService = mock(ClinicProfileService.class);
        TenantUserManagementService tenantUserManagementService = mock(TenantUserManagementService.class);
        DoctorProfileService doctorProfileService = mock(DoctorProfileService.class);
        AppointmentService appointmentService = mock(AppointmentService.class);
        ClinicTimeZoneResolver clinicTimeZoneResolver = mock(ClinicTimeZoneResolver.class);
        PublicCatalogFacade facade = new PublicCatalogFacade(
                tenantService,
                clinicProfileService,
                tenantUserManagementService,
                doctorProfileService,
                appointmentService,
                clinicTimeZoneResolver
        );

        UUID publicTenantId = UUID.randomUUID();
        UUID inactiveTenantId = UUID.randomUUID();
        UUID visibleDoctorId = UUID.randomUUID();
        UUID hiddenDoctorId = UUID.randomUUID();

        when(tenantService.list()).thenReturn(List.of(
                tenant(publicTenantId, "sunrise", "ACTIVE", true),
                tenant(inactiveTenantId, "hidden", "SUSPENDED", true)
        ));
        when(clinicProfileService.findByTenantId(publicTenantId)).thenReturn(Optional.of(clinic(publicTenantId, "Sunrise Clinic", true, true, "sunrise-clinic", "Pune", "Baner")));
        when(clinicProfileService.findByTenantId(inactiveTenantId)).thenReturn(Optional.of(clinic(inactiveTenantId, "Hidden Clinic", true, true, "hidden-clinic", "Mumbai", "Bandra")));

        when(tenantUserManagementService.list(publicTenantId)).thenReturn(List.of(
                doctorUser(visibleDoctorId, publicTenantId, "Dr. Asha Menon", "ACTIVE", "ACTIVE"),
                doctorUser(hiddenDoctorId, publicTenantId, "Dr. Hidden", "ACTIVE", "ACTIVE")
        ));
        when(tenantUserManagementService.list(inactiveTenantId)).thenReturn(List.of(
                doctorUser(UUID.randomUUID(), inactiveTenantId, "Dr. Tenant Hidden", "ACTIVE", "ACTIVE")
        ));

        when(doctorProfileService.findByDoctorUserId(publicTenantId, visibleDoctorId)).thenReturn(Optional.of(
                doctorProfile(publicTenantId, visibleDoctorId, "Dermatology, Skin Care", true, true, "dr-asha-menon", 8)
        ));
        when(doctorProfileService.findByDoctorUserId(publicTenantId, hiddenDoctorId)).thenReturn(Optional.of(
                doctorProfile(publicTenantId, hiddenDoctorId, "Cardiology", true, false, "dr-hidden", 12)
        ));

        when(clinicTimeZoneResolver.resolve(any())).thenReturn(java.time.ZoneOffset.UTC);
        when(appointmentService.listSlots(any(), any(), any(), any())).thenAnswer(invocation -> {
            UUID doctorId = invocation.getArgument(1, UUID.class);
            LocalDate date = invocation.getArgument(2, LocalDate.class);
            if (doctorId.equals(visibleDoctorId) && date.equals(LocalDate.now(java.time.ZoneOffset.UTC))) {
                return List.of(slot(doctorId, date, LocalTime.of(10, 30), true));
            }
            return List.of();
        });
        when(appointmentService.listDoctorAvailabilities(publicTenantId, visibleDoctorId)).thenReturn(List.of(
                availability(publicTenantId, visibleDoctorId, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0))
        ));

        var doctors = facade.listDoctors("asha", "pune", null, null, null, null, 0, 12);
        var clinics = facade.listClinics(null, null, null, "Dermatology", null, 0, 12);
        var detail = facade.doctorDetail("dr-asha-menon");

        assertThat(doctors.items()).hasSize(1);
        assertThat(doctors.items().get(0).doctorDisplayName()).isEqualTo("Dr. Asha Menon");
        assertThat(doctors.items().get(0).availableToday()).isTrue();
        assertThat(doctors.items().get(0).nextAvailableSlotSummary()).contains("Today");

        assertThat(clinics.items()).hasSize(1);
        assertThat(clinics.items().get(0).clinicDisplayName()).isEqualTo("Sunrise Clinic");
        assertThat(clinics.items().get(0).specialities()).containsExactly("Dermatology", "Skin Care");

        assertThat(detail.doctorDisplayName()).isEqualTo("Dr. Asha Menon");
        assertThat(detail.availableToday()).isTrue();
        assertThat(detail.availableDays()).contains("Monday");
        assertThat(detail.nextAvailableSlots()).isNotEmpty();
    }

    @Test
    void specialityFilteringAndClinicFilteringWork() {
        PlatformTenantManagementService tenantService = mock(PlatformTenantManagementService.class);
        ClinicProfileService clinicProfileService = mock(ClinicProfileService.class);
        TenantUserManagementService tenantUserManagementService = mock(TenantUserManagementService.class);
        DoctorProfileService doctorProfileService = mock(DoctorProfileService.class);
        AppointmentService appointmentService = mock(AppointmentService.class);
        ClinicTimeZoneResolver clinicTimeZoneResolver = mock(ClinicTimeZoneResolver.class);
        PublicCatalogFacade facade = new PublicCatalogFacade(
                tenantService,
                clinicProfileService,
                tenantUserManagementService,
                doctorProfileService,
                appointmentService,
                clinicTimeZoneResolver
        );

        UUID tenantId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();

        when(tenantService.list()).thenReturn(List.of(tenant(tenantId, "sunrise", "ACTIVE", true)));
        when(clinicProfileService.findByTenantId(tenantId)).thenReturn(Optional.of(clinic(tenantId, "Sunrise Clinic", true, true, "sunrise-clinic", "Pune", "Baner")));
        when(tenantUserManagementService.list(tenantId)).thenReturn(List.of(
                doctorUser(doctorId, tenantId, "Dr. Asha Menon", "ACTIVE", "ACTIVE")
        ));
        when(doctorProfileService.findByDoctorUserId(tenantId, doctorId)).thenReturn(Optional.of(
                doctorProfile(tenantId, doctorId, "Dermatology, Skin Care", true, true, "dr-asha-menon", 8)
        ));
        when(clinicTimeZoneResolver.resolve(any())).thenReturn(java.time.ZoneOffset.UTC);
        when(appointmentService.listSlots(any(), any(), any(), any())).thenReturn(List.of());
        when(appointmentService.listDoctorAvailabilities(any(), any())).thenReturn(List.of());

        assertThat(facade.listDoctors(null, "pune", null, "Skin Care", "sunrise", null, 0, 12).items()).hasSize(1);
        assertThat(facade.listDoctors(null, "pune", null, "Cardiology", "sunrise", null, 0, 12).items()).isEmpty();
        assertThat(facade.listClinics("sunrise", "pune", "baner", "Dermatology", null, 0, 12).items()).hasSize(1);
        assertThat(facade.listClinics("sunrise", "mumbai", null, null, null, 0, 12).items()).isEmpty();
        assertThat(facade.listSpecialities("skin", "pune", null)).extracting("speciality").containsExactly("Skin Care");
    }

    @Test
    void publicClinicCanAppearWithoutAnyPublishedDoctors() {
        PlatformTenantManagementService tenantService = mock(PlatformTenantManagementService.class);
        ClinicProfileService clinicProfileService = mock(ClinicProfileService.class);
        TenantUserManagementService tenantUserManagementService = mock(TenantUserManagementService.class);
        DoctorProfileService doctorProfileService = mock(DoctorProfileService.class);
        AppointmentService appointmentService = mock(AppointmentService.class);
        ClinicTimeZoneResolver clinicTimeZoneResolver = mock(ClinicTimeZoneResolver.class);
        PublicCatalogFacade facade = new PublicCatalogFacade(
                tenantService,
                clinicProfileService,
                tenantUserManagementService,
                doctorProfileService,
                appointmentService,
                clinicTimeZoneResolver
        );

        UUID tenantId = UUID.randomUUID();
        when(tenantService.list()).thenReturn(List.of(tenant(tenantId, "sunrise", "ACTIVE", true)));
        when(clinicProfileService.findByTenantId(tenantId)).thenReturn(Optional.of(clinic(tenantId, "Sunrise Clinic", true, true, "", "Pune", "Baner")));
        when(tenantUserManagementService.list(tenantId)).thenReturn(List.of(
                doctorUser(UUID.randomUUID(), tenantId, "Dr. Hidden", "ACTIVE", "ACTIVE")
        ));
        when(doctorProfileService.findByDoctorUserId(any(), any())).thenReturn(Optional.of(
                doctorProfile(tenantId, UUID.randomUUID(), "Dermatology", true, false, "", 5)
        ));
        when(clinicTimeZoneResolver.resolve(any())).thenReturn(java.time.ZoneOffset.UTC);
        when(appointmentService.listSlots(any(), any(), any(), any())).thenReturn(List.of());
        when(appointmentService.listDoctorAvailabilities(any(), any())).thenReturn(List.of());

        var clinics = facade.listClinics(null, null, null, null, null, 0, 12);
        var search = facade.search(null, null, null, null, 0, 6);

        assertThat(clinics.items()).hasSize(1);
        assertThat(clinics.items().get(0).clinicDisplayName()).isEqualTo("Sunrise Clinic");
        assertThat(clinics.items().get(0).doctorsCount()).isEqualTo(0);
        assertThat(search.clinics().items()).hasSize(1);
        assertThat(search.doctors().items()).isEmpty();
        assertThat(search.specialities()).isEmpty();
    }

    @Test
    void publicDtosDoNotExposeInternalOrPatientFields() {
        assertThat(componentNames(com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicSummaryResponse.class))
                .doesNotContain("id", "tenantId", "patientId", "email", "phone", "registrationNumber", "notes");
        assertThat(componentNames(com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse.class))
                .doesNotContain("id", "tenantId", "patientId", "email", "mobile", "registrationNumber", "consultationRoom", "notes");
        assertThat(componentNames(com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorDetailResponse.class))
                .doesNotContain("tenantId", "patientId", "email", "mobile", "registrationNumber", "consultationRoom", "notes");
    }

    private List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private PlatformTenantRecord tenant(UUID id, String code, String status, boolean publicListingEnabled) {
        return new PlatformTenantRecord(
                id,
                code,
                code,
                "TRIAL",
                status,
                publicListingEnabled,
                new TenantModulesRecord(true, false, false, false, false, false, false, false, false, false),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private ClinicProfileRecord clinic(
            UUID tenantId,
            String name,
            boolean active,
            boolean publicListingEnabled,
            String slug,
            String city,
            String area
    ) {
        return new ClinicProfileRecord(
                UUID.randomUUID(),
                tenantId,
                name,
                name,
                null,
                null,
                area,
                null,
                city,
                "Maharashtra",
                "India",
                "411001",
                null,
                null,
                null,
                active,
                publicListingEnabled,
                slug,
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

    private DoctorProfileRecord doctorProfile(
            UUID tenantId,
            UUID doctorUserId,
            String specialization,
            boolean active,
            boolean publicListingEnabled,
            String slug,
            Integer yearsOfExperience
    ) {
        return new DoctorProfileRecord(
                UUID.randomUUID(),
                tenantId,
                doctorUserId,
                null,
                specialization,
                "MBBS",
                null,
                null,
                null,
                yearsOfExperience,
                40,
                active,
                publicListingEnabled,
                slug,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private DoctorAvailabilitySlotRecord slot(UUID doctorUserId, LocalDate date, LocalTime time, boolean selectable) {
        return new DoctorAvailabilitySlotRecord(
                doctorUserId,
                "Doctor",
                date,
                time,
                time.plusMinutes(15),
                DoctorAvailabilitySlotStatus.AVAILABLE,
                0,
                1,
                selectable,
                DoctorAvailabilitySlotTimeState.FUTURE,
                false,
                false,
                selectable,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private DoctorAvailabilityRecord availability(UUID tenantId, UUID doctorUserId, DayOfWeek dayOfWeek, LocalTime start, LocalTime end) {
        return new DoctorAvailabilityRecord(
                UUID.randomUUID(),
                tenantId,
                doctorUserId,
                "Doctor",
                dayOfWeek,
                start,
                end,
                null,
                null,
                15,
                1,
                true,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }
}
