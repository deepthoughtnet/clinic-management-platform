package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingDraft;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConversationDecision;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilitySlot;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Operation;
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
    void shortNaturalSlotAcceptancesUseSelectionFastPath() {
        SessionProjection context = availabilityContext();

        for (String text : List.of("11:00 works", "17:30 works", "2 works", "2nd slot works for me")) {
            ConversationDecision decision = gateway.decide(text, "en", context);

            assertThat(decision.operation()).isEqualTo(Operation.SELECT_SLOT);
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
    void showMoreIsAGenericControlInsideReschedule() {
        ConversationDecision decision = gateway.decide("show me next slots", "en", rescheduleContext());

        assertThat(decision.operation()).isEqualTo(Operation.SHOW_MORE_SLOTS);
        assertThat(decision.provider()).isEqualTo("DETERMINISTIC");
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
    void groqFallbackMustConformToSameVersionedSchema() {
        when(orchestration.complete(any())).thenReturn(response("GROQ", true, """
                {"schemaVersion":"1.0","dialogAct":"CHANGE_INFORMATION","operation":"UPDATE_BOOKING",
                 "bookingPatch":{"doctorText":{"mode":"UNCHANGED"},"specialtyText":{"mode":"UNCHANGED"},
                 "dateExpression":{"mode":"SET","value":"2026-09-14"},"timeWindow":{"mode":"UNCHANGED"},
                 "exactTime":{"mode":"UNCHANGED"}},"confirmation":"NONE","topicAction":"CONTINUE",
                 "responseLanguage":"hi","confidence":0.93}
                """));

        ConversationDecision decision = gateway.decide("अगले सोमवार", "hi", emptyContext());

        assertThat(decision.provider()).isEqualTo("GROQ");
        assertThat(decision.fallbackUsed()).isTrue();
        assertThat(decision.bookingPatch().dateExpression().value()).isEqualTo("2026-09-14");
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
        assertThat(decision.bookingPatch().dateExpression().value()).isEqualTo("2026-09-12");
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

        ConversationDecision decision = gateway.decide("Book an appointment", "en", emptyContext());

        assertThat(decision.operation()).isEqualTo(Operation.UNKNOWN);
    }

    private SessionProjection emptyContext() {
        return new SessionProjection("c1", UUID.randomUUID(), UUID.randomUUID().toString(), null, null,
                null, null, null, 1, Instant.now().plusSeconds(600));
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
                        new AvailabilitySlot("slot-6", LocalTime.of(20, 0), LocalTime.of(20, 30), "20:00")),
                null, true, Instant.now(), Instant.now().plusSeconds(600), 3, 3);
        var state = new AivaV2Models.RescheduleResolution("appointment", "Dr Akshu", "doctor-akshu",
                "doctor-akshu", "clinic", "tenant", "clinic", LocalDate.of(2026, 9, 16),
                LocalTime.of(20, 0), LocalDate.of(2026, 9, 18), null, availability.requestId(), availability,
                null, null, AivaV2Models.RescheduleStatus.AVAILABILITY_READY, 2, Instant.now(), Instant.now().plusSeconds(600));
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
