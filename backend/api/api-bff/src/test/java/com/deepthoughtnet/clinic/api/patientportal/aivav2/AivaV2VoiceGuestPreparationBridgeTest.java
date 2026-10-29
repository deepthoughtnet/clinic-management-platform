package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilitySlot;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingDraft;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConfirmationPolarity;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConversationDecision;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.DialogAct;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.DraftStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Operation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCapabilities;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Selection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.TopicAction;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceGuestRegistrationState;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceGuestRegistrationStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ValuePatch;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.LanguageAdapterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AivaV2VoiceGuestPreparationBridgeTest {
    private static final Instant NOW = Instant.parse("2026-10-21T09:00:00Z");

    @Test
    void realVoiceSessionAndKernelPathStoresCanonicalReferenceButCannotConfirm() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        UUID interaction = UUID.randomUUID();
        ProviderCandidate provider = new ProviderCandidate("candidate", "provider", "doctor", "clinic",
                tenant.toString(), "clinic", "booking-ref", "Dr. Ravi Mehta", "Pediatrics", "Clinic",
                ProviderSource.CARE_PRIVATE, new ProviderCapabilities(true, true, false, false, false, true, false));
        UUID requestId = UUID.randomUUID();
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), patient, tenant.toString(), provider, null,
                LocalDate.of(2026, 11, 5), null, null, null, requestId, null,
                DraftStatus.COLLECTING, 1,
                NOW.plusSeconds(1800), null);
        AvailabilitySlot slot = new AvailabilitySlot("canonical-slot-1600", LocalTime.of(16, 0),
                LocalTime.of(16, 30), "16:00");
        AvailabilityResult current = new AvailabilityResult(requestId, draft.draftId(), draft.revision(),
                "criteria", provider.providerHandle(), provider.doctorId(), provider.clinicId(), draft.preferredDate(),
                null, "Asia/Kolkata", List.of(slot), null, false, NOW, NOW.plusSeconds(300));
        AvailabilityResult refreshed = new AvailabilityResult(UUID.randomUUID(), draft.draftId(), draft.revision(),
                "criteria-refreshed", provider.providerHandle(), provider.doctorId(), provider.clinicId(), draft.preferredDate(),
                null, "Asia/Kolkata", List.of(slot), null, false, NOW, NOW.plusSeconds(300));
        VoiceGuestRegistrationState guest = new VoiceGuestRegistrationState(
                VoiceGuestRegistrationStatus.CAPABILITY_GRANTED, tenant, patient, "voice-call-1", interaction,
                Instant.now().plusSeconds(600), true);
        SessionProjection initial = new SessionProjection("voice-call-1", null, tenant.toString(), draft, null,
                null, current, null, 1, NOW.plusSeconds(1800)).withVoiceGuestRegistrationState(guest);

        AivaV2SessionStore sessions = new AivaV2SessionStore(Clock.fixed(NOW, ZoneOffset.UTC));
        sessions.update("VOICE|" + tenant + "|voice-call-1", ignored -> initial);
        AivaV2BookingTools tools = mock(AivaV2BookingTools.class);
        AivaV2TransactionalKernel kernel = new AivaV2TransactionalKernel(tools, Clock.fixed(NOW, ZoneOffset.UTC));
        AivaV2TransactionalExecutionBoundary boundary = new AivaV2TransactionalExecutionBoundary(
                kernel, new AivaTransactionCapabilityGate());
        AivaV2ConversationDecisionGateway decisionGateway = mock(AivaV2ConversationDecisionGateway.class);
        ConversationDecision decision = new ConversationDecision("1.0", DialogAct.SELECT_OPTION,
                Operation.SELECT_SLOT, AivaV2Models.BookingPatch.empty(),
                new Selection(null, null, "canonical-slot-1600", null), ConfirmationPolarity.NONE,
                TopicAction.CONTINUE, "en", 1, "TEST", false, 1);
        when(decisionGateway.decide(any(com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn.class),
                any(SessionProjection.class), any(AivaExecutionContext.class))).thenReturn(decision);
        when(tools.criteriaFingerprint(any())).thenReturn("criteria");
        when(tools.selectBookingSlot(any(), any(), any())).thenReturn(AivaV2Models.ToolResult.success(slot));
        when(tools.revalidateBookingSlot(any(), any(), any())).thenReturn(AivaV2Models.ToolResult.success(
                new AivaV2BookingTools.RevalidatedBookingSlot(refreshed, slot)));
        AivaV2VoiceConversationAdapter adapter = new AivaV2VoiceConversationAdapter(sessions, decisionGateway,
                LanguageAdapterRegistry.defaults(), new AivaTransactionCapabilityGate(), boundary);
        AivaExecutionContext context = new AivaExecutionContext(tenant, null, "VOICE", "en", ZoneId.of("Asia/Kolkata"),
                "voice-call-1", interaction, null, null, Set.of(), "voice-call-1", "corr-1",
                AivaTransactionAuthorization.unauthenticated(tenant, "voice-call-1", null));

        var prepared = adapter.prepareGuestBookingTurn(context, "16:00 works");

        SessionProjection persisted = sessions.find("VOICE|" + tenant + "|voice-call-1").orElseThrow();
        assertThat(prepared.result()).isEqualTo("BOOKING_DRAFT_PREPARED");
        assertThat(prepared.diagnostics()).containsEntry("patientContextType", "GUEST_REGISTERED")
                .containsEntry("canonicalSlotSelected", true).containsEntry("draftPrepared", true)
                .containsEntry("mutationAllowed", false).containsEntry("executionDecision", "BLOCKED");
        assertThat(persisted.patientId()).isEqualTo(patient);
        assertThat(persisted.voiceGuestRegistrationState()).isEqualTo(guest);
        assertThat(persisted.activeDraft().status()).isEqualTo(DraftStatus.PREPARED);
        assertThat(persisted.activeDraft().selectedProvider().doctorId()).isEqualTo("doctor");
        assertThat(persisted.activeDraft().selectedSlotReference()).isEqualTo("canonical-slot-1600");
        assertThat(persisted.latestAvailabilityResult()).isEqualTo(refreshed);
        assertThat(persisted.latestAvailabilityResult().doctorId()).isEqualTo("doctor");
        verify(tools, never()).prepareBooking(any(), any(), any());
        verify(tools, never()).confirmBooking(any(), any());

        ConversationDecision yes = new ConversationDecision("1.0", DialogAct.CONFIRM, Operation.CONFIRM_BOOKING,
                AivaV2Models.BookingPatch.empty(), null, ConfirmationPolarity.POSITIVE,
                TopicAction.CONTINUE, "en", 1, "TEST", false, 1);
        when(decisionGateway.decide(any(com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn.class),
                any(SessionProjection.class), any(AivaExecutionContext.class))).thenReturn(yes);
        var blocked = adapter.prepareGuestBookingTurn(context, "yes, book it");
        assertThat(blocked.result()).isEqualTo("TRANSACTION_EXECUTION_DISABLED");
        assertThat(sessions.find("VOICE|" + tenant + "|voice-call-1").orElseThrow()
                .activeDraft().selectedSlotReference()).isEqualTo("canonical-slot-1600");
        verify(tools, never()).confirmBooking(any(), any());
    }

    @Test
    void voiceConstraintsResolveProviderAndLoadCanonicalAvailabilityBeforeDraftSelection() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        UUID interaction = UUID.randomUUID();
        ProviderCandidate provider = new ProviderCandidate("candidate", "provider", "doctor", "clinic",
                tenant.toString(), "clinic", "booking-ref", "Dr. Ravi Mehta", "Pediatrics", "Clinic",
                ProviderSource.CARE_PRIVATE, new ProviderCapabilities(true, true, false, false, false, true, false));
        AvailabilitySlot slot = new AvailabilitySlot("canonical-ravi-1600", LocalTime.of(16, 0),
                LocalTime.of(16, 30), "16:00");
        VoiceGuestRegistrationState guest = new VoiceGuestRegistrationState(
                VoiceGuestRegistrationStatus.CAPABILITY_GRANTED, tenant, patient, "voice-call-full", interaction,
                Instant.now().plusSeconds(600), true);
        SessionProjection initial = new SessionProjection("voice-call-full", null, tenant.toString(), null,
                null, null, null, null, 1, NOW.plusSeconds(1800)).withVoiceGuestRegistrationState(guest);
        AivaV2SessionStore sessions = new AivaV2SessionStore(Clock.fixed(NOW, ZoneOffset.UTC));
        sessions.update("VOICE|" + tenant + "|voice-call-full", ignored -> initial);

        AivaV2BookingTools tools = mock(AivaV2BookingTools.class);
        AivaV2TransactionalKernel kernel = new AivaV2TransactionalKernel(tools, Clock.fixed(NOW, ZoneOffset.UTC));
        AivaV2TransactionalExecutionBoundary boundary = new AivaV2TransactionalExecutionBoundary(
                kernel, new AivaTransactionCapabilityGate());
        AivaV2ConversationDecisionGateway decisionGateway = mock(AivaV2ConversationDecisionGateway.class);
        AivaV2Models.BookingPatch patch = new AivaV2Models.BookingPatch(
                ValuePatch.set("Dr. Ravi Mehta"), ValuePatch.unchanged(), ValuePatch.set("2026-11-05"),
                ValuePatch.unchanged(), ValuePatch.unchanged(), ValuePatch.unchanged(),
                new AivaV2Models.AvailabilityTimeConstraintPatch(AivaV2Models.PatchMode.SET,
                        new AivaV2Models.AvailabilityTimeConstraint(AivaV2Models.AvailabilityTimeConstraintMode.EXACT,
                                LocalTime.of(16, 0), null)));
        ConversationDecision decision = new ConversationDecision("1.0", DialogAct.START_REQUEST,
                Operation.START_BOOKING, patch, null, ConfirmationPolarity.NONE, TopicAction.CONTINUE,
                "en", 1, "TEST", false, 1);
        when(decisionGateway.decide(any(com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn.class),
                any(SessionProjection.class), any(AivaExecutionContext.class))).thenReturn(decision);
        when(tools.resolveBookingProviderForTenant("Dr. Ravi Mehta", null, null, tenant)).thenReturn(
                new AivaV2Models.ProviderSearchResult(UUID.randomUUID(), AivaV2Models.ResolutionStatus.RESOLVED,
                        "provider-criteria", List.of(provider), provider, NOW, NOW.plusSeconds(300)));
        when(tools.adoptCanonicalBookingProvider(tenant, provider)).thenReturn(provider);
        when(tools.criteriaFingerprint(any())).thenReturn("voice-canonical-criteria");
        when(tools.getBookingAvailability(any(), any())).thenAnswer(invocation -> {
            BookingDraft requested = invocation.getArgument(0);
            AvailabilityResult result = new AvailabilityResult(UUID.randomUUID(), requested.draftId(),
                    requested.revision(), "voice-canonical-criteria", provider.providerHandle(), provider.doctorId(),
                    provider.clinicId(), requested.preferredDate(), null,
                    new AivaV2Models.AvailabilityTimeConstraint(AivaV2Models.AvailabilityTimeConstraintMode.EXACT,
                            LocalTime.of(16, 0), null), "Asia/Kolkata", List.of(slot), null, false,
                    NOW, NOW.plusSeconds(300), 0, 3);
            return AivaV2Models.ToolResult.success(result);
        });
        when(tools.selectBookingSlot(any(), any(), any())).thenReturn(AivaV2Models.ToolResult.success(slot));
        when(tools.revalidateBookingSlot(any(), any(), any())).thenAnswer(invocation -> {
            BookingDraft requested = invocation.getArgument(0);
            AvailabilityResult result = new AvailabilityResult(UUID.randomUUID(), requested.draftId(),
                    requested.revision(), "voice-canonical-criteria", provider.providerHandle(), provider.doctorId(),
                    provider.clinicId(), requested.preferredDate(), null,
                    new AivaV2Models.AvailabilityTimeConstraint(AivaV2Models.AvailabilityTimeConstraintMode.EXACT,
                            LocalTime.of(16, 0), null), "Asia/Kolkata", List.of(slot), null, false,
                    NOW, NOW.plusSeconds(300), 0, 3);
            return AivaV2Models.ToolResult.success(new AivaV2BookingTools.RevalidatedBookingSlot(result, slot));
        });

        AivaV2VoiceConversationAdapter adapter = new AivaV2VoiceConversationAdapter(sessions, decisionGateway,
                LanguageAdapterRegistry.defaults(), new AivaTransactionCapabilityGate(), boundary);
        AivaExecutionContext context = new AivaExecutionContext(tenant, null, "VOICE", "en", ZoneId.of("Asia/Kolkata"),
                "voice-call-full", interaction, null, null, Set.of(), "voice-call-full", "corr-full",
                AivaTransactionAuthorization.unauthenticated(tenant, "voice-call-full", null));

        var prepared = adapter.prepareGuestBookingTurn(context, "November 5 2026 at 4 PM");

        SessionProjection persisted = sessions.find("VOICE|" + tenant + "|voice-call-full").orElseThrow();
        assertThat(prepared.result()).isEqualTo("BOOKING_DRAFT_PREPARED");
        assertThat(persisted.activeDraft().selectedProvider()).isEqualTo(provider);
        assertThat(persisted.activeDraft().preferredDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(persisted.activeDraft().exactTime()).isEqualTo(LocalTime.of(16, 0));
        assertThat(persisted.activeDraft().selectedSlotReference()).isEqualTo("canonical-ravi-1600");
        assertThat(persisted.latestAvailabilityResult().slots()).containsExactly(slot);
        verify(tools).getBookingAvailability(any(), any());
        verify(tools, never()).confirmBooking(any(), any());
    }
}
