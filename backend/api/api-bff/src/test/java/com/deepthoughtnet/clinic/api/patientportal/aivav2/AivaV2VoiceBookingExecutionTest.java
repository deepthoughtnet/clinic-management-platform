package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilitySlot;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingDraft;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.DraftStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCapabilities;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceGuestRegistrationState;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceGuestRegistrationStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceClarificationState;
import com.deepthoughtnet.clinic.api.patientportal.booking.AppointmentBookingApplicationService;
import com.deepthoughtnet.clinic.api.patientportal.booking.BookingExecutionContext;
import com.deepthoughtnet.clinic.api.patientportal.booking.BookingExecutionContextFactory;
import com.deepthoughtnet.clinic.api.patientportal.booking.VoiceBookingExecutionService;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceTransactionAuthPolicy;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceTransactionAuthProperties;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceTransactionPolicyResolver;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AivaV2VoiceBookingExecutionTest {
    @Test
    void activeBookingDraftDoesNotCaptureCourtesyCloseOrUnrelatedNoiseButAcceptsDateContinuation() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        String call = "voice-call-booking-continuation";
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.systemUTC());
        AivaV2VoiceConversationAdapter adapter = new AivaV2VoiceConversationAdapter(store,
                mock(AivaV2ConversationDecisionGateway.class),
                com.deepthoughtnet.clinic.api.patientportal.aivav2.language.LanguageAdapterRegistry.defaults(),
                new AivaTransactionCapabilityGate(), mock(AivaV2TransactionalExecutionBoundary.class),
                mock(VoiceBookingExecutionService.class));
        ProviderCandidate doctor = new ProviderCandidate("h", "p", "doctor-1", "clinic", tenant.toString(),
                "clinic", null, "Amit Verma", "General Medicine", "Clinic", ProviderSource.CARE_PRIVATE,
                new ProviderCapabilities(true, true, false, false, false, true, true));
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), patient, tenant.toString(), doctor, null,
                null, null, null, null, null, DraftStatus.COLLECTING, 1,
                Instant.now().plusSeconds(600), null);
        store.update("VOICE|" + tenant + "|" + call, ignored -> new SessionProjection(call, patient,
                tenant.toString(), draft, null, null, null, null, Instant.now().plusSeconds(600)));
        AivaExecutionContext context = new AivaExecutionContext(tenant, patient, "VOICE", "hi-IN",
                ZoneId.of("Asia/Kolkata"), call, UUID.randomUUID(), null, null, Set.of(), call, "corr",
                AivaTransactionAuthorization.unauthenticated(tenant, call, patient));

        assertThat(adapter.shouldRouteVoiceBookingTurn(context, "Thank you")).isFalse();
        assertThat(adapter.shouldRouteVoiceBookingTurn(context, "আপনি কেমন আছেন")).isFalse();
        assertThat(adapter.shouldRouteVoiceBookingTurn(context, "insurance claim")).isFalse();
        assertThat(adapter.shouldRouteVoiceBookingTurn(context, "कल")).isTrue();
        assertThat(adapter.shouldRouteVoiceBookingTurn(context, "evening 6 PM")).isTrue();
    }

    @Test
    void pendingClarificationWithoutDraftStillRoutesToAuthorizedVoiceAdapter() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        String call = "voice-pending-clarification-route";
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.systemUTC());
        AivaV2VoiceConversationAdapter adapter = new AivaV2VoiceConversationAdapter(store,
                mock(AivaV2ConversationDecisionGateway.class),
                com.deepthoughtnet.clinic.api.patientportal.aivav2.language.LanguageAdapterRegistry.defaults(),
                new AivaTransactionCapabilityGate(), mock(AivaV2TransactionalExecutionBoundary.class),
                mock(VoiceBookingExecutionService.class));
        VoiceClarificationState pending = new VoiceClarificationState(
                AivaV2Models.VoiceClarificationKind.PROVIDER_IDENTITY, 0,
                AivaV2Models.VoiceUnresolvedReason.AMBIGUOUS_DOCTOR, List.of("Ravi Mehta", "Ravi Sharma"),
                false, "PROVIDER_IDENTITY", "AWAIT_PROVIDER", List.of(), false);
        store.update("VOICE|" + tenant + "|" + call, ignored -> new SessionProjection(call, patient,
                tenant.toString(), null, null, null, null, null, Instant.now().plusSeconds(600))
                .withVoiceClarificationState(pending));
        AivaExecutionContext context = new AivaExecutionContext(tenant, patient, "VOICE", "hi-IN",
                ZoneId.of("Asia/Kolkata"), call, UUID.randomUUID(), null, null, Set.of(), call, "corr",
                AivaTransactionAuthorization.unauthenticated(tenant, call, patient));

        assertThat(adapter.hasVoiceBookingWorkflow(context)).isTrue();
        assertThat(adapter.shouldRouteVoiceBookingTurn(context, "Ravi Mehta")).isTrue();
    }

    @Test
    void activeReadOnlyDoctorIsReusedAndExplicitProviderChangeIsDeferredToPhaseThree() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        UUID interaction = UUID.randomUUID();
        String call = "voice-provider-continuity";
        ProviderCandidate amit = new ProviderCandidate("amit-h", "amit-p", "doctor-amit", "clinic",
                tenant.toString(), "clinic", null, "Amit Verma", "General Medicine", "Clinic",
                ProviderSource.CARE_PRIVATE, new ProviderCapabilities(true, true, false, false, false, true, false));
        ProviderCandidate ravi = new ProviderCandidate("ravi-h", "ravi-p", "doctor-ravi", "clinic",
                tenant.toString(), "clinic", null, "Ravi Mehta", "General Medicine", "Clinic",
                ProviderSource.CARE_PRIVATE, new ProviderCapabilities(true, true, false, false, false, true, false));
        ProviderCandidate raviSharma = new ProviderCandidate("ravi-sh-h", "ravi-sh-p", "doctor-ravi-sharma", "clinic",
                tenant.toString(), "clinic", null, "Ravi Sharma", "General Medicine", "Clinic",
                ProviderSource.CARE_PRIVATE, new ProviderCapabilities(true, true, false, false, false, true, false));
        var guest = new VoiceGuestRegistrationState(VoiceGuestRegistrationStatus.CAPABILITY_GRANTED,
                tenant, patient, call, interaction, Instant.now().plusSeconds(600), true);
        AivaExecutionContext voice = new AivaExecutionContext(tenant, null, "VOICE", "hi-IN", ZoneId.of("UTC"),
                call, interaction, null, null, Set.of(), call, "corr",
                AivaTransactionAuthorization.unauthenticated(tenant, call, null));
        BookingExecutionContext bookingContext = new BookingExecutionContextFactory().guestVoice(voice, guest);
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        properties.getDefaultPolicy().setEnabled(true);
        properties.getDefaultPolicy().setBookingEnabled(true);
        properties.getDefaultPolicy().setGuestBookingEnabled(true);
        VoiceTransactionAuthPolicy policy = new VoiceTransactionPolicyResolver(properties).resolve(tenant, null,
                VoiceTransactionAuthPolicy.Operation.BOOK_APPOINTMENT);

        AivaV2SessionStore store = new AivaV2SessionStore(Clock.systemUTC());
        SessionProjection initial = new SessionProjection(call, patient, tenant.toString(), null, null, null,
                null, null, 1, Instant.now().plusSeconds(1800)).withActiveVoiceDoctor(amit)
                .withVoiceGuestRegistrationState(guest);
        store.update("VOICE|" + tenant + "|" + call, ignored -> initial);

        AivaV2BookingTools tools = mock(AivaV2BookingTools.class);
        when(tools.getBookingAvailability(any(), any())).thenReturn(
                AivaV2Models.ToolResult.failure("NO_SCHEDULE", "No schedule in test fixture"));
        var raviResult = new AivaV2Models.ProviderSearchResult(UUID.randomUUID(),
                AivaV2Models.ResolutionStatus.RESOLVED, "ravi-query", List.of(ravi), ravi,
                Instant.now(), Instant.now().plusSeconds(300));
        var raviSharmaResult = new AivaV2Models.ProviderSearchResult(UUID.randomUUID(),
                AivaV2Models.ResolutionStatus.RESOLVED, "ravi-sharma-query", List.of(raviSharma), raviSharma,
                Instant.now(), Instant.now().plusSeconds(300));
        var raviAmbiguity = new AivaV2Models.ProviderSearchResult(UUID.randomUUID(),
                AivaV2Models.ResolutionStatus.AMBIGUOUS, "ravi-query", List.of(ravi, raviSharma), null,
                Instant.now(), Instant.now().plusSeconds(300));
        var amitResolved = new AivaV2Models.ProviderSearchResult(UUID.randomUUID(),
                AivaV2Models.ResolutionStatus.RESOLVED, "amit-query", List.of(amit), amit,
                Instant.now(), Instant.now().plusSeconds(300));
        when(tools.resolveBookingProviderForTenant(anyString(), nullable(String.class), nullable(String.class), eq(tenant)))
                .thenAnswer(invocation -> {
                    String query = ((String) invocation.getArgument(0)).toLowerCase();
                    if (query.contains("anit")) return amitResolved;
                    if (query.contains("mehta")) return raviResult;
                    if (query.contains("sharma")) return raviSharmaResult;
                    return raviAmbiguity;
                });
        when(tools.adoptCanonicalBookingProvider(eq(tenant), eq(ravi))).thenReturn(ravi);
        when(tools.adoptCanonicalBookingProvider(eq(tenant), eq(raviSharma))).thenReturn(raviSharma);
        when(tools.adoptCanonicalBookingProvider(eq(tenant), eq(amit))).thenReturn(amit);
        AivaV2ConversationDecisionGateway decisions = mock(AivaV2ConversationDecisionGateway.class);
        when(decisions.decide(any(NormalizedUserTurn.class), any(SessionProjection.class), eq(voice)))
                .thenAnswer(invocation -> {
                    NormalizedUserTurn turn = invocation.getArgument(0);
                    boolean switchToRavi = turn.rawText().contains("Ravi Mehta");
                    AivaV2Models.BookingPatch patch = new AivaV2Models.BookingPatch(
                            AivaV2Models.ValuePatch.set(switchToRavi ? "Ravi Mehta" : "Dr Kaa Apaintamenta De Deejie"),
                            null, null, null, null);
                    return new AivaV2Models.ConversationDecision("1.0", AivaV2Models.DialogAct.PROVIDE_INFORMATION,
                            switchToRavi ? AivaV2Models.Operation.START_BOOKING : AivaV2Models.Operation.UPDATE_BOOKING,
                            patch, null, AivaV2Models.ConfirmationPolarity.NONE,
                            AivaV2Models.TopicAction.CONTINUE, "hi", 1, "TEST", false, 1);
                });
        AivaTransactionCapabilityGate gate = new AivaTransactionCapabilityGate();
        AivaV2TransactionalKernel kernel = new AivaV2TransactionalKernel(tools, Clock.systemUTC());
        AivaV2TransactionalExecutionBoundary boundary = new AivaV2TransactionalExecutionBoundary(kernel, gate);
        VoiceBookingExecutionService bookingExecution = mock(VoiceBookingExecutionService.class);
        AivaV2VoiceConversationAdapter adapter = new AivaV2VoiceConversationAdapter(store, decisions,
                com.deepthoughtnet.clinic.api.patientportal.aivav2.language.LanguageAdapterRegistry.defaults(),
                gate, boundary, bookingExecution);

        adapter.prepareAuthorizedVoiceBookingTurn(voice, "कल का appointment दे दीजिए", bookingContext, policy);
        SessionProjection afterDate = store.find("VOICE|" + tenant + "|" + call).orElseThrow();
        assertThat(afterDate.activeDraft().selectedProvider().doctorId()).isEqualTo("doctor-amit");
        assertThat(afterDate.activeVoiceDoctor().displayName()).isEqualTo("Amit Verma");
        verify(tools, never()).resolveBookingProviderForTenant(anyString(), any(), any(), eq(tenant));

        adapter.prepareAuthorizedVoiceBookingTurn(voice, "appointment book करना चाहता हूँ", bookingContext, policy);
        SessionProjection afterGenericBooking = store.find("VOICE|" + tenant + "|" + call).orElseThrow();
        assertThat(afterGenericBooking.activeDraft().selectedProvider().doctorId()).isEqualTo("doctor-amit");
        verify(tools, never()).resolveBookingProviderForTenant(anyString(), any(), any(), eq(tenant));

        var safeTypoResolution = adapter.prepareAuthorizedVoiceBookingTurn(voice,
                "Doctor Anit Verma", bookingContext, policy);
        SessionProjection afterSafeTypo = store.find("VOICE|" + tenant + "|" + call).orElseThrow();
        assertThat(safeTypoResolution.result()).isNotEqualTo("PROVIDER_SWITCH_CONFIRMATION_REQUIRED");
        assertThat(afterSafeTypo.activeDraft().selectedProvider().doctorId()).isEqualTo("doctor-amit");
        assertThat(afterSafeTypo.voiceClarificationState() == null
                || !"PROVIDER_SWITCH_CONFIRMATION".equals(afterSafeTypo.voiceClarificationState().pendingAction())).isTrue();

        var providerAmbiguity = adapter.prepareAuthorizedVoiceBookingTurn(voice,
                "Ravi doctor ka appointment chahiye", bookingContext, policy);
        SessionProjection afterAmbiguity = store.find("VOICE|" + tenant + "|" + call).orElseThrow();
        assertThat(providerAmbiguity.result()).isEqualTo("PROVIDER_IDENTITY_AMBIGUOUS");
        assertThat(providerAmbiguity.response()).contains("Ravi Mehta", "Ravi Sharma");
        assertThat(afterAmbiguity.voiceClarificationState().pendingAction()).isEqualTo("PROVIDER_IDENTITY");
        assertThat(afterAmbiguity.voiceClarificationState().canonicalProviderCandidates())
                .extracting(AivaV2Models.VoiceProviderIdentityCandidate::providerId)
                .containsExactly("doctor-ravi", "doctor-ravi-sharma");
        assertThat(afterAmbiguity.activeDraft().selectedProvider().doctorId()).isEqualTo("doctor-amit");

        var yesToMultipleCandidates = adapter.prepareAuthorizedVoiceBookingTurn(voice,
                "हाँ", bookingContext, policy);
        assertThat(yesToMultipleCandidates.result()).isEqualTo("PROVIDER_IDENTITY_CLARIFICATION_PENDING");
        assertThat(store.find("VOICE|" + tenant + "|" + call).orElseThrow().activeDraft().selectedProvider().doctorId())
                .isEqualTo("doctor-amit");
        long decisionCallsBeforeResolvedAnswers = mockingDetails(decisions).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("decide")).count();

        var providerChange = adapter.prepareAuthorizedVoiceBookingTurn(voice,
                "Ravi Mehta", bookingContext, policy);
        SessionProjection afterOverride = store.find("VOICE|" + tenant + "|" + call).orElseThrow();
        assertThat(providerChange.result()).isEqualTo("PROVIDER_SWITCH_CONFIRMATION_REQUIRED");
        assertThat(providerChange.diagnostics()).containsEntry("pendingAction", "PROVIDER_SWITCH_CONFIRMATION")
                .containsEntry("contextPreserved", true).containsEntry("mutationAllowed", false);
        assertThat(afterOverride.activeDraft().selectedProvider().doctorId()).isEqualTo("doctor-amit");
        assertThat(afterOverride.activeVoiceDoctor().displayName()).isEqualTo("Amit Verma");
        var twoCandidateState = afterAmbiguity.voiceClarificationState();
        store.update("VOICE|" + tenant + "|" + call, session -> session.withVoiceClarificationState(twoCandidateState));
        var ordinalSwitch = adapter.prepareAuthorizedVoiceBookingTurn(voice, "पहला", bookingContext, policy);
        assertThat(ordinalSwitch.result()).isEqualTo("PROVIDER_SWITCH_CONFIRMATION_REQUIRED");
        assertThat(ordinalSwitch.diagnostics()).containsEntry("targetProviderId", "doctor-ravi");
        assertThat(store.find("VOICE|" + tenant + "|" + call).orElseThrow().activeDraft()
                .selectedProvider().doctorId()).isEqualTo("doctor-amit");

        store.update("VOICE|" + tenant + "|" + call, session -> session.withVoiceClarificationState(twoCandidateState));
        var secondOrdinalSwitch = adapter.prepareAuthorizedVoiceBookingTurn(voice, "दूसरा", bookingContext, policy);
        assertThat(secondOrdinalSwitch.result()).isEqualTo("PROVIDER_SWITCH_CONFIRMATION_REQUIRED");
        assertThat(secondOrdinalSwitch.diagnostics()).containsEntry("targetProviderId", "doctor-ravi-sharma");
        assertThat(store.find("VOICE|" + tenant + "|" + call).orElseThrow().activeDraft()
                .selectedProvider().doctorId()).isEqualTo("doctor-amit");

        VoiceClarificationState oneCandidate = new VoiceClarificationState(
                AivaV2Models.VoiceClarificationKind.PROVIDER_IDENTITY, 1,
                AivaV2Models.VoiceUnresolvedReason.AMBIGUOUS_DOCTOR, List.of("Ravi Mehta"), false,
                "PROVIDER_IDENTITY", "BOOKING", List.of(new AivaV2Models.VoiceProviderIdentityCandidate(
                "doctor-ravi", tenant, "Ravi Mehta")), true);
        store.update("VOICE|" + tenant + "|" + call, session -> session.withVoiceClarificationState(oneCandidate));
        var singleYes = adapter.prepareAuthorizedVoiceBookingTurn(voice, "हाँ", bookingContext, policy);
        assertThat(singleYes.result()).isEqualTo("PROVIDER_SWITCH_CONFIRMATION_REQUIRED");
        assertThat(store.find("VOICE|" + tenant + "|" + call).orElseThrow().activeDraft()
                .selectedProvider().doctorId()).isEqualTo("doctor-amit");

        store.update("VOICE|" + tenant + "|" + call, session -> session.withVoiceClarificationState(oneCandidate));
        var singleNo = adapter.prepareAuthorizedVoiceBookingTurn(voice, "नहीं", bookingContext, policy);
        assertThat(singleNo.result()).isEqualTo("PROVIDER_IDENTITY_DECLINED");
        assertThat(store.find("VOICE|" + tenant + "|" + call).orElseThrow().voiceClarificationState()).isNull();
        assertThat(store.find("VOICE|" + tenant + "|" + call).orElseThrow().activeDraft()
                .selectedProvider().doctorId()).isEqualTo("doctor-amit");
        store.update("VOICE|" + tenant + "|" + call, session -> session.withVoiceClarificationState(twoCandidateState));
        var noMatch = adapter.prepareAuthorizedVoiceBookingTurn(voice, "insurance claim", bookingContext, policy);
        assertThat(noMatch.result()).isEqualTo("PROVIDER_IDENTITY_CLARIFICATION_PENDING");
        assertThat(store.find("VOICE|" + tenant + "|" + call).orElseThrow().voiceClarificationState()
                .canonicalProviderCandidates()).containsExactlyElementsOf(twoCandidateState.canonicalProviderCandidates());
        assertThat(store.find("VOICE|" + tenant + "|" + call).orElseThrow().activeDraft()
                .selectedProvider().doctorId()).isEqualTo("doctor-amit");
        verify(tools, times(6)).resolveBookingProviderForTenant(anyString(), nullable(String.class), nullable(String.class), eq(tenant));
        assertThat(mockingDetails(decisions).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("decide")).count())
                .isEqualTo(decisionCallsBeforeResolvedAnswers);
        verify(bookingExecution, never()).book(any(), any(), any(), any(), any());
    }

    @Test
    void verifiedRegisteredPatientGrantUsesTheSameFinalBookingBoundary() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID interaction = UUID.randomUUID();
        String call = "voice-call-otp-book-confirm";
        AivaTransactionAuthorization grant = new AivaTransactionAuthorization(
                AivaTransactionAuthorization.IdentityState.AUTHENTICATED, patient,
                AivaTransactionAuthorization.ActorType.VOICE_AUTHENTICATED_PATIENT, actor.toString(),
                Set.of(AivaTransactionAuthorization.Capability.BOOK_APPOINTMENT), "VOICE_OTP", "OTP",
                Instant.now().plusSeconds(600), tenant, call);
        AivaExecutionContext voiceContext = new AivaExecutionContext(tenant, patient, "VOICE", "en", ZoneId.of("UTC"),
                call, interaction, null, null, Set.of(), call, "corr", grant);
        BookingExecutionContext bookingContext = new BookingExecutionContextFactory().authenticatedVoice(voiceContext);
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.systemUTC());
        AppointmentBookingApplicationService shared = mock(AppointmentBookingApplicationService.class);
        VoiceBookingExecutionService execution = new VoiceBookingExecutionService(shared);
        AivaV2TransactionalExecutionBoundary kernelBoundary = mock(AivaV2TransactionalExecutionBoundary.class);
        AivaV2ConversationDecisionGateway decisions = mock(AivaV2ConversationDecisionGateway.class);
        when(decisions.decide(any(NormalizedUserTurn.class), any(SessionProjection.class), eq(voiceContext)))
                .thenReturn(new AivaV2Models.ConversationDecision("1.0", AivaV2Models.DialogAct.PROVIDE_INFORMATION,
                        AivaV2Models.Operation.UNKNOWN,
                        new AivaV2Models.BookingPatch(AivaV2Models.ValuePatch.unchanged(),
                                AivaV2Models.ValuePatch.unchanged(), AivaV2Models.ValuePatch.unchanged(),
                                AivaV2Models.ValuePatch.unchanged(), AivaV2Models.ValuePatch.unchanged()),
                        null, AivaV2Models.ConfirmationPolarity.NONE,
                        AivaV2Models.TopicAction.CONTINUE, "hi", 1, "TEST", false, 1));
        AivaV2VoiceConversationAdapter adapter = new AivaV2VoiceConversationAdapter(store,
                decisions,
                com.deepthoughtnet.clinic.api.patientportal.aivav2.language.LanguageAdapterRegistry.defaults(),
                new AivaTransactionCapabilityGate(), kernelBoundary, execution);
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        properties.getDefaultPolicy().setEnabled(true);
        properties.getDefaultPolicy().setBookingEnabled(true);
        var policy = new VoiceTransactionPolicyResolver(properties).resolve(tenant, null,
                VoiceTransactionAuthPolicy.Operation.BOOK_APPOINTMENT);
        LocalDate date = LocalDate.of(2026, 11, 5);
        UUID doctor = UUID.randomUUID();
        ProviderCandidate provider = new ProviderCandidate("h", "p", doctor.toString(), "clinic", tenant.toString(),
                "clinic-slug", "opaque-booking-ref", "Dr Example", "General Medicine", "Clinic",
                ProviderSource.CARE_PRIVATE, new ProviderCapabilities(true, true, false, true, true, true, true));
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), patient, tenant.toString(), provider, null,
                date, null, LocalTime.of(16, 0), null, UUID.randomUUID(), "canonical-slot", DraftStatus.PREPARED,
                3, Instant.now().plusSeconds(900), null);
        AvailabilityResult availability = new AvailabilityResult(draft.latestAvailabilityRequestId(), draft.draftId(),
                draft.revision(), "criteria", provider.providerHandle(), doctor.toString(), provider.clinicId(), date,
                null, null, "UTC", List.of(new AvailabilitySlot("canonical-slot", LocalTime.of(16, 0),
                LocalTime.of(16, 30), "4 PM")), null, false, Instant.now(), Instant.now().plusSeconds(90), 0, 3);
        String binding = execution.confirmationBinding(bookingContext, draft, availability);
        BookingConfirmation confirmation = new BookingConfirmation(binding, draft.draftId(), draft.revision(),
                provider.providerHandle(), doctor.toString(), provider.clinicId(), availability.requestId(),
                draft.selectedSlotReference(), date, draft.exactTime(), Instant.now().plusSeconds(90), binding);
        SessionProjection session = new SessionProjection(call, patient, tenant.toString(), draft, null, null,
                availability, confirmation, null, null, null, null, null, false, 1,
                Instant.now().plusSeconds(900), null, null, null, null, null);
        store.update("VOICE|" + tenant + "|" + call, ignored -> session);
        when(shared.book(any(), any())).thenReturn(new AppointmentRecord(UUID.randomUUID(), tenant, patient,
                "P-1", "Patient", null, doctor, "Dr Example", null, date, LocalTime.of(16, 0), null,
                "APT-OTP", null, AppointmentType.SCHEDULED, AppointmentPriority.NORMAL, AppointmentStatus.BOOKED,
                OffsetDateTime.now(), OffsetDateTime.now()));
        when(kernelBoundary.prepareAuthorizedVoiceBookingOnly(any(), any(), any(), any(), any(), any(), anyBoolean(),
                any(), any())).thenReturn(new AivaV2TransactionalExecutionBoundary.Result(null,
                new AivaTransactionCapabilityGate.Decision(AivaTransactionAuthorization.Capability.BOOK_APPOINTMENT,
                        AivaTransactionCapabilityGate.Outcome.DENIED, "NO_ACTIVE_FINAL_CONFIRMATION")));

        var result = adapter.prepareAuthorizedVoiceBookingTurn(voiceContext, "हाँ बुक कर दो", bookingContext, policy);
        var replay = adapter.prepareAuthorizedVoiceBookingTurn(voiceContext, "हाँ बुक कर दो", bookingContext, policy);

        assertThat(result.result()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(replay.result()).isNotEqualTo("BOOKING_CONFIRMED");
        verify(shared, org.mockito.Mockito.times(1)).book(any(), any());
    }

    @Test
    void onlyFreshAffirmativeForBoundDraftReachesSharedBookingApplicationService() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        UUID interaction = UUID.randomUUID();
        String call = "voice-call-book-confirm";
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.systemUTC());
        AivaV2TransactionalExecutionBoundary kernelBoundary = mock(AivaV2TransactionalExecutionBoundary.class);
        AppointmentBookingApplicationService shared = mock(AppointmentBookingApplicationService.class);
        VoiceBookingExecutionService execution = new VoiceBookingExecutionService(shared);
        AivaV2VoiceConversationAdapter adapter = new AivaV2VoiceConversationAdapter(store,
                mock(AivaV2ConversationDecisionGateway.class),
                com.deepthoughtnet.clinic.api.patientportal.aivav2.language.LanguageAdapterRegistry.defaults(),
                new AivaTransactionCapabilityGate(), kernelBoundary, execution);
        AivaExecutionContext voiceContext = new AivaExecutionContext(tenant, null, "VOICE", "en", ZoneId.of("UTC"),
                call, interaction, null, null, Set.of(), call, "corr",
                AivaTransactionAuthorization.unauthenticated(tenant, call, null));
        VoiceGuestRegistrationState guest = new VoiceGuestRegistrationState(
                VoiceGuestRegistrationStatus.CAPABILITY_GRANTED, tenant, patient, call, interaction,
                Instant.now().plusSeconds(300), true);
        BookingExecutionContext bookingContext = new BookingExecutionContextFactory().guestVoice(voiceContext, guest);
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        properties.getDefaultPolicy().setEnabled(true);
        properties.getDefaultPolicy().setBookingEnabled(true);
        properties.getDefaultPolicy().setGuestBookingEnabled(true);
        var policy = new VoiceTransactionPolicyResolver(properties).resolve(tenant, null,
                VoiceTransactionAuthPolicy.Operation.BOOK_APPOINTMENT);
        LocalDate date = LocalDate.of(2026, 11, 5);
        UUID doctor = UUID.randomUUID();
        ProviderCandidate provider = new ProviderCandidate("h", "p", doctor.toString(), "clinic", tenant.toString(),
                "clinic-slug", "opaque-booking-ref", "Dr Example", "General Medicine", "Clinic",
                ProviderSource.CARE_PRIVATE, new ProviderCapabilities(true, true, false, true, true, true, true));
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), patient, tenant.toString(), provider, null,
                date, null, LocalTime.of(16, 0), null, UUID.randomUUID(), "canonical-slot", DraftStatus.PREPARED,
                3, Instant.now().plusSeconds(900), null);
        AvailabilityResult availability = new AvailabilityResult(draft.latestAvailabilityRequestId(), draft.draftId(),
                draft.revision(), "criteria", provider.providerHandle(), provider.doctorId(), provider.clinicId(),
                date, null, null, "UTC", List.of(new AvailabilitySlot("canonical-slot", LocalTime.of(16, 0),
                LocalTime.of(16, 30), "4 PM")), null, false, Instant.now(), Instant.now().plusSeconds(90), 0, 3);
        String binding = execution.confirmationBinding(bookingContext, draft, availability);
        BookingConfirmation confirmation = new BookingConfirmation(binding, draft.draftId(), draft.revision(),
                provider.providerHandle(), provider.doctorId(), provider.clinicId(), availability.requestId(),
                draft.selectedSlotReference(), date, draft.exactTime(), Instant.now().plusSeconds(90), binding);
        SessionProjection session = new SessionProjection(call, patient, tenant.toString(), draft, null, null,
                availability, confirmation, null, null, null, null, null, false, 1, Instant.now().plusSeconds(900),
                null, null, null, null, guest);
        store.update("VOICE|" + tenant + "|" + call, ignored -> session);
        AppointmentRecord booked = new AppointmentRecord(UUID.randomUUID(), tenant, patient, "P-1", "Guest", null,
                doctor, "Dr Example", null, date, LocalTime.of(16, 0), null, "APT-1", null,
                AppointmentType.SCHEDULED, AppointmentPriority.NORMAL, AppointmentStatus.BOOKED,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(shared.book(any(), any())).thenReturn(booked);

        var result = adapter.prepareAuthorizedVoiceBookingTurn(voiceContext, "yes", bookingContext, policy);

        assertThat(result.result()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(result.response()).contains("is booked");
        verify(shared).book(any(), any());
        assertThat(store.find("VOICE|" + tenant + "|" + call).orElseThrow().activeDraft()).isNull();
    }

    @Test
    void ambiguousOrNegativeAnswerDoesNotInvokeMutation() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        String call = "voice-call-no-confirm";
        AppointmentBookingApplicationService shared = mock(AppointmentBookingApplicationService.class);
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.systemUTC());
        VoiceBookingExecutionService execution = new VoiceBookingExecutionService(shared);
        AivaV2VoiceConversationAdapter adapter = new AivaV2VoiceConversationAdapter(store,
                mock(AivaV2ConversationDecisionGateway.class),
                com.deepthoughtnet.clinic.api.patientportal.aivav2.language.LanguageAdapterRegistry.defaults(),
                new AivaTransactionCapabilityGate(), mock(AivaV2TransactionalExecutionBoundary.class), execution);
        AivaExecutionContext voiceContext = new AivaExecutionContext(tenant, null, "VOICE", "en", ZoneId.of("UTC"),
                call, UUID.randomUUID(), null, null, Set.of(), call, "corr",
                AivaTransactionAuthorization.unauthenticated(tenant, call, null));
        VoiceGuestRegistrationState guest = new VoiceGuestRegistrationState(
                VoiceGuestRegistrationStatus.CAPABILITY_GRANTED, tenant, patient, call, voiceContext.interactionId(),
                Instant.now().plusSeconds(300), true);
        BookingExecutionContext bookingContext = new BookingExecutionContextFactory().guestVoice(voiceContext, guest);
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        properties.getDefaultPolicy().setEnabled(true);
        properties.getDefaultPolicy().setBookingEnabled(true);
        var policy = new VoiceTransactionPolicyResolver(properties).resolve(tenant, null,
                VoiceTransactionAuthPolicy.Operation.BOOK_APPOINTMENT);
        LocalDate date = LocalDate.of(2026, 11, 5);
        UUID doctor = UUID.randomUUID();
        ProviderCandidate provider = new ProviderCandidate("h", "p", doctor.toString(), "clinic", tenant.toString(),
                "clinic-slug", "opaque", "Dr Example", "General Medicine", "Clinic", ProviderSource.CARE_PRIVATE,
                new ProviderCapabilities(true, true, false, true, true, true, true));
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), patient, tenant.toString(), provider, null, date,
                null, LocalTime.of(16, 0), null, UUID.randomUUID(), "canonical-slot", DraftStatus.PREPARED,
                3, Instant.now().plusSeconds(900), null);
        AvailabilityResult availability = new AvailabilityResult(draft.latestAvailabilityRequestId(), draft.draftId(),
                draft.revision(), "criteria", provider.providerHandle(), provider.doctorId(), provider.clinicId(), date,
                null, null, "UTC", List.of(new AvailabilitySlot("canonical-slot", LocalTime.of(16, 0),
                LocalTime.of(16, 30), "4 PM")), null, false, Instant.now(), Instant.now().plusSeconds(90), 0, 3);
        String binding = execution.confirmationBinding(bookingContext, draft, availability);
        BookingConfirmation confirmation = new BookingConfirmation(binding, draft.draftId(), draft.revision(),
                provider.providerHandle(), provider.doctorId(), provider.clinicId(), availability.requestId(),
                draft.selectedSlotReference(), date, draft.exactTime(), Instant.now().plusSeconds(90), binding);
        SessionProjection session = new SessionProjection(call, patient, tenant.toString(), draft, null, null,
                availability, confirmation, null, null, null, null, null, false, 1, Instant.now().plusSeconds(900),
                null, null, null, null, guest);
        store.update("VOICE|" + tenant + "|" + call, ignored -> session);

        var ambiguous = adapter.prepareAuthorizedVoiceBookingTurn(voiceContext, "maybe", bookingContext, policy);
        assertThat(ambiguous.result()).isEqualTo("BOOKING_CONFIRMATION_PENDING");
        var declined = adapter.prepareAuthorizedVoiceBookingTurn(voiceContext, "no", bookingContext, policy);

        assertThat(declined.result()).isEqualTo("BOOKING_CONFIRMATION_DECLINED");
        verify(shared, never()).book(any(), any());
    }
}
