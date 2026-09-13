package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AppointmentLookupFilter;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AppointmentLookupStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ValuePatch;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiAppointmentOption;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAuthorizedClinicResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AivaV2AppointmentLookupToolTest {
    private PatientPortalService patientPortalService;
    private AivaV2AppointmentLookupTool tool;

    @BeforeEach
    void setUp() {
        patientPortalService = mock(PatientPortalService.class);
        tool = new AivaV2AppointmentLookupTool(patientPortalService);
    }

    @Test
    void filtersAuthorizedUpcomingAppointmentsAndReturnsNextOnly() {
        LocalDate date = LocalDate.of(2026, 9, 14);
        when(patientPortalService.careAiUpcomingAppointmentsAcrossAuthorizedClinics()).thenReturn(List.of(
                appointment("Akshu Kumar", date, LocalTime.of(17, 30)),
                appointment("Other Doctor", date, LocalTime.of(18, 0))));

        var result = tool.lookup(new AppointmentLookupFilter(ValuePatch.set("Dr Akshu"),
                ValuePatch.set(date.toString()), ValuePatch.unchanged(), true), date);

        assertThat(result.status()).isEqualTo(AppointmentLookupStatus.FOUND);
        assertThat(result.appointments()).hasSize(1);
        assertThat(result.appointments().get(0).doctorDisplayName()).isEqualTo("Akshu Kumar");
    }

    @Test
    void returnsNoneWhenAuthorizedResultDoesNotMatchFilters() {
        when(patientPortalService.careAiUpcomingAppointmentsAcrossAuthorizedClinics()).thenReturn(List.of(
                appointment("Other Doctor", LocalDate.of(2026, 9, 14), LocalTime.of(18, 0))));

        var result = tool.lookup(new AppointmentLookupFilter(ValuePatch.set("Akshu"),
                ValuePatch.unchanged(), ValuePatch.unchanged(), false), null);

        assertThat(result.status()).isEqualTo(AppointmentLookupStatus.NONE);
        assertThat(result.appointments()).isEmpty();
    }

    @Test
    void explicitNonexistentDoctorNeverFallsBackToAuthorizedAppointments() {
        LocalDate date = LocalDate.of(2026, 9, 23);
        when(patientPortalService.careAiUpcomingAppointmentsAcrossAuthorizedClinics()).thenReturn(List.of(
                appointment("Akshu Kumar", date, LocalTime.of(20, 0))));

        var result = tool.lookup(new AppointmentLookupFilter(ValuePatch.set("Dr ABC"),
                ValuePatch.unchanged(), ValuePatch.unchanged(), false), null);

        assertThat(result.status()).isEqualTo(AppointmentLookupStatus.NONE);
        assertThat(result.appointments()).isEmpty();
    }

    @Test
    void statusNoneMeansNoStatusFilter() {
        LocalDate date = LocalDate.of(2026, 9, 14);
        when(patientPortalService.careAiUpcomingAppointmentsAcrossAuthorizedClinics()).thenReturn(List.of(
                appointment("Akshu Kumar", date, LocalTime.of(17, 30))));

        var result = tool.lookup(new AppointmentLookupFilter(ValuePatch.unchanged(),
                ValuePatch.unchanged(), ValuePatch.set("NONE"), null, true), date);

        assertThat(result.status()).isEqualTo(AppointmentLookupStatus.FOUND);
        assertThat(result.appointments()).hasSize(1);
    }

    @Test
    void searchesAuthorizedClinicsAndAppliesExplicitClinicFilter() {
        LocalDate date = LocalDate.of(2026, 9, 14);
        UUID clinicTenant = UUID.randomUUID();
        when(patientPortalService.careAiUpcomingAppointmentsAcrossAuthorizedClinics()).thenReturn(List.of(
                new PatientPortalCareAiAppointmentOption(UUID.randomUUID(), UUID.randomUUID(), "Akshu Kumar",
                        clinicTenant, "Jeevanam Automation Lab", date, LocalTime.of(17, 30), "CONFIRMED", null),
                appointment("Other Doctor", date, LocalTime.of(18, 0), "Other Clinic")));
        when(patientPortalService.clinics()).thenReturn(List.of(
                new PatientPortalAuthorizedClinicResponse(
                        clinicTenant, "newauto", "NewAuto Lab", UUID.randomUUID(), "Patient", true, false, "CARE")));

        var result = tool.lookup(new AppointmentLookupFilter(ValuePatch.unchanged(),
                ValuePatch.unchanged(), ValuePatch.unchanged(), ValuePatch.set("NewAuto Lab"), false), null);

        assertThat(result.status()).isEqualTo(AppointmentLookupStatus.FOUND);
        assertThat(result.appointments()).extracting(AivaV2Models.AppointmentSummary::clinicDisplayName)
                .containsExactly("Jeevanam Automation Lab");
    }

    @Test
    void canonicalClinicNameAndTenantCodeRemainSupported() {
        UUID clinicTenant = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 14);
        when(patientPortalService.careAiUpcomingAppointmentsAcrossAuthorizedClinics()).thenReturn(List.of(
                new PatientPortalCareAiAppointmentOption(UUID.randomUUID(), UUID.randomUUID(), "Akshu Kumar",
                        clinicTenant, "Jeevanam Automation Lab", date, LocalTime.of(17, 30), "CONFIRMED", null)));
        when(patientPortalService.clinics()).thenReturn(List.of(new PatientPortalAuthorizedClinicResponse(
                clinicTenant, "newauto", "Jeevanam Automation Lab", UUID.randomUUID(), "NewAuto Lab", true, false, "CARE")));

        var canonical = tool.lookup(new AppointmentLookupFilter(ValuePatch.unchanged(), ValuePatch.unchanged(),
                ValuePatch.unchanged(), ValuePatch.set("Jeevanam Automation Lab"), false), null);
        var code = tool.lookup(new AppointmentLookupFilter(ValuePatch.unchanged(), ValuePatch.unchanged(),
                ValuePatch.unchanged(), ValuePatch.set("newauto"), false), null);

        assertThat(canonical.appointments()).hasSize(1);
        assertThat(code.appointments()).hasSize(1);
    }

    @Test
    void unauthorizedClinicAliasDoesNotResolve() {
        LocalDate date = LocalDate.of(2026, 9, 14);
        when(patientPortalService.careAiUpcomingAppointmentsAcrossAuthorizedClinics()).thenReturn(List.of(
                appointment("Akshu Kumar", date, LocalTime.of(17, 30), "Jeevanam Automation Lab")));
        when(patientPortalService.clinics()).thenReturn(List.of(new PatientPortalAuthorizedClinicResponse(
                UUID.randomUUID(), "newauto", "Jeevanam Automation Lab", UUID.randomUUID(), "NewAuto Lab", true, false, "CARE")));

        var result = tool.lookup(new AppointmentLookupFilter(ValuePatch.unchanged(), ValuePatch.unchanged(),
                ValuePatch.unchanged(), ValuePatch.set("Unauthorized Clinic"), false), null);

        assertThat(result.status()).isEqualTo(AppointmentLookupStatus.NONE);
        assertThat(result.appointments()).isEmpty();
    }

    @Test
    void duplicateAuthorizedAliasRetainsAllMatchingTenantIdentitiesWithoutChoosingOne() {
        LocalDate date = LocalDate.of(2026, 9, 14);
        UUID firstTenant = UUID.randomUUID();
        UUID secondTenant = UUID.randomUUID();
        when(patientPortalService.careAiUpcomingAppointmentsAcrossAuthorizedClinics()).thenReturn(List.of(
                new PatientPortalCareAiAppointmentOption(UUID.randomUUID(), UUID.randomUUID(), "Doctor One",
                        firstTenant, "Clinic One", date, LocalTime.of(9, 0), "CONFIRMED", null),
                new PatientPortalCareAiAppointmentOption(UUID.randomUUID(), UUID.randomUUID(), "Doctor Two",
                        secondTenant, "Clinic Two", date, LocalTime.of(10, 0), "CONFIRMED", null)));
        when(patientPortalService.clinics()).thenReturn(List.of(
                new PatientPortalAuthorizedClinicResponse(firstTenant, "clinic-one", "Clinic One", UUID.randomUUID(),
                        "Shared Clinic", true, false, "CARE"),
                new PatientPortalAuthorizedClinicResponse(secondTenant, "clinic-two", "Clinic Two", UUID.randomUUID(),
                        "Shared Clinic", true, false, "CARE")));

        var result = tool.lookup(new AppointmentLookupFilter(ValuePatch.unchanged(), ValuePatch.unchanged(),
                ValuePatch.unchanged(), ValuePatch.set("Shared Clinic"), false), null);

        assertThat(result.status()).isEqualTo(AppointmentLookupStatus.FOUND);
        assertThat(result.appointments()).hasSize(2);
    }

    @Test
    void deduplicatesRepeatedAuthorizedAppointmentReferences() {
        LocalDate date = LocalDate.of(2026, 9, 14);
        UUID appointmentId = UUID.randomUUID();
        var first = new PatientPortalCareAiAppointmentOption(appointmentId, UUID.randomUUID(), "Akshu Kumar",
                UUID.randomUUID(), "NewAuto Lab", date, LocalTime.of(17, 30), "CONFIRMED", null);
        var duplicate = new PatientPortalCareAiAppointmentOption(appointmentId, first.doctorUserId(), "Akshu Kumar",
                first.tenantId(), "NewAuto Lab", date, LocalTime.of(17, 30), "CONFIRMED", null);
        when(patientPortalService.careAiUpcomingAppointmentsAcrossAuthorizedClinics()).thenReturn(List.of(first, duplicate));

        var result = tool.lookup(AppointmentLookupFilter.empty(), null);

        assertThat(result.status()).isEqualTo(AppointmentLookupStatus.FOUND);
        assertThat(result.appointments()).hasSize(1);
    }

    private PatientPortalCareAiAppointmentOption appointment(String doctor, LocalDate date, LocalTime time) {
        return appointment(doctor, date, time, "Clinic");
    }

    private PatientPortalCareAiAppointmentOption appointment(String doctor, LocalDate date, LocalTime time,
                                                             String clinic) {
        return new PatientPortalCareAiAppointmentOption(UUID.randomUUID(), UUID.randomUUID(), doctor,
                UUID.randomUUID(), clinic, date, time, "CONFIRMED", null);
    }
}
