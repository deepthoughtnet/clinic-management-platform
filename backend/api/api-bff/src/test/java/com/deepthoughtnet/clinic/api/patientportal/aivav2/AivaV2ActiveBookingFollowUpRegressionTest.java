package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilitySlot;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingDraft;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingPatch;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConfirmationPolarity;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConversationDecision;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.DialogAct;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.DraftStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Operation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCapabilities;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSearchResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ResolutionStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Selection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ToolResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.TopicAction;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ValuePatch;
import com.deepthoughtnet.clinic.api.publicsite.PublicCatalogFacade;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AivaV2ActiveBookingFollowUpRegressionTest {
    private static final Instant NOW = Instant.parse("2026-09-25T00:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 9, 28);
    private AiOrchestrationService orchestration;
    private AiAivaV2ConversationDecisionGateway gateway;

    @BeforeEach
    void setUp() {
        orchestration = mock(AiOrchestrationService.class);
        ClinicTimeZoneResolver timeZoneResolver = mock(ClinicTimeZoneResolver.class);
        when(timeZoneResolver.resolve(any())).thenReturn(ZoneId.of("Asia/Kolkata"));
        gateway = new AiAivaV2ConversationDecisionGateway(orchestration, new ObjectMapper(), timeZoneResolver,
                Clock.fixed(NOW, ZoneOffset.UTC));
        RequestContextHolder.set(new RequestContext(new TenantId(UUID.randomUUID()), UUID.randomUUID(),
                "patient", Set.of("PATIENT"), "PATIENT", "corr-v2-follow-up"));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void pendingProviderFieldTreatsShortDoctorPhrasesAsAuthoritativeResolverCandidates() {
        SessionProjection context = providerPendingContext(DATE);

        for (String text : List.of("Doc Akshu Kumar", "Talk Akshu Kumar")) {
            ConversationDecision decision = gateway.decide(text, "en", context);

            assertThat(decision.operation()).as(text).isEqualTo(Operation.UPDATE_BOOKING);
            assertThat(decision.bookingPatch().doctorText().mode()).as(text)
                    .isEqualTo(AivaV2Models.PatchMode.SET);
            assertThat(decision.bookingPatch().doctorText().value()).as(text)
                    .isEqualTo("Talk Akshu Kumar".equals(text) ? "Akshu Kumar" : text);
            assertThat(decision.provider()).as(text).isEqualTo("DETERMINISTIC");
        }
        verifyNoInteractions(orchestration);
    }

    @Test
    void providerResolutionAdvancesWorkflowWithoutLosingRequestedDate() {
        AivaV2BookingTools tools = mock(AivaV2BookingTools.class);
        AivaV2TransactionalKernel kernel = new AivaV2TransactionalKernel(tools,
                Clock.fixed(NOW, ZoneOffset.UTC));
        ProviderCandidate provider = provider();
        ProviderSearchResult resolved = new ProviderSearchResult(UUID.randomUUID(), ResolutionStatus.RESOLVED,
                "provider-criteria", List.of(provider), provider, NOW, NOW.plusSeconds(300));
        when(tools.resolveBookingProvider("Doc Akshu Kumar", null, null)).thenReturn(resolved);
        when(tools.getBookingAvailability(any())).thenAnswer(invocation -> {
            BookingDraft draft = invocation.getArgument(0);
            return ToolResult.success(availability(draft, List.of()));
        });
        SessionProjection context = providerPendingContext(DATE);
        ConversationDecision decision = gateway.decide("Doc Akshu Kumar", "en", context);

        var result = kernel.handle(context, decision,
                AivaV2TemporalResolution.unchanged(DATE, null), ZoneOffset.UTC, "turn-provider");

        verify(tools).resolveBookingProvider("Doc Akshu Kumar", null, null);
        assertThat(result.session().activeDraft().selectedProvider()).isEqualTo(provider);
        assertThat(result.session().activeDraft().preferredDate()).isEqualTo(DATE);
        assertThat(result.response().responseCategory()).isEqualTo("NO_AVAILABILITY");
    }

    @Test
    void genericOrdinalControlsBindToCurrentDisplayedPageWithoutSemanticProvider() {
        SessionProjection context = slotContext();

        for (String text : List.of("3", "third", "3rd", "third one", "Book 3rd one",
                "book the third one", "Book 3, 18")) {
            ConversationDecision decision = gateway.decide(text, "en", context);

            assertThat(decision.operation()).as(text).isEqualTo(Operation.SELECT_SLOT);
            assertThat(decision.selection().ordinal()).as(text).isEqualTo(3);
            assertThat(decision.provider()).as(text).isEqualTo("DETERMINISTIC");
        }
        verifyNoInteractions(orchestration);
    }

    @Test
    void exactTimeControlsBindOnlyToTheCurrentDisplayedSlotSet() {
        SessionProjection context = slotContext();

        for (String text : List.of("18", "18:00", "6 PM")) {
            ConversationDecision decision = gateway.decide(text, "en", context);

            assertThat(decision.operation()).as(text).isEqualTo(Operation.SELECT_SLOT);
            assertThat(decision.selection().exactTime()).as(text).isEqualTo("18:00");
        }
        verifyNoInteractions(orchestration);
    }

    @Test
    void dottedSpokenAmPmSelectionBindsToCurrentDisplayedSlot() {
        SessionProjection context = morningSlotContext();

        for (String[] sample : new String[][]{{"9 a.m.", "09:00"}, {"9:30 a.m.", "09:30"},
                {"Book 9 AM", "09:00"}}) {
            ConversationDecision decision = gateway.decide(sample[0], "en", context);

            assertThat(decision.operation()).as(sample[0]).isEqualTo(Operation.SELECT_SLOT);
            assertThat(decision.selection().exactTime()).as(sample[0]).isEqualTo(sample[1]);
        }
        verifyNoInteractions(orchestration);
    }

    @Test
    void invalidAmPmHourDoesNotSelectDisplayedSlot() {
        ConversationDecision decision = gateway.decide("19 a.m.", "en", morningSlotContext());

        assertThat(decision.operation()).isEqualTo(Operation.SELECT_SLOT);
        assertThat(decision.selection()).isNull();
        verifyNoInteractions(orchestration);
    }

    @Test
    void compoundHindiBookingRequestSelectsExactTimeFromCurrentDisplayedSlots() {
        SessionProjection context = eveningSlotContext();

        for (String text : List.of("रात 9 बजे का अपॉइंटमेंट बुक कर दो।", "Book 9 PM", "third one")) {
            ConversationDecision decision = gateway.decide(text, "hi", context);

            assertThat(decision.operation()).as(text).isEqualTo(Operation.SELECT_SLOT);
            assertThat(decision.provider()).as(text).isEqualTo("DETERMINISTIC");
            if ("third one".equals(text)) {
                assertThat(decision.selection().ordinal()).as(text).isEqualTo(3);
            } else {
                assertThat(decision.selection().ordinal()).as(text).isNull();
                assertThat(decision.selection().exactTime()).as(text).isEqualTo("21:00");
            }
        }
        verifyNoInteractions(orchestration);
    }

    @Test
    void invalidDisplayedIndexCannotSelectAnUndisplayedSlot() {
        AivaV2BookingTools tools = new AivaV2BookingTools(mock(PatientPortalService.class),
                mock(PublicCatalogFacade.class), Clock.fixed(NOW, ZoneOffset.UTC));
        SessionProjection context = slotContextWithHiddenFourth(tools);
        ConversationDecision decision = gateway.decide("book fourth one", "en", context);

        ToolResult<AvailabilitySlot> result = tools.selectBookingSlot(context.activeDraft(),
                context.latestAvailabilityResult(), decision.selection());

        assertThat(decision.operation()).isEqualTo(Operation.SELECT_SLOT);
        assertThat(decision.selection().ordinal()).isEqualTo(4);
        assertThat(result.category()).isEqualTo("NEEDS_INPUT");
        assertThat(result.value()).isNull();

        ConversationDecision unavailableTime = gateway.decide("19:00", "en", context);
        ToolResult<AvailabilitySlot> unavailable = tools.selectBookingSlot(context.activeDraft(),
                context.latestAvailabilityResult(), unavailableTime.selection());
        assertThat(unavailableTime.operation()).isEqualTo(Operation.SELECT_SLOT);
        assertThat(unavailable.category()).isEqualTo("NEEDS_INPUT");
        assertThat(unavailable.value()).isNull();

        AivaV2TransactionalKernel kernel = new AivaV2TransactionalKernel(tools,
                Clock.fixed(NOW, ZoneOffset.UTC));
        var rejected = kernel.handle(context, decision, "turn-invalid-fourth");
        var rendered = new AivaResponseRenderer().render(rejected.response(), "en",
                com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.STANDARD);
        assertThat(rejected.response().responseCategory()).isEqualTo("SLOT_SELECTION_REQUIRED");
        assertThat(rendered.assistantMessage()).isEqualTo("Please select one of the displayed slots.");
        assertThat(rejected.session().activeDraft()).isEqualTo(context.activeDraft());
        assertThat(rejected.session().latestAvailabilityResult()).isEqualTo(context.latestAvailabilityResult());
        verifyNoInteractions(orchestration);
    }

    @Test
    void unrecognizedTurnPreservesDraftAndDisplayedSlotsForNextOrdinalSelection() {
        AivaV2BookingTools tools = mock(AivaV2BookingTools.class);
        AivaV2TransactionalKernel kernel = new AivaV2TransactionalKernel(tools,
                Clock.fixed(NOW, ZoneOffset.UTC));
        SessionProjection before = slotContext();
        when(tools.criteriaFingerprint(before.activeDraft())).thenReturn("criteria");
        ConversationDecision unknown = ConversationDecision.unknown("en", "TEST", false, 1);

        var failed = kernel.handle(before, unknown, "turn-unrecognized");

        assertThat(failed.response().responseCategory()).isEqualTo("CLARIFICATION");
        assertThat(failed.session().activeDraft()).isEqualTo(before.activeDraft());
        assertThat(failed.session().latestAvailabilityResult()).isEqualTo(before.latestAvailabilityResult());
        assertThat(failed.session().activeDraft().revision()).isEqualTo(3);
        assertThat(failed.session().activeDraft().selectedProvider().displayName()).isEqualTo("Doc Akshu Kumar");
        assertThat(failed.session().activeDraft().preferredDate()).isEqualTo(DATE);
        assertThat(failed.session().activeDraft().preferredTimeWindow()).isEqualTo("evening");
        assertThat(failed.session().latestAvailabilityResult().slots())
                .extracting(AvailabilitySlot::displayTime).containsExactly("17:00", "17:30", "18:00");

        ConversationDecision selection = gateway.decide("Book 3rd one", "en", failed.session());
        AvailabilitySlot third = failed.session().latestAvailabilityResult().slots().get(2);
        when(tools.selectBookingSlot(failed.session().activeDraft(), failed.session().latestAvailabilityResult(),
                selection.selection())).thenReturn(ToolResult.success(third));
        BookingConfirmation confirmation = new BookingConfirmation("confirm", before.activeDraft().draftId(), 3,
                provider().providerHandle(), provider().doctorId(), provider().clinicId(),
                before.latestAvailabilityResult().requestId(), third.slotReference(), DATE, third.startsAt(),
                NOW.plusSeconds(300), "idempotency");
        when(tools.prepareBooking(any(), eq(failed.session().latestAvailabilityResult())))
                .thenReturn(ToolResult.success(confirmation));

        var selected = kernel.handle(failed.session(), selection, "turn-select-third");

        assertThat(selected.response().responseCategory()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(selected.session().activeDraft().exactTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(selected.session().activeDraft().selectedSlotReference()).isEqualTo("slot-3");
        assertThat(selected.session().pendingConfirmation()).isEqualTo(confirmation);
    }

    private SessionProjection providerPendingContext(LocalDate date) {
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), UUID.randomUUID(), "tenant", null, null,
                date, null, null, null, null, DraftStatus.COLLECTING, 1, NOW.plusSeconds(1800), null);
        return session(draft, null);
    }

    private SessionProjection slotContext() {
        UUID requestId = UUID.randomUUID();
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), UUID.randomUUID(), "tenant", provider(), null,
                DATE, "evening", null, requestId, null, DraftStatus.COLLECTING, 3,
                NOW.plusSeconds(1800), null);
        return session(draft, availability(draft, List.of(
                slot("slot-1", "17:00"), slot("slot-2", "17:30"), slot("slot-3", "18:00"))));
    }

    private SessionProjection slotContextWithHiddenFourth(AivaV2BookingTools tools) {
        UUID requestId = UUID.randomUUID();
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), UUID.randomUUID(), "tenant", provider(), null,
                DATE, "evening", null, requestId, null, DraftStatus.COLLECTING, 3,
                NOW.plusSeconds(1800), null);
        AvailabilityResult availability = new AvailabilityResult(requestId, draft.draftId(), draft.revision(),
                tools.criteriaFingerprint(draft), provider().providerHandle(), provider().doctorId(), provider().clinicId(),
                DATE, "evening", "Asia/Kolkata", List.of(slot("slot-1", "17:00"),
                slot("slot-2", "17:30"), slot("slot-3", "18:00"), slot("hidden-slot-4", "18:30")),
                null, true, NOW, NOW.plusSeconds(300));
        return session(draft, availability);
    }

    private SessionProjection eveningSlotContext() {
        UUID requestId = UUID.randomUUID();
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), UUID.randomUUID(), "tenant", provider(), null,
                LocalDate.of(2026, 10, 2), "evening", null, requestId, null, DraftStatus.COLLECTING, 3,
                NOW.plusSeconds(1800), null);
        return session(draft, availability(draft, List.of(
                slot("evening-1", "20:00"), slot("evening-2", "20:30"), slot("evening-3", "21:00"))));
    }

    private SessionProjection morningSlotContext() {
        UUID requestId = UUID.randomUUID();
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), UUID.randomUUID(), "tenant", provider(), null,
                DATE, "morning", null, requestId, null, DraftStatus.COLLECTING, 3,
                NOW.plusSeconds(1800), null);
        return session(draft, availability(draft, List.of(
                slot("morning-1", "09:00"), slot("morning-2", "09:30"), slot("morning-3", "10:00"))));
    }

    private AvailabilityResult availability(BookingDraft draft, List<AvailabilitySlot> slots) {
        return new AvailabilityResult(draft.latestAvailabilityRequestId() == null
                ? UUID.randomUUID() : draft.latestAvailabilityRequestId(), draft.draftId(), draft.revision(),
                "criteria", provider().providerHandle(), provider().doctorId(), provider().clinicId(),
                DATE, draft.preferredTimeWindow(), "Asia/Kolkata", slots, null, slots.size() > 3,
                NOW, NOW.plusSeconds(300));
    }

    private SessionProjection session(BookingDraft draft, AvailabilityResult availability) {
        return new SessionProjection("conversation", draft.patientSubjectId(), draft.tenantScope(), draft, null,
                null, availability, null, 1, NOW.plusSeconds(1800));
    }

    private ProviderCandidate provider() {
        return new ProviderCandidate("candidate", "provider", "doctor-akshu", "clinic", "tenant", "clinic",
                null, "Doc Akshu Kumar", "General Medicine", "Clinic", ProviderSource.CARE_PRIVATE,
                new ProviderCapabilities(true, true, false, false, false, true, false));
    }

    private AvailabilitySlot slot(String reference, String time) {
        LocalTime start = LocalTime.parse(time);
        return new AvailabilitySlot(reference, start, start.plusMinutes(30), time);
    }
}
