package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.*;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.LanguageAdapterRegistry;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiAppointmentOption;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentConfirmationResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorAvailabilityResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorSlotResponse;
import com.deepthoughtnet.clinic.api.publicsite.PublicCatalogFacade;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicPageResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentConfirmationResponse;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exercises locale normalization through the real gateway, kernel, and booking tool boundary. */
class AivaV2LanguageBoundaryVerticalTest {
    private static final LocalDate REFERENCE = LocalDate.of(2026, 9, 1);
    private static final LocalDate EXPECTED = LocalDate.of(2026, 9, 24);
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC);
    private PatientPortalService patientPortal;
    private AiOrchestrationService semanticProvider;
    private LanguageAdapterRegistry registry;
    private AiAivaV2ConversationDecisionGateway gateway;
    private AivaV2BookingTools tools;
    private AivaV2TransactionalKernel kernel;
    private final AivaResponseRenderer renderer = new AivaResponseRenderer();
    private UUID tenantUuid;
    private String tenant;
    private UUID patient;
    private ProviderCandidate provider;

    @BeforeEach
    void setUp() {
        tenantUuid = UUID.randomUUID();
        tenant = tenantUuid.toString();
        patient = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(new TenantId(tenantUuid), UUID.randomUUID(),
                "patient", Set.of("PATIENT"), "PATIENT", "aiva-language-vertical"));
        semanticProvider = mock(AiOrchestrationService.class);
        ClinicTimeZoneResolver zoneResolver = mock(ClinicTimeZoneResolver.class);
        when(zoneResolver.resolve(any())).thenReturn(ZONE);
        registry = LanguageAdapterRegistry.defaults();
        gateway = new AiAivaV2ConversationDecisionGateway(semanticProvider, new ObjectMapper(), zoneResolver,
                clock, registry);
        patientPortal = mock(PatientPortalService.class);
        when(patientPortal.doctorAvailability(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            LocalDate date = invocation.getArgument(5);
            return new PatientPortalDoctorAvailabilityResponse(date, List.of(
                    new PatientPortalDoctorSlotResponse("fixture-20-30", date, LocalTime.of(20, 30),
                            LocalTime.of(21, 0), "AVAILABLE", true)), List.of());
        });
        tools = new AivaV2BookingTools(patientPortal, mock(PublicCatalogFacade.class), zoneResolver, clock);
        kernel = new AivaV2TransactionalKernel(tools, clock);
        provider = new ProviderCandidate("fixture-provider", "provider-handle", "doctor-akshu", null,
                tenant, null, null, "Doc Akshu Kumar", "General Medicine", "Clinic",
                ProviderSource.CARE_PRIVATE, new ProviderCapabilities(true, true, false, false, false, true, false));
    }

    @AfterEach
    void tearDown() { RequestContextHolder.clear(); }

    @Test
    void equivalentDatesReachRealAvailabilityToolAsTheSameLocalDate() {
        for (String[] input : new String[][]{{"en", "24 September"}, {"en", "show slots on 24 September"}, {"hi", "24 September"},
                {"hi", "24 सितंबर"}, {"hi", "24 September ki slots dikhao"},
                {"hi", "24 सितंबर की स्लॉट्स दिखाओ"}}) {
            org.mockito.Mockito.clearInvocations(patientPortal);
            SessionProjection session = session();
            var turn = registry.resolve(input[0], input[1]).normalize(input[1], REFERENCE);
            assertThat(turn.temporal().localDate()).as(input[1]).isEqualTo(EXPECTED);
            ConversationDecision decision = gateway.decide(turn, session);
            assertThat(decision.bookingPatch().dateExpression().value()).as(input[1]).isEqualTo("2026-09-24");
            AivaV2TemporalResolution temporal = new AivaV2TemporalNormalizer(clock, ZONE)
                    .normalize(turn.temporal(), decision.bookingPatch().dateExpression(), null);
            KernelResultHolder result = runKernel(session, decision, temporal, input[1]);
            assertThat(result.session().latestAvailabilityResult().date()).as(input[1]).isEqualTo(EXPECTED);
            assertThat(result.session().activeDraft().preferredDate()).as(input[1]).isEqualTo(EXPECTED);
            assertThat(result.response().structuredResponse().type()).as(input[1])
                    .isEqualTo(AivaStructuredResponse.ResponseType.AVAILABLE_SLOTS);
            String rendered = renderer.render(result.response(), turn.responseLanguage(), turn.responseStyle()).assistantMessage();
            if ("hi".equals(turn.responseLanguage()) && turn.responseStyle() == com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.STANDARD) {
                assertThat(rendered).contains("24 सितंबर 2026 के लिए उपलब्ध स्लॉट");
            } else if (turn.responseLanguage().equals("hi")) {
                assertThat(rendered).contains("24 September 2026").contains("Aapke liye");
            } else {
                assertThat(rendered).contains("Available slots for 24 September 2026");
            }
            verify(patientPortal).doctorAvailability(null, "doctor-akshu", null, tenant, null, EXPECTED);
        }
        org.mockito.Mockito.verifyNoInteractions(semanticProvider);
    }

    @Test
    void fullBookingConversationUsesRealAdapterGatewayKernelToolsAndRendererInAllStyles() {
        for (String[] input : new String[][]{{"en", "19:00 works"}, {"hi", "19:00 wali theek hai"},
                {"hi", "19:00 वाली ठीक है।"}, {"en", "no, show me other slots"},
                {"hi", "nahi, dusri slots dikhao"}, {"hi", "नहीं, दूसरी स्लॉट्स दिखाओ।"}}) {
            var turn = registry.resolve(input[0], input[1]).normalize(input[1], REFERENCE);
            if (input[1].contains("19:00")) assertThat(turn.exactTime()).as(input[1]).isEqualTo(LocalTime.of(19, 0));
            else assertThat(turn.confirmation()).as(input[1]).isEqualTo(NormalizedUserTurn.Confirmation.NEGATIVE);
        }
        when(patientPortal.currentPatientId()).thenReturn(patient);
        PublicCatalogFacade catalog = mock(PublicCatalogFacade.class);
        when(catalog.listDoctors(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PublicPageResponse<>(List.of(new PublicDoctorSummaryResponse("doctor-akshu",
                        "akshu", "/doctors/akshu", "Doc Akshu Kumar", null, "000", "General Medicine",
                        12, BigDecimal.valueOf(500), List.of("Hindi", "English"), "Area", "City",
                        "ONLINE_BOOKING", null, null, "Clinic", "clinic", true, null, null,
                        "BOOK-1", true)), 0, 50, 1, 1));
        when(patientPortal.doctorAvailability(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            LocalDate date = invocation.getArgument(5);
            return new PatientPortalDoctorAvailabilityResponse(date, List.of(
                    slot(date, "slot-17", "17:00"), slot(date, "slot-18", "18:00"),
                    slot(date, "slot-19", "19:00"), slot(date, "slot-1930", "19:30"),
                    slot(date, "slot-20", "20:00"), slot(date, "slot-2030", "20:30"),
                    slot(date, "slot-21", "21:00")), List.of());
        });
        when(patientPortal.bookAppointment(any())).thenReturn(new PatientPortalAppointmentConfirmationResponse(
                "APT-STAGE3", "BOOKED", EXPECTED, LocalTime.of(20, 0), "Asia/Kolkata", "Doc Akshu Kumar",
                "Clinic", null, "ONLINE", "AIVA", "BOOKED", null, null, null, null, false, null, null));
        String startBooking = "{\"schemaVersion\":\"1.0\",\"dialogAct\":\"START_REQUEST\",\"operation\":\"START_BOOKING\",\"confidence\":0.99}";
        when(semanticProvider.complete(any())).thenReturn(new AiOrchestrationResponse(UUID.randomUUID(), UUID.randomUUID(),
                AiProductCode.GENERIC, AiTaskType.GENERIC_EXTRACTION, "CONTROLLED", "fixture", startBooking,
                startBooking, BigDecimal.ONE, List.of(), List.of(), List.of(), null, 1L, false, null, "STOP"));
        AivaV2BookingTools realBookingTools = new AivaV2BookingTools(patientPortal, catalog, zoneResolver(), clock);
        AivaV2TransactionalKernel realKernel = new AivaV2TransactionalKernel(realBookingTools, clock);
        AivaV2SessionStore store = new AivaV2SessionStore(clock);
        AivaV2ConversationService service = new AivaV2ConversationService(gateway, realKernel, store,
                patientPortal, zoneResolver(), clock, renderer);

        String[][] conversations = {
                {"en", "I want to book an appointment with Dr Akshu", "24 September", "show evening slots",
                        "show more slots", "19:00 works", "no, show me other slots", "20:00 works", "yes"},
                {"hi", "Mujhe Dr Akshu ke saath appointment book karni hai", "24 September", "shaam ki slots dikhao",
                        "aur slots dikhao", "19:00 wali theek hai", "nahi, dusri slots dikhao", "20:00 wali theek hai", "haan"},
                {"hi", "मुझे डॉ. अक्षु के साथ अपॉइंटमेंट बुक करनी है।", "24 सितंबर", "शाम की स्लॉट्स दिखाओ।",
                        "और स्लॉट्स दिखाओ।", "19:00 वाली ठीक है।", "नहीं, दूसरी स्लॉट्स दिखाओ।", "20:00 वाली ठीक है।", "हाँ।"}
        };
        int bookings = 0;
        for (String[] flow : conversations) {
            MessageResponse response = service.message(new MessageRequest(null, flow[1], flow[0]));
            String conversationId = response.conversationId();
            assertThat(response.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.NEED_DATE);
            assertRenderedStyle(response, flow);
            response = service.message(new MessageRequest(conversationId, flow[2], flow[0]));
            assertThat(response.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.AVAILABLE_SLOTS);
            assertRenderedStyle(response, flow);
            assertThat(response.state().date()).isEqualTo(EXPECTED);
            response = service.message(new MessageRequest(conversationId, flow[3], flow[0]));
            assertThat(response.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.AVAILABLE_SLOTS);
            assertRenderedStyle(response, flow);
            assertThat(response.state().timeWindow()).isEqualTo("evening");
            response = service.message(new MessageRequest(conversationId, flow[4], flow[0]));
            assertThat(response.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.AVAILABLE_SLOTS);
            assertRenderedStyle(response, flow);
            response = service.message(new MessageRequest(conversationId, flow[5], flow[0]));
            assertThat(response.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.BOOKING_CONFIRMATION);
            assertRenderedStyle(response, flow);
            assertThat(response.state().exactTime()).isEqualTo(LocalTime.of(19, 0));
            response = service.message(new MessageRequest(conversationId, flow[6], flow[0]));
            assertThat(response.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.AVAILABLE_SLOTS);
            assertRenderedStyle(response, flow);
            response = service.message(new MessageRequest(conversationId, flow[7], flow[0]));
            assertThat(response.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.BOOKING_CONFIRMATION);
            assertRenderedStyle(response, flow);
            assertThat(response.state().exactTime()).isEqualTo(LocalTime.of(20, 0));
            response = service.message(new MessageRequest(conversationId, flow[8], flow[0]));
            assertThat(response.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.BOOKING_SUCCESS);
            assertRenderedStyle(response, flow);
            assertThat(response.state().appointmentReference()).isEqualTo("APT-STAGE3");
            if ("en".equals(flow[0])) assertThat(response.assistantMessage()).isEqualTo("Your appointment is booked.");
            if ("hi".equals(flow[0]) && flow[1].startsWith("Mujhe")) assertThat(response.assistantMessage()).isEqualTo("Aapki appointment book ho gayi hai.");
            if (flow[1].startsWith("मुझे")) assertThat(response.assistantMessage()).isEqualTo("आपकी अपॉइंटमेंट बुक हो गई है।");
            bookings++;
        }
        assertThat(bookings).isEqualTo(3);
        verify(patientPortal, org.mockito.Mockito.times(3)).bookAppointment(any());
    }

    @Test
    void lookupConversationUsesRealServiceToolStructuredMappingAndRendererInAllStyles() {
        when(patientPortal.currentPatientId()).thenReturn(patient);
        LocalDate appointmentDate = LocalDate.of(2026, 9, 23);
        UUID appointmentId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        when(patientPortal.careAiUpcomingAppointmentsAcrossAuthorizedClinics()).thenReturn(List.of(
                new PatientPortalCareAiAppointmentOption(appointmentId, doctorId, "Doc Akshu Kumar", tenantUuid,
                        "Clinic", appointmentDate, LocalTime.of(20, 0), "CONFIRMED", null)));
        String lookup = "{\"schemaVersion\":\"1.0\",\"dialogAct\":\"ASK_QUESTION\",\"operation\":\"LOOKUP_APPOINTMENTS\",\"confidence\":0.99}";
        when(semanticProvider.complete(any())).thenReturn(orchestrationResponse(lookup));
        AivaV2BookingTools realBookingTools = new AivaV2BookingTools(patientPortal, mock(PublicCatalogFacade.class), zoneResolver(), clock);
        AivaV2TransactionalKernel realKernel = new AivaV2TransactionalKernel(realBookingTools,
                new AivaV2AppointmentLookupTool(patientPortal), clock);
        AivaV2ConversationService service = new AivaV2ConversationService(gateway, realKernel,
                new AivaV2SessionStore(clock), patientPortal, zoneResolver(), clock, renderer);
        String[][] equivalentTurns = {
                {"en", "What appointments do I have?", "Do I have an appointment with Dr Akshu?", "Do I have an appointment with Dr Nonexistent?"},
                {"hi", "Meri appointments kya hain?", "Kya meri Dr Akshu ke saath appointment hai?", "Kya meri Dr Nonexistent ke saath appointment hai?"},
                {"hi", "मेरी अपॉइंटमेंट्स क्या हैं?", "क्या मेरी डॉ. अक्षु के साथ अपॉइंटमेंट है?", "क्या मेरी डॉ. नॉनएक्सिस्टेंट के साथ अपॉइंटमेंट है?"}
        };
        for (String[] flow : equivalentTurns) {
            MessageResponse all = service.message(new MessageRequest(null, flow[1], flow[0]));
            assertStructuredResponseRendersInAllStyles(all);
            assertThat(all.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.APPOINTMENTS_FOUND);
            var allPayload = (AivaStructuredResponse.AppointmentListPayload) all.structuredResponse().payload();
            assertThat(allPayload.appointments()).hasSize(1);
            assertThat(allPayload.appointments().get(0).providerDisplayName()).isEqualTo("Doc Akshu Kumar");
            assertThat(allPayload.appointments().get(0).date()).isEqualTo(appointmentDate);
            assertThat(allPayload.appointments().get(0).time()).isEqualTo(LocalTime.of(20, 0));

            MessageResponse found = service.message(new MessageRequest(null, flow[2], flow[0]));
            assertStructuredResponseRendersInAllStyles(found);
            assertThat(found.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.APPOINTMENTS_FOUND);
            var foundPayload = (AivaStructuredResponse.AppointmentListPayload) found.structuredResponse().payload();
            assertThat(foundPayload.appointments()).containsExactlyElementsOf(allPayload.appointments());
            assertThat(foundPayload.appliedFilters().doctor()).isEqualTo("Dr Akshu");

            MessageResponse none = service.message(new MessageRequest(null, flow[3], flow[0]));
            assertStructuredResponseRendersInAllStyles(none);
            assertThat(none.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.APPOINTMENTS_NONE);
            assertThat(((AivaStructuredResponse.AppointmentListPayload) none.structuredResponse().payload()).appointments()).isEmpty();
            if (flow[0].equals("hi") && flow[1].startsWith("मेरी")) {
                assertThat(all.assistantMessage()).contains("अपॉइंटमेंट").doesNotContain("You have", "upcoming appointments", "with", "on", "at");
                assertThat(found.assistantMessage()).contains("23 सितंबर 2026", "20:00 बजे").doesNotContain("appointment is", "You have");
                assertThat(none.assistantMessage()).contains("कोई आगामी अपॉइंटमेंट").doesNotContain("upcoming appointment");
            }
        }
        verify(patientPortal, org.mockito.Mockito.times(9)).careAiUpcomingAppointmentsAcrossAuthorizedClinics();
    }

    @Test
    void explicitLookupFactsOverrideConflictingProviderAcrossLanguages() {
        LocalDate appointmentDate = LocalDate.of(2026, 9, 24);
        UUID appointmentId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        PatientPortalService fixturePortal = mock(PatientPortalService.class);
        when(fixturePortal.currentPatientId()).thenReturn(patient);
        when(fixturePortal.careAiUpcomingAppointmentsAcrossAuthorizedClinics()).thenReturn(List.of(
                new PatientPortalCareAiAppointmentOption(appointmentId, doctorId, "Doc Akshu Kumar", tenantUuid,
                        "Clinic", appointmentDate, LocalTime.of(20, 0), "CONFIRMED", null)));
        String conflictingLookup = "{\"schemaVersion\":\"1.0\",\"dialogAct\":\"ASK_QUESTION\","
                + "\"operation\":\"LOOKUP_APPOINTMENTS\",\"lookupFilter\":{"
                + "\"doctorText\":{\"mode\":\"SET\",\"value\":\"Dr Mehta\"},"
                + "\"dateExpression\":{\"mode\":\"SET\",\"value\":\"2026-09-25\"}},\"confidence\":0.99}";
        when(semanticProvider.complete(any())).thenReturn(orchestrationResponse(conflictingLookup));
        ClinicTimeZoneResolver resolver = zoneResolver();
        AivaV2BookingTools bookingTools = new AivaV2BookingTools(fixturePortal, mock(PublicCatalogFacade.class), resolver, clock);
        AivaV2TransactionalKernel realKernel = new AivaV2TransactionalKernel(bookingTools,
                new AivaV2AppointmentLookupTool(fixturePortal), clock);
        AivaV2ConversationService service = new AivaV2ConversationService(gateway, realKernel,
                new AivaV2SessionStore(clock), fixturePortal, resolver, clock, renderer);
        String[][] turns = {
                {"en", "Do I have an appointment with Dr Akshu on 24 September?"},
                {"hi", "Kya meri Dr Akshu ke saath 24 September ki appointment hai?"},
                {"hi", "क्या मेरी डॉ. अक्षु के साथ 24 सितंबर की अपॉइंटमेंट है?"}
        };
        for (String[] input : turns) {
            MessageResponse response = service.message(new MessageRequest(null, input[1], input[0]));
            assertStructuredResponseRendersInAllStyles(response);
            assertThat(response.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.APPOINTMENTS_FOUND);
            var payload = (AivaStructuredResponse.AppointmentListPayload) response.structuredResponse().payload();
            assertThat(payload.appointments()).hasSize(1);
            assertThat(payload.appointments().get(0).appointmentReference()).isEqualTo(appointmentId.toString());
            assertThat(payload.appointments().get(0).providerDisplayName()).isEqualTo("Doc Akshu Kumar");
            assertThat(payload.appointments().get(0).date()).isEqualTo(appointmentDate);
            assertThat(payload.appliedFilters().doctor()).isEqualTo("Dr Akshu");
            assertThat(payload.appliedFilters().date()).isEqualTo(appointmentDate);
        }
    }

    @Test
    void providerAmbiguityFlowsThroughConversationServiceAndRendersAllCandidates() {
        when(patientPortal.currentPatientId()).thenReturn(patient);
        PublicCatalogFacade catalog = mock(PublicCatalogFacade.class);
        List<PublicDoctorSummaryResponse> candidates = List.of(
                publicDoctor("doctor-akshu", "Doc Akshu Kumar"),
                publicDoctor("doctor-uat", "Doc UAT Automation Doctor"),
                publicDoctor("doctor-demo", "Demo Doctor"));
        when(catalog.listDoctors(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PublicPageResponse<>(candidates, 0, 50, 1, 1));
        String start = "{\"schemaVersion\":\"1.0\",\"dialogAct\":\"START_REQUEST\",\"operation\":\"START_BOOKING\",\"confidence\":0.99}";
        when(semanticProvider.complete(any())).thenReturn(orchestrationResponse(start));
        AivaV2BookingTools realBookingTools = new AivaV2BookingTools(patientPortal, catalog, zoneResolver(), clock);
        AivaV2TransactionalKernel realKernel = new AivaV2TransactionalKernel(realBookingTools, clock);
        AivaV2ConversationService service = new AivaV2ConversationService(gateway, realKernel,
                new AivaV2SessionStore(clock), patientPortal, zoneResolver(), clock, renderer);
        for (String[] input : new String[][]{{"en", "I want to book with Dr R"},
                {"hi", "Mujhe Dr R ke saath appointment book karni hai"},
                {"hi", "मुझे डॉ. र् के साथ अपॉइंटमेंट बुक करनी है।"}}) {
            MessageResponse response = service.message(new MessageRequest(null, input[1], input[0]));
            assertStructuredResponseRendersInAllStyles(response);
            assertThat(response.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.PROVIDER_CHOICES);
            var payload = (AivaStructuredResponse.ProviderChoicesPayload) response.structuredResponse().payload();
            assertThat(payload.candidates()).extracting(AivaStructuredResponse.ProviderOption::doctorDisplayName)
                    .containsExactly("Doc Akshu Kumar", "Doc UAT Automation Doctor", "Demo Doctor");
            assertThat(response.actions()).isEmpty();
            assertThat(response.assistantMessage()).contains("1. Doc Akshu Kumar", "2. Doc UAT Automation Doctor", "3. Demo Doctor");
            assertThat(response.assistantMessage()).doesNotContain("डॉक्टर मिले: ।");
        }
    }

    @Test
    void cancellationConfirmationAndExecutionAreEquivalentAcrossLanguages() {
        LocalDate appointmentDate = LocalDate.of(2026, 9, 24);
        String cancel = "{\"schemaVersion\":\"1.0\",\"dialogAct\":\"START_REQUEST\",\"operation\":\"CANCEL_APPOINTMENT\","
                + "\"lookupFilter\":{\"doctorText\":{\"mode\":\"SET\",\"value\":\"Dr Mehta\"},"
                + "\"dateExpression\":{\"mode\":\"SET\",\"value\":\"2026-09-25\"}},\"confidence\":0.99}";
        when(semanticProvider.complete(any())).thenReturn(orchestrationResponse(cancel));
        String[][] flows = {
                {"en", "Cancel my appointment with Dr Akshu on 24 September"},
                {"hi", "Mujhe Dr Akshu ke saath 24 September ki appointment cancel karni hai"},
                {"hi", "डॉ. अक्षु के साथ मेरी 24 सितंबर की अपॉइंटमेंट रद्द करें।"}
        };
        for (String[] flow : flows) {
            PatientPortalService fixturePortal = mock(PatientPortalService.class);
            when(fixturePortal.currentPatientId()).thenReturn(patient);
            UUID sourceId = UUID.randomUUID();
            when(fixturePortal.careAiUpcomingAppointmentsAcrossAuthorizedClinics()).thenReturn(List.of(
                    new PatientPortalCareAiAppointmentOption(sourceId, UUID.randomUUID(), "Doc Akshu Kumar",
                            tenantUuid, "Clinic", appointmentDate, LocalTime.of(20, 0), "CONFIRMED", null)));
            when(fixturePortal.cancelAppointmentInOwningTenant(any(), any(), any(), any())).thenReturn(
                    new PatientPortalAppointmentConfirmationResponse(sourceId.toString(), "CANCELLED", appointmentDate,
                            LocalTime.of(20, 0), "Asia/Kolkata", "Doc Akshu Kumar", "Clinic", null,
                            "ONLINE", "AIVA", "CANCELLED", null, "Cancelled", null, null, false, null, null));
            Clock liveFixtureClock = Clock.systemUTC();
            ClinicTimeZoneResolver resolver = zoneResolver();
            AivaV2BookingTools bookingTools = new AivaV2BookingTools(fixturePortal, mock(PublicCatalogFacade.class), resolver, liveFixtureClock);
            AivaV2AppointmentLookupTool lookupTool = new AivaV2AppointmentLookupTool(fixturePortal);
            AivaV2CancellationTool cancellationTool = new AivaV2CancellationTool(fixturePortal);
            AivaV2TransactionalKernel realKernel = new AivaV2TransactionalKernel(bookingTools, lookupTool, cancellationTool, liveFixtureClock);
            AivaV2ConversationService service = new AivaV2ConversationService(gateway, realKernel,
                    new AivaV2SessionStore(liveFixtureClock), fixturePortal, resolver, liveFixtureClock, renderer);
            MessageResponse confirmation = service.message(new MessageRequest(null, flow[1], flow[0]));
            assertStructuredResponseRendersInAllStyles(confirmation);
            String conversationId = confirmation.conversationId();
            assertThat(confirmation.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.CANCELLATION_CONFIRMATION);
            var payload = (AivaStructuredResponse.CancellationConfirmationPayload) confirmation.structuredResponse().payload();
            assertThat(payload.appointment().appointmentReference()).isEqualTo(sourceId.toString());
            assertThat(payload.appointment().providerDisplayName()).isEqualTo("Doc Akshu Kumar");
            assertThat(payload.appointment().date()).isEqualTo(appointmentDate);
            assertThat(payload.appointment().time()).isEqualTo(LocalTime.of(20, 0));
            assertThat(confirmation.assistantMessage()).isNotBlank();

            MessageResponse rejected = service.message(new MessageRequest(conversationId,
                    flow[0].equals("en") ? "no" : flow[0].equals("hi") && flow[1].startsWith("Dr ") ? "nahi" : "नहीं", flow[0]));
            assertStructuredResponseRendersInAllStyles(rejected);
            assertThat(rejected.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.CANCELLATION_REJECTED);
            assertThat(rejected.structuredResponse().payload()).isInstanceOf(AivaStructuredResponse.CancellationConfirmationPayload.class);
            org.mockito.Mockito.verify(fixturePortal, org.mockito.Mockito.times(1)).careAiUpcomingAppointmentsAcrossAuthorizedClinics();
            org.mockito.Mockito.verify(fixturePortal, org.mockito.Mockito.never()).cancelAppointment(any(), any(), any());

            confirmation = service.message(new MessageRequest(conversationId, flow[1], flow[0]));
            assertStructuredResponseRendersInAllStyles(confirmation);
            assertThat(confirmation.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.CANCELLATION_CONFIRMATION);
            org.mockito.Mockito.verify(fixturePortal, org.mockito.Mockito.times(2)).careAiUpcomingAppointmentsAcrossAuthorizedClinics();
            MessageResponse success = service.message(new MessageRequest(conversationId,
                    flow[0].equals("en") ? "yes" : flow[0].equals("hi") && flow[1].startsWith("Dr ") ? "haan" : "हाँ", flow[0]));
            assertStructuredResponseRendersInAllStyles(success);
            assertThat(success.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.CANCELLATION_SUCCESS);
            var successPayload = (AivaStructuredResponse.CancellationSuccessPayload) success.structuredResponse().payload();
            assertThat(successPayload.appointment().appointmentReference()).isEqualTo(sourceId.toString());
            assertThat(successPayload.appointment().date()).isEqualTo(appointmentDate);
            assertThat(successPayload.appointment().time()).isEqualTo(LocalTime.of(20, 0));
            assertThat(success.assistantMessage()).isNotBlank();
            org.mockito.Mockito.verify(fixturePortal, org.mockito.Mockito.times(1)).cancelAppointmentInOwningTenant(any(), any(), any(), any());
            MessageResponse repeatedYes = service.message(new MessageRequest(conversationId,
                    flow[0].equals("en") ? "yes" : flow[0].equals("hi") && flow[1].startsWith("Dr ") ? "haan" : "हाँ", flow[0]));
            assertStructuredResponseRendersInAllStyles(repeatedYes);
            assertThat(repeatedYes.structuredResponse().type()).isNotEqualTo(AivaStructuredResponse.ResponseType.CANCELLATION_SUCCESS);
            org.mockito.Mockito.verify(fixturePortal, org.mockito.Mockito.times(1)).cancelAppointmentInOwningTenant(any(), any(), any(), any());
        }
    }

    @Test
    void hinglishThikHaiKarDoConfirmsThePendingCancellationWithoutProviderCall() {
        String cancel = "{\"schemaVersion\":\"1.0\",\"dialogAct\":\"START_REQUEST\","
                + "\"operation\":\"CANCEL_APPOINTMENT\",\"confidence\":0.99}";
        when(semanticProvider.complete(any())).thenReturn(orchestrationResponse(cancel));
        PatientPortalService fixturePortal = mock(PatientPortalService.class);
        when(fixturePortal.currentPatientId()).thenReturn(patient);
        UUID selectedId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 24);
        when(fixturePortal.careAiUpcomingAppointmentsAcrossAuthorizedClinics()).thenReturn(List.of(
                new PatientPortalCareAiAppointmentOption(selectedId, UUID.randomUUID(), "Doc Akshu Kumar",
                        tenantUuid, "Clinic", date, LocalTime.of(20, 30), "CONFIRMED", null)));
        when(fixturePortal.cancelAppointmentInOwningTenant(any(), any(), any(), any())).thenReturn(
                new PatientPortalAppointmentConfirmationResponse(selectedId.toString(), "CANCELLED", date,
                        LocalTime.of(20, 30), "Asia/Kolkata", "Doc Akshu Kumar", "Clinic", null,
                        "ONLINE", "AIVA", "CANCELLED", null, "Cancelled", null, null, false, null, null));
        Clock liveFixtureClock = Clock.systemUTC();
        ClinicTimeZoneResolver resolver = zoneResolver();
        AivaV2BookingTools bookingTools = new AivaV2BookingTools(fixturePortal,
                mock(PublicCatalogFacade.class), resolver, liveFixtureClock);
        AivaV2TransactionalKernel realKernel = new AivaV2TransactionalKernel(bookingTools,
                new AivaV2AppointmentLookupTool(fixturePortal), new AivaV2CancellationTool(fixturePortal), liveFixtureClock);
        AivaV2SessionStore sessionStore = new AivaV2SessionStore(liveFixtureClock);
        AivaV2ConversationService service = new AivaV2ConversationService(gateway, realKernel,
                sessionStore, fixturePortal, resolver, liveFixtureClock, renderer);

        MessageResponse confirmation = service.message(new MessageRequest(null,
                "Mujhe Dr Akshu ke saath 24 September ki appointment cancel karni hai", "hi"));
        assertThat(confirmation.structuredResponse().type())
                .isEqualTo(AivaStructuredResponse.ResponseType.CANCELLATION_CONFIRMATION);
        assertThat(((AivaStructuredResponse.CancellationConfirmationPayload) confirmation.structuredResponse()
                .payload()).appointment().appointmentReference()).isEqualTo(selectedId.toString());
        String sessionKey = tenant + "|" + patient + "|" + confirmation.conversationId();
        var pending = sessionStore.find(sessionKey).orElseThrow().pendingCancellation();
        assertThat(pending).isNotNull();
        assertThat(pending.appointmentReference()).isEqualTo(selectedId.toString());
        assertThat(pending.expiresAt()).isAfter(Instant.now());
        assertThat(sessionStore.find(sessionKey).orElseThrow().version()).isEqualTo(2L);
        org.mockito.Mockito.clearInvocations(semanticProvider);

        MessageResponse success = service.message(new MessageRequest(confirmation.conversationId(),
                "thik hai kar do", "hi"));

        assertThat(success.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.CANCELLATION_SUCCESS);
        assertThat(((AivaStructuredResponse.CancellationSuccessPayload) success.structuredResponse()
                .payload()).appointment().appointmentReference()).isEqualTo(selectedId.toString());
        org.mockito.Mockito.verifyNoInteractions(semanticProvider);
        verify(fixturePortal).cancelAppointmentInOwningTenant(org.mockito.ArgumentMatchers.eq(selectedId), any(), any(), any());
        assertThat(success.conversationId()).isEqualTo(confirmation.conversationId());
        assertThat(sessionStore.find(sessionKey).orElseThrow().pendingCancellation()).isNull();
        verify(fixturePortal, org.mockito.Mockito.times(1)).cancelAppointmentInOwningTenant(any(), any(), any(), any());
    }

    @Test
    void realSessionRetainsOrdinalSelectedCancellationCapabilityThroughPositiveConfirmation() {
        String cancelJson = "{\"schemaVersion\":\"1.0\",\"dialogAct\":\"START_REQUEST\","
                + "\"operation\":\"CANCEL_APPOINTMENT\",\"confidence\":0.99}";
        when(semanticProvider.complete(any())).thenReturn(orchestrationResponse(cancelJson));
        PatientPortalService fixturePortal = mock(PatientPortalService.class);
        when(fixturePortal.currentPatientId()).thenReturn(patient);
        LocalDate date = LocalDate.of(2026, 9, 24);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(fixturePortal.careAiUpcomingAppointmentsAcrossAuthorizedClinics()).thenReturn(List.of(
                new PatientPortalCareAiAppointmentOption(firstId, UUID.randomUUID(), "Doc Akshu Kumar", tenantUuid,
                        "Clinic", date, LocalTime.of(20, 0), "CONFIRMED", null),
                new PatientPortalCareAiAppointmentOption(secondId, UUID.randomUUID(), "Doc Akshu Kumar", tenantUuid,
                        "Clinic", date, LocalTime.of(20, 30), "CONFIRMED", null)));
        when(fixturePortal.cancelAppointmentInOwningTenant(any(), any(), any(), any())).thenReturn(
                new PatientPortalAppointmentConfirmationResponse(secondId.toString(), "CANCELLED", date,
                        LocalTime.of(20, 30), "Asia/Kolkata", "Doc Akshu Kumar", "Clinic", null,
                        "ONLINE", "AIVA", "CANCELLED", null, "Cancelled", null, null, false, null, null));
        Clock liveFixtureClock = Clock.systemUTC();
        ClinicTimeZoneResolver resolver = zoneResolver();
        AivaV2BookingTools bookingTools = new AivaV2BookingTools(fixturePortal,
                mock(PublicCatalogFacade.class), resolver, liveFixtureClock);
        AivaV2TransactionalKernel realKernel = new AivaV2TransactionalKernel(bookingTools,
                new AivaV2AppointmentLookupTool(fixturePortal), new AivaV2CancellationTool(fixturePortal), liveFixtureClock);
        AivaV2SessionStore sessionStore = new AivaV2SessionStore(liveFixtureClock);
        AivaV2ConversationService service = new AivaV2ConversationService(gateway, realKernel,
                sessionStore, fixturePortal, resolver, liveFixtureClock, renderer);

        MessageResponse choices = service.message(new MessageRequest(null,
                "डॉ. अक्षु के साथ 24 सितंबर की अपॉइंटमेंट रद्द करें।", "hi"));
        assertThat(choices.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.CANCELLATION_CHOICES);
        String sessionKey = tenant + "|" + patient + "|" + choices.conversationId();
        assertThat(sessionStore.find(sessionKey).orElseThrow().pendingCancellationResolution().candidates()).hasSize(2);
        org.mockito.Mockito.clearInvocations(semanticProvider);

        MessageResponse confirmation = service.message(new MessageRequest(choices.conversationId(), "2", "hi"));
        assertThat(confirmation.structuredResponse().type())
                .isEqualTo(AivaStructuredResponse.ResponseType.CANCELLATION_CONFIRMATION);
        var capability = sessionStore.find(sessionKey).orElseThrow().pendingCancellation();
        assertThat(capability).isNotNull();
        assertThat(capability.appointmentReference()).isEqualTo(secondId.toString());
        assertThat(capability.expiresAt()).isAfter(Instant.now());
        assertThat(confirmation.conversationId()).isEqualTo(choices.conversationId());
        org.mockito.Mockito.clearInvocations(semanticProvider);

        MessageResponse success = service.message(new MessageRequest(choices.conversationId(), "haan kar do", "hi"));

        assertThat(success.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.CANCELLATION_SUCCESS);
        assertThat(((AivaStructuredResponse.CancellationSuccessPayload) success.structuredResponse().payload())
                .appointment().appointmentReference()).isEqualTo(secondId.toString());
        assertThat(success.conversationId()).isEqualTo(choices.conversationId());
        org.mockito.Mockito.verifyNoInteractions(semanticProvider);
        verify(fixturePortal, org.mockito.Mockito.times(1)).cancelAppointmentInOwningTenant(
                org.mockito.ArgumentMatchers.eq(secondId), any(), any(), any());
        verify(fixturePortal, org.mockito.Mockito.times(1)).careAiUpcomingAppointmentsAcrossAuthorizedClinics();
        assertThat(sessionStore.find(sessionKey).orElseThrow().pendingCancellation()).isNull();
    }

    @Test
    void freshCancellationUsesOnlyTypedExactTimeAndNeverProviderInventedOrdinalAcrossLanguages() {
        String providerInventedSelection = "{\"schemaVersion\":\"1.0\",\"dialogAct\":\"START_REQUEST\","
                + "\"operation\":\"CANCEL_APPOINTMENT\",\"lookupFilter\":{"
                + "\"doctorText\":{\"mode\":\"SET\",\"value\":\"Dr Mehta\"},"
                + "\"dateExpression\":{\"mode\":\"SET\",\"value\":\"2026-09-25\"}},"
                + "\"selection\":{\"ordinal\":1,\"candidateRef\":\"not-user-selected\"},\"confidence\":0.99}";
        String[][] cases = {
                {"en", "Cancel my appointment with Dr Akshu on 24 September", "choices", ""},
                {"en", "Cancel my appointment with Dr Akshu on 24 September at 20:00", "confirmation", "20:00"},
                {"en", "Cancel my appointment with Dr Akshu on 24 September at 20:30", "confirmation", "20:30"},
                {"hi", "Mujhe Dr Akshu ke saath 24 September ki appointment cancel karni hai", "choices", ""},
                {"hi", "Mujhe Dr Akshu ke saath 24 September ki appointment cancel karni hai at 20:00", "confirmation", "20:00"},
                {"hi", "Mujhe Dr Akshu ke saath 24 September ki appointment cancel karni hai at 20:30", "confirmation", "20:30"},
                {"hi", "Doc Akshu Kumar के साथ 24 सितंबर 2026 को अपॉइंटमेंट रद्द करें।", "choices", ""},
                {"hi", "Doc Akshu Kumar के साथ 24 सितंबर 2026 को 20:00 बजे की अपॉइंटमेंट रद्द करें।", "confirmation", "20:00"},
                {"hi", "Doc Akshu Kumar के साथ 24 सितंबर 2026 को 20:30 बजे की अपॉइंटमेंट रद्द करें।", "confirmation", "20:30"}
        };
        for (String[] testCase : cases) {
            PatientPortalService fixturePortal = mock(PatientPortalService.class);
            when(fixturePortal.currentPatientId()).thenReturn(patient);
            UUID priorId = UUID.randomUUID();
            UUID at2000 = UUID.randomUUID();
            UUID at2030 = UUID.randomUUID();
            when(fixturePortal.careAiUpcomingAppointmentsAcrossAuthorizedClinics()).thenReturn(List.of(
                    new PatientPortalCareAiAppointmentOption(priorId, UUID.randomUUID(), "Doc Akshu Kumar",
                            tenantUuid, "Clinic", LocalDate.of(2026, 9, 23), LocalTime.of(20, 0), "CONFIRMED", null),
                    new PatientPortalCareAiAppointmentOption(at2000, UUID.randomUUID(), "Doc Akshu Kumar",
                            tenantUuid, "Clinic", EXPECTED, LocalTime.of(20, 0), "CONFIRMED", null),
                    new PatientPortalCareAiAppointmentOption(at2030, UUID.randomUUID(), "Doc Akshu Kumar",
                            tenantUuid, "Clinic", EXPECTED, LocalTime.of(20, 30), "CONFIRMED", null)));
            when(semanticProvider.complete(any())).thenReturn(orchestrationResponse(providerInventedSelection));
            ClinicTimeZoneResolver resolver = zoneResolver();
            AivaV2BookingTools bookingTools = new AivaV2BookingTools(fixturePortal,
                    mock(PublicCatalogFacade.class), resolver, clock);
            AivaV2TransactionalKernel realKernel = new AivaV2TransactionalKernel(bookingTools,
                    new AivaV2AppointmentLookupTool(fixturePortal), new AivaV2CancellationTool(fixturePortal), clock);
            AivaV2ConversationService service = new AivaV2ConversationService(gateway, realKernel,
                    new AivaV2SessionStore(clock), fixturePortal, resolver, clock, renderer);

            MessageResponse response = service.message(new MessageRequest(null, testCase[1], testCase[0]));
            AivaStructuredResponse.ResponseType expectedType = "choices".equals(testCase[2])
                    ? AivaStructuredResponse.ResponseType.CANCELLATION_CHOICES
                    : AivaStructuredResponse.ResponseType.CANCELLATION_CONFIRMATION;
            assertThat(response.structuredResponse().type()).as(testCase[1]).isEqualTo(expectedType);
            if ("choices".equals(testCase[2])) {
                assertThat(((AivaStructuredResponse.CancellationChoicesPayload) response.structuredResponse()
                        .payload()).candidates()).hasSize(2);
                org.mockito.Mockito.verify(fixturePortal, org.mockito.Mockito.never())
                        .cancelAppointment(any(), any(), any());
            } else {
                var selected = ((AivaStructuredResponse.CancellationConfirmationPayload)
                        response.structuredResponse().payload()).appointment();
                assertThat(selected.date()).isEqualTo(EXPECTED);
                assertThat(selected.time()).isEqualTo(LocalTime.parse(testCase[3]));
                assertThat(selected.appointmentReference()).isEqualTo(
                        LocalTime.parse(testCase[3]).equals(LocalTime.of(20, 0)) ? at2000.toString() : at2030.toString());
            }
            org.mockito.Mockito.verify(fixturePortal, org.mockito.Mockito.times(1))
                    .careAiUpcomingAppointmentsAcrossAuthorizedClinics();
        }
    }

    @Test
    void ambiguousCancellationFollowUpsSelectOnlyFromTheActiveCandidatesAcrossLanguages() {
        String cancelJson = "{\"schemaVersion\":\"1.0\",\"dialogAct\":\"START_REQUEST\","
                + "\"operation\":\"CANCEL_APPOINTMENT\",\"lookupFilter\":{"
                + "\"doctorText\":{\"mode\":\"SET\",\"value\":\"Dr Mehta\"},"
                + "\"dateExpression\":{\"mode\":\"SET\",\"value\":\"2026-09-25\"}},\"confidence\":0.99}";
        String[][] scenarios = {
                {"en", "Cancel my appointment with Dr Akshu on 24 September", "1", "20:00", "confirmation"},
                {"hi", "Mujhe Dr Akshu ke saath 24 September ki appointment cancel karni hai", "2", "20:30", "confirmation"},
                {"hi", "डॉ. अक्षु के साथ 24 सितंबर की अपॉइंटमेंट रद्द करें।", "दूसरा", "20:30", "confirmation"},
                {"en", "Cancel my appointment with Dr Akshu on 24 September", "20:00", "20:00", "confirmation"},
                {"hi", "Mujhe Dr Akshu ke saath 24 September ki appointment cancel karni hai", "20:30 wali", "20:30", "confirmation"},
                {"hi", "डॉ. अक्षु के साथ 24 सितंबर की अपॉइंटमेंट रद्द करें।", "20:00 बजे की अपॉइंटमेंट रद्द करें", "20:00", "confirmation"},
                {"en", "Cancel my appointment with Dr Akshu on 24 September", "Cancel my appointment with Dr Akshu on 24 September 2026 at 20:00", "20:00", "confirmation"},
                {"hi", "Mujhe Dr Akshu ke saath 24 September ki appointment cancel karni hai", "Dr Akshu ke saath 24 September 2026 ko 20:00 wali appointment cancel karo", "20:00", "confirmation"},
                {"hi", "डॉ. अक्षु के साथ 24 सितंबर की अपॉइंटमेंट रद्द करें।", "डॉ. अक्षु के साथ 24 सितंबर 2026 को 20:00 बजे की अपॉइंटमेंट रद्द करें।", "20:00", "confirmation"},
                {"en", "Cancel my appointment with Dr Akshu on 24 September", "19:00", "19:00", "choices"}
        };
        LocalDate priorDate = LocalDate.of(2026, 9, 23);
        LocalDate targetDate = LocalDate.of(2026, 9, 24);
        for (String[] scenario : scenarios) {
            PatientPortalService fixturePortal = mock(PatientPortalService.class);
            when(fixturePortal.currentPatientId()).thenReturn(patient);
            UUID priorId = UUID.randomUUID();
            UUID morningId = UUID.randomUUID();
            UUID eveningId = UUID.randomUUID();
            when(fixturePortal.careAiUpcomingAppointmentsAcrossAuthorizedClinics()).thenReturn(List.of(
                    new PatientPortalCareAiAppointmentOption(priorId, UUID.randomUUID(), "Doc Akshu Kumar",
                            tenantUuid, "Clinic", priorDate, LocalTime.of(20, 0), "CONFIRMED", null),
                    new PatientPortalCareAiAppointmentOption(morningId, UUID.randomUUID(), "Doc Akshu Kumar",
                            tenantUuid, "Clinic", targetDate, LocalTime.of(20, 0), "CONFIRMED", null),
                    new PatientPortalCareAiAppointmentOption(eveningId, UUID.randomUUID(), "Doc Akshu Kumar",
                            tenantUuid, "Clinic", targetDate, LocalTime.of(20, 30), "CONFIRMED", null)));
            when(semanticProvider.complete(any())).thenReturn(orchestrationResponse(cancelJson));
            ClinicTimeZoneResolver resolver = zoneResolver();
            AivaV2BookingTools bookingTools = new AivaV2BookingTools(fixturePortal,
                    mock(PublicCatalogFacade.class), resolver, clock);
            AivaV2TransactionalKernel realKernel = new AivaV2TransactionalKernel(bookingTools,
                    new AivaV2AppointmentLookupTool(fixturePortal), new AivaV2CancellationTool(fixturePortal), clock);
            AivaV2ConversationService service = new AivaV2ConversationService(gateway, realKernel,
                    new AivaV2SessionStore(clock), fixturePortal, resolver, clock, renderer);

            MessageResponse choices = service.message(new MessageRequest(null, scenario[1], scenario[0]));
            assertThat(choices.structuredResponse().type()).as(scenario[1])
                    .isEqualTo(AivaStructuredResponse.ResponseType.CANCELLATION_CHOICES);
            var choicesPayload = (AivaStructuredResponse.CancellationChoicesPayload) choices.structuredResponse().payload();
            assertThat(choicesPayload.candidates()).hasSize(2);
            String conversationId = choices.conversationId();
            org.mockito.Mockito.clearInvocations(fixturePortal, semanticProvider);

            MessageResponse confirmation = service.message(new MessageRequest(conversationId, scenario[2], scenario[0]));
            if ("choices".equals(scenario[4])) {
                assertThat(confirmation.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.CANCELLATION_CHOICES);
                assertThat(((AivaStructuredResponse.CancellationChoicesPayload) confirmation.structuredResponse().payload())
                        .candidates()).hasSize(2);
                org.mockito.Mockito.verify(fixturePortal, org.mockito.Mockito.never())
                        .careAiUpcomingAppointmentsAcrossAuthorizedClinics();
                org.mockito.Mockito.verifyNoInteractions(semanticProvider);
                continue;
            }
            assertThat(confirmation.structuredResponse().type()).as(scenario[2])
                    .isEqualTo(AivaStructuredResponse.ResponseType.CANCELLATION_CONFIRMATION);
            var selected = ((AivaStructuredResponse.CancellationConfirmationPayload)
                    confirmation.structuredResponse().payload()).appointment();
            assertThat(selected.date()).isEqualTo(targetDate);
            assertThat(selected.time()).isEqualTo(LocalTime.parse(scenario[3]));
            assertThat(selected.appointmentReference()).isEqualTo(
                    LocalTime.parse(scenario[3]).equals(LocalTime.of(20, 0)) ? morningId.toString() : eveningId.toString());
            assertThat(selected.appointmentReference()).isNotEqualTo(priorId.toString());
            org.mockito.Mockito.verify(fixturePortal, org.mockito.Mockito.never())
                    .careAiUpcomingAppointmentsAcrossAuthorizedClinics();
            org.mockito.Mockito.verifyNoInteractions(semanticProvider);
        }
    }

    @Test
    void rescheduleConversationUsesRealServiceToolsAndStructuredResponsesInAllStyles() {
        String reschedule = "{\"schemaVersion\":\"1.0\",\"dialogAct\":\"START_REQUEST\",\"operation\":\"RESCHEDULE_APPOINTMENT\","
                + "\"lookupFilter\":{\"doctorText\":{\"mode\":\"SET\",\"value\":\"Dr Mehta\"},"
                + "\"dateExpression\":{\"mode\":\"SET\",\"value\":\"2026-09-25\"}},\"confidence\":0.99}";
        when(semanticProvider.complete(any())).thenReturn(orchestrationResponse(reschedule));
        LocalDate sourceDate = LocalDate.of(2026, 9, 24);
        LocalDate targetDate = LocalDate.of(2026, 9, 25);
        String[][] flows = {
                {"en", "Reschedule my appointment with Dr Akshu on 24 September", "25 September", "evening", "show more slots", "19:00 works", "no", "20:00 works", "yes"},
                {"hi", "Mujhe Dr Akshu ke saath 24 September ki appointment reschedule karni hai", "25 September", "shaam", "aur slots dikhao", "19:00 wali theek hai", "nahi", "20:00 wali theek hai", "haan"},
                {"hi", "डॉ. अक्षु के साथ मेरी 24 सितंबर की अपॉइंटमेंट फिर से तय करें।", "25 सितंबर", "शाम", "और स्लॉट्स दिखाओ।", "19:00 वाली ठीक है।", "नहीं", "20:00 वाली ठीक है।", "हाँ।"}
        };
        for (String[] flow : flows) {
            PatientPortalService fixturePortal = mock(PatientPortalService.class);
            when(fixturePortal.currentPatientId()).thenReturn(patient);
            UUID sourceId = UUID.randomUUID();
            UUID doctorId = UUID.randomUUID();
            when(fixturePortal.careAiUpcomingAppointmentsAcrossAuthorizedClinics()).thenReturn(List.of(
                    new PatientPortalCareAiAppointmentOption(sourceId, doctorId, "Doc Akshu Kumar", tenantUuid,
                            "Clinic", sourceDate, LocalTime.of(18, 0), "CONFIRMED", null,
                            "clinic-id", "clinic")));
            when(fixturePortal.doctorAvailability(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
                LocalDate date = invocation.getArgument(5);
                return new PatientPortalDoctorAvailabilityResponse(date, List.of(
                        slot(date, "rs-18", "18:00"), slot(date, "rs-19", "19:00"), slot(date, "rs-1930", "19:30"),
                        slot(date, "rs-20", "20:00"), slot(date, "rs-2030", "20:30"), slot(date, "rs-21", "21:00")), List.of());
            });
            when(fixturePortal.rescheduleAppointment(any(), any(), any(), any(), any())).thenReturn(
                    new PatientPortalAppointmentConfirmationResponse(sourceId.toString(), "RESCHEDULED", targetDate,
                            LocalTime.of(20, 0), "Asia/Kolkata", "Doc Akshu Kumar", "Clinic", null,
                            "ONLINE", "AIVA", "CONFIRMED", null, "Rescheduled", null, null, false, null, null));
            Clock liveFixtureClock = Clock.systemUTC();
            ClinicTimeZoneResolver resolver = zoneResolver();
            AivaV2BookingTools bookingTools = new AivaV2BookingTools(fixturePortal, mock(PublicCatalogFacade.class), resolver, liveFixtureClock);
            AivaV2AppointmentLookupTool lookupTool = new AivaV2AppointmentLookupTool(fixturePortal);
            AivaV2RescheduleTools rescheduleTools = new AivaV2RescheduleTools(fixturePortal, bookingTools, liveFixtureClock);
            AivaV2TransactionalKernel realKernel = new AivaV2TransactionalKernel(bookingTools, lookupTool,
                    new AivaV2CancellationTool(fixturePortal), rescheduleTools, liveFixtureClock);
            AivaV2ConversationService service = new AivaV2ConversationService(gateway, realKernel,
                    new AivaV2SessionStore(liveFixtureClock), fixturePortal, resolver, liveFixtureClock, renderer);
            MessageResponse response = service.message(new MessageRequest(null, flow[1], flow[0]));
            assertStructuredResponseRendersInAllStyles(response);
            String conversationId = response.conversationId();
            assertThat(response.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.RESCHEDULE_NEED_DATE);
            response = service.message(new MessageRequest(conversationId, flow[2], flow[0]));
            assertStructuredResponseRendersInAllStyles(response);
            assertThat(response.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.RESCHEDULE_AVAILABLE_SLOTS);
            assertThat(((AivaStructuredResponse.AvailabilityPayload) response.structuredResponse().payload()).date()).isEqualTo(targetDate);
            response = service.message(new MessageRequest(conversationId, flow[3], flow[0]));
            assertStructuredResponseRendersInAllStyles(response);
            assertThat(response.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.RESCHEDULE_AVAILABLE_SLOTS);
            assertThat(((AivaStructuredResponse.AvailabilityPayload) response.structuredResponse().payload()).date()).isEqualTo(targetDate);
            response = service.message(new MessageRequest(conversationId, flow[4], flow[0]));
            assertStructuredResponseRendersInAllStyles(response);
            assertThat(response.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.RESCHEDULE_AVAILABLE_SLOTS);
            response = service.message(new MessageRequest(conversationId, flow[5], flow[0]));
            assertStructuredResponseRendersInAllStyles(response);
            assertThat(response.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.RESCHEDULE_CONFIRMATION);
            response = service.message(new MessageRequest(conversationId, flow[6], flow[0]));
            assertStructuredResponseRendersInAllStyles(response);
            assertThat(response.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.RESCHEDULE_REJECTED);
            assertThat(((AivaStructuredResponse.RescheduleConfirmationPayload) response.structuredResponse().payload()).targetDate())
                    .isEqualTo(targetDate);
            response = service.message(new MessageRequest(conversationId, flow[7], flow[0]));
            assertStructuredResponseRendersInAllStyles(response);
            assertThat(response.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.RESCHEDULE_CONFIRMATION);
            var confirmation = (AivaStructuredResponse.RescheduleConfirmationPayload) response.structuredResponse().payload();
            assertThat(confirmation.originalAppointment().appointmentReference()).isEqualTo(sourceId.toString());
            assertThat(confirmation.targetDate()).isEqualTo(targetDate);
            assertThat(confirmation.targetTime()).isEqualTo(LocalTime.of(20, 0));
            response = service.message(new MessageRequest(conversationId, flow[8], flow[0]));
            assertStructuredResponseRendersInAllStyles(response);
            assertThat(response.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.RESCHEDULE_SUCCESS);
            var success = (AivaStructuredResponse.RescheduleSuccessPayload) response.structuredResponse().payload();
            assertThat(success.oldDate()).isEqualTo(sourceDate);
            assertThat(success.newDate()).isEqualTo(targetDate);
            assertThat(success.newTime()).isEqualTo(LocalTime.of(20, 0));
            assertThat(response.assistantMessage()).isNotBlank();
            org.mockito.Mockito.verify(fixturePortal, org.mockito.Mockito.times(1)).rescheduleAppointment(any(), any(), any(), any(), any());
        }
    }

    @Test
    void serviceSessionKeysIsolatePatientConversationTenantAndPresentation() {
        var currentPatient = new java.util.concurrent.atomic.AtomicReference<>(patient);
        when(patientPortal.currentPatientId()).thenAnswer(ignored -> currentPatient.get());
        PublicCatalogFacade catalog = mock(PublicCatalogFacade.class);
        when(catalog.listDoctors(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PublicPageResponse<>(List.of(
                        publicDoctor("doctor-akshu", "Doc Akshu Kumar"),
                        publicDoctor("doctor-arjun", "Dr Arjun Mehta")), 0, 50, 2, 1));
        when(semanticProvider.complete(any())).thenReturn(orchestrationResponse(
                "{\"schemaVersion\":\"1.0\",\"dialogAct\":\"START_REQUEST\",\"operation\":\"START_BOOKING\",\"confidence\":0.99}"));
        AivaV2BookingTools realTools = new AivaV2BookingTools(patientPortal, catalog, zoneResolver(), clock);
        AivaV2TransactionalKernel realKernel = new AivaV2TransactionalKernel(realTools, clock);
        AivaV2SessionStore realStore = new AivaV2SessionStore(clock);
        AivaV2ConversationService service = new AivaV2ConversationService(gateway, realKernel, realStore,
                patientPortal, zoneResolver(), clock, renderer);

        UUID tenantA = tenantUuid;
        UUID tenantB = UUID.randomUUID();
        UUID patientA = patient;
        UUID patientB = UUID.randomUUID();

        setIdentity(tenantA, patientA);
        MessageResponse patientAStart = service.message(new MessageRequest("shared-id",
                "मुझे डॉ. अक्षु के साथ अपॉइंटमेंट बुक करनी है।", "hi"));
        assertStructuredResponseRendersInAllStyles(patientAStart);
        MessageResponse patientADate = service.message(new MessageRequest("shared-id", "24 सितंबर", "en"));
        assertStructuredResponseRendersInAllStyles(patientADate);
        assertThat(patientAStart.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.NEED_DATE);
        assertThat(patientADate.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.AVAILABLE_SLOTS);
        assertThat(patientADate.assistantMessage()).contains("24 सितंबर 2026");
        MessageResponse patientASelect = service.message(new MessageRequest("shared-id", "1", "en"));
        assertStructuredResponseRendersInAllStyles(patientASelect);
        assertThat(patientASelect.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.BOOKING_CONFIRMATION);
        SessionProjection patientABefore = realStore.find(sessionKey(tenantA, patientA, "shared-id")).orElseThrow();
        assertThat(patientABefore.activeDraft().preferredDate()).isEqualTo(EXPECTED);
        assertThat(patientABefore.latestAvailabilityResult()).isNotNull();
        assertThat(patientABefore.activeDraft().selectedSlotReference()).isNotNull();
        assertThat(patientABefore.pendingConfirmation()).isNotNull();

        currentPatient.set(patientB);
        setIdentity(tenantA, patientB);
        MessageResponse patientBStart = service.message(new MessageRequest("shared-id",
                "I want to book with Dr Arjun", "en"));
        assertStructuredResponseRendersInAllStyles(patientBStart);
        assertThat(patientBStart.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.NEED_DATE);
        assertThat(patientBStart.assistantMessage()).isEqualTo("What date would you prefer?");
        SessionProjection patientBState = realStore.find(sessionKey(tenantA, patientB, "shared-id")).orElseThrow();
        assertThat(patientBState.activeDraft().selectedProvider().doctorId()).isEqualTo("doctor-arjun");
        assertThat(patientBState.activeDraft().preferredDate()).isNull();
        assertThat(patientBState.latestAvailabilityResult()).isNull();
        assertThat(patientBState.activeDraft().selectedSlotReference()).isNull();
        assertThat(patientBState.pendingConfirmation()).isNull();
        assertThat(realStore.find(sessionKey(tenantA, patientA, "shared-id")).orElseThrow()).isEqualTo(patientABefore);

        currentPatient.set(patientA);
        setIdentity(tenantA, patientA);
        MessageResponse parallelHi = service.message(new MessageRequest("parallel-hi",
                "मुझे डॉ. अक्षु के साथ अपॉइंटमेंट बुक करनी है।", "hi"));
        assertStructuredResponseRendersInAllStyles(parallelHi);
        MessageResponse parallelHiDate = service.message(new MessageRequest("parallel-hi", "24 सितंबर", "hi"));
        assertStructuredResponseRendersInAllStyles(parallelHiDate);
        MessageResponse parallelHiSelect = service.message(new MessageRequest("parallel-hi", "1", "en"));
        assertStructuredResponseRendersInAllStyles(parallelHiSelect);
        assertThat(parallelHiSelect.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.BOOKING_CONFIRMATION);
        MessageResponse parallelEn = service.message(new MessageRequest("parallel-en",
                "I want to book with Dr Arjun", "en"));
        assertStructuredResponseRendersInAllStyles(parallelEn);
        MessageResponse parallelEnDate = service.message(new MessageRequest("parallel-en", "25 September", "en"));
        assertStructuredResponseRendersInAllStyles(parallelEnDate);
        assertThat(parallelHi.assistantMessage()).contains("तारीख");
        assertThat(parallelHiDate.assistantMessage()).contains("24 सितंबर 2026");
        assertThat(parallelEn.assistantMessage()).isEqualTo("What date would you prefer?");
        assertThat(parallelEnDate.assistantMessage()).contains("25 September 2026");
        SessionProjection hiState = realStore.find(sessionKey(tenantA, patientA, "parallel-hi")).orElseThrow();
        SessionProjection enState = realStore.find(sessionKey(tenantA, patientA, "parallel-en")).orElseThrow();
        assertThat(hiState.activeDraft().selectedProvider().doctorId()).isEqualTo("doctor-akshu");
        assertThat(hiState.activeDraft().preferredDate()).isEqualTo(EXPECTED);
        assertThat(hiState.pendingConfirmation()).isNotNull();
        assertThat(hiState.activeDraft().selectedSlotReference()).isNotNull();
        assertThat(enState.activeDraft().selectedProvider().doctorId()).isEqualTo("doctor-arjun");
        assertThat(enState.activeDraft().preferredDate()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(enState.pendingConfirmation()).isNull();

        setIdentity(tenantB, patientA);
        MessageResponse tenantBStart = service.message(new MessageRequest("shared-id",
                "I want to book with Dr Arjun", "en"));
        assertStructuredResponseRendersInAllStyles(tenantBStart);
        assertThat(tenantBStart.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.NEED_DATE);
        SessionProjection tenantBState = realStore.find(sessionKey(tenantB, patientA, "shared-id")).orElseThrow();
        assertThat(tenantBState.tenantId()).isEqualTo(tenantB.toString());
        assertThat(tenantBState.activeDraft().selectedProvider().doctorId()).isEqualTo("doctor-arjun");
        assertThat(tenantBState.activeDraft().preferredDate()).isNull();
        assertThat(tenantBState.activeDraft().selectedSlotReference()).isNull();
        assertThat(tenantBState.pendingConfirmation()).isNull();
        assertThat(realStore.find(sessionKey(tenantA, patientA, "shared-id")).orElseThrow()).isEqualTo(patientABefore);
    }

    @Test
    void standaloneControlTurnsKeepEstablishedConversationPresentationThroughService() {
        when(patientPortal.currentPatientId()).thenReturn(patient);
        when(semanticProvider.complete(any())).thenReturn(orchestrationResponse(
                "{\"schemaVersion\":\"1.0\",\"dialogAct\":\"START_REQUEST\",\"operation\":\"START_BOOKING\",\"confidence\":0.99}"));
        PublicCatalogFacade catalog = mock(PublicCatalogFacade.class);
        when(catalog.listDoctors(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PublicPageResponse<>(List.of(publicDoctor("doctor-akshu", "Doc Akshu Kumar")), 0, 50, 1, 1));
        when(patientPortal.doctorAvailability(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            LocalDate date = invocation.getArgument(5);
            return new PatientPortalDoctorAvailabilityResponse(date, List.of(
                    slot(date, "style-18", "18:00"), slot(date, "style-20", "20:00")), List.of());
        });
        when(patientPortal.bookAppointment(any())).thenReturn(new PatientPortalAppointmentConfirmationResponse(
                "APT-STYLE", "BOOKED", EXPECTED, LocalTime.of(20, 0), "Asia/Kolkata", "Doc Akshu Kumar",
                "Clinic", null, "ONLINE", "AIVA", "BOOKED", null, null, null, null, false, null, null));
        AivaV2BookingTools realTools = new AivaV2BookingTools(patientPortal, catalog, zoneResolver(), clock);
        AivaV2TransactionalKernel realKernel = new AivaV2TransactionalKernel(realTools, clock);
        AivaV2ConversationService service = new AivaV2ConversationService(gateway, realKernel,
                new AivaV2SessionStore(clock), patientPortal, zoneResolver(), clock, renderer);
        String[][] flows = {
                {"hi", "मुझे डॉ. अक्षु के साथ अपॉइंटमेंट बुक करनी है।", "24 सितंबर", "1", "नहीं", "20:00", "हाँ"},
                {"hi", "Mujhe Dr Akshu ke saath appointment book karni hai", "24 September", "1", "nahi", "20:00", "haan"},
                {"en", "I want to book with Dr Akshu", "24 September", "1", "no", "20:00", "yes"}
        };
        for (String[] flow : flows) {
            MessageResponse response = service.message(new MessageRequest(null, flow[1], flow[0]));
            String id = response.conversationId();
            assertPresentationStyle(response, flow[0], flow[1].startsWith("मुझे"));
            for (int i = 2; i < flow.length; i++) {
                response = service.message(new MessageRequest(id, flow[i], flow[0]));
                assertStructuredResponseRendersInAllStyles(response);
                assertPresentationStyle(response, flow[0], flow[1].startsWith("मुझे"));
                if (i == 2) assertThat(response.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.AVAILABLE_SLOTS);
                if (i == 3) assertThat(response.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.BOOKING_CONFIRMATION);
                if (i == 5) assertThat(response.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.BOOKING_CONFIRMATION);
                if (i == 6) assertThat(response.structuredResponse().type()).isEqualTo(AivaStructuredResponse.ResponseType.BOOKING_SUCCESS);
            }
        }
    }

    private void assertPresentationStyle(MessageResponse response, String language, boolean devanagari) {
        assertStructuredResponseRendersInAllStyles(response);
        if (devanagari) {
            assertThat(response.assistantMessage().codePoints().anyMatch(cp -> cp >= 0x0900 && cp <= 0x097F)).isTrue();
        } else if ("hi".equals(language)) {
            assertThat(response.assistantMessage().codePoints().noneMatch(cp -> cp >= 0x0900 && cp <= 0x097F)).isTrue();
        } else {
            assertThat(response.assistantMessage().codePoints().noneMatch(cp -> cp >= 0x0900 && cp <= 0x097F)).isTrue();
        }
    }

    private void assertStructuredResponseRendersInAllStyles(MessageResponse response) {
        assertThat(response.structuredResponse()).isNotNull();
        assertThat(response.structuredResponse().type()).isNotEqualTo(AivaStructuredResponse.ResponseType.LEGACY);
        for (String[] locale : new String[][]{{"en", "STANDARD"}, {"hi", "HINGLISH"}, {"hi", "STANDARD"}}) {
            MessageResponse rendered = renderer.render(response, locale[0],
                    com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.valueOf(locale[1]));
            assertThat(rendered.assistantMessage()).isNotBlank().doesNotContain("English legacy");
        }
    }

    private void setIdentity(UUID tenantId, UUID patientId) {
        RequestContextHolder.set(new RequestContext(new TenantId(tenantId), UUID.randomUUID(),
                patientId.toString(), Set.of("PATIENT"), "PATIENT", "aiva-stage3d-certification"));
    }

    private String sessionKey(UUID tenantId, UUID patientId, String conversationId) {
        return tenantId + "|" + patientId + "|" + conversationId;
    }

    private PublicDoctorSummaryResponse publicDoctor(String id, String name) {
        return new PublicDoctorSummaryResponse(id, id, "/doctors/" + id, name, null, "000", "General Medicine",
                12, BigDecimal.valueOf(500), List.of("Hindi", "English"), "Area", "City", "ONLINE_BOOKING",
                null, null, "Clinic", "clinic", true, null, null, "BOOK-1", true);
    }

    private void assertRenderedStyle(MessageResponse response, String[] flow) {
        assertStructuredResponseRendersInAllStyles(response);
        String rendered = response.assistantMessage();
        if (flow[1].startsWith("मुझे")) {
            assertThat(rendered.codePoints().anyMatch(cp -> cp >= 0x0900 && cp <= 0x097F)).isTrue();
        } else if (flow[0].equals("hi")) {
            assertThat(rendered.codePoints().noneMatch(cp -> cp >= 0x0900 && cp <= 0x097F)).isTrue();
            assertThat(rendered).doesNotContain("Your appointment is booked", "What date would you prefer");
        } else {
            assertThat(rendered.codePoints().noneMatch(cp -> cp >= 0x0900 && cp <= 0x097F)).isTrue();
        }
    }

    private AiOrchestrationResponse orchestrationResponse(String output) {
        return new AiOrchestrationResponse(UUID.randomUUID(), UUID.randomUUID(), AiProductCode.GENERIC,
                AiTaskType.GENERIC_EXTRACTION, "CONTROLLED", "fixture", output, output, BigDecimal.ONE,
                List.of(), List.of(), List.of(), null, 1L, false, null, "STOP");
    }

    private PatientPortalDoctorSlotResponse slot(LocalDate date, String reference, String time) {
        LocalTime startsAt = LocalTime.parse(time);
        return new PatientPortalDoctorSlotResponse(reference, date, startsAt, startsAt.plusMinutes(30), "AVAILABLE", true);
    }

    private ClinicTimeZoneResolver zoneResolver() {
        ClinicTimeZoneResolver resolver = mock(ClinicTimeZoneResolver.class);
        when(resolver.resolve(any())).thenReturn(ZONE);
        return resolver;
    }

    private KernelResultHolder runKernel(SessionProjection session, ConversationDecision decision,
                                         AivaV2TemporalResolution temporal, String text) {
        var result = kernel.handle(session, decision, temporal, ZONE, "vertical-" + text.hashCode());
        return new KernelResultHolder(result.session(), result.response());
    }

    private SessionProjection session() {
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), patient, tenant, provider, null, null,
                null, null, null, null, DraftStatus.COLLECTING, 1, Instant.parse("2026-09-01T01:00:00Z"), null);
        return new SessionProjection("vertical", patient, tenant, draft, null, null, null, null,
                1, Instant.parse("2026-09-01T01:00:00Z"));
    }

    private record KernelResultHolder(SessionProjection session, MessageResponse response) { }
}
