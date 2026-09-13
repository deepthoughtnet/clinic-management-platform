package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilitySlot;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingPatch;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingReceipt;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConfirmationPolarity;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConversationDecision;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.DialogAct;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageRequest;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Operation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCapabilities;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSearchResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ResolutionStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Selection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ToolResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.TopicAction;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ValuePatch;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AivaV2PocCertificationTest {
    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");
    private static final UUID PATIENT = UUID.randomUUID();
    private AivaV2BookingTools tools;
    private PatientPortalService patientPortalService;
    private AivaV2SessionStore store;

    @BeforeEach
    void setUp() {
        tools = mock(AivaV2BookingTools.class);
        patientPortalService = mock(PatientPortalService.class);
        store = new AivaV2SessionStore(Clock.fixed(NOW, ZoneOffset.UTC));
        when(patientPortalService.currentPatientId()).thenReturn(PATIENT);
        RequestContextHolder.set(new RequestContext(new TenantId(UUID.fromString("720be1ee-f2ae-4dd9-b477-e05e3f97441a")),
                PATIENT, "patient", Set.of("PATIENT"), "PATIENT", "corr-v2"));
        when(tools.criteriaFingerprint(any())).thenAnswer(invocation -> "fp-" + ((AivaV2Models.BookingDraft) invocation.getArgument(0)).revision());
        when(tools.abandonBooking(any())).thenAnswer(invocation -> {
            AivaV2Models.BookingDraft draft = invocation.getArgument(0);
            return new AivaV2Models.BookingDraft(draft.draftId(), draft.patientSubjectId(), draft.tenantScope(),
                    draft.selectedProvider(), draft.specialtyFilter(), draft.preferredDate(), draft.preferredTimeWindow(),
                    draft.exactTime(), null, null, AivaV2Models.DraftStatus.ABANDONED,
                    draft.revision() + 1, draft.expiresAt(), draft.confirmedAppointmentReference());
        });
    }

    @AfterEach
    void tearDown() { RequestContextHolder.clear(); }

    @Test
    void englishHappyPathMoreSlotsContextQuestionAndDuplicateConfirmation() {
        ProviderCandidate akshu = provider("Akshu", true, false);
        stubResolvedProvider(akshu);
        stubAvailability();
        when(tools.selectBookingSlot(any(), any(), any())).thenReturn(ToolResult.success(
                new AvailabilitySlot("slot-1630", LocalTime.of(16, 30), LocalTime.of(17, 0), "16:30")));
        doAnswer(invocation -> {
            AivaV2Models.BookingDraft draft = invocation.getArgument(0);
            AvailabilityResult availability = invocation.getArgument(1);
            return ToolResult.success(new BookingConfirmation("confirm", draft.draftId(), draft.revision(),
                    akshu.providerHandle(), akshu.doctorId(), null, availability.requestId(), "slot-1630",
                    draft.preferredDate(), LocalTime.of(16, 30), NOW.plusSeconds(300), "idem"));
        }).when(tools).prepareBooking(any(), any());
        doReturn(ToolResult.success(new BookingReceipt("APT-1", "BOOKED", null)))
                .when(tools).confirmBooking(any(), any());
        Queue<ConversationDecision> decisions = new ArrayDeque<>(List.of(
                decision(Operation.START_BOOKING, patch(ValuePatch.set("Akshu"), ValuePatch.set("2026-09-11")), null, ConfirmationPolarity.NONE, TopicAction.CONTINUE, "en"),
                decision(Operation.GET_AVAILABILITY, BookingPatch.empty(), null, ConfirmationPolarity.NONE, TopicAction.CONTINUE, "en"),
                decision(Operation.SHOW_MORE_SLOTS, BookingPatch.empty(), null, ConfirmationPolarity.NONE, TopicAction.CONTINUE, "en"),
                decision(Operation.SELECT_SLOT, BookingPatch.empty(), new Selection(null, null, null, "16:30"), ConfirmationPolarity.NONE, TopicAction.CONTINUE, "en"),
                decision(Operation.ANSWER_CONTEXT, BookingPatch.empty(), null, ConfirmationPolarity.NONE, TopicAction.CONTINUE, "en"),
                decision(Operation.CONFIRM_BOOKING, BookingPatch.empty(), null, ConfirmationPolarity.POSITIVE, TopicAction.CONTINUE, "en"),
                decision(Operation.CONFIRM_BOOKING, BookingPatch.empty(), null, ConfirmationPolarity.POSITIVE, TopicAction.CONTINUE, "en")
        ));
        AivaV2ConversationService service = service(decisions);

        var first = service.message(new MessageRequest(null, "Book Akshu tomorrow.", "en"));
        String conversation = first.conversationId();
        assertThat(first.responseCategory()).isEqualTo("SLOT_CHOICES");
        service.message(new MessageRequest(conversation, "What slots are available?", "en"));
        assertThat(service.message(new MessageRequest(conversation, "Anything later?", "en")).responseCategory()).isEqualTo("SLOT_CHOICES");
        assertThat(service.message(new MessageRequest(conversation, "4:30 PM.", "en")).responseCategory()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(service.message(new MessageRequest(conversation, "What date are these slots for?", "en")).responseCategory()).isEqualTo("CONTEXT_ANSWER");
        assertThat(service.message(new MessageRequest(conversation, "Yes, book it.", "en")).responseCategory()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(service.message(new MessageRequest(conversation, "Yes", "en")).state().appointmentReference()).isEqualTo("APT-1");
        verify(tools, times(1)).confirmBooking(any(), any());
    }

    @Test
    void hindiAndEnglishConfirmationHaveIdenticalKernelSemantics() {
        assertConfirmationLanguage("Yes", "en");
        store.clear();
        assertConfirmationLanguage("हाँ, बुक कर दीजिए", "hi");
    }

    @Test
    void hinglishDateCorrectionRetainsProviderAndInvalidatesSlot() {
        ProviderCandidate akshu = provider("Akshu", true, false);
        stubResolvedProvider(akshu);
        stubAvailability();
        Queue<ConversationDecision> decisions = new ArrayDeque<>(List.of(
                decision(Operation.START_BOOKING, patch(ValuePatch.set("Akshu"), ValuePatch.set("2026-09-14")), null, ConfirmationPolarity.NONE, TopicAction.CONTINUE, "hi-en"),
                decision(Operation.UPDATE_BOOKING, patch(ValuePatch.unchanged(), ValuePatch.set("2026-09-11")), null, ConfirmationPolarity.NEGATIVE, TopicAction.CONTINUE, "hi-en")
        ));
        AivaV2ConversationService service = service(decisions);

        var first = service.message(new MessageRequest(null, "Akshu ke saath Monday", "hi-en"));
        var corrected = service.message(new MessageRequest(first.conversationId(), "Nahi, kal instead", "hi-en"));

        assertThat(corrected.state().providerName()).isEqualTo("Doc Akshu Kumar");
        assertThat(corrected.state().date()).isEqualTo(LocalDate.parse("2026-09-11"));
        assertThat(corrected.state().selectedSlotReference()).isNull();
    }

    @Test
    void callToBookNeverInvokesAvailabilityOrBooking() {
        ProviderCandidate provider = provider("Arjun", false, true);
        stubResolvedProvider(provider);
        Queue<ConversationDecision> decisions = new ArrayDeque<>(List.of(
                decision(Operation.START_BOOKING, patch(ValuePatch.set("Arjun"), ValuePatch.set("2026-09-11")), null,
                        ConfirmationPolarity.NONE, TopicAction.CONTINUE, "en")));

        var response = service(decisions).message(new MessageRequest(null, "Book Dr Arjun tomorrow", "en"));

        assertThat(response.responseCategory()).isEqualTo("CALL_TO_BOOK");
        verify(tools, never()).getBookingAvailability(any());
        verify(tools, never()).confirmBooking(any(), any());
    }

    @Test
    void abandonAndResumeUseIsolatedDraftProjection() {
        Queue<ConversationDecision> decisions = new ArrayDeque<>(List.of(
                decision(Operation.START_BOOKING, BookingPatch.empty(), null, ConfirmationPolarity.NONE, TopicAction.CONTINUE, "en"),
                decision(Operation.SUSPEND_BOOKING, BookingPatch.empty(), null, ConfirmationPolarity.NONE, TopicAction.SUSPEND, "en"),
                decision(Operation.RESUME_BOOKING, BookingPatch.empty(), null, ConfirmationPolarity.NONE, TopicAction.RESUME, "en"),
                decision(Operation.ABANDON_BOOKING, BookingPatch.empty(), null, ConfirmationPolarity.NONE, TopicAction.ABANDON, "en")
        ));
        AivaV2ConversationService service = service(decisions);
        var first = service.message(new MessageRequest(null, "Book an appointment", "en"));
        String id = first.conversationId();

        assertThat(service.message(new MessageRequest(id, "Let me ask something else", "en")).responseCategory()).isEqualTo("BOOKING_SUSPENDED");
        assertThat(service.message(new MessageRequest(id, "Continue my booking", "en")).responseCategory()).isEqualTo("NEED_PROVIDER");
        assertThat(service.message(new MessageRequest(id, "Forget it", "en")).state().status()).isEqualTo(AivaV2Models.DraftStatus.ABANDONED);
    }

    @Test
    void sameConversationIdentifierCannotReuseDraftAcrossTenantScopes() {
        ProviderCandidate akshu = provider("Akshu", true, false);
        stubResolvedProvider(akshu);
        stubAvailability();
        Queue<ConversationDecision> decisions = new ArrayDeque<>(List.of(
                decision(Operation.START_BOOKING, patch(ValuePatch.set("Akshu"), ValuePatch.set("2026-09-11")), null,
                        ConfirmationPolarity.NONE, TopicAction.CONTINUE, "en"),
                decision(Operation.START_BOOKING, BookingPatch.empty(), null,
                        ConfirmationPolarity.NONE, TopicAction.CONTINUE, "en")
        ));
        AivaV2ConversationService service = service(decisions);
        var first = service.message(new MessageRequest("shared-conversation", "Book Akshu tomorrow", "en"));

        RequestContextHolder.set(new RequestContext(new TenantId(UUID.fromString("8f8aa7f7-452b-4ef5-bcff-189c34537e10")),
                PATIENT, "patient", Set.of("PATIENT"), "PATIENT", "corr-v2-tenant-2"));
        var second = service.message(new MessageRequest(first.conversationId(), "Book an appointment", "en"));

        assertThat(first.state().providerName()).isEqualTo("Doc Akshu Kumar");
        assertThat(second.responseCategory()).isEqualTo("NEED_PROVIDER");
        assertThat(second.state().providerName()).isNull();
    }

    private void assertConfirmationLanguage(String phrase, String language) {
        englishHappyPathSetupForSingleConfirmation();
        Queue<ConversationDecision> decisions = new ArrayDeque<>(List.of(
                decision(Operation.START_BOOKING, patch(ValuePatch.set("Akshu"), ValuePatch.set("2026-09-11")), null, ConfirmationPolarity.NONE, TopicAction.CONTINUE, language),
                decision(Operation.SELECT_SLOT, BookingPatch.empty(), new Selection(null, 1, null, null), ConfirmationPolarity.NONE, TopicAction.CONTINUE, language),
                decision(Operation.CONFIRM_BOOKING, BookingPatch.empty(), null, ConfirmationPolarity.POSITIVE, TopicAction.CONTINUE, language)
        ));
        AivaV2ConversationService service = service(decisions);
        var first = service.message(new MessageRequest(null, "Book Akshu", language));
        var selected = service.message(new MessageRequest(first.conversationId(), "first", language));
        var confirmed = service.message(new MessageRequest(first.conversationId(), phrase, language));
        assertThat(selected.responseCategory()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(confirmed.responseCategory()).isEqualTo("BOOKING_CONFIRMED");
    }

    private void englishHappyPathSetupForSingleConfirmation() {
        ProviderCandidate akshu = provider("Akshu", true, false);
        stubResolvedProvider(akshu);
        stubAvailability();
        doReturn(ToolResult.success(
                new AvailabilitySlot("slot-1630", LocalTime.of(16, 30), LocalTime.of(17, 0), "16:30")))
                .when(tools).selectBookingSlot(any(), any(), any());
        doAnswer(invocation -> {
            AivaV2Models.BookingDraft draft = invocation.getArgument(0);
            AvailabilityResult availability = invocation.getArgument(1);
            return ToolResult.success(new BookingConfirmation("confirm", draft.draftId(), draft.revision(),
                    akshu.providerHandle(), akshu.doctorId(), null, availability.requestId(), "slot-1630",
                    draft.preferredDate(), LocalTime.of(16, 30), NOW.plusSeconds(300), "idem"));
        }).when(tools).prepareBooking(any(), any());
        doReturn(ToolResult.success(new BookingReceipt("APT-1", "BOOKED", null)))
                .when(tools).confirmBooking(any(), any());
    }

    private void stubResolvedProvider(ProviderCandidate provider) {
        when(tools.resolveBookingProvider(any(), any(), any())).thenAnswer(invocation -> new ProviderSearchResult(
                UUID.randomUUID(), ResolutionStatus.RESOLVED, "provider-fp", List.of(provider), provider,
                NOW, NOW.plusSeconds(300)));
    }

    private void stubAvailability() {
        doAnswer(invocation -> {
            AivaV2Models.BookingDraft draft = invocation.getArgument(0);
            String fingerprint = "fp-" + draft.revision();
            return ToolResult.success(new AvailabilityResult(UUID.randomUUID(), draft.draftId(), draft.revision(), fingerprint,
                    draft.selectedProvider().providerHandle(), draft.selectedProvider().doctorId(), null,
                    draft.preferredDate(), draft.preferredTimeWindow(), "Asia/Kolkata",
                    List.of(new AvailabilitySlot("slot-1600", LocalTime.of(16, 0), LocalTime.of(16, 30), "16:00"),
                            new AvailabilitySlot("slot-1630", LocalTime.of(16, 30), LocalTime.of(17, 0), "16:30"),
                            new AvailabilitySlot("slot-1700", LocalTime.of(17, 0), LocalTime.of(17, 30), "17:00"),
                            new AvailabilitySlot("slot-1730", LocalTime.of(17, 30), LocalTime.of(18, 0), "17:30")),
                    "3", true, NOW, NOW.plusSeconds(300)));
        }).when(tools).getBookingAvailability(any());
    }

    private AivaV2ConversationService service(Queue<ConversationDecision> decisions) {
        AivaV2ConversationDecisionGateway gateway = (text, language, context) -> decisions.remove();
        return new AivaV2ConversationService(gateway,
                new AivaV2TransactionalKernel(tools, Clock.fixed(NOW, ZoneOffset.UTC)), store,
                patientPortalService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ProviderCandidate provider(String name, boolean online, boolean call) {
        return new ProviderCandidate("candidate-" + name, "provider-" + name, "doctor-" + name,
                null, "720be1ee-f2ae-4dd9-b477-e05e3f97441a", null, null,
                "Doc " + name + " Kumar", "General Medicine", "Clinic", ProviderSource.CARE_PRIVATE,
                new ProviderCapabilities(online, online, call, call, call, true, false));
    }

    private BookingPatch patch(ValuePatch doctor, ValuePatch date) {
        return new BookingPatch(doctor, null, date, null, null);
    }

    private ConversationDecision decision(Operation operation, BookingPatch patch, Selection selection,
                                          ConfirmationPolarity confirmation, TopicAction topic, String language) {
        return new ConversationDecision("1.0", DialogAct.PROVIDE_INFORMATION, operation, patch, selection,
                confirmation, topic, language, 1, "TEST", false, 1);
    }
}
