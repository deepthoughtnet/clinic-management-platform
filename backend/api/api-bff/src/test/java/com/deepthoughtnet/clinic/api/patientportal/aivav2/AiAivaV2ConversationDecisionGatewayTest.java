package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingDraft;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConversationDecision;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilitySlot;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityTimeConstraint;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Operation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSearchResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
import org.mockito.ArgumentCaptor;

class AiAivaV2ConversationDecisionGatewayTest {
    private AiOrchestrationService orchestration;
    private AiAivaV2ConversationDecisionGateway gateway;

    @BeforeEach
    void setUp() {
        orchestration = mock(AiOrchestrationService.class);
        ClinicTimeZoneResolver timeZoneResolver = mock(ClinicTimeZoneResolver.class);
        when(timeZoneResolver.resolve(any())).thenReturn(ZoneId.of("Asia/Kolkata"));
        gateway = new AiAivaV2ConversationDecisionGateway(orchestration, new ObjectMapper(), timeZoneResolver,
                Clock.fixed(Instant.parse("2026-09-10T00:00:00Z"), ZoneOffset.UTC));
        RequestContextHolder.set(new RequestContext(new TenantId(UUID.randomUUID()), UUID.randomUUID(),
                "patient", Set.of("PATIENT"), "PATIENT", "corr-v2"));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void exactConfirmationUsesSafeFastPathOnlyWhenConfirmationExists() {
        SessionProjection context = pendingContext();

        ConversationDecision decision = gateway.decide("हाँ", "hi", context);

        assertThat(decision.operation()).isEqualTo(Operation.CONFIRM_BOOKING);
        assertThat(decision.confirmation()).isEqualTo(AivaV2Models.ConfirmationPolarity.POSITIVE);
        assertThat(decision.provider()).isEqualTo("DETERMINISTIC");
    }

    @Test
    void activeGreetingUsesDeterministicPresenceWithoutCallingSemanticProvider() {
        for (String[] sample : new String[][]{{"Hi", "en"}, {"Hi AIVA", "en"}, {"नमस्ते", "hi"}, {"Namaste AIVA", "hi"}}) {
            ConversationDecision decision = gateway.decide(sample[0], sample[1], pendingContext());
            assertThat(decision.operation()).as(sample[0]).isEqualTo(Operation.PRESENCE_CHECK);
            assertThat(decision.provider()).as(sample[0]).isEqualTo("DETERMINISTIC");
        }
        verifyNoInteractions(orchestration);
    }

    @Test
    void providerContextQuestionUsesResolvedBookingState() {
        var provider = providerCandidate("akshu", "Doc Akshu Kumar");
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), UUID.randomUUID(), "tenant", provider,
                "General Medicine", LocalDate.of(2026, 9, 29), null, null, null, null,
                AivaV2Models.DraftStatus.COLLECTING, 1, Instant.now().plusSeconds(600), null);
        SessionProjection context = new SessionProjection("c1", draft.patientSubjectId(), "tenant", draft, null,
                null, null, null, 1, Instant.now().plusSeconds(600));

        ConversationDecision decision = gateway.decide("Which doctor is this?", "en", context);

