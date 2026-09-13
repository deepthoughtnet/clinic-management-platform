package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentConfirmationResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorSlotResponse;
import com.deepthoughtnet.clinic.api.publicsite.PublicCatalogFacade;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicPageResponse;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceCategory;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceDataService;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceOptionRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class PatientPortalCareAiToolRegistryExecutionTest {
    @Mock
    private PatientPortalService patientPortalService;
    @Mock
    private PublicCatalogFacade publicCatalogFacade;
    @Mock
    private DiscoverReferenceDataService discoverReferenceDataService;

    private PatientPortalCareAiToolRegistry registry;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(patientPortalService.currentPatientId()).thenReturn(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        when(patientPortalService.currentPatientMobile()).thenReturn("9999999999");
        registry = new PatientPortalCareAiToolRegistry(
                new PatientPortalCareAiBusinessLookupService(patientPortalService, publicCatalogFacade, discoverReferenceDataService),
                patientPortalService,
                publicCatalogFacade,
                discoverReferenceDataService
        );
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void doctorFindReturnsSingleMatch() {
        when(publicCatalogFacade.listDoctors(
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<BigDecimal>isNull(),
                org.mockito.ArgumentMatchers.<BigDecimal>isNull(),
                org.mockito.ArgumentMatchers.<Integer>isNull(),
                eq(0),
                eq(24)))
                .thenReturn(new PublicPageResponse<>(List.of(doctor("doctor-akshu", "Dr Akshu Kumar", "General Medicine", "Jeevanam Automation Lab", "ONLINE_BOOKING", true)), 0, 24, 1, 1));

        PatientPortalCareAiSkillResult<List<PublicDoctorSummaryResponse>> result = registry.doctorFind().execute(
                new PatientPortalCareAiDoctorFindSkillInput("Akshu", "General Medicine", null, null, null, null)
        );

        assertThat(result.outcome()).isEqualTo(PatientPortalCareAiSkillOutcome.SUCCESS);
        assertThat(result.value()).hasSize(1);
        assertThat(result.value().getFirst().doctorDisplayName()).isEqualTo("Dr Akshu Kumar");
    }

    @Test
    void doctorFindIncludesAuthorizedPrivateCareDoctorsWhenPublicCatalogIsEmpty() {
        when(patientPortalService.doctors()).thenReturn(List.of(
                PatientPortalCareAiTestSupport.doctor("doctor-akshay", "Dr Akshay Kumar", "General Medicine")
        ));
        when(publicCatalogFacade.listDoctors(
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<BigDecimal>isNull(),
                org.mockito.ArgumentMatchers.<BigDecimal>isNull(),
                org.mockito.ArgumentMatchers.<Integer>isNull(),
                eq(0),
                eq(24)))
                .thenReturn(new PublicPageResponse<>(List.of(), 0, 24, 0, 1));

        PatientPortalCareAiSkillResult<List<PublicDoctorSummaryResponse>> result = registry.doctorFind().execute(
                new PatientPortalCareAiDoctorFindSkillInput("Akshay Kumar", "General Medicine", null, null, null, null)
        );

        assertThat(result.outcome()).isEqualTo(PatientPortalCareAiSkillOutcome.SUCCESS);
        assertThat(result.value()).hasSize(1);
        assertThat(result.value().getFirst().doctorDisplayName()).isEqualTo("Dr Akshay Kumar");
        verify(patientPortalService, org.mockito.Mockito.atLeastOnce()).doctors();
    }

    @Test
    void doctorFindExplainsWhenLocationFilterRemovesBusinessCandidates() {
        when(publicCatalogFacade.listDoctors(
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<BigDecimal>isNull(),
                org.mockito.ArgumentMatchers.<BigDecimal>isNull(),
                org.mockito.ArgumentMatchers.<Integer>isNull(),
                eq(0),
                eq(24)))
                .thenReturn(new PublicPageResponse<>(
                        List.of(doctor("doctor-akshu", "Dr Akshu Kumar", "General Medicine", "Jeevanam Automation Lab", "ONLINE_BOOKING", true)),
                        0, 24, 1, 1));

        PatientPortalCareAiSkillResult<List<PublicDoctorSummaryResponse>> result = registry.doctorFind().execute(
                new PatientPortalCareAiDoctorFindSkillInput(null, null, null, "Mumbai", null, null)
        );

        assertThat(result.outcome()).isEqualTo(PatientPortalCareAiSkillOutcome.NO_MATCH);
        assertThat(result.message()).isEqualTo("No doctors matched the requested location.");
    }

    @Test
    void clinicFindReturnsMultipleMatches() {
        when(publicCatalogFacade.listClinics(
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<String>nullable(String.class),
                org.mockito.ArgumentMatchers.<BigDecimal>isNull(),
                org.mockito.ArgumentMatchers.<BigDecimal>isNull(),
                org.mockito.ArgumentMatchers.<Integer>isNull(),
                eq(1),
                eq(24)))
                .thenReturn(new PublicPageResponse<>(
                        List.of(
                                clinic("cura-demo-clinic", "Cura Demo Clinic", "Baner", "Pune"),
                                clinic("jeevanam-demo-clinic", "Jeevanam Demo Clinic", "Baner", "Pune")
                        ),
                        0,
                        24,
                        2,
                        1
                ));

        PatientPortalCareAiSkillResult<List<PublicClinicSummaryResponse>> result = registry.clinicFind().execute(
                new PatientPortalCareAiClinicFindSkillInput("Clinic", null, "Pune", null, null)
        );

        assertThat(result.outcome()).isEqualTo(PatientPortalCareAiSkillOutcome.MULTIPLE_MATCHES);
        assertThat(result.candidateCount()).isEqualTo(2);
    }

    @Test
    void serviceFindReturnsSingleMatch() {
        when(discoverReferenceDataService.listServices()).thenReturn(List.of(
                new DiscoverReferenceOptionRecord(UUID.randomUUID(), DiscoverReferenceCategory.SERVICE, "CONSULTATION", "Consultation", List.of(), 1, true)
        ));

        PatientPortalCareAiSkillResult<List<DiscoverReferenceOptionRecord>> result = registry.serviceFind().execute(
                new PatientPortalCareAiServiceFindSkillInput("consultation", null, null, null, null)
        );

        assertThat(result.outcome()).isEqualTo(PatientPortalCareAiSkillOutcome.SUCCESS);
        assertThat(result.value()).hasSize(1);
        assertThat(result.value().getFirst().code()).isEqualTo("CONSULTATION");
    }

    @Test
    void availabilityCheckReturnsNoMatchWhenNoSlotsExist() {
        when(patientPortalService.doctorSlots(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        PatientPortalCareAiSkillResult<List<PatientPortalDoctorSlotResponse>> result = registry.availabilityCheck().execute(
                new PatientPortalCareAiAvailabilityCheckSkillInput("booking-ref", "doctor-akshu", "cura-demo-clinic", "tenant-a", "clinic-a", LocalDate.of(2026, 9, 9))
        );

        assertThat(result.outcome()).isEqualTo(PatientPortalCareAiSkillOutcome.NO_MATCH);
        assertThat(result.value()).isNull();
    }

    @Test
    void appointmentCheckReturnsUpcomingAppointments() {
        when(patientPortalService.careAiUpcomingAppointments()).thenReturn(List.of(
                new PatientPortalCareAiAppointmentOption(UUID.randomUUID(), UUID.randomUUID(), "Doc Akshu Kumar", UUID.randomUUID(), "Jeevanam Automation Lab", LocalDate.of(2026, 9, 9), LocalTime.of(10, 30), "BOOKED", "Review visit")
        ));

        PatientPortalCareAiSkillResult<List<PatientPortalCareAiAppointmentOption>> result = registry.appointmentCheck().execute(
                new PatientPortalCareAiAppointmentCheckSkillInput("11111111-1111-1111-1111-111111111111", "9999999999")
        );

        assertThat(result.outcome()).isEqualTo(PatientPortalCareAiSkillOutcome.SUCCESS);
        assertThat(result.value()).hasSize(1);
        verify(patientPortalService).careAiUpcomingAppointments();
        verify(patientPortalService, never()).debugAppointments();
    }

    @Test
    void appointmentBookRequiresConfirmation() {
        PatientPortalCareAiSkillResult<PatientPortalAppointmentConfirmationResponse> result = registry.appointmentBook().execute(
                new PatientPortalCareAiAppointmentBookSkillInput(
                        "doctor-akshu",
                        "cura-demo-clinic",
                        "tenant-a",
                        "clinic-a",
                        "booking-ref",
                        "ONLINE_BOOKING",
                        LocalDate.of(2026, 9, 9),
                        LocalTime.of(10, 30),
                        "Review visit",
                        false
                )
        );

        assertThat(result.outcome()).isEqualTo(PatientPortalCareAiSkillOutcome.NEEDS_CONFIRMATION);
        verify(patientPortalService, never()).bookAppointment(any());
    }

    @Test
    void appointmentBookRejectsCallToBookProviders() {
        PatientPortalCareAiSkillResult<PatientPortalAppointmentConfirmationResponse> result = registry.appointmentBook().execute(
                new PatientPortalCareAiAppointmentBookSkillInput(
                        "doctor-arjun",
                        "cura-demo-clinic",
                        "tenant-a",
                        "clinic-a",
                        "booking-ref",
                        "CALL_TO_BOOK",
                        LocalDate.of(2026, 9, 9),
                        LocalTime.of(10, 30),
                        "Review visit",
                        true
                )
        );

        assertThat(result.outcome()).isEqualTo(PatientPortalCareAiSkillOutcome.NOT_BOOKABLE);
        verify(patientPortalService, never()).bookAppointment(any());
    }

    @Test
    void appointmentCancelExecutesOnceWhenConfirmed() {
        UUID appointmentId = UUID.randomUUID();
        when(patientPortalService.cancelAppointment(eq(appointmentId), anyString(), anyString()))
                .thenReturn(new PatientPortalAppointmentConfirmationResponse(LocalDate.of(2026, 9, 10), LocalTime.of(11, 0), "Doc", "Clinic", "patient-portal", "CANCELLED", null, "Cancelled"));

        PatientPortalCareAiSkillResult<PatientPortalAppointmentConfirmationResponse> result = registry.appointmentCancel().execute(
                new PatientPortalCareAiAppointmentCancelSkillInput(appointmentId, true, "cancel-key")
        );

        assertThat(result.outcome()).isEqualTo(PatientPortalCareAiSkillOutcome.SUCCESS);
        verify(patientPortalService).cancelAppointment(eq(appointmentId), anyString(), anyString());
    }

    @Test
    void appointmentRescheduleExecutesOnceWhenConfirmed() {
        UUID appointmentId = UUID.randomUUID();
        when(patientPortalService.rescheduleAppointment(eq(appointmentId), any(LocalDate.class), any(LocalTime.class), anyString(), anyString()))
                .thenReturn(new PatientPortalAppointmentConfirmationResponse(LocalDate.of(2026, 9, 12), LocalTime.of(12, 0), "Doc", "Clinic", "patient-portal", "RESCHEDULED", null, "Rescheduled"));

        PatientPortalCareAiSkillResult<PatientPortalAppointmentConfirmationResponse> result = registry.appointmentReschedule().execute(
                new PatientPortalCareAiAppointmentRescheduleSkillInput(appointmentId, LocalDate.of(2026, 9, 12), LocalTime.of(12, 0), "Follow-up review", true, "reschedule-key")
        );

        assertThat(result.outcome()).isEqualTo(PatientPortalCareAiSkillOutcome.SUCCESS);
        verify(patientPortalService).rescheduleAppointment(eq(appointmentId), any(LocalDate.class), any(LocalTime.class), anyString(), anyString());
    }

    private PublicDoctorSummaryResponse doctor(String publicDoctorId, String doctorName, String speciality, String clinicName, String bookingMode, boolean canBookOnline) {
        return new PublicDoctorSummaryResponse(
                publicDoctorId,
                "doctor-slug",
                "/api/public/doctors/doctor-slug",
                doctorName,
                null,
                null,
                speciality,
                8,
                BigDecimal.valueOf(700),
                List.of("English"),
                "Baner",
                "Pune",
                bookingMode,
                "Subtitle",
                "Summary",
                clinicName,
                "clinic-slug",
                true,
                "10:30 AM",
                BigDecimal.ZERO,
                null,
                canBookOnline
        );
    }

    private PublicClinicSummaryResponse clinic(String slug, String name, String area, String city) {
        return new PublicClinicSummaryResponse(
                slug,
                "/api/public/clinics/" + slug,
                name,
                null,
                null,
                null,
                null,
                area,
                city,
                "ONLINE_BOOKING",
                2,
                1,
                0,
                0,
                false,
                List.of("General Medicine"),
                null,
                null,
                true,
                null
        );
    }
}
