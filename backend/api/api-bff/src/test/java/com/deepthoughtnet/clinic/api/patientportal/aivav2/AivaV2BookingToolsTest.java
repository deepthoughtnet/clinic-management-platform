package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiDoctorOption;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingDraft;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCapabilities;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ResolutionStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.DraftStatus;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorAvailabilityResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorSlotResponse;
import com.deepthoughtnet.clinic.api.publicsite.PublicCatalogFacade;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicPageResponse;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AivaV2BookingToolsTest {
    private static final UUID TENANT = UUID.fromString("720be1ee-f2ae-4dd9-b477-e05e3f97441a");
    private PatientPortalService patientPortalService;
    private PublicCatalogFacade publicCatalogFacade;
    private AivaV2BookingTools tools;

    @BeforeEach
    void setUp() {
        patientPortalService = mock(PatientPortalService.class);
        publicCatalogFacade = mock(PublicCatalogFacade.class);
        tools = new AivaV2BookingTools(patientPortalService, publicCatalogFacade,
                Clock.fixed(Instant.parse("2026-09-10T00:00:00Z"), ZoneOffset.UTC));
        RequestContextHolder.set(new RequestContext(new TenantId(TENANT), UUID.randomUUID(), "patient",
                Set.of("PATIENT"), "PATIENT", "corr-v2"));
        when(publicCatalogFacade.listDoctors(nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class), nullable(String.class), nullable(String.class), nullable(java.math.BigDecimal.class),
                nullable(java.math.BigDecimal.class), nullable(Integer.class), anyInt(), anyInt()))
                .thenReturn(new PublicPageResponse<>(List.of(), 0, 50, 0, 0));
    }

    @AfterEach
    void tearDown() { RequestContextHolder.clear(); }

    @Test
    void exactCareDoctorResolvesToAuthoritativeHandle() {
        when(patientPortalService.careAiDoctorsAcrossAuthorizedClinics()).thenReturn(List.of(careDoctor()));

        var result = tools.resolveBookingProvider("Dr Akshu Kumar", null);

        assertThat(result.status()).isEqualTo(ResolutionStatus.RESOLVED);
        assertThat(result.resolvedProvider().doctorId()).isEqualTo("doctor-1");
        assertThat(result.resolvedProvider().source()).isEqualTo(ProviderSource.CARE_PRIVATE);
    }

    @Test
    void akshayNeverSilentlyBecomesAkshu() {
        when(patientPortalService.careAiDoctorsAcrossAuthorizedClinics()).thenReturn(List.of(careDoctor()));

        var result = tools.resolveBookingProvider("Akshay Kumar", null);

        assertThat(result.status()).isEqualTo(ResolutionStatus.SUGGESTION);
        assertThat(result.resolvedProvider()).isNull();
    }

    @Test
    void sameDoctorAcrossAuthorizedClinicsIsAmbiguous() {
        UUID otherTenant = UUID.randomUUID();
        when(patientPortalService.careAiDoctorsAcrossAuthorizedClinics()).thenReturn(List.of(
                careDoctor(), new PatientPortalCareAiDoctorOption("doctor-2", "Doc Akshu Kumar", "General Medicine",
                        UUID.randomUUID(), UUID.randomUUID(), otherTenant, "automation-lab", "Automation Lab")));

        var result = tools.resolveBookingProvider("Akshu Kumar", null);

        assertThat(result.status()).isEqualTo(ResolutionStatus.AMBIGUOUS);
        assertThat(result.resolvedProvider()).isNull();
        assertThat(result.candidates()).hasSize(2);
    }

    @Test
    void explicitClinicSelectsOnlyAuthorizedClinicCandidate() {
        UUID otherTenant = UUID.randomUUID();
        when(patientPortalService.careAiDoctorsAcrossAuthorizedClinics()).thenReturn(List.of(
                careDoctor(), new PatientPortalCareAiDoctorOption("doctor-2", "Doc Akshu Kumar", "General Medicine",
                        UUID.randomUUID(), UUID.randomUUID(), otherTenant, "automation-lab", "Automation Lab")));

        var result = tools.resolveBookingProvider("Akshu Kumar", null, "Automation Lab");

        assertThat(result.status()).isEqualTo(ResolutionStatus.RESOLVED);
        assertThat(result.resolvedProvider().tenantId()).isEqualTo(otherTenant.toString());
    }

    @Test
    void callToBookProviderIsResolvedButHasNoLiveAvailabilityCapability() {
        when(patientPortalService.careAiDoctorsAcrossAuthorizedClinics()).thenReturn(List.of());
        when(publicCatalogFacade.listDoctors(nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class), nullable(String.class), nullable(String.class), nullable(java.math.BigDecimal.class),
                nullable(java.math.BigDecimal.class), nullable(Integer.class), anyInt(), anyInt()))
                .thenReturn(new PublicPageResponse<>(List.of(publicDoctor("public-1", "Dr Arjun Mehta", "CALL_TO_BOOK", false)), 0, 50, 1, 1));

        var result = tools.resolveBookingProvider("Arjun Mehta", null);

        assertThat(result.status()).isEqualTo(ResolutionStatus.RESOLVED);
        assertThat(result.resolvedProvider().capabilities().callToBook()).isTrue();
        assertThat(result.resolvedProvider().capabilities().liveAvailability()).isFalse();
    }

    @Test
    void availabilityUsesRealPatientPortalContractAndStableSlotReference() {
        ProviderCandidate provider = new ProviderCandidate("provider", "provider", "doctor-1", null,
                TENANT.toString(), null, null, "Doc Akshu Kumar", "General Medicine", null,
                ProviderSource.CARE_PRIVATE, new ProviderCapabilities(true, true, false, false, false, true, false));
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), UUID.randomUUID(), TENANT.toString(), provider,
                null, LocalDate.parse("2026-09-11"), null, null, null, null,
                AivaV2Models.DraftStatus.COLLECTING, 3, Instant.parse("2026-09-10T01:00:00Z"), null);
        when(patientPortalService.doctorAvailability(null, "doctor-1", null, TENANT.toString(), null,
                LocalDate.parse("2026-09-11"))).thenReturn(new PatientPortalDoctorAvailabilityResponse(
                LocalDate.parse("2026-09-11"), List.of(new PatientPortalDoctorSlotResponse(
                "care-slot-stable", LocalDate.parse("2026-09-11"), LocalTime.of(16, 30),
                LocalTime.of(17, 0), "AVAILABLE", true)), List.of()));

        var result = tools.getBookingAvailability(draft);

        assertThat(result.category()).isEqualTo("SUCCESS");
        assertThat(result.value().slots()).extracting(AivaV2Models.AvailabilitySlot::slotReference)
                .containsExactly("care-slot-stable");
    }

    private PatientPortalCareAiDoctorOption careDoctor() {
        return new PatientPortalCareAiDoctorOption("doctor-1", "Doc Akshu Kumar", "General Medicine",
                UUID.randomUUID(), UUID.randomUUID(), TENANT, "demo-clinic", "Demo Clinic");
    }

    @Test
    void availabilityTimeConstraintFiltersAuthoritativeSlots() {
        ProviderCandidate provider = new ProviderCandidate("provider", "provider", "doctor-1", null,
                TENANT.toString(), null, null, "Doc Akshu Kumar", "General Medicine", null,
                ProviderSource.CARE_PRIVATE, new ProviderCapabilities(true, true, false, false, false, true, false));
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), UUID.randomUUID(), TENANT.toString(), provider,
                null, LocalDate.parse("2026-09-14"), null, null,
                new AivaV2Models.AvailabilityTimeConstraint(
                        AivaV2Models.AvailabilityTimeConstraintMode.AFTER, LocalTime.of(20, 0), null),
                null, null, AivaV2Models.DraftStatus.COLLECTING, 3,
                Instant.parse("2026-09-10T01:00:00Z"), null);
        when(patientPortalService.doctorAvailability(null, "doctor-1", null, TENANT.toString(), null,
                LocalDate.parse("2026-09-14"))).thenReturn(new PatientPortalDoctorAvailabilityResponse(
                LocalDate.parse("2026-09-14"), List.of(
                        new PatientPortalDoctorSlotResponse("before", LocalDate.parse("2026-09-14"), LocalTime.of(19, 30), LocalTime.of(20, 0), "AVAILABLE", true),
                        new PatientPortalDoctorSlotResponse("after", LocalDate.parse("2026-09-14"), LocalTime.of(20, 30), LocalTime.of(21, 0), "AVAILABLE", true)), List.of()));

        var result = tools.getBookingAvailability(draft);

        assertThat(result.value().slots()).extracting(AivaV2Models.AvailabilitySlot::slotReference)
                .containsExactly("after");
    }

    @Test
    void betweenConstraintUsesStrictBounds() {
        ProviderCandidate provider = new ProviderCandidate("provider", "provider", "doctor-1", null,
                TENANT.toString(), null, null, "Doc Akshu Kumar", "General Medicine", null,
                ProviderSource.CARE_PRIVATE, new ProviderCapabilities(true, true, false, false, false, true, false));
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), UUID.randomUUID(), TENANT.toString(), provider,
                null, LocalDate.parse("2026-09-14"), null, null,
                new AivaV2Models.AvailabilityTimeConstraint(
                        AivaV2Models.AvailabilityTimeConstraintMode.BETWEEN, LocalTime.of(18, 0), LocalTime.of(20, 0)),
                null, null, AivaV2Models.DraftStatus.COLLECTING, 3,
                Instant.parse("2026-09-10T01:00:00Z"), null);
        when(patientPortalService.doctorAvailability(null, "doctor-1", null, TENANT.toString(), null,
                LocalDate.parse("2026-09-14"))).thenReturn(new PatientPortalDoctorAvailabilityResponse(
                LocalDate.parse("2026-09-14"), List.of(
                        slot("lower", "18:00"), slot("inside", "18:30"), slot("upper", "20:00"),
                        slot("after", "20:30")), List.of()));

        var result = tools.getBookingAvailability(draft);

        assertThat(result.value().slots()).extracting(AivaV2Models.AvailabilitySlot::slotReference)
                .containsExactly("inside");
    }

    @Test
    void confirmationStaleReasonIdentifiesExpiryWithFixedClock() {
        ProviderCandidate provider = new ProviderCandidate("provider", "provider", "doctor-1", null,
                TENANT.toString(), null, null, "Doc Akshu Kumar", "General Medicine", null,
                ProviderSource.CARE_PRIVATE, new ProviderCapabilities(true, true, false, false, false, true, false));
        UUID draftId = UUID.randomUUID();
        UUID availabilityRequestId = UUID.randomUUID();
        BookingDraft draft = new BookingDraft(draftId, UUID.randomUUID(), TENANT.toString(), provider, null,
                LocalDate.parse("2026-09-14"), null, null, availabilityRequestId, "slot-1", DraftStatus.READY_FOR_CONFIRMATION,
                6, Instant.parse("2026-09-10T01:00:00Z"), null);
        BookingConfirmation current = new BookingConfirmation("confirmation", draftId, 6, "provider", "doctor-1",
                null, availabilityRequestId, "slot-1", draft.preferredDate(), LocalTime.of(16, 30),
                Instant.parse("2026-09-10T00:05:00Z"), "idempotency");
        BookingConfirmation expired = new BookingConfirmation("confirmation", draftId, 6, "provider", "doctor-1",
                null, availabilityRequestId, "slot-1", draft.preferredDate(), LocalTime.of(16, 30),
                Instant.parse("2026-09-09T23:59:59Z"), "idempotency");

        assertThat(tools.confirmationStaleReason(draft, current)).isEqualTo("NONE");
        assertThat(tools.confirmationStaleReason(draft, expired)).isEqualTo("EXPIRED");
    }

    private PatientPortalDoctorSlotResponse slot(String reference, String time) {
        LocalDate date = LocalDate.parse("2026-09-14");
        LocalTime startsAt = LocalTime.parse(time);
        return new PatientPortalDoctorSlotResponse(reference, date, startsAt, startsAt.plusMinutes(30),
                "AVAILABLE", true);
    }

    private PublicDoctorSummaryResponse publicDoctor(String id, String name, String mode, boolean online) {
        return new PublicDoctorSummaryResponse(id, "doctor", "/doctor", name, null, "9999999999",
                "General Medicine", 10, null, List.of(), "Baner", "Pune", mode, null, null,
                "Clinic", "clinic", false, null, null, "booking-ref", online);
    }
}