        assertThat(decision.operation()).isEqualTo(Operation.ANSWER_PROVIDER_CONTEXT);
        assertThat(decision.provider()).isEqualTo("DETERMINISTIC");
        verifyNoInteractions(orchestration);
    }

    @Test
    void pendingBookingCapabilityConsumesNaturalAffirmativesWithoutReinterpretingBooking() {
        for (String[] sample : List.of(
                new String[]{"Yeah, book it", "en"}, new String[]{"yes please", "en"},
                new String[]{"go ahead", "en"}, new String[]{"haan kar do", "hi"},
                new String[]{"बुक कर दीजिए", "hi"})) {
            String text = sample[0];
            ConversationDecision decision = gateway.decide(text, sample[1], pendingContext());
            assertThat(decision.operation()).as(text).isEqualTo(Operation.CONFIRM_BOOKING);
            assertThat(decision.confirmation()).as(text)
                    .isEqualTo(AivaV2Models.ConfirmationPolarity.POSITIVE);
            assertThat(decision.provider()).as(text).isEqualTo("DETERMINISTIC");
        }
    }

    @Test
    void exactTimeSelectionUsesCurrentDisplayedCandidatesBeforeAvailabilityRefresh() {
        ConversationDecision decision = gateway.decide("Look, 8:30 PM", "en", currentTimePageContext());

        assertThat(decision.operation()).isEqualTo(Operation.SELECT_SLOT);
        assertThat(decision.selection().exactTime()).isEqualTo("20:30");
        assertThat(decision.provider()).isEqualTo("DETERMINISTIC");
    }

    @Test
    void ordinalBindsToPendingRescheduleCandidates() {
        var first = new AivaV2Models.AppointmentSummary("apt-1", "Doc Akshu Kumar", "Clinic",
                LocalDate.of(2026, 9, 23), LocalTime.of(20, 0), "CONFIRMED", null);
        var second = new AivaV2Models.AppointmentSummary("apt-2", "Doc Akshu Kumar", "Clinic",
                LocalDate.of(2026, 9, 25), LocalTime.of(9, 30), "CONFIRMED", null);
        var pending = new AivaV2Models.RescheduleResolution(
                null, AivaV2Models.AppointmentLookupFilter.empty(), List.of(first, second),
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                AivaV2Models.RescheduleStatus.SOURCE_AMBIGUOUS, 1, Instant.now(), Instant.now().plusSeconds(300));
        SessionProjection context = new SessionProjection("c1", UUID.randomUUID(), "tenant", null, null, null, null,
                null, null, null, pending, null, 1, Instant.now().plusSeconds(600));

        ConversationDecision decision = gateway.decide("Second one", "en", context);

        assertThat(decision.operation()).isEqualTo(Operation.RESCHEDULE_APPOINTMENT);
        assertThat(decision.selection().ordinal()).isEqualTo(2);
        assertThat(decision.provider()).isEqualTo("DETERMINISTIC");
    }

    @Test
    void bareMultilingualAppointmentOrdinalBindsOnlyInsideActionCandidates() {
        var first = new AivaV2Models.AppointmentSummary("apt-1", "Doc Akshu Kumar", "Clinic",
                LocalDate.of(2026, 9, 23), LocalTime.of(20, 0), "CONFIRMED", null);
        var second = new AivaV2Models.AppointmentSummary("apt-2", "Doc Akshu Kumar", "Clinic",
                LocalDate.of(2026, 9, 25), LocalTime.of(9, 30), "CONFIRMED", null);
        var pending = new AivaV2Models.RescheduleResolution(
                null, AivaV2Models.AppointmentLookupFilter.empty(), List.of(first, second),
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                AivaV2Models.RescheduleStatus.SOURCE_AMBIGUOUS, 1, Instant.now(), Instant.now().plusSeconds(300));
        SessionProjection context = new SessionProjection("c1", UUID.randomUUID(), "tenant", null, null, null, null,
                null, null, null, pending, null, 1, Instant.now().plusSeconds(600));

        for (String text : List.of("2", "two", "do", "दो", "दूसरा")) {
            ConversationDecision decision = gateway.decide(text, "hi", context);
            assertThat(decision.operation()).as(text).isEqualTo(Operation.RESCHEDULE_APPOINTMENT);
            assertThat(decision.selection().ordinal()).as(text).isEqualTo(2);
        }
        assertThat(gateway.decide("दो", "hi", emptyContext()).operation())
                .isNotEqualTo(Operation.RESCHEDULE_APPOINTMENT);
    }

    @Test
    void immediateSingularAppointmentReferentBindsRescheduleAndCancellation() {
        var appointment = new AivaV2Models.AppointmentSummary("apt-2", "Doc Akshu Kumar", "Clinic",
                LocalDate.of(2026, 9, 25), LocalTime.of(9, 30), "CONFIRMED", null);
        SessionProjection context = availabilityContext().withAppointmentReferent(appointment);

        ConversationDecision reschedule = gateway.decide("Reschedule it", "en", context);
        ConversationDecision cancel = gateway.decide("Cancel that appointment", "en", context);

        assertThat(reschedule.operation()).isEqualTo(Operation.RESCHEDULE_APPOINTMENT);
        assertThat(reschedule.selection().candidateRef()).isEqualTo("apt-2");
        assertThat(cancel.operation()).isEqualTo(Operation.CANCEL_APPOINTMENT);
        assertThat(cancel.selection().candidateRef()).isEqualTo("apt-2");
    }

    @Test
    void multilingualRescheduleOrdinalAndDateAreOneCanonicalDecision() {
        for (String[] sample : new String[][]{
                {"hi", "Meri second appointment reschedule karo"},
                {"hi", "दूसरी अपॉइंटमेंट रीशेड्यूल करो"},
                {"hi", "सेकंड अपॉइंटमेंट को 29 तारीख को रीशेड्यूल कर दो"},
                {"hi", "dusri appointment 29 September ko reschedule karo"}}) {
            ConversationDecision decision = gateway.decide(sample[1], sample[0], emptyContext());
            assertThat(decision.operation()).as(sample[1]).isEqualTo(Operation.RESCHEDULE_APPOINTMENT);
            assertThat(decision.selection().ordinal()).as(sample[1]).isEqualTo(2);
        }
        ConversationDecision withDate = gateway.decide(
                "सेकंड अपॉइंटमेंट को 29 तारीख को रीशेड्यूल कर दो", "hi", emptyContext());
        assertThat(withDate.lookupFilter().dateExpression().value()).isEqualTo("2026-09-29");
    }

    @Test
    void nextAppointmentQuestionsUseNextOnlyLookupInsteadOfBooking() {
        for (String[] sample : new String[][]{
                {"en", "What is my next appointment?"},
                {"hi", "मेरी अगली अपॉइंटमेंट कब है?"},
                {"hi", "Meri agli appointment kaunsi hai?"}}) {
            ConversationDecision decision = gateway.decide(sample[1], sample[0], emptyContext());
            assertThat(decision.operation()).as(sample[1]).isEqualTo(Operation.NEXT_UPCOMING_APPOINTMENT);
            assertThat(decision.lookupFilter().nextOnly()).as(sample[1]).isTrue();
        }
    }

    @Test
    void farewellIsTerminalAndNotBookingAbandonment() {
        for (String[] sample : new String[][]{{"en", "Good night"}, {"hi", "शुभ रात्रि"}, {"hi", "Theek hai, good night"}}) {
            ConversationDecision decision = gateway.decide(sample[1], sample[0], emptyContext());
            assertThat(decision.operation()).as(sample[1]).isEqualTo(Operation.END_CONVERSATION);
            assertThat(decision.operation()).isNotEqualTo(Operation.ABANDON_BOOKING);
        }
    }

    @Test
    void explicitBookingStillStartsBooking() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false,
                "{\"schemaVersion\":\"1.0\",\"dialogAct\":\"START_REQUEST\",\"operation\":\"START_BOOKING\",\"confidence\":0.95}"));
        ConversationDecision decision = gateway.decide("I want to book an appointment", "en", emptyContext());
        assertThat(decision.operation()).isEqualTo(Operation.START_BOOKING);
    }

    @Test
    void noAvailabilityRecoveryUsesServerHeldBookingContext() {
        SessionProjection context = noAvailabilityBookingContext();

        for (String[] sample : new String[][]{
                {"en", "Yes"},
                {"en", "Yes, please go ahead"},
                {"en", "When is the next available slot?"},
                {"hi", "haan, aage dekho"},
                {"hi", "अगला उपलब्ध स्लॉट कब है?"}}) {
            ConversationDecision decision = gateway.decide(sample[1], sample[0], context);
            assertThat(decision.operation()).as(sample[1]).isEqualTo(Operation.FIND_NEXT_AVAILABLE_SLOT);
            assertThat(decision.provider()).as(sample[1]).isEqualTo("DETERMINISTIC");
        }
    }

    @Test
    void canonicalDateFactReachesGatewayAndKernelDecisionAsLocalDateWithoutProvider() {
        var turn = gateway.normalizeTurn("24 सितंबर", "hi", LocalDate.of(2026, 9, 1));
        ConversationDecision decision = gateway.decide(turn, emptyContext());

        assertThat(turn.temporal().localDate()).isEqualTo(LocalDate.of(2026, 9, 24));
        assertThat(decision.operation()).isEqualTo(Operation.UPDATE_BOOKING);
        assertThat(decision.bookingPatch().dateExpression().value()).isEqualTo("2026-09-24");
        assertThat(decision.provider()).isEqualTo("DETERMINISTIC");
        org.mockito.Mockito.verifyNoInteractions(orchestration);
    }

    @Test
    void canonicalBookingMessageAndTypedDoctorAreTheSemanticProviderInput() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false,
                "{\"schemaVersion\":\"1.0\",\"dialogAct\":\"START_REQUEST\","
                        + "\"operation\":\"START_BOOKING\",\"confidence\":0.95}"));
        var turn = gateway.normalizeTurn("मुझे डॉ. अक्षु के साथ अपॉइंटमेंट बुक करनी है।", "hi",
                LocalDate.of(2026, 9, 1));
        ConversationDecision decision = gateway.decide(turn, emptyContext());

        org.mockito.ArgumentCaptor<com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest> captor =
                org.mockito.ArgumentCaptor.forClass(com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest.class);
        org.mockito.Mockito.verify(orchestration).complete(captor.capture());
        assertThat(captor.getValue().inputVariables()).containsEntry("message", "book appointment");
        assertThat(captor.getValue().inputVariables()).doesNotContainKey("boundedRawContext");
        assertThat(captor.getValue().inputVariables().get("canonicalFacts").toString()).contains("Dr Akshu");
        assertThat(decision.operation()).isEqualTo(Operation.START_BOOKING);
        assertThat(decision.bookingPatch().doctorText().value()).isEqualTo("Dr Akshu");
    }

    @Test
    void hinglishConfirmationControlsUseTheExistingConfirmationContract() {
        SessionProjection context = pendingContext();

        assertThat(gateway.decide("haan ji", "hi", context).confirmation())
                .isEqualTo(AivaV2Models.ConfirmationPolarity.POSITIVE);
        assertThat(gateway.decide("ji haan", "hi", context).confirmation())
                .isEqualTo(AivaV2Models.ConfirmationPolarity.POSITIVE);
        assertThat(gateway.decide("nahi", "hi", context).confirmation())
                .isEqualTo(AivaV2Models.ConfirmationPolarity.NEGATIVE);
        assertThat(gateway.decide("nahin", "hi", context).confirmation())
                .isEqualTo(AivaV2Models.ConfirmationPolarity.NEGATIVE);
    }

    @Test
    void thikHaiKarDoUsesDeterministicPendingCancellationConfirmation() {
        SessionProjection context = pendingCancellationConfirmationContext();

        ConversationDecision decision = gateway.decide("thik hai kar do", "hi", context);

        assertThat(decision.operation()).isEqualTo(Operation.CONFIRM_CANCELLATION);
        assertThat(decision.confirmation()).isEqualTo(AivaV2Models.ConfirmationPolarity.POSITIVE);
        assertThat(decision.provider()).isEqualTo("DETERMINISTIC");
        assertThat(context.pendingCancellation().appointmentReference()).isEqualTo("appointment-20-30");
        org.mockito.Mockito.verifyNoInteractions(orchestration);
    }

    @Test
    void cancellationSelectionUsesTypedUserFactsAndDropsProviderInventedSelection() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false, """
                {"schemaVersion":"1.0","dialogAct":"START_REQUEST","operation":"CANCEL_APPOINTMENT",
                 "lookupFilter":{"doctorText":{"mode":"SET","value":"Dr Mehta"},
                 "dateExpression":{"mode":"SET","value":"2026-09-25"}},
                 "selection":{"ordinal":1,"candidateRef":"provider-invented"},"confidence":0.95}
                """));
        var turn = gateway.normalizeTurn("Cancel my appointment with Dr Akshu on 24 September", "en",
                LocalDate.of(2026, 9, 1));

        ConversationDecision decision = gateway.decide(turn, emptyContext());

        assertThat(decision.operation()).isEqualTo(Operation.CANCEL_APPOINTMENT);
        assertThat(decision.lookupFilter().doctorText().value()).isEqualTo("Dr Akshu");
        assertThat(decision.lookupFilter().dateExpression().value()).isEqualTo("2026-09-24");
        assertThat(decision.selection()).isNull();
    }

    @Test
    void trustedCancellationExactTimeOverridesProviderOrdinalAndCandidateReference() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false, """
                {"schemaVersion":"1.0","dialogAct":"START_REQUEST","operation":"CANCEL_APPOINTMENT",
                 "selection":{"ordinal":1,"candidateRef":"provider-invented"},"confidence":0.95}
                """));
        var turn = gateway.normalizeTurn("Cancel my appointment with Dr Akshu on 24 September at 20:30", "en",
                LocalDate.of(2026, 9, 1));

        ConversationDecision decision = gateway.decide(turn, emptyContext());

        assertThat(turn.exactTime()).isEqualTo(LocalTime.of(20, 30));
        assertThat(decision.selection()).isEqualTo(new AivaV2Models.Selection(null, null, null, "20:30"));
        assertThat(decision.lookupFilter().doctorText().value()).isEqualTo("Dr Akshu");
        assertThat(decision.lookupFilter().dateExpression().value()).isEqualTo("2026-09-24");
    }

    @Test
    void shortNaturalSlotAcceptancesUseSelectionFastPath() {
        SessionProjection context = availabilityContext();

        for (String text : List.of("11:00 works", "17:30 works", "2 works", "2nd slot works for me")) {
            ConversationDecision decision = gateway.decide(text, "en", context);

            assertThat(decision.operation()).as(text).isEqualTo(Operation.SELECT_SLOT);
            assertThat(decision.selection()).isNotNull();
            assertThat(decision.bookingPatch().exactTime().mode()).isEqualTo(AivaV2Models.PatchMode.UNCHANGED);
            assertThat(decision.provider()).isEqualTo("DETERMINISTIC");
        }
    }

    @Test
    void acceptedTimeAndOrdinalAreMappedToCurrentAvailability() {
        SessionProjection context = availabilityContext();

        ConversationDecision time = gateway.decide("11:00 works", "en", context);
        ConversationDecision ordinal = gateway.decide("2nd slot works for me", "en", context);

        assertThat(time.selection().exactTime()).isEqualTo("11:00");
        assertThat(ordinal.selection().ordinal()).isEqualTo(2);
    }

    @Test
    void rescheduleExactTimeUsesNestedAuthoritativeAvailabilityWithoutProvider() {
        ConversationDecision decision = gateway.decide("20:00 works", "en", rescheduleAvailabilityContext());

        assertThat(decision.operation()).isEqualTo(Operation.SELECT_SLOT);
        assertThat(decision.selection().exactTime()).isEqualTo("20:00");
        assertThat(decision.provider()).isEqualTo("DETERMINISTIC");
        org.mockito.Mockito.verifyNoInteractions(orchestration);
    }

    @Test
    void voiceStyleExactTimeUsesNestedRescheduleAvailabilityWithoutProvider() {
        ConversationDecision decision = gateway.decide("eight PM works", "en", rescheduleAvailabilityContext());

        assertThat(decision.operation()).isEqualTo(Operation.SELECT_SLOT);
        assertThat(decision.selection().exactTime()).isEqualTo("20:00");
        assertThat(decision.provider()).isEqualTo("DETERMINISTIC");
        org.mockito.Mockito.verifyNoInteractions(orchestration);
    }

    @Test
    void genericOrdinalControlsUseNestedRescheduleAvailabilityWithoutProvider() {
        ConversationDecision third = gateway.decide("take the third one", "en", rescheduleAvailabilityContext());
        ConversationDecision second = gateway.decide("the second slot", "en", rescheduleAvailabilityContext());

        assertThat(third.operation()).isEqualTo(Operation.SELECT_SLOT);
        assertThat(third.selection().ordinal()).isEqualTo(3);
        assertThat(second.operation()).isEqualTo(Operation.SELECT_SLOT);
        assertThat(second.selection().ordinal()).isEqualTo(2);
        org.mockito.Mockito.verifyNoInteractions(orchestration);
    }

    @Test
    void continuationDecisionUsesOneTypedOperation() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false,
                "{\"schemaVersion\":\"1.0\",\"dialogAct\":\"PROVIDE_INFORMATION\","
                        + "\"operation\":\"SHOW_MORE_SLOTS\",\"bookingPatch\":{},"
                        + "\"selection\":{},\"confirmation\":\"NONE\","
                        + "\"topicAction\":\"CONTINUE\",\"confidence\":0.95}"));

        for (String text : List.of("Do you have more slots?", "Show me more", "show more slots", "show next slots")) {
            assertThat(gateway.decide(text, "en", emptyContext()).operation())
                    .isEqualTo(Operation.SHOW_MORE_SLOTS);
        }
    }

    @Test
    void daypartRefinementUsesGenericAvailabilitySemanticInsideReschedule() {
        ConversationDecision decision = gateway.decide("show me evening slots", "en", rescheduleContext());

        assertThat(decision.operation()).isEqualTo(Operation.GET_RESCHEDULE_AVAILABILITY);
        assertThat(decision.bookingPatch().timeWindow().value()).isEqualTo("evening");
        assertThat(decision.provider()).isEqualTo("DETERMINISTIC");
    }

    @Test
    void hindiDaypartsReuseTheExistingAvailabilitySemantic() {
        for (String text : List.of("shaam ki slots dikhao", "शाम की स्लॉट्स दिखाओ")) {
            ConversationDecision decision = gateway.decide(text, "hi", rescheduleContext());

            assertThat(decision.operation()).isEqualTo(Operation.GET_RESCHEDULE_AVAILABILITY);
            assertThat(decision.bookingPatch().timeWindow().value()).isEqualTo("evening");
            assertThat(decision.provider()).isEqualTo("DETERMINISTIC");
        }
        assertThat(gateway.decide("subah ki slots dikhao", "hi", rescheduleContext())
                .bookingPatch().timeWindow().value()).isEqualTo("morning");
    }

    @Test
    void hindiRescheduleTimeConstraintsUseCanonicalRangeModel() {
        ConversationDecision after = gateway.decide("8 बजे के बाद का स्लॉट बताओ", "hi", rescheduleAvailabilityContext());
        assertThat(after.operation()).isEqualTo(Operation.GET_RESCHEDULE_AVAILABILITY);
        assertThat(after.bookingPatch().availabilityTimeConstraint().value().mode())
                .isEqualTo(AivaV2Models.AvailabilityTimeConstraintMode.AFTER);
        assertThat(after.bookingPatch().availabilityTimeConstraint().value().startTime()).isEqualTo(LocalTime.of(20, 0));

        ConversationDecision between = gateway.decide("सात से नौ के बीच का स्लॉट बताओ", "hi", rescheduleAvailabilityContext());
        assertThat(between.operation()).isEqualTo(Operation.GET_RESCHEDULE_AVAILABILITY);
        assertThat(between.bookingPatch().availabilityTimeConstraint().value().mode())
                .isEqualTo(AivaV2Models.AvailabilityTimeConstraintMode.BETWEEN);
        assertThat(between.bookingPatch().availabilityTimeConstraint().value().startTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(between.bookingPatch().availabilityTimeConstraint().value().endTime()).isEqualTo(LocalTime.of(21, 0));
    }

    @Test
    void hindiPmMarkersAreAuthoritativeAndCanonicalEveningContextResolvesBareHours() {
        ConversationDecision explicitPm = gateway.decide(
                "सात पी एम से नौ पी एम के बीच स्लॉट बताइए", "hi", rescheduleAvailabilityContext());
        assertThat(explicitPm.bookingPatch().availabilityTimeConstraint().value().mode())
                .isEqualTo(AivaV2Models.AvailabilityTimeConstraintMode.BETWEEN);
        assertThat(explicitPm.bookingPatch().availabilityTimeConstraint().value().startTime())
                .isEqualTo(LocalTime.of(19, 0));
        assertThat(explicitPm.bookingPatch().availabilityTimeConstraint().value().endTime())
                .isEqualTo(LocalTime.of(21, 0));

        ConversationDecision bareAfter = gateway.decide(
                "8 बजे के बाद का स्लॉट बताइए", "hi", rescheduleAvailabilityContextWithEveningConstraint());
        assertThat(bareAfter.bookingPatch().availabilityTimeConstraint().value().mode())
                .isEqualTo(AivaV2Models.AvailabilityTimeConstraintMode.AFTER);
        assertThat(bareAfter.bookingPatch().availabilityTimeConstraint().value().startTime())
                .isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    void genericHindiSlotRequestsStayInsideActiveRescheduleWorkflow() {
        for (String text : List.of("स्लॉट बताइए", "शाम का स्लॉट बताइए", "और स्लॉट दिखाइए")) {
            ConversationDecision decision = gateway.decide(text, "hi", rescheduleContext());
            assertThat(decision.operation()).as(text)
                    .isIn(Operation.GET_RESCHEDULE_AVAILABILITY, Operation.SHOW_MORE_SLOTS);
        }
    }

    @Test
    void showMoreIsAGenericControlInsideReschedule() {
        ConversationDecision decision = gateway.decide("show me next slots", "en", rescheduleContext());

        assertThat(decision.operation()).isEqualTo(Operation.SHOW_MORE_SLOTS);
        assertThat(decision.provider()).isEqualTo("DETERMINISTIC");
    }

    @Test
    void hindiShowMoreControlsReuseGenericPagination() {
        for (String text : List.of("aur slots dikhao", "aur dikhao", "dusri slots dikhao",
                "agali slots dikhao", "और स्लॉट्स दिखाओ", "दूसरी स्लॉट्स दिखाओ")) {
            assertThat(gateway.decide(text, "hi", rescheduleContext()).operation())
                    .isEqualTo(Operation.SHOW_MORE_SLOTS);
        }
    }

    @Test
    void hindiExactTimeAcceptanceUsesTheDeterministicSelectionPath() {
        assertThat(gateway.normalizeTurn("रात 8 बजे वाला ठीक है", "hi", LocalDate.of(2026, 9, 18)).exactTime())
                .isEqualTo(LocalTime.of(20, 0));
        for (String text : List.of("20 baje wali theek hai", "19:30 wali theek hai",
                "रात 8 बजे वाला ठीक है", "20:00 वाला ठीक है")) {
            ConversationDecision decision = gateway.decide(text, "hi", rescheduleAvailabilityContext());

            assertThat(decision.operation()).as(text).isEqualTo(Operation.SELECT_SLOT);
            assertThat(decision.provider()).isEqualTo("DETERMINISTIC");
        }
        assertThat(gateway.decide("20 baje wali theek hai", "hi", rescheduleAvailabilityContext())
                .selection().exactTime()).isEqualTo("20:00");
        assertThat(gateway.decide("रात 8 बजे वाला ठीक है", "hi", rescheduleAvailabilityContext())
                .selection().exactTime()).isEqualTo("20:00");
    }

    @Test
    void hinglishBookingDoctorIsRecoveredWithoutKnowingDoctorNames() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false,
                "{\"schemaVersion\":\"1.0\",\"dialogAct\":\"START_REQUEST\","
                        + "\"operation\":\"START_BOOKING\",\"confidence\":0.95}"));

        ConversationDecision decision = gateway.decide(
                "Mujhe Dr Akshu ke saath appointment book karni hai", "hi", emptyContext());

        assertThat(decision.operation()).isEqualTo(Operation.START_BOOKING);
        assertThat(decision.bookingPatch().doctorText().value()).isEqualTo("Dr Akshu");
    }

    @Test
    void activeProviderCandidatesAreSelectedBeforeCallingSemanticProvider() {
        SessionProjection context = providerDisambiguationContext();

        for (String text : List.of("1", "first one", "Doc Akshu", "Doc Akshu Kumar",
                "book with Doc Akshu", "1. Doc Akshu Kumar")) {
            ConversationDecision decision = gateway.decide(text, "en", context);

            assertThat(decision.operation()).as(text).isEqualTo(Operation.RESOLVE_PROVIDER);
            assertThat(decision.selection()).as(text).isNotNull();
            assertThat(decision.selection().candidateRef()).as(text).isEqualTo("akshu");
            assertThat(decision.provider()).as(text).isEqualTo("DETERMINISTIC");
        }
        org.mockito.Mockito.verifyNoInteractions(orchestration);
    }

    @Test
    void compoundNegativeAndShowMoreNormalizesToOneOrderedKernelControl() {
        for (String[] input : new String[][]{
                {"no, show me other slots", "en"},
                {"nahi, dusri slots dikhao", "hi"},
                {"नहीं, दूसरी स्लॉट्स दिखाओ", "hi"}}) {
            ConversationDecision decision = gateway.decide(input[0], input[1], availabilityConfirmationContext());

            assertThat(decision.operation()).as(input[0]).isEqualTo(Operation.SHOW_MORE_SLOTS);
            assertThat(decision.confirmation()).as(input[0])
                    .isEqualTo(AivaV2Models.ConfirmationPolarity.NEGATIVE);
            assertThat(decision.provider()).as(input[0]).isEqualTo("DETERMINISTIC");
        }
        org.mockito.Mockito.verifyNoInteractions(orchestration);
    }

    @Test
    void unresolvedProviderTextPreservesCandidateContextForClarification() {
        ConversationDecision decision = gateway.decide("Doc Unknown", "en", providerDisambiguationContext());

        assertThat(decision.operation()).isEqualTo(Operation.RESOLVE_PROVIDER);
        assertThat(decision.selection()).isNull();
        assertThat(decision.provider()).isEqualTo("DETERMINISTIC");
        org.mockito.Mockito.verifyNoInteractions(orchestration);
    }

    @Test
    void ambiguousProviderTextDoesNotSilentlySelect() {
        ProviderSearchResult result = providerResult(List.of(
                providerCandidate("akshu", "Doc Akshu Kumar"),
                providerCandidate("akshu-2", "Doc Akshu Singh")));
        ConversationDecision decision = gateway.decide("Doc Akshu", "en", providerContext(result));

        assertThat(decision.operation()).isEqualTo(Operation.RESOLVE_PROVIDER);
        assertThat(decision.selection()).isNull();
    }

    @Test
    void providerListOrdinalWordsSelectOnlyFromCurrentAuthorizedCandidates() {
        SessionProjection context = providerContext(providerResult(List.of(
                providerCandidate("a", "Demo Doctor"),
                providerCandidate("b", "Doc Akshu Kumar"),
                providerCandidate("c", "Doc UAT Automation"))));

        for (String input : List.of("Three", "third", "3", "third one")) {
            ConversationDecision decision = gateway.decide(input, "en", context);
            assertThat(decision.operation()).as(input).isEqualTo(Operation.RESOLVE_PROVIDER);
            assertThat(decision.selection()).as(input).isNotNull();
            assertThat(decision.selection().candidateRef()).as(input).isEqualTo("c");
            assertThat(decision.provider()).as(input).isEqualTo("DETERMINISTIC");
        }
        ConversationDecision invalid = gateway.decide("four", "en", context);
        assertThat(invalid.operation()).isEqualTo(Operation.UNKNOWN);
        assertThat(invalid.selection()).isNull();
    }

    @Test
    void providerSelectionAcceptsNaturalWrappersAndRejectsMultipleChoices() {
        SessionProjection context = providerContext(providerResult(List.of(
                providerCandidate("a", "Demo Doctor"),
                providerCandidate("b", "Doc Akshu Kumar"),
                providerCandidate("c", "Doc UAT Automation"))));
        for (String input : List.of("number three", "the first one", "I want first",
                "I would like second", "second one", "choose number two", "two please",
                "One, Doc Akshu Kumar")) {
            ConversationDecision decision = gateway.decide(input, "en", context);
            assertThat(decision.operation()).as(input).isEqualTo(Operation.RESOLVE_PROVIDER);
            assertThat(decision.selection()).as(input).isNotNull();
        }
        ConversationDecision hindiName = gateway.decide("डॉक्टर अक्षु कुमार के साथ", "hi", context);
        assertThat(hindiName.operation()).isEqualTo(Operation.RESOLVE_PROVIDER);
        assertThat(hindiName.selection()).isNotNull();
        assertThat(hindiName.selection().candidateRef()).isEqualTo("b");
        ConversationDecision multiple = gateway.decide("1 or 2", "en", context);
        assertThat(multiple.operation()).isEqualTo(Operation.RESOLVE_PROVIDER);
        assertThat(multiple.selection()).isNull();
    }

    @Test
    void explicitHindiTimeWinsOverOrdinalOnDisplayedEveningSlots() {
        AvailabilityResult availability = new AvailabilityResult(UUID.randomUUID(), UUID.randomUUID(), 1,
                "criteria", "provider", "doctor", "clinic", LocalDate.of(2026, 9, 25), null,
                "Asia/Kolkata", List.of(
                new AvailabilitySlot("s1", LocalTime.of(17, 0), LocalTime.of(17, 30), "17:00"),
                new AvailabilitySlot("s2", LocalTime.of(17, 30), LocalTime.of(18, 0), "17:30"),
                new AvailabilitySlot("s3", LocalTime.of(18, 30), LocalTime.of(19, 0), "18:30")),
                null, false, Instant.now(), Instant.now().plusSeconds(600));
        SessionProjection context = new SessionProjection("c1", UUID.randomUUID(), "tenant", null, null,
                null, availability, null, 1, Instant.now().plusSeconds(600));
        ConversationDecision five = gateway.decide("शाम पाँच बजे", "hi", context);
        assertThat(five.operation()).isEqualTo(Operation.SELECT_SLOT);
        assertThat(five.selection().exactTime()).isEqualTo("17:00");
        ConversationDecision one = gateway.decide("1 बजे", "hi", context);
        assertThat(one.operation()).isEqualTo(Operation.SELECT_SLOT);
        assertThat(one.selection().ordinal()).isNull();
        assertThat(one.selection().exactTime()).isNotEqualTo("17:00");
        ConversationDecision bare = gateway.decide("one", "en", context);
        assertThat(bare.selection().ordinal()).isEqualTo(1);
    }

    @Test
    void appointmentLookupDecisionPreservesTypedFilters() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false, """
                {"schemaVersion":"1.0","dialogAct":"ASK_QUESTION","operation":"LOOKUP_APPOINTMENTS",
                 "bookingPatch":{},"selection":{},"lookupFilter":{
                   "doctorText":{"mode":"SET","value":"Dr Akshu"},
                   "dateExpression":{"mode":"SET","value":"2026-09-14"},
                   "status":{"mode":"UNCHANGED","value":null},"nextOnly":true},
                 "confidence":0.95}
                """));

        ConversationDecision decision = gateway.decide("When is my next appointment with Dr Akshu?", "en", emptyContext());

        assertThat(decision.operation()).isEqualTo(AivaV2Models.Operation.LOOKUP_APPOINTMENTS);
        assertThat(decision.lookupFilter().doctorText().value()).isEqualTo("Dr Akshu");
        assertThat(decision.lookupFilter().dateExpression().value()).isEqualTo("2026-09-14");
        assertThat(decision.lookupFilter().nextOnly()).isTrue();
    }

    @Test
    void lookupRecoversExplicitDoctorWhenProviderOmitsOptionalFilter() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false, """
                {"schemaVersion":"1.0","dialogAct":"ASK_QUESTION","operation":"LOOKUP_APPOINTMENTS",
                 "confidence":0.95}
                """));

        ConversationDecision decision = gateway.decide("Do I have an appointment with Dr ABC?", "en", emptyContext());

        assertThat(decision.operation()).isEqualTo(Operation.LOOKUP_APPOINTMENTS);
        assertThat(decision.lookupFilter().doctorText().mode()).isEqualTo(AivaV2Models.PatchMode.SET);
        assertThat(decision.lookupFilter().doctorText().value()).isEqualTo("Dr ABC");
    }

    @Test
    void trustedLookupDoctorAndDateOverrideConflictingProviderFacts() {
        assertTrustedLookupFacts("LOOKUP_APPOINTMENTS",
                "Do I have an appointment with Dr Akshu on 24 September?", true);
    }

    @Test
    void trustedCancellationSourceDoctorAndDateOverrideConflictingProviderFacts() {
        assertTrustedLookupFacts("CANCEL_APPOINTMENT",
                "Cancel my appointment with Dr Akshu on 24 September", true);
    }

    @Test
    void trustedRescheduleSourceDoctorAndDateOverrideConflictingProviderFacts() {
        assertTrustedLookupFacts("RESCHEDULE_APPOINTMENT",
                "Reschedule my appointment with Dr Akshu on 24 September", true);
    }

    @Test
    void trustedLookupFactsFillProviderOmissionsForEverySourceResolutionOperation() {
        assertTrustedLookupFacts("LOOKUP_APPOINTMENTS",
                "Do I have an appointment with Dr Akshu on 24 September?", false);
        assertTrustedLookupFacts("CANCEL_APPOINTMENT",
                "Cancel my appointment with Dr Akshu on 24 September", false);
        assertTrustedLookupFacts("RESCHEDULE_APPOINTMENT",
                "Reschedule my appointment with Dr Akshu on 24 September", false);
    }

    @Test
    void providerAgreementPreservesTheSameLookupFacts() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false,
                sourceResolutionJson("LOOKUP_APPOINTMENTS", "Dr Akshu", "2026-09-24")));

        ConversationDecision decision = gateway.decide(
                gateway.normalizeTurn("Do I have an appointment with Dr Akshu on 24 September?", "en",
                        LocalDate.of(2026, 9, 10)), emptyContext());

        assertThat(decision.lookupFilter().doctorText().value()).isEqualTo("Dr Akshu");
        assertThat(decision.lookupFilter().dateExpression().value()).isEqualTo("2026-09-24");
    }

    @Test
    void activeCancellationChoicesUseTypedOrdinalAndExactTimeWithoutProvider() {
        SessionProjection context = cancellationAmbiguityContext();
        for (String[] input : new String[][]{{"en", "2"}, {"hi", "dusra"}, {"hi", "दूसरा"}}) {
            var turn = gateway.normalizeTurn(input[1], input[0], LocalDate.of(2026, 9, 1));
            ConversationDecision decision = gateway.decide(turn, context);
            assertThat(decision.operation()).as(input[1]).isEqualTo(Operation.CANCEL_APPOINTMENT);
            assertThat(decision.selection().ordinal()).as(input[1]).isEqualTo(2);
        }
        for (String[] input : new String[][]{{"en", "20:00"}, {"hi", "20:00 wali"},
                {"hi", "20:00 बजे की अपॉइंटमेंट रद्द करें"}}) {
            var turn = gateway.normalizeTurn(input[1], input[0], LocalDate.of(2026, 9, 1));
            ConversationDecision decision = gateway.decide(turn, context);
            assertThat(turn.exactTime()).as(input[1]).isEqualTo(LocalTime.of(20, 0));
            assertThat(decision.operation()).as(input[1]).isEqualTo(Operation.CANCEL_APPOINTMENT);
            assertThat(decision.selection().exactTime()).as(input[1]).isEqualTo("20:00");
        }
        org.mockito.Mockito.verifyNoInteractions(orchestration);
    }

    private void assertTrustedLookupFacts(String operation, String userText, boolean providerConflicts) {
        String providerDoctor = providerConflicts ? "Dr Mehta" : null;
        String providerDate = providerConflicts ? "2026-09-25" : null;
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false,
                sourceResolutionJson(operation, providerDoctor, providerDate)));

        var turn = gateway.normalizeTurn(userText, "en", LocalDate.of(2026, 9, 10));
        ConversationDecision decision = gateway.decide(turn, emptyContext());

        assertThat(turn.doctorEntity()).isNotNull();
        assertThat(turn.doctorEntity().canonicalQuery()).isEqualTo("Dr Akshu");
        assertThat(turn.temporal().localDate()).isEqualTo(LocalDate.of(2026, 9, 24));
        assertThat(decision.operation().name()).isEqualTo(operation);
        assertThat(decision.lookupFilter().doctorText().mode()).isEqualTo(AivaV2Models.PatchMode.SET);
        assertThat(decision.lookupFilter().doctorText().value()).isEqualTo("Dr Akshu");
        assertThat(decision.lookupFilter().dateExpression().mode()).isEqualTo(AivaV2Models.PatchMode.SET);
        assertThat(decision.lookupFilter().dateExpression().value()).isEqualTo("2026-09-24");
    }

    private String sourceResolutionJson(String operation, String doctor, String date) {
        String doctorField = doctor == null ? "" : "\"doctorText\":{\"mode\":\"SET\",\"value\":\"" + doctor + "\"},";
        String dateField = date == null ? "" : "\"dateExpression\":{\"mode\":\"SET\",\"value\":\"" + date + "\"},";
        return "{\"schemaVersion\":\"1.0\",\"dialogAct\":\"START_REQUEST\",\"operation\":\"" + operation
                + "\",\"lookupFilter\":{" + doctorField + dateField + "\"nextOnly\":false},\"confidence\":0.99}";
    }

    @Test
    void lookupMarksUnsupportedExplicitQualifierInsteadOfDroppingIt() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false, """
                {"schemaVersion":"1.0","dialogAct":"ASK_QUESTION","operation":"LOOKUP_APPOINTMENTS",
                 "confidence":0.95}
                """));

        ConversationDecision decision = gateway.decide("Do I have an appointment with Cardiology?", "en", emptyContext());

        assertThat(decision.operation()).isEqualTo(Operation.LOOKUP_APPOINTMENTS);
        assertThat(decision.unresolvedExplicitQualifierPresent()).isTrue();
        assertThat(decision.lookupFilter().doctorText().mode()).isEqualTo(AivaV2Models.PatchMode.UNCHANGED);
    }

    @Test
    void geminiMinimalLookupDefaultsOmittedOptionalFields() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false, """
                {"operation":"LOOKUP_APPOINTMENTS","dialogAct":"ASK_QUESTION",
                 "schemaVersion":"1.0","topicAction":"CONTINUE"}
                """));

        ConversationDecision decision = gateway.decide("What appointments do I have?", "en", emptyContext());

        assertThat(decision.operation()).isEqualTo(Operation.LOOKUP_APPOINTMENTS);
        assertThat(decision.dialogAct()).isEqualTo(AivaV2Models.DialogAct.ASK_QUESTION);
        assertThat(decision.confirmation()).isEqualTo(AivaV2Models.ConfirmationPolarity.NONE);
        assertThat(decision.lookupFilter()).isEqualTo(AivaV2Models.AppointmentLookupFilter.empty());
        assertThat(decision.confidence()).isEqualTo(1.0d);
    }

    @Test
    void groqMinimalLookupWithNullOptionalObjectsMatchesGemini() {
        when(orchestration.complete(any())).thenReturn(response("GROQ", false, """
                {"operation":"LOOKUP_APPOINTMENTS","dialogAct":"ASK_QUESTION",
                 "schemaVersion":"1.0","topicAction":"CONTINUE","confidence":1,
                 "confirmation":null,"lookupFilter":null,"bookingPatch":null,"selection":null}
                """));

        ConversationDecision decision = gateway.decide("What appointments do I have?", "en", emptyContext());

        assertThat(decision.operation()).isEqualTo(Operation.LOOKUP_APPOINTMENTS);
        assertThat(decision.confirmation()).isEqualTo(AivaV2Models.ConfirmationPolarity.NONE);
        assertThat(decision.lookupFilter()).isEqualTo(AivaV2Models.AppointmentLookupFilter.empty());
    }

    @Test
    void geminiMinimalCancellationDoesNotRequireLookupFilter() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false, """
                {"operation":"CANCEL_APPOINTMENT","dialogAct":"START_REQUEST",
                 "schemaVersion":"1.0","topicAction":"CONTINUE"}
                """));

        ConversationDecision decision = gateway.decide("Cancel my appointment", "en", emptyContext());

        assertThat(decision.operation()).isEqualTo(Operation.CANCEL_APPOINTMENT);
        assertThat(decision.confirmation()).isEqualTo(AivaV2Models.ConfirmationPolarity.NONE);
        assertThat(decision.lookupFilter()).isEqualTo(AivaV2Models.AppointmentLookupFilter.empty());
    }

    @Test
    void groqMinimalCancellationWithNullOptionalObjectsMatchesGemini() {
        when(orchestration.complete(any())).thenReturn(response("GROQ", false, """
                {"operation":"CANCEL_APPOINTMENT","dialogAct":"START_REQUEST",
                 "schemaVersion":"1.0","topicAction":"CONTINUE","confidence":1,
                 "confirmation":null,"lookupFilter":null,"bookingPatch":null,"selection":null}
                """));

        ConversationDecision decision = gateway.decide("Cancel my appointment", "en", emptyContext());

        assertThat(decision.operation()).isEqualTo(Operation.CANCEL_APPOINTMENT);
        assertThat(decision.confirmation()).isEqualTo(AivaV2Models.ConfirmationPolarity.NONE);
        assertThat(decision.lookupFilter()).isEqualTo(AivaV2Models.AppointmentLookupFilter.empty());
    }

    @Test
    void explicitNullConfidenceRemainsInvalid() {
        when(orchestration.complete(any())).thenReturn(response("GROQ", false, """
                {"operation":"LOOKUP_APPOINTMENTS","dialogAct":"ASK_QUESTION",
                 "schemaVersion":"1.0","confidence":null}
                """));

        assertThat(gateway.decide("What appointments do I have?", "en", emptyContext()).operation())
                .isEqualTo(Operation.UNKNOWN);
    }

    @Test
    void availabilityTimeConstraintIsParsedAsSearchCriteria() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false, """
                {"schemaVersion":"1.0","dialogAct":"PROVIDE_INFORMATION","operation":"GET_AVAILABILITY",
                 "bookingPatch":{"availabilityTimeConstraint":{"mode":"SET","value":{"mode":"AFTER","startTime":"20.00"}}},
                 "selection":{},"confirmation":"NONE","topicAction":"CONTINUE","confidence":0.95}
                """));

        ConversationDecision decision = gateway.decide("Do you have slots after 20:00?", "en", emptyContext());

        assertThat(decision.bookingPatch().availabilityTimeConstraint().mode()).isEqualTo(AivaV2Models.PatchMode.SET);
        assertThat(decision.bookingPatch().availabilityTimeConstraint().value().mode())
                .isEqualTo(AivaV2Models.AvailabilityTimeConstraintMode.AFTER);
        assertThat(decision.bookingPatch().availabilityTimeConstraint().value().startTime())
                .isEqualTo(LocalTime.of(20, 0));
        assertThat(decision.selection()).isNull();
    }

    @Test
    void availabilityTimeConstraintNormalizesSupportedTimeForms() {
        when(orchestration.complete(any())).thenReturn(
                response("GEMINI", false, constraintJson("BEFORE", "12:00", null)));

        ConversationDecision before = gateway.decide("before noon", "en", emptyContext());
        ConversationDecision between = gateway.decide("between 5 PM and 7 PM", "en", emptyContext());

        assertThat(before.bookingPatch().availabilityTimeConstraint().value().mode())
                .isEqualTo(AivaV2Models.AvailabilityTimeConstraintMode.BEFORE);
        assertThat(before.bookingPatch().availabilityTimeConstraint().value().endTime()).isEqualTo(LocalTime.NOON);
        assertThat(between.bookingPatch().availabilityTimeConstraint().value().startTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(between.bookingPatch().availabilityTimeConstraint().value().endTime()).isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    void relationalTimeFastPathProducesCanonicalSearchConstraints() {
        ConversationDecision between = gateway.decide("do you have slot after 6 pm and before 8 pm?", "en", emptyContext());
        ConversationDecision before = gateway.decide("do you have slot before 8 pm?", "en", emptyContext());

        assertThat(between.operation()).isEqualTo(Operation.GET_AVAILABILITY);
        assertThat(between.bookingPatch().availabilityTimeConstraint().value())
                .isEqualTo(new AivaV2Models.AvailabilityTimeConstraint(
                        AivaV2Models.AvailabilityTimeConstraintMode.BETWEEN,
                        LocalTime.of(18, 0), LocalTime.of(20, 0)));
        assertThat(before.bookingPatch().availabilityTimeConstraint().value())
                .isEqualTo(new AivaV2Models.AvailabilityTimeConstraint(
                        AivaV2Models.AvailabilityTimeConstraintMode.BEFORE,
                        null, LocalTime.of(20, 0)));
    }

    @Test
    void comparativeTimeFormsShareTheCanonicalConstraintContract() {
        assertConstraint("after 6 pm", AivaV2Models.AvailabilityTimeConstraintMode.AFTER,
                LocalTime.of(18, 0), null);
        assertConstraint("later than 6 pm", AivaV2Models.AvailabilityTimeConstraintMode.AFTER,
                LocalTime.of(18, 0), null);
        assertConstraint("before 8 pm", AivaV2Models.AvailabilityTimeConstraintMode.BEFORE,
                null, LocalTime.of(20, 0));
        assertConstraint("earlier than 8 pm", AivaV2Models.AvailabilityTimeConstraintMode.BEFORE,
                null, LocalTime.of(20, 0));
        assertConstraint("between 6 pm and 8 pm", AivaV2Models.AvailabilityTimeConstraintMode.BETWEEN,
                LocalTime.of(18, 0), LocalTime.of(20, 0));
        assertConstraint("after 6 pm and before 8 pm", AivaV2Models.AvailabilityTimeConstraintMode.BETWEEN,
                LocalTime.of(18, 0), LocalTime.of(20, 0));
        assertConstraint("later than 6 pm but earlier than 8 pm",
                AivaV2Models.AvailabilityTimeConstraintMode.BETWEEN,
                LocalTime.of(18, 0), LocalTime.of(20, 0));
        assertConstraint("from 6 pm to 8 pm", AivaV2Models.AvailabilityTimeConstraintMode.BETWEEN,
                LocalTime.of(18, 0), LocalTime.of(20, 0));
    }

    @Test
    void explicitRangesSupportEnglishHindiAndHinglishAndOverrideEveningBucket() {
        ConversationDecision english = gateway.decide("slot between 7 to 9", "en", eveningAvailabilityContext());
        ConversationDecision hindi = gateway.decide("7 से 8 के बीच में स्लॉट दिखाइए", "hi", eveningAvailabilityContext());
        ConversationDecision hinglish = gateway.decide("shaam 7 se 9", "hi", eveningAvailabilityContext());
        ConversationDecision meridiem = gateway.decide("7 p.m. to 9 p.m.", "en", eveningAvailabilityContext());

        for (ConversationDecision decision : List.of(english, hindi, hinglish, meridiem)) {
            assertThat(decision.operation()).isEqualTo(Operation.GET_AVAILABILITY);
            assertThat(decision.bookingPatch().timeWindow().mode()).isEqualTo(AivaV2Models.PatchMode.CLEAR);
            assertThat(decision.bookingPatch().availabilityTimeConstraint().value().mode())
                    .isEqualTo(AivaV2Models.AvailabilityTimeConstraintMode.BETWEEN);
        }
        assertThat(english.bookingPatch().availabilityTimeConstraint().value())
                .isEqualTo(new AivaV2Models.AvailabilityTimeConstraint(
                        AivaV2Models.AvailabilityTimeConstraintMode.BETWEEN,
                        LocalTime.of(19, 0), LocalTime.of(21, 0)));
        assertThat(hindi.bookingPatch().availabilityTimeConstraint().value())
                .isEqualTo(new AivaV2Models.AvailabilityTimeConstraint(
                        AivaV2Models.AvailabilityTimeConstraintMode.BETWEEN,
                        LocalTime.of(19, 0), LocalTime.of(20, 0)));
    }

    @Test
    void contextualExclusionsUseCanonicalConstraints() {
        ConversationDecision exact = gateway.decide("not 7 pm", "en", eveningAvailabilityContext());
        ConversationDecision hindi = gateway.decide("morning nahi", "hi", eveningAvailabilityContext());

        assertThat(exact.operation()).isEqualTo(Operation.GET_AVAILABILITY);
        assertThat(exact.bookingPatch().availabilityTimeConstraint().value()).isEqualTo(
                new AivaV2Models.AvailabilityTimeConstraint(
                        AivaV2Models.AvailabilityTimeConstraintMode.EXCLUDE, LocalTime.of(19, 0), null));
        assertThat(hindi.operation()).isEqualTo(Operation.GET_AVAILABILITY);
        assertThat(hindi.bookingPatch().availabilityTimeConstraint().value()).isEqualTo(
                new AivaV2Models.AvailabilityTimeConstraint(
                        AivaV2Models.AvailabilityTimeConstraintMode.EXCLUDE, LocalTime.MIDNIGHT, LocalTime.NOON));
    }

    private void assertConstraint(String text, AivaV2Models.AvailabilityTimeConstraintMode mode,
                                  LocalTime start, LocalTime end) {
        ConversationDecision decision = gateway.decide(text, "en", emptyContext());
        assertThat(decision.operation()).isEqualTo(Operation.GET_AVAILABILITY);
        assertThat(decision.bookingPatch().availabilityTimeConstraint().value())
                .isEqualTo(new AivaV2Models.AvailabilityTimeConstraint(mode, start, end));
    }

    @Test
    void exactTimeSearchRemainsProviderBacked() {
        when(orchestration.complete(any())).thenReturn(
                response("GEMINI", false, constraintJson("EXACT", "17.30", null)));

        ConversationDecision exact = gateway.decide("at 17:30", "en", emptyContext());

        assertThat(exact.bookingPatch().availabilityTimeConstraint().value())
                .isEqualTo(new AivaV2Models.AvailabilityTimeConstraint(
                        AivaV2Models.AvailabilityTimeConstraintMode.EXACT,
                        LocalTime.of(17, 30), null));
    }

    private String constraintJson(String mode, String start, String end) {
        return "{\"schemaVersion\":\"1.0\",\"dialogAct\":\"PROVIDE_INFORMATION\",\"operation\":\"GET_AVAILABILITY\","
                + "\"bookingPatch\":{\"availabilityTimeConstraint\":{\"mode\":\"SET\",\"value\":{"
                + "\"mode\":\"" + mode + "\",\"startTime\":\"" + start + "\""
                + (end == null ? "" : ",\"endTime\":\"" + end + "\"")
                + "}}},\"selection\":{},\"confirmation\":\"NONE\",\"topicAction\":\"CONTINUE\",\"confidence\":0.95}";
    }

    @Test
    void acceptedSlotTimeUsesSelectionExactTimeNotSearchPatch() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false,
                "{\"schemaVersion\":\"1.0\",\"dialogAct\":\"SELECT_OPTION\","
                        + "\"operation\":\"SELECT_SLOT\",\"bookingPatch\":{},"
                        + "\"selection\":{\"exactTime\":\"11:00\"},"
                        + "\"confirmation\":\"NONE\",\"topicAction\":\"CONTINUE\","
                        + "\"confidence\":0.95}"));

        ConversationDecision decision = gateway.decide("11:00 works", "en", emptyContext());

        assertThat(decision.operation()).isEqualTo(Operation.SELECT_SLOT);
        assertThat(decision.selection().exactTime()).isEqualTo("11:00");
        assertThat(decision.bookingPatch().exactTime().mode()).isEqualTo(AivaV2Models.PatchMode.UNCHANGED);
    }

    @Test
    void localizedRelativeDateIsResolvedByTheAdapterWithoutSemanticProvider() {
        when(orchestration.complete(any())).thenReturn(response("GROQ", true, """
                {"schemaVersion":"1.0","dialogAct":"CHANGE_INFORMATION","operation":"UPDATE_BOOKING",
                 "bookingPatch":{"doctorText":{"mode":"UNCHANGED"},"specialtyText":{"mode":"UNCHANGED"},
                 "dateExpression":{"mode":"SET","value":"2026-09-14"},"timeWindow":{"mode":"UNCHANGED"},
                 "exactTime":{"mode":"UNCHANGED"}},"confirmation":"NONE","topicAction":"CONTINUE",
                 "responseLanguage":"hi","confidence":0.93}
                """));

        ConversationDecision decision = gateway.decide("अगले सोमवार", "hi", emptyContext());

        assertThat(decision.provider()).isEqualTo("DETERMINISTIC");
        assertThat(decision.fallbackUsed()).isFalse();
        assertThat(decision.bookingPatch().dateExpression().value()).isEqualTo("2026-09-14");
        org.mockito.Mockito.verifyNoInteractions(orchestration);
    }

    @Test
    void invalidProviderOutputBecomesUnknownAndCannotMutateState() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false,
                "{\"schemaVersion\":\"2.0\",\"operation\":\"CONFIRM_BOOKING\",\"confidence\":1.0}"));

        ConversationDecision decision = gateway.decide("Please do it", "en", emptyContext());

        assertThat(decision.operation()).isEqualTo(Operation.UNKNOWN);
        assertThat(decision.confidence()).isZero();
    }

    @Test
    void directRootDecisionIsAccepted() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false, decisionJson()));

        ConversationDecision decision = gateway.decide("Book Akshu tomorrow", "en", emptyContext());

        assertThat(decision.operation()).isEqualTo(Operation.START_BOOKING);
        assertThat(decision.bookingPatch().doctorText().value()).isEqualTo("Dr. Akshu Kumar");
    }

    @Test
    void activeBookingContextIncludesBoundedDraftValuesAndPendingProviderFieldForSemanticSpecialty() {
        Instant now = Instant.parse("2026-09-10T00:00:00Z");
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), UUID.randomUUID(), "tenant", null, null,
                LocalDate.of(2026, 9, 22), null, null, null, null,
                AivaV2Models.DraftStatus.COLLECTING, 2, now.plusSeconds(1800), null);
        SessionProjection context = new SessionProjection("c1", draft.patientSubjectId(), draft.tenantScope(),
                draft, null, null, null, null, 1, now.plusSeconds(1800));
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false, """
                {"schemaVersion":"1.0","dialogAct":"PROVIDE_INFORMATION","operation":"UPDATE_BOOKING",
                 "bookingPatch":{"specialtyText":{"mode":"SET","value":"General Medicine"}},
                 "responseLanguage":"hi","confidence":0.95}
                """));

        ConversationDecision decision = gateway.decide("General Medicine", "hi", context);

        ArgumentCaptor<com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest> captor =
                ArgumentCaptor.forClass(com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest.class);
        verify(orchestration).complete(captor.capture());
        @SuppressWarnings("unchecked")
        var bookingContext = (java.util.Map<String, Object>) captor.getValue().inputVariables().get("bookingContext");
        @SuppressWarnings("unchecked")
        var bookingDraft = (java.util.Map<String, Object>) bookingContext.get("bookingDraft");

        assertThat(bookingContext).containsEntry("activeWorkflow", "BOOKING")
                .containsEntry("pendingField", "PROVIDER_OR_SPECIALTY");
        assertThat(bookingDraft).containsEntry("providerName", null)
                .containsEntry("specialty", null)
                .containsEntry("date", "2026-09-22")
                .containsEntry("timeWindow", null)
                .containsEntry("exactTime", null);
        assertThat(decision.operation()).isEqualTo(Operation.UPDATE_BOOKING);
        assertThat(decision.bookingPatch().specialtyText().value()).isEqualTo("General Medicine");
    }

    @Test
    void devanagariDoctorEntityIsRecoveredBeforeProviderResolution() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false, """
                {"dialogAct":"START_REQUEST","operation":"START_BOOKING","schemaVersion":"1.0",
                 "bookingPatch":{"doctorText":{"mode":"UNCHANGED"}},"responseLanguage":"hi"}
                """));

        ConversationDecision decision = gateway.decide(
                "मुझे डॉ. अक्षु के साथ अपॉइंटमेंट बुक करनी है।", "hi", emptyContext());

        assertThat(decision.operation()).isEqualTo(Operation.START_BOOKING);
        assertThat(decision.bookingPatch().doctorText().value()).isEqualTo("Dr Akshu");
    }

    @Test
    void unsolicitedDevanagariBookingRequestCannotBecomeConfirmation() {
        when(orchestration.complete(any())).thenReturn(response("SARVAM", false, """
                {"dialogAct":"CONFIRM","operation":"CONFIRM_BOOKING","confirmation":"POSITIVE",
                 "schemaVersion":"1.0","responseLanguage":"hi","bookingPatch":{}}
                """));

        ConversationDecision decision = gateway.decide(
                "मुझे डॉ. अक्षु के साथ अपॉइंटमेंट बुक करनी है।", "hi", emptyContext());

        assertThat(decision.operation()).isEqualTo(Operation.START_BOOKING);
        assertThat(decision.confirmation()).isEqualTo(AivaV2Models.ConfirmationPolarity.NONE);
        assertThat(decision.bookingPatch().doctorText().value()).isEqualTo("Dr Akshu");
    }

    @Test
    void decisionEnvelopeIsAccepted() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false,
                "{\"decision\":" + decisionJson() + "}"));

        ConversationDecision decision = gateway.decide("Book Akshu tomorrow", "en", emptyContext());

        assertThat(decision.operation()).isEqualTo(Operation.START_BOOKING);
    }

    @Test
    void answerEnvelopeIsAccepted() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false,
                "{\"answer\":" + decisionJson() + "}"));

        ConversationDecision decision = gateway.decide("Book an appointment with Dr. Akshu Kumar tomorrow.", "en", emptyContext());

        assertThat(decision.operation()).isEqualTo(Operation.START_BOOKING);
        assertThat(decision.bookingPatch().doctorText().value()).isEqualTo("Dr. Akshu Kumar");
        // The adapter's clinic-local resolution of "tomorrow" outranks a conflicting provider date.
        assertThat(decision.bookingPatch().dateExpression().value()).isEqualTo("2026-09-11");
    }

    @Test
    void exactRuntimePayloadAcceptsAbsentOptionalFieldsWithDefaults() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false, """
                {"bookingPatch":{"dateExpression":{"mode":"SET","value":"2026-09-14"},
                 "doctorText":{"mode":"SET","value":"Dr Akshu"}},"confidence":1,
                 "dialogAct":"START_REQUEST","operation":"START_BOOKING",
                 "responseLanguage":"auto","schemaVersion":"1.0"}
                """));

        ConversationDecision decision = gateway.decide("Book Dr Akshu on 14 September 2026", "en", emptyContext());

        assertThat(decision.operation()).isEqualTo(Operation.START_BOOKING);
        assertThat(decision.dialogAct()).isEqualTo(AivaV2Models.DialogAct.START_REQUEST);
        assertThat(decision.confidence()).isEqualTo(1.0d);
        assertThat(decision.bookingPatch().doctorText().value()).isEqualTo("Dr Akshu");
        assertThat(decision.bookingPatch().dateExpression().value()).isEqualTo("2026-09-14");
        assertThat(decision.confirmation()).isEqualTo(AivaV2Models.ConfirmationPolarity.NONE);
        assertThat(decision.topicAction()).isEqualTo(AivaV2Models.TopicAction.CONTINUE);
    }

    @Test
    void explicitOptionalEnumsAreAccepted() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false,
                "{" + "\"schemaVersion\":\"1.0\",\"dialogAct\":\"CONFIRM\","
                        + "\"operation\":\"CONFIRM_BOOKING\",\"confirmation\":\"POSITIVE\","
                        + "\"topicAction\":\"SUSPEND\",\"confidence\":1}"));

        ConversationDecision decision = gateway.decide("Yes", "en", emptyContext());

        assertThat(decision.confirmation()).isEqualTo(AivaV2Models.ConfirmationPolarity.POSITIVE);
        assertThat(decision.topicAction()).isEqualTo(AivaV2Models.TopicAction.SUSPEND);
    }

    @Test
    void explicitNullOptionalFieldsUseSafeDefaults() {
        when(orchestration.complete(any())).thenReturn(response("GROQ", true, """
                {"schemaVersion":"1.0","dialogAct":"START_REQUEST","operation":"START_BOOKING",
                 "bookingPatch":null,"selection":null,"confirmation":null,"topicAction":null,
                 "responseLanguage":"auto","confidence":1}
                """));

        ConversationDecision decision = gateway.decide("Book an appointment", "en", emptyContext());

        assertThat(decision.operation()).isEqualTo(Operation.START_BOOKING);
        assertThat(decision.confirmation()).isEqualTo(AivaV2Models.ConfirmationPolarity.NONE);
        assertThat(decision.topicAction()).isEqualTo(AivaV2Models.TopicAction.CONTINUE);
    }

    @Test
    void invalidOptionalEnumsAreRejected() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false,
                "{\"schemaVersion\":\"1.0\",\"dialogAct\":\"START_REQUEST\","
                        + "\"operation\":\"START_BOOKING\",\"confirmation\":\"INVALID\","
                        + "\"confidence\":1}"));

        assertThat(gateway.decide("Book an appointment", "en", emptyContext()).operation())
                .isEqualTo(Operation.UNKNOWN);
    }

    @Test
    void malformedAnswerEnvelopeIsRejected() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false,
                "{\"answer\":{\"schemaVersion\":\"1.0\",\"operation\":\"START_BOOKING\"}}"));

        ConversationDecision decision = gateway.decide("Book an appointment", "en", emptyContext());

        assertThat(decision.operation()).isEqualTo(Operation.UNKNOWN);
    }

    @Test
    void unknownEnumIsRejectedWithoutMutationDecision() {
        when(orchestration.complete(any())).thenReturn(response("GEMINI", false,
                "{\"schemaVersion\":\"1.0\",\"dialogAct\":\"BAD\",\"operation\":\"START_BOOKING\","
                        + "\"bookingPatch\":{},\"confirmation\":\"NONE\",\"topicAction\":\"CONTINUE\",\"confidence\":1.0}"));

        ConversationDecision decision = gateway.decide("Start a consultation", "en", emptyContext());

        assertThat(decision.operation()).isEqualTo(Operation.UNKNOWN);
    }

    @Test
    void explicitBookingRecoversFromStaleProviderSearchState() {
        ConversationDecision decision = gateway.decide("Book an appointment", "en",
                providerContext(new ProviderSearchResult(UUID.randomUUID(), AivaV2Models.ResolutionStatus.NOT_FOUND,
                        "stale", List.of(), null, Instant.now(), Instant.now().plusSeconds(300))));

        assertThat(decision.operation()).isEqualTo(Operation.START_BOOKING);
        assertThat(decision.provider()).isEqualTo("DETERMINISTIC");
        verifyNoInteractions(orchestration);
    }

    private SessionProjection emptyContext() {
        return new SessionProjection("c1", UUID.randomUUID(), UUID.randomUUID().toString(), null, null,
                null, null, null, 1, Instant.now().plusSeconds(600));
    }

    private SessionProjection pendingCancellationConfirmationContext() {
        Instant expires = Instant.now().plusSeconds(300);
        var pending = new AivaV2Models.CancellationConfirmation("confirmation", UUID.randomUUID(),
                "appointment-20-30", "Doc Akshu Kumar", "Clinic", LocalDate.of(2026, 9, 24),
                LocalTime.of(20, 30), expires, "cancel-idempotency");
        return new SessionProjection("c1", UUID.randomUUID(), "tenant", null, null, null, null,
                null, pending, null, null, null, 1, expires);
    }

    private SessionProjection cancellationAmbiguityContext() {
        UUID patient = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(300);
        var candidates = List.of(
                new AivaV2Models.AppointmentSummary("apt-1", "Doc Akshu Kumar", "Clinic",
                        LocalDate.of(2026, 9, 24), LocalTime.of(20, 0), "CONFIRMED", null),
                new AivaV2Models.AppointmentSummary("apt-2", "Doc Akshu Kumar", "Clinic",
                        LocalDate.of(2026, 9, 24), LocalTime.of(20, 30), "CONFIRMED", null));
        var criteria = new AivaV2Models.AppointmentLookupFilter(AivaV2Models.ValuePatch.set("Dr Akshu"),
                AivaV2Models.ValuePatch.set("2026-09-24"), AivaV2Models.ValuePatch.unchanged(), null, false);
        var resolution = new AivaV2Models.CancellationResolution(criteria, candidates,
                Instant.now(), expiresAt);
        return new SessionProjection("c1", patient, UUID.randomUUID().toString(), null, null, null, null,
                null, null, resolution, null, null, 1, expiresAt);
    }

    private SessionProjection providerDisambiguationContext() {
        return providerContext(providerResult(List.of(
                providerCandidate("akshu", "Doc Akshu Kumar"),
                providerCandidate("uat", "Doc UAT Automation Doctor"),
                providerCandidate("demo", "Demo Doctor"))));
    }

    private SessionProjection providerContext(ProviderSearchResult result) {
        return new SessionProjection("c1", UUID.randomUUID(), "tenant", null, null,
                result, null, null, 1, Instant.now().plusSeconds(600));
    }

    private ProviderSearchResult providerResult(List<ProviderCandidate> candidates) {
        return new ProviderSearchResult(UUID.randomUUID(), AivaV2Models.ResolutionStatus.AMBIGUOUS,
                "criteria", candidates, null, Instant.now(), Instant.now().plusSeconds(600));
    }

    private ProviderCandidate providerCandidate(String handle, String displayName) {
        return new ProviderCandidate(handle, handle, handle, null, "tenant", null, null,
                displayName, null, null, AivaV2Models.ProviderSource.CARE_PRIVATE,
                new AivaV2Models.ProviderCapabilities(true, true, false, false, false, true, false));
    }

    private SessionProjection availabilityContext() {
        AvailabilityResult availability = new AvailabilityResult(UUID.randomUUID(), UUID.randomUUID(), 1,
                "criteria", "provider", "doctor", "clinic", LocalDate.of(2026, 9, 14), null,
                "Asia/Kolkata", List.of(
                        new AvailabilitySlot("slot-1", LocalTime.of(10, 30), LocalTime.of(11, 0), "10:30"),
                        new AvailabilitySlot("slot-2", LocalTime.of(11, 0), LocalTime.of(11, 30), "11:00"),
                        new AvailabilitySlot("slot-3", LocalTime.of(17, 30), LocalTime.of(18, 0), "17:30")),
                null, false, Instant.now(), Instant.now().plusSeconds(300));
        return new SessionProjection("c1", UUID.randomUUID(), "tenant", null, null, null,
                availability, null, 1, Instant.now().plusSeconds(600));
    }

    private SessionProjection currentTimePageContext() {
        BookingDraft draft = BookingDraft.create(UUID.randomUUID(), UUID.randomUUID().toString(), Instant.now());
        AvailabilityResult availability = new AvailabilityResult(UUID.randomUUID(), draft.draftId(), draft.revision(),
                "criteria", "provider", "doctor", "clinic", LocalDate.of(2026, 9, 28), "evening",
                "Asia/Kolkata", List.of(
                        new AvailabilitySlot("slot-1", LocalTime.of(20, 30), LocalTime.of(21, 0), "20:30"),
                        new AvailabilitySlot("slot-2", LocalTime.of(21, 0), LocalTime.of(21, 30), "21:00")),
                null, false, Instant.now(), Instant.now().plusSeconds(300));
        return new SessionProjection("c1", draft.patientSubjectId(), draft.tenantScope(), draft, null, null,
                availability, null, 1, Instant.now().plusSeconds(600));
    }

    private SessionProjection eveningAvailabilityContext() {
        AvailabilityResult availability = new AvailabilityResult(UUID.randomUUID(), UUID.randomUUID(), 1,
                "criteria", "provider", "doctor", "clinic", LocalDate.of(2026, 9, 28), "evening",
                "Asia/Kolkata", List.of(
                        new AvailabilitySlot("slot-1", LocalTime.of(17, 0), LocalTime.of(17, 30), "17:00")),
                null, false, Instant.now(), Instant.now().plusSeconds(300));
        return new SessionProjection("c1", UUID.randomUUID(), "tenant", null, null, null,
                availability, null, 1, Instant.now().plusSeconds(600));
    }

    private SessionProjection noAvailabilityBookingContext() {
        Instant now = Instant.parse("2026-09-10T00:00:00Z");
        ProviderCandidate provider = providerCandidate("provider", "Doc Akshu Kumar");
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), UUID.randomUUID(), "tenant", provider, null,
                LocalDate.of(2026, 9, 26), null, null, null, null,
                AivaV2Models.DraftStatus.COLLECTING, 1, now.plusSeconds(1800), null);
        AvailabilityResult empty = new AvailabilityResult(UUID.randomUUID(), draft.draftId(), draft.revision(),
                "criteria", provider.providerHandle(), provider.doctorId(), provider.clinicId(), draft.preferredDate(),
                null, "Asia/Kolkata", List.of(), null, false, now, now.plusSeconds(300));
        return new SessionProjection("c1", draft.patientSubjectId(), "tenant", draft, null, null,
                empty, null, 1, now.plusSeconds(1800));
    }

    private SessionProjection availabilityConfirmationContext() {
        SessionProjection context = availabilityContext();
        BookingConfirmation confirmation = new BookingConfirmation("confirmation", UUID.randomUUID(), 1,
                "provider", "doctor", "clinic", UUID.randomUUID(), "slot-1",
                LocalDate.of(2026, 9, 24), LocalTime.of(20, 30), Instant.now().plusSeconds(600), "idempotency");
        return new SessionProjection(context.conversationId(), context.patientId(), context.tenantId(), null, null,
                null, context.latestAvailabilityResult(), confirmation, 1, Instant.now().plusSeconds(600));
    }

    private SessionProjection rescheduleContext() {
        var state = new AivaV2Models.RescheduleResolution("appointment", "Dr Akshu", "doctor-akshu",
                "doctor-akshu", "clinic", "tenant", "clinic", LocalDate.of(2026, 9, 14),
                LocalTime.of(17, 30), LocalDate.of(2026, 9, 16), null, null, null, null, null,
                AivaV2Models.RescheduleStatus.TARGET_COLLECTING, 1, Instant.now(), Instant.now().plusSeconds(600));
        return new SessionProjection("c1", UUID.randomUUID(), "tenant", null, null, null, null, null,
                null, null, state, null, 1, Instant.now().plusSeconds(600));
    }

    private SessionProjection rescheduleAvailabilityContext() {
        AvailabilityResult availability = new AvailabilityResult(UUID.randomUUID(), UUID.randomUUID(), 1,
                "criteria", "provider", "doctor", "clinic", LocalDate.of(2026, 9, 18), "evening", null,
                "Asia/Kolkata", List.of(
                        new AvailabilitySlot("slot-1", LocalTime.of(17, 30), LocalTime.of(18, 0), "17:30"),
                        new AvailabilitySlot("slot-2", LocalTime.of(18, 0), LocalTime.of(18, 30), "18:00"),
                        new AvailabilitySlot("slot-3", LocalTime.of(18, 30), LocalTime.of(19, 0), "18:30"),
                        new AvailabilitySlot("slot-4", LocalTime.of(19, 0), LocalTime.of(19, 30), "19:00"),
                        new AvailabilitySlot("slot-5", LocalTime.of(19, 30), LocalTime.of(20, 0), "19:30"),
                        new AvailabilitySlot("slot-6", LocalTime.of(20, 0), LocalTime.of(20, 30), "20:00"),
                        new AvailabilitySlot("slot-7", LocalTime.of(20, 30), LocalTime.of(21, 0), "20:30")),
                null, true, Instant.now(), Instant.now().plusSeconds(600), 3, 3);
        var state = new AivaV2Models.RescheduleResolution("appointment", "Dr Akshu", "doctor-akshu",
                "doctor-akshu", "clinic", "tenant", "clinic", LocalDate.of(2026, 9, 16),
                LocalTime.of(20, 0), LocalDate.of(2026, 9, 18), null, availability.requestId(), availability,
                null, null, AivaV2Models.RescheduleStatus.AVAILABILITY_READY, 2, Instant.now(), Instant.now().plusSeconds(600));
        return new SessionProjection("c1", UUID.randomUUID(), "tenant", null, null, null, null, null,
                null, null, state, null, 1, Instant.now().plusSeconds(600));
    }

    private SessionProjection rescheduleAvailabilityContextWithEveningConstraint() {
        AvailabilityTimeConstraint evening = new AvailabilityTimeConstraint(
                AivaV2Models.AvailabilityTimeConstraintMode.AFTER, LocalTime.of(17, 0), null);
        var state = new AivaV2Models.RescheduleResolution("appointment", "Dr Akshu", "doctor-akshu",
                "doctor-akshu", "clinic", "tenant", "clinic", LocalDate.of(2026, 9, 16),
                LocalTime.of(20, 0), LocalDate.of(2026, 9, 18), evening, null, null, null, null,
                AivaV2Models.RescheduleStatus.AVAILABILITY_READY, 2, Instant.now(), Instant.now().plusSeconds(600));
        return new SessionProjection("c1", UUID.randomUUID(), "tenant", null, null, null, null, null,
                null, null, state, null, 1, Instant.now().plusSeconds(600));
    }

    private SessionProjection pendingContext() {
        BookingDraft draft = BookingDraft.create(UUID.randomUUID(), UUID.randomUUID().toString(), Instant.now());
        BookingConfirmation confirmation = new BookingConfirmation("confirm", draft.draftId(), draft.revision(),
                "provider", "doctor", null, UUID.randomUUID(), "slot", LocalDate.now().plusDays(1),
                LocalTime.of(16, 30), Instant.now().plusSeconds(300), "idem");
        return new SessionProjection("c1", draft.patientSubjectId(), draft.tenantScope(), draft, null,
                null, null, confirmation, 1, Instant.now().plusSeconds(600));
    }

    private AiOrchestrationResponse response(String provider, boolean fallback, String json) {
        return new AiOrchestrationResponse(UUID.randomUUID(), UUID.randomUUID(), AiProductCode.GENERIC,
                AiTaskType.GENERIC_EXTRACTION, provider, "model", json, json, BigDecimal.ONE,
                List.of(), List.of(), List.of(), null, 20L, fallback, null, "STOP");
    }

    private String decisionJson() {
        return "{\"schemaVersion\":\"1.0\",\"dialogAct\":\"START_REQUEST\",\"operation\":\"START_BOOKING\","
                + "\"bookingPatch\":{\"doctorText\":{\"mode\":\"SET\",\"value\":\"Dr. Akshu Kumar\"},"
                + "\"specialtyText\":{\"mode\":\"UNCHANGED\"},\"dateExpression\":{\"mode\":\"SET\",\"value\":\"2026-09-12\"},"
                + "\"timeWindow\":{\"mode\":\"UNCHANGED\"},\"exactTime\":{\"mode\":\"UNCHANGED\"}},"
                + "\"selection\":{},\"confirmation\":\"NONE\",\"topicAction\":\"CONTINUE\","
                + "\"responseLanguage\":\"en\",\"confidence\":0.95}";
    }
}
