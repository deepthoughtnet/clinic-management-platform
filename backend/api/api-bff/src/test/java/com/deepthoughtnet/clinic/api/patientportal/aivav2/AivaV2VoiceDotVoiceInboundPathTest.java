package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.api.carepilot.CarePilotMessagingStatusService;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingPatch;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilitySlot;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityTimeConstraint;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityTimeConstraintMode;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConfirmationPolarity;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConversationDecision;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.DialogAct;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Operation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCapabilities;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSearchResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ResolutionStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.TopicAction;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ValuePatch;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.LanguageAdapterRegistry;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn;
import com.deepthoughtnet.clinic.api.patientportal.auth.PatientPortalOtpService;
import com.deepthoughtnet.clinic.api.patientportal.booking.BookingExecutionContextFactory;
import com.deepthoughtnet.clinic.api.patientportal.booking.AppointmentBookingApplicationService;
import com.deepthoughtnet.clinic.api.patientportal.booking.VoiceBookingExecutionService;
import com.deepthoughtnet.clinic.api.reliability.service.IdempotencyService;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiReadOnlyToolGateway;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiDoctorOption;
import com.deepthoughtnet.clinic.api.platform.communicationtest.CommunicationTestService;
import com.deepthoughtnet.clinic.api.platform.communicationtest.CommunicationTestDtos.InboundVoiceResult;
import com.deepthoughtnet.clinic.api.platform.communicationtest.ReminderVoiceRenderer;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceIdentityTrust;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceReadOnlyAivaService;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceTransactionAuthProperties;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceTransactionPolicyResolver;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceOperatorProperties;
import com.deepthoughtnet.clinic.api.voice.VoiceOrchestratorService;
import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionResult;
import com.deepthoughtnet.clinic.carepilot.ai_call.provider.DotVoiceMediaStreamClient;
import com.deepthoughtnet.clinic.carepilot.ai_call.service.VoiceConversationContextRegistry;
import com.deepthoughtnet.clinic.carepilot.ai_call.service.VoiceResponseInterpreter;
import com.deepthoughtnet.clinic.carepilot.messaging.service.MessageOrchestratorService;
import com.deepthoughtnet.clinic.communication.service.CommunicationChannelResolver;
import com.deepthoughtnet.clinic.communication.service.CommunicationInteractionService;
import com.deepthoughtnet.clinic.communication.service.CommunicationChannelModels.Resolution;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotRecord;
import com.deepthoughtnet.clinic.tts.spi.SpeechSynthesisResult;
import com.deepthoughtnet.clinic.api.publicsite.PublicCatalogFacade;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorSlotResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** Exercises the same final-STT inbound service entry used by DotVoice, not an adapter-only call. */
class AivaV2VoiceDotVoiceInboundPathTest {
    private static final UUID TENANT = UUID.fromString("a077d945-48e1-4ee5-a508-06d8599b89c0");
    private static final UUID PATIENT = UUID.fromString("b9c3c41f-3298-4caa-8eaf-9ce869cc96d1");
    private static final UUID CLINIC = UUID.fromString("5bfdfcc1-d91f-4f16-906b-a64fcfcc617a");
    private static final UUID DOCTOR = UUID.fromString("1f1f8569-66a4-45b9-9e98-c48131b69f9c");

    @Test
    void authorizedDotVoiceBooksWithBothSharedChannelFlagsOff() throws IOException {
        var application = new org.springframework.boot.env.YamlPropertySourceLoader()
                .load("application", new org.springframework.core.io.ClassPathResource("application.yml")).getFirst();
        assertThat(application.getProperty("aiva.v2.enabled")).isEqualTo("${AIVA_V2_ENABLED:false}");
        assertThat(application.getProperty("voice.aiva-v2-enabled")).isEqualTo("${VOICE_AIVA_V2_ENABLED:false}");

        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए।", "कल।", "10 works", "हाँ कर दो"));

        assertThat(harness.result().conversationTurns().get(3).toolResultSummary()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(harness.sessions().find("VOICE|" + TENANT + "|" + harness.result().callId())
                .orElseThrow().completedBookingOutcome()).isNotNull();
        verify(harness.appointments(), org.mockito.Mockito.times(1))
                .createScheduled(eq(TENANT), any(), eq(null), eq(false), any(), any());
        verifyNoSemanticAi(harness);
    }

    @Test
    void authorizedDotVoiceKeepsDraftConfirmationAndCompletedOutcomeTenantScopedForSameCallId() {
        AivaV2SessionStore sessions = new AivaV2SessionStore(Clock.systemUTC());
        String sharedCallId = "phase8b-shared-voice-call";
        UUID tenantB = UUID.randomUUID();
        UUID patientB = UUID.randomUUID();
        UUID clinicB = UUID.randomUUID();
        UUID doctorB = UUID.randomUUID();

        InboundHarness tenantAStart = runInboundSequence(List.of(
                        "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल"),
                false, true, List.of(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0)),
                List.of(), false, null, false, TENANT, PATIENT, CLINIC, DOCTOR, sharedCallId, sessions);
        String keyA = "VOICE|" + TENANT + "|" + sharedCallId;
        SessionProjection tenantAAwaitSlot = sessions.find(keyA).orElseThrow();
        assertThat(AivaV2VoiceSlotContext.isAwaitSlot(tenantAAwaitSlot, Instant.now())).isTrue();
        assertThat(tenantAAwaitSlot.tenantId()).isEqualTo(TENANT.toString());

        InboundHarness tenantBProbe = runInboundSequence(List.of("हाँ कर दो"), false, true, null, List.of(),
                false, null, false, tenantB, patientB, clinicB, doctorB, sharedCallId, sessions);
        String keyB = "VOICE|" + tenantB + "|" + sharedCallId;
        assertThat(tenantBProbe.result().conversationTurns().getFirst().toolResultSummary())
                .isNotEqualTo("BOOKING_CONFIRMED");
        assertThat(tenantBProbe.appointments()).isNotSameAs(tenantAStart.appointments());
        verify(tenantBProbe.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
        SessionProjection tenantBEmpty = sessions.find(keyB).orElseThrow();
        assertThat(tenantBEmpty.activeDraft()).isNull();
        assertThat(tenantBEmpty.pendingConfirmation()).isNull();
        assertThat(tenantBEmpty.completedBookingOutcome()).isNull();
        assertThat(sessions.find(keyA).orElseThrow()).isEqualTo(tenantAAwaitSlot);

        InboundHarness tenantBStart = runInboundSequence(List.of(
                        "मुझे डॉक्टर अमित वर्मा का appointment चाहिए"), false, true, null, List.of(),
                false, null, false, tenantB, patientB, clinicB, doctorB, sharedCallId, sessions);
        SessionProjection tenantBState = sessions.find(keyB).orElseThrow();
        assertThat(tenantBState.tenantId()).isEqualTo(tenantB.toString());
        assertThat(tenantBState.patientId()).isEqualTo(patientB);
        assertThat(tenantBState.activeDraft().draftId()).isNotEqualTo(tenantAAwaitSlot.activeDraft().draftId());
        assertThat(tenantBState.activeDraft().selectedProvider().doctorId()).isEqualTo(doctorB.toString());
        assertThat(tenantBState.activeDraft().preferredDate()).isNull();
        assertThat(tenantBState.latestAvailabilityResult()).isNull();
        assertThat(tenantBState.pendingConfirmation()).isNull();
        assertThat(sessions.find(keyA).orElseThrow()).isEqualTo(tenantAAwaitSlot);
        verify(tenantBStart.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());

        InboundHarness tenantASelect = runInboundSequence(List.of("10 works"), false, true, null, List.of(),
                false, null, false, TENANT, PATIENT, CLINIC, DOCTOR, sharedCallId, sessions);
        SessionProjection tenantAConfirmation = sessions.find(keyA).orElseThrow();
        assertThat(tenantAConfirmation.pendingConfirmation()).isNotNull();
        assertThat(tenantAConfirmation.activeDraft().selectedProvider().doctorId()).isEqualTo(DOCTOR.toString());
        assertThat(sessions.find(keyB).orElseThrow()).isEqualTo(tenantBState);
        verify(tenantASelect.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());

        InboundHarness tenantBAttack = runInboundSequence(List.of("हाँ कर दो"), false, true, null, List.of(),
                false, null, false, tenantB, patientB, clinicB, doctorB, sharedCallId, sessions);
        assertThat(tenantBAttack.result().conversationTurns().getFirst().toolResultSummary())
                .isNotEqualTo("BOOKING_CONFIRMED");
        verify(tenantBAttack.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
        assertThat(sessions.find(keyA).orElseThrow()).isEqualTo(tenantAConfirmation);
        assertThat(sessions.find(keyB).orElseThrow()).isEqualTo(tenantBState);

        InboundHarness tenantACommit = runInboundSequence(List.of("हाँ कर दो"), false, true, null, List.of(),
                false, null, false, TENANT, PATIENT, CLINIC, DOCTOR, sharedCallId, sessions);
        SessionProjection completedA = sessions.find(keyA).orElseThrow();
        assertThat(completedA.completedBookingOutcome()).isNotNull();
        assertThat(tenantACommit.result().conversationTurns().getFirst().toolResultSummary())
                .isEqualTo("BOOKING_CONFIRMED");
        verify(tenantACommit.appointments(), times(1)).createScheduled(
                eq(TENANT), any(), eq(null), eq(false), any(), any());

        InboundHarness tenantBStatus = runInboundSequence(List.of("booking ho gayi?", "repeat"), false, true, null,
                List.of(), false, null, false, tenantB, patientB, clinicB, doctorB, sharedCallId, sessions);
        assertThat(tenantBStatus.result().conversationTurns()).allSatisfy(turn ->
        {
            assertThat(turn.toolResultSummary()).isNotEqualTo("BOOKING_CONFIRMED");
            assertThat(turn.aivaResponseText()).doesNotContain("AIVA-TEST-APT");
        });
        assertThat(sessions.find(keyB).orElseThrow().completedBookingOutcome()).isNull();
        assertThat(sessions.find(keyA).orElseThrow().completedBookingOutcome()).isEqualTo(completedA.completedBookingOutcome());
        verify(tenantBStatus.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void providerClarificationCandidatesDoNotCrossTenantForSameDotVoiceCallId() {
        AivaV2SessionStore sessions = new AivaV2SessionStore(Clock.systemUTC());
        String sharedCallId = "phase8b-provider-candidates-call";
        UUID tenantB = UUID.randomUUID();
        UUID patientB = UUID.randomUUID();
        UUID clinicB = UUID.randomUUID();
        UUID doctorB = UUID.randomUUID();
        List<PatientPortalCareAiDoctorOption> candidatesA = List.of(
                additionalDoctor("Dr Ravi Mehta"), additionalDoctor("Dr Ravi Sharma"));

        runInboundSequence(List.of("Ravi doctor ka appointment chahiye"), false, true, null, candidatesA,
                false, null, false, TENANT, PATIENT, CLINIC, DOCTOR, sharedCallId, sessions);
        String keyA = "VOICE|" + TENANT + "|" + sharedCallId;
        SessionProjection pendingA = sessions.find(keyA).orElseThrow();
        assertThat(pendingA.voiceClarificationState().canonicalProviderCandidates()).hasSize(2);

        InboundHarness tenantBTurn = runInboundSequence(List.of("दूसरा वाला"), false, true, null, List.of(),
                false, null, false, tenantB, patientB, clinicB, doctorB, sharedCallId, sessions);
        assertThat(tenantBTurn.result().conversationTurns().getFirst().toolResultSummary())
                .isNotEqualTo("PROVIDER_IDENTITY_RESOLVED");
        assertThat(sessions.find(keyA).orElseThrow()).isEqualTo(pendingA);
        SessionProjection tenantBState = sessions.find("VOICE|" + tenantB + "|" + sharedCallId).orElseThrow();
        assertThat(tenantBState.voiceClarificationState()).isNull();
        assertThat(tenantBState.activeDraft()).isNull();
        verify(tenantBTurn.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void authorizedDotVoiceTwentyOneTurnConversationPreservesContextThroughDetoursAndReplay() {
        PatientPortalCareAiDoctorOption raviMehta = additionalDoctor("Dr Ravi Mehta");
        PatientPortalCareAiDoctorOption raviSharma = additionalDoctor("Dr Ravi Sharma");
        List<String> turns = List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए",
                "appointment book karna hai",
                "xyz",
                "कल",
                "फिर से बताओ",
                "शाम में",
                "और प्लॉट बताओ",
                "repeat",
                "second one",
                "कौन सा time था?",
                "repeat",
                "Ravi doctor ka appointment chahiye",
                "Ravi Mehta",
                "नहीं",
                "repeat",
                "शायद",
                "हाँ कर दो",
                "हाँ कर दो",
                "booking ho gayi?",
                "repeat",
                "कौन सा time book हुआ?");
        InboundHarness harness = runInboundSequence(turns, true, true,
                List.of(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0),
                        LocalTime.of(17, 0), LocalTime.of(17, 30), LocalTime.of(18, 0),
                        LocalTime.of(18, 30), LocalTime.of(19, 0), LocalTime.of(19, 30)),
                List.of(raviMehta, raviSharma));

        assertThat(harness.result().conversationTurns()).hasSize(turns.size());
        assertThat(harness.result().conversationTurns()).allSatisfy(turn ->
                assertThat(turn.aivaResponseText()).isNotBlank());
        assertThat(harness.sessionSnapshots()).hasSize(turns.size());
        SessionProjection initial = harness.sessionSnapshots().getFirst();
        UUID draftId = initial.activeDraft().draftId();
        assertThat(initial.activeDraft().selectedProvider().doctorId()).isEqualTo(DOCTOR.toString());
        for (int i = 0; i < harness.sessionSnapshots().size(); i++) {
            SessionProjection current = harness.sessionSnapshots().get(i);
            assertThat(current.tenantId()).as("turn %s tenant", i + 1).isEqualTo(TENANT.toString());
            assertThat(current.patientId()).as("turn %s patient", i + 1).isEqualTo(PATIENT);
            assertThat(current.presentationMode()).as("turn %s presentation", i + 1)
                    .isEqualTo(initial.presentationMode());
            if (i < 15) {
                assertThat(current.activeDraft()).as("turn %s draft", i + 1).isNotNull();
                assertThat(current.activeDraft().draftId()).as("turn %s draft identity", i + 1).isEqualTo(draftId);
                assertThat(current.activeDraft().selectedProvider().doctorId())
                        .as("turn %s provider", i + 1).isEqualTo(DOCTOR.toString());
                if (i < 3) {
                    assertThat(current.activeDraft().preferredDate()).as("turn %s date", i + 1).isNull();
                } else {
                    assertThat(current.activeDraft().preferredDate()).as("turn %s date", i + 1)
                            .isEqualTo(harness.sessionSnapshots().get(3).activeDraft().preferredDate());
                }
            }
            assertThat(harness.result().conversationTurns().get(i).callerTranscript()).isEqualTo(turns.get(i));
        }

        SessionProjection afterShowMore = harness.sessionSnapshots().get(6);
        assertThat(afterShowMore.latestAvailabilityResult().requestId())
                .isEqualTo(harness.sessionSnapshots().get(5).latestAvailabilityResult().requestId());
        assertThat(afterShowMore.latestAvailabilityResult().displayOffset()).isEqualTo(3);
        assertThat(harness.sessionSnapshots().get(8).activeDraft().selectedSlotReference()).isNotNull();
        assertThat(harness.sessionSnapshots().get(9).pendingConfirmation()).isNotNull();
        assertThat(harness.sessionSnapshots().get(11).voiceClarificationState().pendingAction())
                .isEqualTo("PROVIDER_IDENTITY");
        assertThat(harness.sessionSnapshots().get(12).voiceClarificationState().pendingAction())
                .isEqualTo("PROVIDER_SWITCH_CONFIRMATION");
        assertThat(harness.sessionSnapshots().get(13).activeDraft().selectedProvider().doctorId())
                .isEqualTo(DOCTOR.toString());
        assertThat(harness.sessionSnapshots().get(13).latestAvailabilityResult())
                .isEqualTo(harness.sessionSnapshots().get(12).latestAvailabilityResult());
        assertThat(harness.sessionSnapshots().get(14).pendingConfirmation()).isNotNull();
        assertThat(harness.sessionSnapshots().get(15).pendingConfirmation()).isNotNull();
        assertThat(harness.sessionSnapshots().get(16).completedBookingOutcome()).isNotNull();
        assertThat(harness.sessionSnapshots().get(17).completedBookingOutcome())
                .isEqualTo(harness.sessionSnapshots().get(16).completedBookingOutcome());
        assertThat(harness.sessionSnapshots().get(18).completedBookingOutcome())
                .isEqualTo(harness.sessionSnapshots().get(16).completedBookingOutcome());
        assertThat(harness.sessionSnapshots().get(19).completedBookingOutcome())
                .isEqualTo(harness.sessionSnapshots().get(16).completedBookingOutcome());
        assertThat(harness.sessionSnapshots().get(20).completedBookingOutcome())
                .isEqualTo(harness.sessionSnapshots().get(16).completedBookingOutcome());
        assertThat(harness.result().conversationTurns().get(16).toolResultSummary()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(harness.result().conversationTurns().get(17).toolResultSummary()).isNotEqualTo("BOOKING_CONFIRMED");
        assertThat(harness.result().conversationTurns().get(18).aivaResponseText())
                .contains("Amit Verma", "AIVA-TEST-APT");
        assertThat(harness.availabilityCallCounts().get(7)).as("repeat does not reload availability")
                .isEqualTo(harness.availabilityCallCounts().get(6));
        assertThat(harness.availabilityCallCounts().get(8)).as("canonical slot preparation revalidates once")
                .isEqualTo(harness.availabilityCallCounts().get(7) + 1);
        assertThat(harness.availabilityCallCounts().get(9)).as("confirmation repeat does not reload availability")
                .isEqualTo(harness.availabilityCallCounts().get(8));
        assertThat(harness.availabilityCallCounts().getLast())
                .as("date, daypart and slot preparation make the only availability calls")
                .isEqualTo(3);
        assertThat(harness.providerDiscoveryCallCounts().get(0)).isPositive();
        assertThat(harness.providerDiscoveryCallCounts().get(11))
                .as("provider-change request performs bounded canonical candidate lookup")
                .isGreaterThan(harness.providerDiscoveryCallCounts().get(10));
        verify(harness.appointments(), times(1)).createScheduled(eq(TENANT), any(), eq(null), eq(false), any(), any());
        verifyNoInteractions(harness.otpService());
        verifyNoSemanticAi(harness);
    }

    @Test
    void finalHindiSttBookingPhrasesStayBookIntentThroughRealDotVoiceInboundEntryWithBookingPolicyEnabled() {
        for (String phrase : List.of(
                "मुझे अपॉइंटमेंट बुक करनी है।",
                "मुझे डॉक्टर अमित वर्मा से अपॉइंटमेंट चाहिए।",
                "appointment book karna hai",
                "I want to book an appointment")) {
            var result = runInbound(phrase);

            assertThat(result.lastDetectedIntent()).as(phrase).isEqualTo("BOOK_APPOINTMENT");
            assertThat(result.conversationTurns()).isNotEmpty();
            var turn = result.conversationTurns().getFirst();
            assertThat(turn.callerTranscript()).isEqualTo(phrase);
            assertThat(turn.detectedIntent()).isEqualTo("BOOK_APPOINTMENT");
            assertThat(turn.runtimeDiagnostics().toString()).contains("bookingEnabled=true",
                    "configuredOtpRequired=true", "otpRequired=false");
            assertThat(turn.toolExecutionStatus()).isNotEqualTo("EXECUTED");
        }
    }

    @Test
    void displayedHindiLateSlotIsSelectedFromRetainedPageThenConfirmedExactlyOnce() {
        AivaV2SessionStore sessions = new AivaV2SessionStore(Clock.systemUTC());
        String callId = "hindi-displayed-late-slot-booking";
        LocalDate tomorrow = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
        ProviderCandidate doctor = new ProviderCandidate("amit", "amit", DOCTOR.toString(), CLINIC.toString(),
                TENANT.toString(), null, null, "Amit Verma", "General Medicine", "Clinic", ProviderSource.CARE_PRIVATE,
                new ProviderCapabilities(true, true, false, false, false, true, false));
        List<LocalTime> allTimes = List.of(LocalTime.of(17, 0), LocalTime.of(17, 30), LocalTime.of(18, 0),
                LocalTime.of(18, 30), LocalTime.of(19, 0), LocalTime.of(19, 30), LocalTime.of(20, 0),
                LocalTime.of(20, 30), LocalTime.of(21, 0));
        Instant now = Instant.now();
        AvailabilityResult displayedPage = new AvailabilityResult(UUID.randomUUID(), null, 0, "retained-page",
                doctor.providerHandle(), doctor.doctorId(), doctor.clinicId(), tomorrow, "EVENING", null,
                "Asia/Kolkata", allTimes.stream().map(time -> new AvailabilitySlot(null, time,
                time.plusMinutes(30), time.toString())).toList(), "VOICE_DAYPART:EVENING", true,
                now, now.plusSeconds(600), 6, 3);
        sessions.update("VOICE|" + TENANT + "|" + callId, ignored -> new SessionProjection(callId, PATIENT,
                TENANT.toString(), null, null, null, displayedPage, null, now.plusSeconds(1800))
                .withActiveVoiceDoctor(doctor));

        InboundHarness harness = runInboundSequence(List.of("नौ बजे का बुक कर दो", "हाँ बुक कर दो", "हाँ बुक कर दो"),
                false, true, List.of(LocalTime.of(21, 0)), List.of(), false, null, false,
                TENANT, PATIENT, CLINIC, DOCTOR, callId, sessions);

        assertThat(harness.result().conversationTurns()).hasSize(3);
        assertThat(harness.sessionSnapshots().get(0).activeDraft().preferredDate()).isEqualTo(tomorrow);
        assertThat(harness.sessionSnapshots().get(0).activeDraft().selectedProvider().doctorId())
                .isEqualTo(DOCTOR.toString());
        assertThat(harness.sessionSnapshots().get(0).activeDraft().exactTime()).isEqualTo(LocalTime.of(21, 0));
        assertThat(harness.sessionSnapshots().get(0).activeDraft().selectedSlotReference()).isNotBlank();
        assertThat(harness.sessionSnapshots().get(0).pendingConfirmation()).isNotNull();
        assertThat(harness.result().conversationTurns().get(0).toolResultSummary())
                .isNotEqualTo("BOOKING_CONFIRMED");
        assertThat(harness.result().conversationTurns().get(1).toolResultSummary()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(harness.result().conversationTurns().get(2).toolResultSummary()).isNotEqualTo("BOOKING_CONFIRMED");
        verify(harness.appointments(), times(1)).createScheduled(eq(TENANT), any(), eq(null), eq(false), any(), any());
        verifyNoInteractions(harness.otpService());
        verifyNoSemanticAi(harness);
    }

    @Test
    void bookingDateContinuationUsesSameInboundSessionAndAdoptsCanonicalProvider() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का अपॉइंटमेंट चाहिए", "कल"));
        InboundVoiceResult result = harness.result();
        assertThat(result.conversationTurns()).hasSize(2);
        assertThat(result.conversationTurns().get(0).detectedIntent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(result.conversationTurns().get(0).toolResultSummary()).isEqualTo("NEED_DATE");
        assertThat(AivaV2VoiceCollectionContext.isAwaitDate(harness.sessionSnapshots().getFirst(), Instant.now()))
                .isTrue();
        assertThat(result.conversationTurns().get(1).detectedIntent()).isEqualTo("BOOK_APPOINTMENT");
        SessionProjection session = harness.sessions().find("VOICE|" + TENANT + "|" + result.callId()).orElseThrow();
        assertThat(session.activeWorkflow()).isEqualTo("BOOKING");
        assertThat(session.activeDraft()).isNotNull();
        assertThat(session.activeDraft().selectedProvider()).isNotNull();
        assertThat(session.activeDraft().selectedProvider().displayName()).contains("Amit Verma");
        assertThat(session.activeDraft().preferredDate()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1));
        assertThat(session.activeDraft().revision()).isGreaterThan(1);
        assertThat(result.conversationTurns().get(1).toolResultSummary())
                .isNotEqualTo("GUEST_PREPARATION_DENIED");
        verify(harness.portal()).doctorSlotsForTenant(eq(TENANT), eq(DOCTOR.toString()), isNull(), any(LocalDate.class));
        verify(harness.orchestration(), never()).complete(any());
    }

    @Test
    void dateCollectionRefinesExistingDraftWhenDateUtteranceRepeatsAppointmentIntent() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "परसों appointment करना है"));
        SessionProjection awaitingDate = harness.sessionSnapshots().get(0);
        SessionProjection dated = harness.sessionSnapshots().get(1);
        assertThat(harness.result().conversationTurns().get(0).toolResultSummary()).isEqualTo("NEED_DATE");
        assertThat(dated.activeDraft().draftId()).isEqualTo(awaitingDate.activeDraft().draftId());
        assertThat(dated.activeDraft().selectedProvider()).isEqualTo(awaitingDate.activeDraft().selectedProvider());
        assertThat(dated.activeDraft().preferredDate())
                .isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(2));
        assertThat(dated.latestAvailabilityResult()).isNotNull();
        assertThat(harness.availabilityCallCounts().get(1)).isEqualTo(1);
        verify(harness.orchestration(), never()).complete(any());
        verify(harness.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void repeatedBookingIntentWhileAwaitingDatePreservesDraftAndDoesNotLoadAvailability() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "appointment book karna hai"));
        SessionProjection before = harness.sessionSnapshots().get(0);
        SessionProjection after = harness.sessionSnapshots().get(1);
        assertThat(after.activeDraft()).isEqualTo(before.activeDraft());
        assertThat(after.activeVoiceDoctor()).isEqualTo(before.activeVoiceDoctor());
        assertThat(after.activeDraft().preferredDate()).isNull();
        assertThat(after.latestAvailabilityResult()).isNull();
        assertThat(harness.result().conversationTurns().getLast().aivaResponseText().toLowerCase(Locale.ROOT))
                .containsAnyOf("date", "तारीख");
        assertThat(harness.availabilityCallCounts().get(1)).isZero();
        verify(harness.orchestration(), never()).complete(any());
        verify(harness.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void daypartCollectionRepeatsContextAndRecomputesEveningAvailability() {
        InboundHarness harness = runInboundSequence(List.of(
                        "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल",
                        "appointment chahiye", "शाम का appointment"),
                true, true, List.of(LocalTime.of(9, 0), LocalTime.of(14, 0),
                        LocalTime.of(17, 0), LocalTime.of(18, 0)));
        SessionProjection beforeRepeat = harness.sessionSnapshots().get(1);
        SessionProjection afterRepeat = harness.sessionSnapshots().get(2);
        SessionProjection afterEvening = harness.sessionSnapshots().get(3);
        assertThat(harness.result().conversationTurns().get(1).toolResultSummary()).isEqualTo("DAYPART_SELECTION");
        assertThat(AivaV2VoiceCollectionContext.isAwaitDaypart(beforeRepeat, Instant.now())).isTrue();
        assertThat(afterRepeat.activeDraft()).isEqualTo(beforeRepeat.activeDraft());
        assertThat(afterRepeat.latestAvailabilityResult()).isEqualTo(beforeRepeat.latestAvailabilityResult());
        assertThat(harness.availabilityCallCounts().get(2)).isEqualTo(harness.availabilityCallCounts().get(1));
        assertThat(afterEvening.activeDraft().selectedProvider().doctorId()).isEqualTo(DOCTOR.toString());
        assertThat(afterEvening.activeDraft().preferredDate())
                .isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1));
        assertThat(afterEvening.activeDraft().preferredTimeWindow()).isEqualTo("evening");
        assertThat(afterEvening.latestAvailabilityResult().slots()).extracting(slot -> slot.startsAt())
                .containsExactly(LocalTime.of(17, 0), LocalTime.of(18, 0));
        // Daypart refinement is allowed to re-run canonical availability so the
        // current provider/date is filtered by the existing availability boundary.
        assertThat(harness.availabilityCallCounts().get(3)).isEqualTo(harness.availabilityCallCounts().get(2) + 1);
        assertThat(harness.providerDiscoveryCallCounts().get(3))
                .isEqualTo(harness.providerDiscoveryCallCounts().get(2));
        verify(harness.orchestration(), never()).complete(any());
        verify(harness.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void unclearDateReplyKeepsProviderAndAsksTargetedDateQuestion() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "xyz"));
        SessionProjection before = harness.sessionSnapshots().get(0);
        SessionProjection after = harness.sessionSnapshots().get(1);
        assertThat(after.activeDraft()).isEqualTo(before.activeDraft());
        assertThat(after.activeVoiceDoctor()).isEqualTo(before.activeVoiceDoctor());
        assertThat(after.latestAvailabilityResult()).isNull();
        assertThat(harness.result().conversationTurns().getLast().aivaResponseText()).contains("date", "kal", "parson");
        assertThat(harness.availabilityCallCounts().get(1)).isZero();
        verify(harness.orchestration(), never()).complete(any());
    }

    @Test
    void repeatedBookingIntentWhileAwaitingProviderDoesNotReplaceCollectingDraft() {
        InboundHarness harness = runInboundSequence(List.of(
                "I want to book an appointment", "appointment book karna hai"));
        SessionProjection before = harness.sessionSnapshots().get(0);
        SessionProjection after = harness.sessionSnapshots().get(1);
        assertThat(AivaV2VoiceCollectionContext.isAwaitProvider(before, Instant.now())).isTrue();
        assertThat(after.activeDraft()).isEqualTo(before.activeDraft());
        assertThat(after.activeDraft().selectedProvider()).isNull();
        assertThat(after.latestAvailabilityResult()).isNull();
        assertThat(harness.availabilityCallCounts().get(1)).isZero();
        verify(harness.orchestration(), never()).complete(any());
        verify(harness.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void singleAvailableDaypartSkipsUnnecessaryDaypartQuestion() {
        InboundHarness harness = runInboundSequence(List.of(
                        "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल"),
                false, true, List.of(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0)));
        assertThat(harness.result().conversationTurns().getLast().toolResultSummary()).isEqualTo("SLOT_CHOICES");
        assertThat(harness.sessionSnapshots().getLast().latestAvailabilityResult().slots())
                .extracting(slot -> slot.startsAt())
                .allSatisfy(time -> assertThat(time).isBefore(LocalTime.NOON));
    }

    @Test
    void existingBeforeAfterTimeConstraintParserRefinesAwaitDaypartWithoutAi() {
        InboundHarness harness = runInboundSequence(List.of(
                        "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "after 5 pm"),
                true, true, List.of(LocalTime.of(9, 0), LocalTime.of(14, 0),
                        LocalTime.of(17, 0), LocalTime.of(18, 0)));
        SessionProjection refined = harness.sessionSnapshots().getLast();
        assertThat(refined.activeDraft().selectedProvider().doctorId()).isEqualTo(DOCTOR.toString());
        assertThat(refined.activeDraft().preferredDate())
                .isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1));
        assertThat(refined.activeDraft().availabilityTimeConstraint()).isNotNull();
        assertThat(refined.activeDraft().availabilityTimeConstraint().mode())
                .isEqualTo(AivaV2Models.AvailabilityTimeConstraintMode.AFTER);
        assertThat(refined.latestAvailabilityResult().slots()).extracting(slot -> slot.startsAt())
                .containsExactly(LocalTime.of(18, 0));
        assertThat(harness.availabilityCallCounts().get(2)).isEqualTo(harness.availabilityCallCounts().get(1) + 1);
        verify(harness.orchestration(), never()).complete(any());
        verify(harness.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void existingBetweenTimeRangeParserRefinesAwaitDaypartWithoutAi() {
        InboundHarness harness = runInboundSequence(List.of(
                        "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल",
                        "between 5 pm and 7 pm"),
                true, true, List.of(LocalTime.of(9, 0), LocalTime.of(14, 0),
                        LocalTime.of(17, 0), LocalTime.of(18, 0), LocalTime.of(19, 0)));
        SessionProjection refined = harness.sessionSnapshots().getLast();
        assertThat(refined.activeDraft().availabilityTimeConstraint()).isNotNull();
        assertThat(refined.activeDraft().availabilityTimeConstraint().mode())
                .isEqualTo(AivaV2Models.AvailabilityTimeConstraintMode.BETWEEN);
        assertThat(refined.latestAvailabilityResult().slots()).extracting(slot -> slot.startsAt())
                .containsExactly(LocalTime.of(17, 0), LocalTime.of(18, 0), LocalTime.of(19, 0));
        assertThat(harness.availabilityCallCounts().get(2)).isEqualTo(harness.availabilityCallCounts().get(1) + 1);
        verify(harness.orchestration(), never()).complete(any());
        verify(harness.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void bookingDateContinuationResolvesSupportedDateOnlyRepliesWithoutCallingLlm() {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        LocalDate today = LocalDate.now(zone);
        List<ExpectedDateTurn> cases = List.of(
                new ExpectedDateTurn("कल", today.plusDays(1)),
                new ExpectedDateTurn("कल।", today.plusDays(1)),
                new ExpectedDateTurn("कल का", today.plusDays(1)),
                new ExpectedDateTurn("कल के लिए", today.plusDays(1)),
                new ExpectedDateTurn("Tomorrow.", today.plusDays(1)),
                new ExpectedDateTurn("Tomorrow please", today.plusDays(1)),
                new ExpectedDateTurn(today.plusDays(2).format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)),
                        today.plusDays(2)));

        for (ExpectedDateTurn dateCase : cases) {
            InboundHarness harness = runInboundSequence(List.of(
                    "मुझे डॉक्टर अमित वर्मा का अपॉइंटमेंट चाहिए।", dateCase.utterance()));
            InboundVoiceResult result = harness.result();
            SessionProjection session = harness.sessions().find("VOICE|" + TENANT + "|" + result.callId()).orElseThrow();
            assertThat(result.conversationTurns()).hasSize(2);
            assertThat(result.conversationTurns().get(0).toolResultSummary()).isEqualTo("NEED_DATE");
            assertThat(result.conversationTurns().get(1).detectedIntent()).as(dateCase.utterance())
                    .isEqualTo("BOOK_APPOINTMENT");
            assertThat(result.conversationTurns().get(1).toolResultSummary()).as(dateCase.utterance())
                    .isNotEqualTo("GUEST_PREPARATION_DENIED");
            assertThat(session.activeWorkflow()).isEqualTo("BOOKING");
            assertThat(session.activeDraft().selectedProvider()).as(dateCase.utterance()).isNotNull();
            assertThat(session.activeDraft().selectedProvider().displayName()).as(dateCase.utterance()).contains("Amit Verma");
            assertThat(session.activeDraft().preferredDate()).isEqualTo(dateCase.expectedDate());
            assertThat(session.activeDraft().revision()).isGreaterThan(2);
            verify(harness.orchestration(), never()).complete(any());
        }
    }

    @Test
    void unresolvableDateInBookingDateContinuationAsksKernelClarificationAndKeepsDraft() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का अपॉइंटमेंट चाहिए।", "31 February"));
        InboundVoiceResult result = harness.result();
        SessionProjection session = harness.sessions().find("VOICE|" + TENANT + "|" + result.callId()).orElseThrow();
        assertThat(result.conversationTurns().get(1).detectedIntent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(result.conversationTurns().get(1).toolResultSummary()).isNotEqualTo("GUEST_PREPARATION_DENIED");
        assertThat(session.activeWorkflow()).isEqualTo("BOOKING");
        assertThat(session.activeDraft().selectedProvider().displayName()).contains("Amit Verma");
        assertThat(session.activeDraft().preferredDate()).isNull();
        verify(harness.orchestration(), never()).complete(any());
    }

    @Test
    void bareHindiKalRemainsAmbiguousOutsideBookingDateContinuation() {
        var adapter = LanguageAdapterRegistry.defaults().resolve("hi-IN", "कल");
        var turn = adapter.normalize("कल", LocalDate.of(2026, 10, 23));
        assertThat(turn.temporal().status()).isEqualTo(NormalizedUserTurn.TemporalStatus.AMBIGUOUS);
    }

    @Test
    void readOnlyDoctorContextIsAdoptedWhenBookingStartsOnTheSameInboundCall() {
        InboundHarness harness = runInboundSequence(List.of("Doctor Amit Verma", "मुझे कल का appointment चाहिए"));
        InboundVoiceResult result = harness.result();
        SessionProjection session = harness.sessions().find("VOICE|" + TENANT + "|" + result.callId()).orElseThrow();
        assertThat(result.conversationTurns()).hasSize(2);
        assertThat(result.conversationTurns().get(0).detectedIntent()).isEqualTo("FIND_DOCTOR");
        assertThat(result.conversationTurns().get(1).detectedIntent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(session.activeVoiceDoctor()).isNotNull();
        assertThat(session.activeVoiceDoctor().displayName()).contains("Amit Verma");
        assertThat(session.activeDraft()).isNotNull();
        assertThat(session.activeDraft().selectedProvider()).isNotNull();
        assertThat(session.activeDraft().selectedProvider().doctorId()).isEqualTo(session.activeVoiceDoctor().doctorId());
        assertThat(session.activeDraft().preferredDate()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1));
    }

    @Test
    void activeDoctorAndDateContextRouteContextualBookingRequestsBeforeGenericFallback() {
        for (String request : List.of("कल का अपॉइंटमेंट दे दीजिए।", "कल के लिए appointment चाहिए",
                "appointment book करना चाहता हूँ", "book an appointment", "कल book कर दीजिए")) {
            InboundHarness harness = runInboundSequence(List.of("Doctor Amit Verma", request));
            InboundVoiceResult result = harness.result();
            SessionProjection session = harness.sessions().find("VOICE|" + TENANT + "|" + result.callId()).orElseThrow();
            assertThat(result.conversationTurns().getLast().detectedIntent()).as(request).isEqualTo("BOOK_APPOINTMENT");
            assertThat(session.activeDraft()).as(request).isNotNull();
            assertThat(session.activeDraft().selectedProvider()).as(request).isNotNull();
            assertThat(session.activeDraft().selectedProvider().doctorId()).as(request).isEqualTo(DOCTOR.toString());
            assertThat(session.activeDraft().preferredDate()).as(request)
                    .isEqualTo(request.contains("कल") ? LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1) : null);
        }
    }

    @Test
    void currentExplicitDoctorInFullHindiBookingUtteranceWinsOverExistingDraftState() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल",
                "हेलो डॉक्टर अमित वर्मा का कल का अपॉइंटमेंट बुक करना है।"));
        InboundVoiceResult result = harness.result();
        SessionProjection session = harness.sessions().find("VOICE|" + TENANT + "|" + result.callId()).orElseThrow();

        assertThat(result.conversationTurns()).hasSize(3);
        assertThat(result.conversationTurns().getLast().detectedIntent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(result.conversationTurns().getLast().toolResultSummary()).isNotEqualTo("NEED_PROVIDER");
        assertThat(session.activeDraft()).isNotNull();
        assertThat(session.activeDraft().selectedProvider()).isNotNull();
        assertThat(session.activeDraft().selectedProvider().doctorId()).isEqualTo(DOCTOR.toString());
        assertThat(session.activeDraft().preferredDate())
                .isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1));
        assertThat(session.activeVoiceDoctor().doctorId()).isEqualTo(DOCTOR.toString());
    }

    @Test
    void bookingAvailabilityAsksOnlyForPopulatedDaypartsAndThenFiltersCanonicalSlots() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "शाम"), true);
        InboundVoiceResult result = harness.result();
        assertThat(result.conversationTurns()).hasSize(3);
        assertThat(result.conversationTurns().get(1).toolResultSummary()).isEqualTo("DAYPART_SELECTION");
        SessionProjection pending = harness.sessionSnapshots().get(1);
        assertThat(pending.latestAvailabilityResult().cursor()).isEqualTo("AIVA_BOOKING_PENDING_DAYPART");
        assertThat(pending.latestAvailabilityResult().slots()).extracting(slot -> slot.startsAt())
                .contains(LocalTime.of(9, 0), LocalTime.of(14, 0), LocalTime.of(17, 0));
        SessionProjection narrowed = harness.sessions().find("VOICE|" + TENANT + "|" + result.callId()).orElseThrow();
        assertThat(result.conversationTurns().get(2).toolResultSummary()).isEqualTo("SLOT_CHOICES");
        assertThat(narrowed.activeDraft().selectedProvider().doctorId()).isEqualTo(DOCTOR.toString());
        assertThat(narrowed.activeDraft().preferredDate()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1));
        assertThat(narrowed.latestAvailabilityResult().slots()).extracting(slot -> slot.startsAt())
                .containsExactly(LocalTime.of(17, 0), LocalTime.of(18, 0));
        assertThat(narrowed.latestAvailabilityResult().slots()).allSatisfy(slot ->
                assertThat(slot.slotReference()).isNotBlank());
    }

    @Test
    void nextSlotControlsPageTheRetainedEveningAvailabilityThroughRealInboundPath() {
        assertSlotPageContinuations(List.of(
                "और स्लॉट दिखाओ", "Next slot batao", "नेक्स्ट स्लॉट बताओ", "अगला स्लॉट बताओ"));
    }

    @Test
    void slotPageSynonymsAndLikelySttPlotSubstitutionsUseTheSameContinuation() {
        for (String control : List.of("और प्लॉट बताओ", "और स्लॉट बताओ", "दूसरे स्लॉट दिखाओ",
                "अगला स्लॉट बताओ", "नेक्स्ट स्लॉट बताओ", "next slot", "next slot batao", "aur dikhao",
                "more slots", "show more", "show next slots", "more plots")) {
            assertSlotPageContinuations(List.of(control));
        }
        assertSlotPageContinuations(java.util.Collections.nCopies(5, "show more"));
    }

    private void assertSlotPageContinuations(List<String> controls) {
        List<String> phrases = new java.util.ArrayList<>(List.of(
                "मुझे डॉक्टर अमित वर्मा का अपॉइंटमेंट चाहिए", "परसों", "शाम"));
        phrases.addAll(controls);
        List<LocalTime> times = new java.util.ArrayList<>(List.of(LocalTime.of(9, 0), LocalTime.of(14, 0)));
        for (int halfHour = 34; halfHour < 48; halfHour++) {
            times.add(LocalTime.of(halfHour / 2, halfHour % 2 * 30));
        }
        InboundHarness harness = runInboundSequence(phrases, true, true, times);
        SessionProjection firstPage = harness.sessionSnapshots().get(2);
        var availability = firstPage.latestAvailabilityResult();
        assertThat(harness.result().conversationTurns().get(1).toolResultSummary()).isEqualTo("DAYPART_SELECTION");
        assertThat(harness.result().conversationTurns().get(2).toolResultSummary()).isEqualTo("SLOT_CHOICES");
        assertThat(availability.displayOffset()).isZero();
        assertThat(availability.slots()).hasSize(14);
        assertThat(availability.pageSize()).isEqualTo(3);
        assertThat(AivaV2VoiceSlotContext.isAwaitSlot(firstPage, Instant.now()))
                .as("draft=%s availability=%s workflow=%s", firstPage.activeDraft(), availability,
                        firstPage.activeWorkflow()).isTrue();
        assertThat(harness.kernelDecisions()).hasSize(phrases.size());
        long initialAvailabilityCalls = harness.availabilityCallCounts().get(2);
        assertThat(initialAvailabilityCalls).isPositive();
        for (int i = 3; i < phrases.size(); i++) {
            SessionProjection page = harness.sessionSnapshots().get(i);
            var decision = harness.kernelDecisions().get(i);
            assertThat(decision.operation()).as(phrases.get(i)).isEqualTo(Operation.SHOW_MORE_SLOTS);
            assertThat(decision.dialogAct()).isEqualTo(DialogAct.REQUEST_ALTERNATIVE);
            assertThat(page.activeWorkflow()).isEqualTo("BOOKING");
            assertThat(page.activeDraft()).isEqualTo(firstPage.activeDraft());
            assertThat(page.activeVoiceDoctor()).isEqualTo(firstPage.activeVoiceDoctor());
            var current = page.latestAvailabilityResult();
            assertThat(current.requestId()).isEqualTo(availability.requestId());
            assertThat(current.criteriaFingerprint()).isEqualTo(availability.criteriaFingerprint());
            assertThat(current.generatedAt()).isEqualTo(availability.generatedAt());
            assertThat(current.expiresAt()).isEqualTo(availability.expiresAt());
            assertThat(current.timeWindow()).isEqualTo(availability.timeWindow());
            assertThat(current.availabilityTimeConstraint()).isEqualTo(availability.availabilityTimeConstraint());
            assertThat(current.slots()).isEqualTo(availability.slots());
            assertThat(current.pageSize()).isEqualTo(availability.pageSize());
            int requestedOffset = (i - 2) * 3;
            assertThat(current.hasMore()).isEqualTo(requestedOffset + availability.pageSize()
                    < availability.slots().size());
            assertThat(current.displayOffset()).isEqualTo(Math.min(requestedOffset, 12));
            assertThat(harness.availabilityCallCounts().get(i)).isEqualTo(initialAvailabilityCalls);
            assertThat(harness.result().conversationTurns().get(i).toolResultSummary())
                    .isEqualTo(requestedOffset < availability.slots().size() ? "SLOT_CHOICES" : "NO_MORE_SLOTS");
            if (requestedOffset >= availability.slots().size()) {
                assertThat(page.voiceClarificationState()).isNotNull();
                assertThat(page.voiceClarificationState().pendingAction()).isEqualTo("SEARCH_NEXT_DATE");
                assertThat(harness.result().conversationTurns().get(i).aivaResponseText())
                        .contains("दूसरी तारीख");
            } else {
                assertThat(harness.result().conversationTurns().get(i).runtimeDiagnostics())
                        .containsEntry("acceptedDecisionCount", 1)
                        .containsEntry("decisionOwner", "AIVA_V2_VOICE_ADAPTER")
                        .containsEntry("decisionContext", "AWAIT_SLOT")
                        .containsEntry("decisionAction", Operation.SHOW_MORE_SLOTS.name())
                        .containsEntry("semanticAiInvoked", false);
            }
        }
        verify(harness.orchestration(), never()).complete(any());
        verify(harness.readOnlyTools(), never()).execute(anyString(), any(), any());
        verify(harness.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
        assertThat(harness.gatewayTurns()).extracting(NormalizedUserTurn::rawText)
                .doesNotContainAnyElementsOf(controls);
    }

    @Test
    void slotPageControlsDoNotOverrideMissingContextOrExplicitDoctorSyntax() {
        InboundHarness outside = runInboundSequence(List.of("और प्लॉट बताओ"));
        assertThat(outside.kernelDecisions()).isEmpty();
        InboundHarness explicit = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का अपॉइंटमेंट चाहिए", "कल",
                "Doctor Ravi Mehta next slot batao"));
        assertThat(explicit.gatewayTurns()).extracting(NormalizedUserTurn::rawText)
                .doesNotContain("Doctor Ravi Mehta next slot batao");
        SessionProjection active = explicit.sessions().find(
                "VOICE|" + TENANT + "|" + explicit.result().callId()).orElseThrow();
        assertThat(active.activeDraft().selectedProvider().doctorId()).isEqualTo(DOCTOR.toString());
        assertThat(explicit.result().conversationTurns().getLast().toolResultSummary())
                .isNotEqualTo("FIND_DOCTOR");
    }

    @Test
    void pendingSlotClarificationYesAndNoStayBoundToCurrentDotVoiceBooking() {
        List<String> prefix = List.of("मुझे डॉक्टर अमित वर्मा का अपॉइंटमेंट चाहिए", "परसों", "शाम");
        List<LocalTime> offered = List.of(LocalTime.of(17, 0), LocalTime.of(17, 30), LocalTime.of(18, 0),
                LocalTime.of(18, 30), LocalTime.of(19, 0), LocalTime.of(19, 30));

        List<String> yesTurns = new java.util.ArrayList<>(prefix);
        yesTurns.add("hmm");
        yesTurns.add("blah");
        yesTurns.add("हाँ");
        InboundHarness yes = runInboundSequence(yesTurns, true, true, offered);
        SessionProjection beforeYes = yes.sessionSnapshots().get(3);
        SessionProjection repeated = yes.sessionSnapshots().get(4);
        SessionProjection afterYes = yes.sessionSnapshots().get(5);
        assertThat(beforeYes.voiceClarificationState()).isNotNull();
        assertThat(beforeYes.voiceClarificationState().pendingAction()).isEqualTo("SHOW_MORE");
        assertThat(beforeYes.tenantId()).isEqualTo(TENANT.toString());
        assertThat(beforeYes.activeDraft().selectedProvider().doctorId()).isEqualTo(DOCTOR.toString());
        assertThat(beforeYes.activeDraft().preferredDate())
                .isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(2));
        assertThat(beforeYes.activeDraft().revision()).isEqualTo(afterYes.activeDraft().revision());
        assertThat(beforeYes.latestAvailabilityResult().requestId())
                .isEqualTo(afterYes.latestAvailabilityResult().requestId());
        assertThat(beforeYes.latestAvailabilityResult().slots()).isEqualTo(afterYes.latestAvailabilityResult().slots());
        assertThat(repeated.voiceClarificationState().attemptCount()).isEqualTo(2);
        assertThat(repeated.voiceClarificationState().pendingAction()).isEqualTo("SHOW_MORE");
        assertThat(yes.kernelDecisions().getLast().operation()).isEqualTo(Operation.SHOW_MORE_SLOTS);
        assertThat(afterYes.activeDraft()).isEqualTo(beforeYes.activeDraft());
        assertThat(afterYes.activeVoiceDoctor()).isEqualTo(beforeYes.activeVoiceDoctor());
        assertThat(afterYes.latestAvailabilityResult().requestId())
                .isEqualTo(beforeYes.latestAvailabilityResult().requestId());
        assertThat(afterYes.latestAvailabilityResult().displayOffset()).isGreaterThan(0);
        assertThat(afterYes.voiceClarificationState()).isNull();
        assertThat(yes.result().conversationTurns().getLast().detectedIntent()).isNotEqualTo("CONFIRM_BOOKING");
        verify(yes.orchestration(), never()).complete(any());
        verify(yes.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());

        List<String> noTurns = new java.util.ArrayList<>(prefix);
        noTurns.add("hmm");
        noTurns.add("नहीं");
        InboundHarness no = runInboundSequence(noTurns, true, true, offered);
        SessionProjection beforeNo = no.sessionSnapshots().get(3);
        SessionProjection afterNo = no.sessionSnapshots().get(4);
        assertThat(beforeNo.voiceClarificationState().pendingAction()).isEqualTo("SHOW_MORE");
        assertThat(beforeNo.tenantId()).isEqualTo(TENANT.toString());
        assertThat(beforeNo.activeDraft().selectedProvider().doctorId()).isEqualTo(DOCTOR.toString());
        assertThat(beforeNo.activeDraft().preferredDate())
                .isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(2));
        assertThat(no.kernelDecisions()).hasSize(3);
        assertThat(afterNo.activeDraft()).isEqualTo(beforeNo.activeDraft());
        assertThat(afterNo.activeVoiceDoctor()).isEqualTo(beforeNo.activeVoiceDoctor());
        assertThat(afterNo.latestAvailabilityResult()).isEqualTo(beforeNo.latestAvailabilityResult());
        assertThat(afterNo.voiceClarificationState()).isNull();
        verify(no.orchestration(), never()).complete(any());
        verify(no.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void ambiguousHindiAndHinglishSlotTurnsPreserveTheDisplayedBookingContext() {
        List<String> prefix = List.of("मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "परसों", "शाम");
        for (String unclear : List.of("मुझे समझ नहीं आया", "not sure")) {
            List<String> turns = new java.util.ArrayList<>(prefix);
            turns.add(unclear);
            InboundHarness harness = runInboundSequence(turns, true, true,
                    List.of(LocalTime.of(17, 0), LocalTime.of(17, 30), LocalTime.of(18, 0)));
            SessionProjection before = harness.sessionSnapshots().get(2);
            SessionProjection after = harness.sessionSnapshots().get(3);
            assertThat(harness.result().conversationTurns().getLast().detectedIntent())
                    .as(unclear).isEqualTo("CLARIFY_SLOT_CONTINUATION");
            assertThat(after.tenantId()).isEqualTo(before.tenantId());
            assertThat(after.activeDraft()).isEqualTo(before.activeDraft());
            assertThat(after.activeVoiceDoctor()).isEqualTo(before.activeVoiceDoctor());
            assertThat(after.latestAvailabilityResult().requestId())
                    .isEqualTo(before.latestAvailabilityResult().requestId());
            assertThat(after.latestAvailabilityResult().displayOffset())
                    .isEqualTo(before.latestAvailabilityResult().displayOffset());
            assertThat(after.latestAvailabilityResult().slots()).isEqualTo(before.latestAvailabilityResult().slots());
            assertThat(harness.availabilityCallCounts().get(3)).isEqualTo(harness.availabilityCallCounts().get(2));
            verify(harness.orchestration(), never()).complete(any());
            verify(harness.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
        }
    }

    @Test
    void clearProviderChangeThroughDotVoicePreservesCurrentBookingForPhaseThree() {
        PatientPortalCareAiDoctorOption ravi = additionalDoctor("Dr Ravi Mehta");
        PatientPortalCareAiDoctorOption raviSharma = additionalDoctor("Dr Ravi Sharma");
        InboundHarness harness = runInboundSequence(List.of("Doctor Amit Verma",
                "कल का appointment book कर दीजिए", "Ravi doctor ka appointment chahiye", "Ravi Mehta"),
                false, true, null, List.of(ravi, raviSharma));
        SessionProjection before = harness.sessionSnapshots().get(1);
        SessionProjection ambiguous = harness.sessionSnapshots().get(2);
        SessionProjection after = harness.sessionSnapshots().get(3);
        var ambiguityTurn = harness.result().conversationTurns().get(2);
        var change = harness.result().conversationTurns().get(3);

        assertThat(ambiguityTurn.toolResultSummary()).isEqualTo("PROVIDER_IDENTITY_AMBIGUOUS");
        assertThat(ambiguityTurn.aivaResponseText()).contains("Ravi Mehta", "Ravi Sharma");
        assertThat(ambiguous.voiceClarificationState().pendingAction()).isEqualTo("PROVIDER_IDENTITY");

        assertThat(change.runtimeDiagnostics())
                .containsEntry("clarificationType", "PROVIDER_SWITCH")
                .containsEntry("pendingAction", "PROVIDER_SWITCH_CONFIRMATION")
                .containsEntry("mutationAllowed", false)
                .containsEntry("contextPreserved", true);
        assertThat(change.runtimeDiagnostics().get("voiceTransactionPolicy").toString())
                .contains("decision=PROVIDER_SWITCH_CONFIRMATION_REQUIRED");
        assertThat(change.toolResultSummary()).isEqualTo("PROVIDER_SWITCH_CONFIRMATION_REQUIRED");
        assertThat(after.activeDraft()).isEqualTo(before.activeDraft());
        assertThat(after.activeDraft().selectedProvider().doctorId()).isEqualTo(DOCTOR.toString());
        assertThat(after.activeVoiceDoctor()).isEqualTo(before.activeVoiceDoctor());
        assertThat(after.latestAvailabilityResult()).isEqualTo(before.latestAvailabilityResult());
        assertThat(harness.availabilityCallCounts().get(3)).isEqualTo(harness.availabilityCallCounts().get(1));
        verify(harness.orchestration(), never()).complete(any());
        verify(harness.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void providerChangeFromDisplayedSlotContextIsDeferredWithoutReloadOrDraftChange() {
        PatientPortalCareAiDoctorOption raviMehta = additionalDoctor("Dr Ravi Mehta");
        PatientPortalCareAiDoctorOption raviSharma = additionalDoctor("Dr Ravi Sharma");
        List<String> turns = List.of("मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "परसों", "शाम",
                "Ravi doctor ka appointment chahiye", "Ravi Mehta");
        InboundHarness harness = runInboundSequence(turns, false, true,
                List.of(LocalTime.of(17, 0), LocalTime.of(17, 30), LocalTime.of(18, 0)),
                List.of(raviMehta, raviSharma));
        SessionProjection before = harness.sessionSnapshots().get(2);
        SessionProjection ambiguous = harness.sessionSnapshots().get(3);
        SessionProjection after = harness.sessionSnapshots().get(4);

        assertThat(AivaV2VoiceSlotContext.isAwaitSlot(before, Instant.now())).isTrue();
        assertThat(ambiguous.voiceClarificationState().pendingAction()).isEqualTo("PROVIDER_IDENTITY");
        assertThat(after.activeDraft()).isEqualTo(before.activeDraft());
        assertThat(after.activeDraft().selectedProvider().doctorId()).isEqualTo(DOCTOR.toString());
        assertThat(after.latestAvailabilityResult()).isEqualTo(before.latestAvailabilityResult());
        assertThat(harness.availabilityCallCounts().get(3)).isEqualTo(harness.availabilityCallCounts().get(2));
        assertThat(harness.availabilityCallCounts().get(4)).isEqualTo(harness.availabilityCallCounts().get(2));
        assertThat(harness.result().conversationTurns().getLast().runtimeDiagnostics().toString())
                .contains("PROVIDER_SWITCH_CONFIRMATION");
        verify(harness.orchestration(), never()).complete(any());
        verify(harness.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void providerSwitchYesRevalidatesTargetAndPreservesDateAndDaypart() {
        PatientPortalCareAiDoctorOption ravi = additionalDoctor("Dr Ravi Mehta");
        InboundHarness harness = runInboundSequence(List.of("मुझे डॉक्टर अमित वर्मा का appointment चाहिए",
                        "कल", "शाम", "Ravi doctor dikhao", "हाँ"),
                false, true, List.of(LocalTime.of(17, 0), LocalTime.of(17, 30)), List.of(ravi));
        SessionProjection before = harness.sessionSnapshots().get(2);
        SessionProjection pending = harness.sessionSnapshots().get(3);
        SessionProjection after = harness.sessionSnapshots().get(4);
        assertThat(pending.activeDraft()).isEqualTo(before.activeDraft());
        assertThat(pending.latestAvailabilityResult()).isEqualTo(before.latestAvailabilityResult());
        assertThat(pending.voiceClarificationState().pendingAction()).isEqualTo("PROVIDER_SWITCH_CONFIRMATION");
        assertThat(pending.voiceClarificationState().pendingProviderSwitch().targetProviderId())
                .isEqualTo(ravi.publicDoctorId());
        assertThat(after.activeDraft().selectedProvider().doctorId()).isEqualTo(ravi.publicDoctorId());
        assertThat(after.activeVoiceDoctor().doctorId()).isEqualTo(ravi.publicDoctorId());
        assertThat(after.activeDraft().preferredDate()).isEqualTo(before.activeDraft().preferredDate());
        assertThat(after.activeDraft().preferredTimeWindow()).isEqualTo(before.activeDraft().preferredTimeWindow());
        assertThat(after.activeDraft().selectedSlotReference()).isNull();
        assertThat(after.latestAvailabilityResult()).isNotNull();
        assertThat(after.latestAvailabilityResult().doctorId()).isEqualTo(ravi.publicDoctorId());
        assertThat(after.latestAvailabilityResult().requestId())
                .isNotEqualTo(before.latestAvailabilityResult().requestId());
        assertThat(after.suspendedDraft().status()).isEqualTo(AivaV2Models.DraftStatus.ABANDONED);
        assertThat(harness.availabilityCallCounts().get(4)).isGreaterThan(harness.availabilityCallCounts().get(3));
        assertThat(harness.result().conversationTurns().get(3).aivaResponseText())
                .contains("Amit Verma", "Ravi Mehta", "switch");
        verify(harness.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
        verify(harness.orchestration(), never()).complete(any());
    }

    @Test
    void providerSwitchNoRetainsExactSlotAndConfirmationContext() {
        PatientPortalCareAiDoctorOption ravi = additionalDoctor("Dr Ravi Mehta");
        InboundHarness harness = runInboundSequence(List.of("मुझे डॉक्टर अमित वर्मा का appointment चाहिए",
                        "कल", "शाम", "second one", "Ravi Mehta appointment chahiye", "नहीं"),
                false, true, List.of(LocalTime.of(17, 0), LocalTime.of(17, 30)), List.of(ravi));
        SessionProjection selected = harness.sessionSnapshots().get(3);
        SessionProjection pending = harness.sessionSnapshots().get(4);
        SessionProjection after = harness.sessionSnapshots().get(5);
        assertThat(selected.activeDraft().selectedSlotReference()).isNotNull();
        assertThat(selected.pendingConfirmation()).isNotNull();
        assertThat(pending.activeDraft()).isEqualTo(selected.activeDraft());
        assertThat(pending.latestAvailabilityResult()).isEqualTo(selected.latestAvailabilityResult());
        assertThat(after.activeDraft()).isEqualTo(selected.activeDraft());
        assertThat(after.activeVoiceDoctor()).isEqualTo(selected.activeVoiceDoctor());
        assertThat(after.latestAvailabilityResult()).isEqualTo(selected.latestAvailabilityResult());
        assertThat(after.pendingConfirmation()).isEqualTo(selected.pendingConfirmation());
        assertThat(after.voiceClarificationState()).isNull();
        assertThat(harness.availabilityCallCounts().get(5)).isEqualTo(harness.availabilityCallCounts().get(4));
        verify(harness.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
        verify(harness.orchestration(), never()).complete(any());
    }

    @Test
    void providerSwitchYesFromConfirmationInvalidatesOldConfirmation() {
        PatientPortalCareAiDoctorOption ravi = additionalDoctor("Dr Ravi Mehta");
        InboundHarness harness = runInboundSequence(List.of("मुझे डॉक्टर अमित वर्मा का appointment चाहिए",
                        "कल", "शाम", "second one", "Ravi Mehta appointment chahiye", "हाँ"),
                false, true, List.of(LocalTime.of(17, 0), LocalTime.of(17, 30)), List.of(ravi));
        SessionProjection selected = harness.sessionSnapshots().get(3);
        SessionProjection pending = harness.sessionSnapshots().get(4);
        SessionProjection after = harness.sessionSnapshots().get(5);
        assertThat(selected.pendingConfirmation()).isNotNull();
        assertThat(pending.pendingConfirmation()).isEqualTo(selected.pendingConfirmation());
        assertThat(pending.activeDraft()).isEqualTo(selected.activeDraft());
        assertThat(after.activeDraft().selectedProvider().doctorId()).isEqualTo(ravi.publicDoctorId());
        assertThat(after.activeDraft().selectedSlotReference()).isNull();
        assertThat(after.pendingConfirmation()).isNull();
        assertThat(after.latestAvailabilityResult().doctorId()).isEqualTo(ravi.publicDoctorId());
        assertThat(after.suspendedDraft().status()).isEqualTo(AivaV2Models.DraftStatus.ABANDONED);
        verify(harness.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
        verify(harness.orchestration(), never()).complete(any());
    }

    @Test
    void sameProviderReaffirmationDoesNotCreateSwitchOrInvalidateBooking() {
        InboundHarness harness = runInboundSequence(List.of("मुझे डॉक्टर अमित वर्मा का appointment चाहिए",
                        "कल", "शाम", "अमित वर्मा ही"),
                false, true, List.of(LocalTime.of(17, 0), LocalTime.of(17, 30)));
        SessionProjection before = harness.sessionSnapshots().get(2);
        SessionProjection after = harness.sessionSnapshots().get(3);
        assertThat(after.activeDraft()).isEqualTo(before.activeDraft());
        assertThat(after.latestAvailabilityResult()).isEqualTo(before.latestAvailabilityResult());
        assertThat(after.voiceClarificationState()).isNull();
        assertThat(harness.result().conversationTurns().getLast().toolResultSummary()).isEqualTo("SAME_PROVIDER_REAFFIRMED");
        assertThat(harness.availabilityCallCounts().get(3)).isEqualTo(harness.availabilityCallCounts().get(2));
        verify(harness.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void explicitNextAvailableDateSearchStaysDistinctFromCurrentPagePagination() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "परसों", "शाम", "next available date"),
                false, true, List.of(LocalTime.of(17, 0), LocalTime.of(17, 30), LocalTime.of(18, 0)));
        assertThat(harness.kernelDecisions().getLast().operation()).isEqualTo(Operation.FIND_NEXT_AVAILABLE_SLOT);
        assertThat(harness.gatewayTurns()).extracting(NormalizedUserTurn::rawText)
                .doesNotContain("next available date");
        verify(harness.portal()).doctorAvailability(any(), any(), any(), any(), any(), any());
        verify(harness.orchestration(), never()).complete(any());
    }

    @Test
    void explicitDateChangeFromDisplayedSlotContextRetainsProviderAndInvalidatesDependentPage() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "शाम", "आज"),
                false, true, List.of(LocalTime.of(17, 0), LocalTime.of(17, 30), LocalTime.of(18, 0)));
        SessionProjection before = harness.sessionSnapshots().get(2);
        SessionProjection after = harness.sessionSnapshots().get(3);
        assertThat(AivaV2VoiceSlotContext.isAwaitSlot(before, Instant.now())).isTrue();
        assertThat(after.activeDraft().selectedProvider().doctorId()).isEqualTo(DOCTOR.toString());
        assertThat(after.activeDraft().preferredDate()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")));
        assertThat(after.activeDraft().selectedSlotReference()).isNull();
        assertThat(after.latestAvailabilityResult().requestId())
                .isNotEqualTo(before.latestAvailabilityResult().requestId());
        assertThat(after.latestAvailabilityResult().displayOffset()).isZero();
        assertThat(harness.availabilityCallCounts().get(3)).isGreaterThan(harness.availabilityCallCounts().get(2));
        verify(harness.orchestration(), never()).complete(any());
        verify(harness.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void daypartChangeFromDisplayedSlotContextRetainsProviderAndDateAndRefreshesChoices() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "सुबह", "शाम"),
                false, true, List.of(LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(17, 0),
                        LocalTime.of(17, 30), LocalTime.of(18, 0)));
        SessionProjection before = harness.sessionSnapshots().get(2);
        SessionProjection after = harness.sessionSnapshots().get(3);
        assertThat(AivaV2VoiceSlotContext.isAwaitSlot(before, Instant.now())).isTrue();
        assertThat(after.activeDraft().selectedProvider().doctorId()).isEqualTo(DOCTOR.toString());
        assertThat(after.activeDraft().preferredDate()).isEqualTo(before.activeDraft().preferredDate());
        assertThat(after.activeDraft().preferredTimeWindow()).isEqualToIgnoringCase("evening");
        assertThat(after.activeDraft().selectedSlotReference()).isNull();
        assertThat(after.latestAvailabilityResult().requestId())
                .isNotEqualTo(before.latestAvailabilityResult().requestId());
        assertThat(after.latestAvailabilityResult().displayOffset()).isZero();
        assertThat(harness.availabilityCallCounts().get(3)).isGreaterThan(harness.availabilityCallCounts().get(2));
        verify(harness.orchestration(), never()).complete(any());
        verify(harness.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void ambiguousCanonicalProviderFollowUpIsBoundedToCandidatesThroughDotVoice() {
        PatientPortalCareAiDoctorOption raviMehta = additionalDoctor("Dr Ravi Mehta");
        PatientPortalCareAiDoctorOption raviSharma = additionalDoctor("Dr Ravi Sharma");
        List<PatientPortalCareAiDoctorOption> candidates = List.of(raviMehta, raviSharma);

        InboundHarness named = runInboundSequence(List.of(
                "Ravi doctor ka appointment chahiye", "Ravi Mehta"), false, true, null, candidates);
        assertThat(named.result().conversationTurns()).hasSize(2);
        assertThat(named.result().conversationTurns().getFirst().aivaResponseText())
                .contains("Ravi Mehta", "Ravi Sharma");
        assertThat(named.sessionSnapshots().getFirst().voiceClarificationState().canonicalProviderCandidates())
                .extracting(AivaV2Models.VoiceProviderIdentityCandidate::displayName)
                .containsExactly("Dr Ravi Mehta", "Dr Ravi Sharma");
        assertThat(named.result().conversationTurns().getLast().toolResultSummary())
                .isNotEqualTo("PROVIDER_IDENTITY_CLARIFICATION_PENDING");
        assertThat(named.sessions().find("VOICE|" + TENANT + "|" + named.result().callId()).orElseThrow()
                .voiceClarificationState()).isNull();
        assertThat(named.sessions().find("VOICE|" + TENANT + "|" + named.result().callId()).orElseThrow()
                .activeDraft().selectedProvider().doctorId()).isEqualTo(raviMehta.publicDoctorId());
        verify(named.orchestration(), never()).complete(any());
        verify(named.portal(), never()).doctorSlotsForTenant(any(), any(), any(), any());
        verify(named.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());

        InboundHarness yes = runInboundSequence(List.of(
                "Ravi doctor ka appointment chahiye", "हाँ"), false, true, null, candidates);
        SessionProjection stillPending = yes.sessions().find("VOICE|" + TENANT + "|" + yes.result().callId())
                .orElseThrow();
        assertThat(stillPending.voiceClarificationState()).isNotNull();
        assertThat(stillPending.voiceClarificationState().pendingAction()).isEqualTo("PROVIDER_IDENTITY");
        assertThat(stillPending.activeDraft() == null || stillPending.activeDraft().selectedProvider() == null)
                .isTrue();
        assertThat(yes.result().conversationTurns().getLast().aivaResponseText())
                .contains("Ravi Mehta", "Ravi Sharma");
        verify(yes.orchestration(), never()).complete(any());
        verify(yes.portal(), never()).doctorSlotsForTenant(any(), any(), any(), any());
        verify(yes.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
    }

    private PatientPortalCareAiDoctorOption additionalDoctor(String name) {
        return new PatientPortalCareAiDoctorOption(UUID.randomUUID().toString(), name, "General Medicine",
                UUID.randomUUID(), CLINIC, TENANT, "clinic", "Clinic");
    }

    @Test
    void readOnlyPublicDoctorWithUnknownTenantCapabilityIsResolvedBeforeBookingAdoption() {
        InboundHarness harness = runInboundSequence(List.of(
                "Doctor Amit Verma", "कल का appointment book कर दीजिए"), false, false);
        SessionProjection discovered = harness.sessionSnapshots().getFirst();
        SessionProjection booking = harness.sessions().find("VOICE|" + TENANT + "|"
                + harness.result().callId()).orElseThrow();

        assertThat(discovered.activeVoiceDoctor().doctorId()).isEqualTo(DOCTOR.toString());
        assertThat(discovered.activeVoiceDoctor().source()).isEqualTo(ProviderSource.DISCOVER_PUBLIC);
        assertThat(discovered.activeVoiceDoctor().capabilities().onlineBooking()).isFalse();
        assertThat(booking.activeDraft().selectedProvider().doctorId()).isEqualTo(DOCTOR.toString());
        assertThat(booking.activeDraft().selectedProvider().source()).isEqualTo(ProviderSource.CARE_PRIVATE);
        assertThat(booking.activeDraft().selectedProvider().capabilities().onlineBooking()).isTrue();
        assertThat(booking.latestAvailabilityResult()).isNotNull();
        assertThat(harness.result().conversationTurns().getLast().toolResultSummary())
                .isIn("SLOT_CHOICES", "DAYPART_SELECTION");
    }

    @Test
    void readOnlyPublicBookableDoctorIsCanonicalizedBeforeDraftAndRemainsCarePrivateThroughBooking() {
        InboundHarness harness = runInboundSequence(List.of(
                "Doctor Amit Verma", "कल का appointment book कर दीजिए", "10 works", "हाँ बुक कर दीजिए"));
        InboundVoiceResult result = harness.result();
        SessionProjection discovered = harness.sessionSnapshots().getFirst();
        assertThat(discovered.activeVoiceDoctor().source()).isEqualTo(ProviderSource.DISCOVER_PUBLIC);
        assertThat(result.conversationTurns().get(1).toolResultSummary()).isEqualTo("SLOT_CHOICES");
        assertThat(result.conversationTurns().get(2).toolResultSummary()).isEqualTo("BOOKING_CONFIRMATION_PENDING");
        assertThat(result.conversationTurns().get(3).toolResultSummary()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(harness.sessionSnapshots()).allSatisfy(snapshot -> {
            if (snapshot.activeDraft() != null && snapshot.activeDraft().selectedProvider() != null) {
                assertThat(snapshot.activeDraft().selectedProvider().source()).isEqualTo(ProviderSource.CARE_PRIVATE);
                assertThat(snapshot.activeDraft().selectedProvider().bookingReference()).isNull();
            }
        });
        verify(harness.appointments(), org.mockito.Mockito.times(1))
                .createScheduled(eq(TENANT), any(), eq(null), eq(false), any(), any());
    }

    @Test
    void presentationModeIsConversationScopedAndOnlyChangesOnExplicitSwitch() {
        InboundHarness hindi = runInboundSequence(List.of("मुझे appointment book करनी है", "appointment book karna hai"));
        SessionProjection hindiSession = hindi.sessions().find("VOICE|" + TENANT + "|" + hindi.result().callId()).orElseThrow();
        assertThat(hindiSession.presentationMode()).isEqualTo(AivaV2Models.VoicePresentationMode.HINGLISH);
        assertThat(hindi.result().conversationTurns().getFirst().aivaResponseText()).contains("doctor");
        assertThat(hindi.result().conversationTurns().getLast().aivaResponseText()).contains("doctor");

        InboundHarness hinglish = runInboundSequence(List.of("Doctor Amit Verma available hain?", "Is he available tomorrow?"));
        SessionProjection hinglishSession = hinglish.sessions().find("VOICE|" + TENANT + "|" + hinglish.result().callId()).orElseThrow();
        assertThat(hinglishSession.presentationMode()).isEqualTo(AivaV2Models.VoicePresentationMode.HINGLISH);

        InboundHarness english = runInboundSequence(List.of("I want to book an appointment", "कल"));
        SessionProjection englishSession = english.sessions().find("VOICE|" + TENANT + "|" + english.result().callId()).orElseThrow();
        assertThat(englishSession.presentationMode()).isEqualTo(AivaV2Models.VoicePresentationMode.ENGLISH);

        InboundHarness switched = runInboundSequence(List.of("Doctor Amit Verma available hain?", "Please speak in English"));
        SessionProjection switchedSession = switched.sessions().find("VOICE|" + TENANT + "|" + switched.result().callId()).orElseThrow();
        assertThat(switchedSession.presentationMode()).isEqualTo(AivaV2Models.VoicePresentationMode.ENGLISH);
        verify(switched.renderer(), org.mockito.Mockito.atLeastOnce()).render(anyString(), eq("ENGLISH"), eq("en-IN"));

        InboundHarness hindiAfterSwitch = runInboundSequence(List.of(
                "Doctor Amit Verma available hain?", "Hindi mein bolo", "repeat"));
        SessionProjection hindiAfterSwitchSession = hindiAfterSwitch.sessions().find(
                "VOICE|" + TENANT + "|" + hindiAfterSwitch.result().callId()).orElseThrow();
        assertThat(hindiAfterSwitchSession.presentationMode()).isEqualTo(AivaV2Models.VoicePresentationMode.HINDI);
        verify(hindiAfterSwitch.renderer(), org.mockito.Mockito.atLeastOnce()).render(anyString(), eq("HINDI"), eq("hi-IN"));

        InboundHarness englishPlease = runInboundSequence(List.of("मुझे डॉक्टर चाहिए", "English please."));
        assertThat(englishPlease.sessions().find("VOICE|" + TENANT + "|" + englishPlease.result().callId())
                .orElseThrow().presentationMode()).isEqualTo(AivaV2Models.VoicePresentationMode.ENGLISH);

        InboundHarness devanagariHindiSwitch = runInboundSequence(List.of("Which doctors are available?",
                "हिंदी में बोलिए।"));
        assertThat(devanagariHindiSwitch.sessions().find("VOICE|" + TENANT + "|"
                + devanagariHindiSwitch.result().callId()).orElseThrow().presentationMode())
                .isEqualTo(AivaV2Models.VoicePresentationMode.HINDI);

        InboundHarness englishInDevanagariSwitch = runInboundSequence(List.of("Which doctors are available?",
                "अब English में बात करें।"));
        assertThat(englishInDevanagariSwitch.sessions().find("VOICE|" + TENANT + "|"
                + englishInDevanagariSwitch.result().callId()).orElseThrow().presentationMode())
                .isEqualTo(AivaV2Models.VoicePresentationMode.ENGLISH);
    }

    @Test
    void establishedPresentationModeStaysStickyForOrdinaryTurnsAndCourtesyClose() {
        assertPresentationMode(List.of("मुझे डॉक्टर चाहिए", "Okay."),
                AivaV2Models.VoicePresentationMode.HINDI, "hi-IN", false);
        assertPresentationMode(List.of("मुझे डॉक्टर चाहिए", "Thank you."),
                AivaV2Models.VoicePresentationMode.HINDI, "hi-IN", true);
        InboundHarness hindiCourtesy = assertPresentationMode(List.of("मुझे डॉक्टर चाहिए", "Okay. Thank you."),
                AivaV2Models.VoicePresentationMode.HINDI, "hi-IN", true);
        assertThat(hindiCourtesy.result().conversationTurns().getLast().detectedIntent()).isEqualTo("END_CONVERSATION");
        assertThat(hindiCourtesy.result().conversationTurns().getLast().aivaResponseText()).contains("धन्यवाद")
                .doesNotContain("Thank you");
        assertPresentationMode(List.of("मुझे डॉक्टर चाहिए", "Yes."),
                AivaV2Models.VoicePresentationMode.HINDI, "hi-IN", false);
        assertPresentationMode(List.of("मुझे डॉक्टर चाहिए", "doctor appointment"),
                AivaV2Models.VoicePresentationMode.HINDI, "hi-IN", false);
        assertPresentationMode(List.of("kal kaunse doctor available hain?", "Thank you."),
                AivaV2Models.VoicePresentationMode.HINGLISH, "hi-IN", true);
        assertPresentationMode(List.of("kal kaunse doctor available hain?", "Yes."),
                AivaV2Models.VoicePresentationMode.HINGLISH, "hi-IN", false);
        assertPresentationMode(List.of("Which doctors are available?", "haan"),
                AivaV2Models.VoicePresentationMode.ENGLISH, "en-IN", false);
    }

    private InboundHarness assertPresentationMode(List<String> transcripts,
            AivaV2Models.VoicePresentationMode expectedMode, String expectedTtsLanguage, boolean closing) {
        InboundHarness harness = runInboundSequence(transcripts);
        SessionProjection session = harness.sessions().find(
                "VOICE|" + TENANT + "|" + harness.result().callId()).orElseThrow();
        assertThat(session.presentationMode()).isEqualTo(expectedMode);
        var lastTurn = harness.result().conversationTurns().getLast();
        assertThat(lastTurn.runtimeDiagnostics()).containsEntry("presentationMode", expectedMode.name())
                .containsEntry("ttsLanguage", expectedTtsLanguage);
        assertThat(lastTurn.closing()).isEqualTo(closing);
        return harness;
    }

    @Test
    void offeredCanonicalSlotsResolveAcceptedTimesAndOrdinalsThroughInboundPath() {
        for (String utterance : List.of("10 works", "10", "10 AM", "10:00", "ten", "third one", "3",
                "दस बजे वाला", "10 baje wala")) {
            assertSelectedCanonicalSlot(utterance, "slot-1000");
        }
        for (String utterance : List.of("first one", "option 1", "choose number 1", "पहला स्लॉट", "first wala")) {
            assertSelectedCanonicalSlot(utterance, "slot-0900");
        }
        for (String utterance : List.of("the second slot", "दूसरा वाला", "second slot le lo")) {
            assertSelectedCanonicalSlot(utterance, "slot-0930");
        }
        assertSelectedCanonicalSlot("second one", "slot-0930");
        assertSelectedCanonicalSlot("first", "slot-0900");
        assertSelectedCanonicalSlot("9:30", "slot-0930");
        assertSelectedCanonicalSlot("nine thirty", "slot-0930");
    }

    @Test
    void exactTimeSelectionUsesMostRecentlyDisplayedSlotPageWithoutReload() {
        List<LocalTime> offered = List.of(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0),
                LocalTime.of(10, 30), LocalTime.of(11, 0), LocalTime.of(11, 30));
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "और स्लॉट बताओ", "Book for 11:30 a.m."),
                false, true, offered);
        SessionProjection session = harness.sessions().find("VOICE|" + TENANT + "|" + harness.result().callId())
                .orElseThrow();
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
        assertThat(session.activeDraft().selectedSlotReference()).isEqualTo(slotReference(LocalTime.of(11, 30), date));
        assertThat(session.activeDraft().preferredDate()).isEqualTo(date);
        assertThat(session.activeDraft().selectedProvider().doctorId()).isEqualTo(DOCTOR.toString());
        assertThat(session.latestAvailabilityResult().displayOffset()).isEqualTo(3);
        assertThat(harness.sessionSnapshots().get(2).latestAvailabilityResult().displayOffset())
                .as("SHOW_MORE advances the retained snapshot to its second page")
                .isEqualTo(3);
        assertThat(harness.availabilityCallCounts()).containsExactly(0L, 1L, 1L, 2L);
        // SHOW_MORE itself reuses the retained snapshot. Slot selection then
        // performs the required canonical stale-slot revalidation before asking
        // for final confirmation.
        assertThat(harness.availabilityCallTranscripts()).containsExactly("कल", "Book for 11:30 a.m.");
        assertThat(harness.result().conversationTurns().get(2).toolResultSummary()).contains("SLOT_CHOICES");
        verify(harness.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
        verifyNoSemanticAi(harness);
    }

    @Test
    void observedShowMoreSttVariantsAdvanceTheRetainedPageExactlyOnce() {
        List<LocalTime> offered = List.of(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0),
                LocalTime.of(10, 30), LocalTime.of(11, 0), LocalTime.of(11, 30));
        for (String phrase : List.of("Or slots.", "Or slots, tell me.", "Other slots, tell me.",
                "aur slots", "और स्लॉट")) {
            InboundHarness harness = runInboundSequence(List.of(
                    "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", phrase), false, true, offered);
            SessionProjection session = harness.sessions().find("VOICE|" + TENANT + "|"
                    + harness.result().callId()).orElseThrow();
            assertThat(session.latestAvailabilityResult().displayOffset()).as(phrase).isEqualTo(3);
            assertThat(harness.availabilityCallTranscripts()).as(phrase).containsExactly("कल");
            assertThat(harness.result().conversationTurns().getLast().toolResultSummary()).as(phrase)
                    .contains("SLOT_CHOICES");
            verify(harness.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
            verifyNoSemanticAi(harness);
        }
    }

    @Test
    void repeatedSlotPageIsStableAndDistinctFromShowMore() {
        List<LocalTime> offered = List.of(LocalTime.of(17, 0), LocalTime.of(17, 30), LocalTime.of(18, 0),
                LocalTime.of(18, 30), LocalTime.of(19, 0), LocalTime.of(19, 30),
                LocalTime.of(20, 0), LocalTime.of(20, 30), LocalTime.of(21, 0));
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "शाम", "repeat the slots",
                "स्लॉट दोबारा बताइए", "और समय बताइए", "say those times again"),
                true, true, offered);
        List<SessionProjection> states = harness.sessionSnapshots();
        assertThat(states.get(3).latestAvailabilityResult().displayOffset()).isZero();
        assertThat(states.get(4).latestAvailabilityResult().displayOffset()).isZero();
        assertThat(states.get(5).latestAvailabilityResult().displayOffset()).isEqualTo(3);
        assertThat(states.get(6).latestAvailabilityResult().displayOffset()).isEqualTo(3);
        assertThat(harness.result().conversationTurns().get(3).toolResultSummary()).isEqualTo("REPEAT_SLOT_PAGE");
        assertThat(harness.result().conversationTurns().get(4).toolResultSummary()).isEqualTo("REPEAT_SLOT_PAGE");
        assertThat(harness.result().conversationTurns().get(6).toolResultSummary()).isEqualTo("REPEAT_SLOT_PAGE");
        assertThat(harness.availabilityCallTranscripts()).containsExactly("कल", "शाम");
        verify(harness.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
    }

    @Test
    void currentBookingCanBeCancelledAndInspectionDoesNotMutateContext() {
        InboundHarness cancelled = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "appointment नहीं चाहिए"));
        assertThat(cancelled.sessionSnapshots().getLast().activeDraft()).isNull();
        assertThat(cancelled.result().conversationTurns().getLast().toolResultSummary())
                .isEqualTo("CURRENT_BOOKING_CANCELLED");
        verify(cancelled.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());

        InboundHarness close = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "appointment नहीं चाहिए, thank you"));
        assertThat(close.sessionSnapshots().getLast().closed()).isTrue();
        assertThat(close.sessionSnapshots().getLast().activeDraft()).isNull();
        assertThat(close.result().conversationTurns().getLast().detectedIntent()).isEqualTo("END_CONVERSATION");

        InboundHarness inspected = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "और समय बताइए",
                "ये किस डॉक्टर का appointment है?", "कौन सी तारीख?"), true, true,
                List.of(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0),
                        LocalTime.of(10, 30), LocalTime.of(11, 0), LocalTime.of(11, 30)));
        assertThat(inspected.sessionSnapshots().get(3).activeDraft())
                .isEqualTo(inspected.sessionSnapshots().get(2).activeDraft());
        assertThat(inspected.sessionSnapshots().get(4).activeDraft())
                .isEqualTo(inspected.sessionSnapshots().get(2).activeDraft());
        assertThat(inspected.sessionSnapshots().get(4).latestAvailabilityResult().displayOffset()).isEqualTo(3);
    }

    @Test
    void hindiProviderChangeControlPrecedesProviderExtractionAndSlotInspectionUsesPageContext() {
        InboundHarness change = runInboundSequence(List.of(
                "I want to book an appointment with Dr Amit Verma", "tomorrow", "10 AM", "मुझे डॉक्टर बदलना है"));
        assertThat(change.result().conversationTurns().getLast().toolResultSummary())
                .isEqualTo("CONTEXT_CHANGE_CLARIFICATION");
        assertThat(change.result().conversationTurns().getLast().aivaResponseText())
                .doesNotContain("Badalana");
        assertThat(change.sessionSnapshots().getLast().voiceClarificationState().pendingAction())
                .isEqualTo("CHANGE_PROVIDER");
        verify(change.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());

        AivaV2SessionStore sessions = new AivaV2SessionStore(Clock.systemUTC());
        String callId = "slot-page-context-inspection";
        LocalDate pageDate = LocalDate.of(2026, 10, 31);
        ProviderCandidate doctor = new ProviderCandidate("amit", "amit", DOCTOR.toString(), CLINIC.toString(),
                TENANT.toString(), null, null, "Amit Verma", "General Medicine", "Clinic", ProviderSource.CARE_PRIVATE,
                new ProviderCapabilities(true, true, false, false, false, true, false));
        Instant now = Instant.now();
        AvailabilityResult page = new AvailabilityResult(UUID.randomUUID(), null, 0, "inspection-page",
                doctor.providerHandle(), doctor.doctorId(), doctor.clinicId(), pageDate, "EVENING", null,
                "Asia/Kolkata", List.of(new AvailabilitySlot("slot-1", LocalTime.of(19, 0),
                LocalTime.of(19, 30), "7 PM")), "VOICE_DAYPART:EVENING", false,
                now, now.plusSeconds(600), 0, 3);
        sessions.update("VOICE|" + TENANT + "|" + callId, ignored -> new SessionProjection(callId, PATIENT,
                TENANT.toString(), null, null, null, page, null, now.plusSeconds(1800)).withActiveVoiceDoctor(doctor));
        InboundHarness inspected = runInboundSequence(List.of("ये किस डॉक्टर के slots हैं?", "ये किस डॉक्टर के स्लाइस है?",
                "कौन सी तारीख?"), false, true, null, List.of(), false, null, false,
                TENANT, PATIENT, CLINIC, DOCTOR, callId, sessions);
        assertThat(inspected.result().conversationTurns()).allSatisfy(turn ->
                assertThat(turn.toolResultSummary()).isEqualTo("READ_ONLY_CONTEXT_INSPECTION"));
        assertThat(inspected.result().conversationTurns().get(0).aivaResponseText()).contains("Amit Verma");
        assertThat(inspected.result().conversationTurns().get(1).aivaResponseText()).contains("Amit Verma");
        assertThat(inspected.result().conversationTurns().get(2).aivaResponseText()).contains("31", "2026");
        assertThat(inspected.sessionSnapshots()).allSatisfy(state -> {
            assertThat(state.activeDraft()).isNull();
            assertThat(state.latestAvailabilityResult().displayOffset()).isZero();
        });
    }

    @Test
    void slotDeclineRepeatAndCombinedCloseRemainReadOnlyAndLocalized() {
        List<LocalTime> offered = List.of(LocalTime.of(17, 0), LocalTime.of(17, 30), LocalTime.of(18, 0),
                LocalTime.of(18, 30), LocalTime.of(19, 0), LocalTime.of(19, 30));
        for (String phrase : List.of("इनमें से कोई नहीं", "इनमें से नहीं", "नहीं चुनना")) {
            InboundHarness declined = runInboundSequence(List.of(
                    "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "शाम", phrase), true, true, offered);
            assertThat(declined.result().conversationTurns().getLast().toolResultSummary()).as(phrase)
                    .isEqualTo("DECLINE_CURRENT_SLOT_OPTIONS");
            assertThat(declined.sessionSnapshots().getLast().latestAvailabilityResult().displayOffset()).isZero();
            verify(declined.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
        }
        InboundHarness repeat = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "शाम", "फिर से स्लॉट बताइए", "फिर से स्लॉट बताइए"),
                true, true, offered);
        assertThat(repeat.result().conversationTurns().get(3).toolResultSummary()).isEqualTo("REPEAT_SLOT_PAGE");
        assertThat(repeat.result().conversationTurns().get(4).toolResultSummary()).isEqualTo("REPEAT_SLOT_PAGE");
        assertThat(repeat.sessionSnapshots().get(3).latestAvailabilityResult().displayOffset()).isZero();
        assertThat(repeat.sessionSnapshots().get(4).latestAvailabilityResult().displayOffset()).isZero();
        assertThat(repeat.availabilityCallTranscripts()).containsExactly("कल", "शाम");

        InboundHarness close = runInboundSequence(List.of("मुझे डॉक्टर अमित वर्मा का अपॉइंटमेंट चाहिए", "कल",
                "ओके booking नहीं करनी, thank you"));
        assertThat(close.result().conversationTurns().getLast().detectedIntent()).isEqualTo("END_CONVERSATION");
        assertThat(close.result().conversationTurns().getLast().aivaResponseText()).contains("धन्यवाद");
        assertThat(close.sessionSnapshots().getLast().closed()).isTrue();
        assertThat(close.sessionSnapshots().getLast().activeDraft()).isNull();
        verify(close.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
    }

    @Test
    void relativeNextDayChainsFromRetainedDisplayedDateAndHindiNightRangeIsParsed() {
        LocalDate tomorrow = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
        for (String nextDay : List.of("उसके अगले दिन", "एक दिन बाद", "next day")) {
            InboundHarness chained = runInboundSequence(List.of("मुझे डॉक्टर अमित वर्मा का appointment चाहिए",
                    "कल", nextDay), false, true, List.of(LocalTime.of(18, 0), LocalTime.of(19, 0)));
            assertThat(chained.sessionSnapshots().get(1).activeDraft().preferredDate()).as(nextDay).isEqualTo(tomorrow);
            assertThat(chained.sessionSnapshots().getLast().activeDraft().preferredDate()).as(nextDay)
                    .isEqualTo(tomorrow.plusDays(1));
        }

        InboundHarness range = runInboundSequence(List.of("मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल",
                "रात में 6 से 9"), false, true,
                List.of(LocalTime.of(17, 0), LocalTime.of(18, 0), LocalTime.of(20, 0), LocalTime.of(21, 0)));
        assertThat(range.result().conversationTurns().getLast().toolResultSummary())
                .isEqualTo("SLOT_CHOICES");
        assertThat(range.sessionSnapshots().getLast().activeDraft().availabilityTimeConstraint())
                .isEqualTo(new AvailabilityTimeConstraint(AvailabilityTimeConstraintMode.BETWEEN,
                        LocalTime.of(18, 0), LocalTime.of(21, 0)));
        assertThat(range.result().conversationTurns().getLast().aivaResponseText())
                .doesNotContain("Select one of the current slots.");
    }


    @Test
    void explicitProviderChangeEscapesSlotCollectionWithoutMutation() {
        PatientPortalCareAiDoctorOption pediatrician = additionalDoctor("Dr Ravi Mehta");
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "doctor बदलना है",
                "Ravi Mehta"), false, true, null, List.of(pediatrician));
        assertThat(harness.result().conversationTurns().get(2).toolResultSummary())
                .isEqualTo("CONTEXT_CHANGE_CLARIFICATION");
        assertThat(harness.sessionSnapshots().get(2).activeDraft().selectedSlotReference()).isNull();
        verify(harness.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
    }

    @Test
    void specialtyRequestWhileSlotsAreDisplayedStartsCanonicalProviderSwitch() {
        PatientPortalCareAiDoctorOption pediatrician = new PatientPortalCareAiDoctorOption(
                UUID.randomUUID().toString(), "Dr Pooja Rao", "Pediatrics", UUID.randomUUID(), CLINIC,
                TENANT, "clinic", "Clinic");
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "और समय बताइए",
                "मुझे बच्चे के डॉक्टर का appointment चाहिए"), true, true,
                List.of(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0)), List.of(pediatrician));

        var switchTurn = harness.result().conversationTurns().getLast();
        assertThat(switchTurn.toolResultSummary()).isEqualTo("PROVIDER_SWITCH_CONFIRMATION_REQUIRED");
        assertThat(harness.sessionSnapshots().getLast().voiceClarificationState().pendingProviderSwitch()
                .targetProviderId()).isEqualTo(pediatrician.publicDoctorId());
        assertThat(harness.sessionSnapshots().getLast().activeDraft().preferredDate())
                .isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1));
        verify(harness.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
    }

    @Test
    void explicitDiscoveryDateAndDaypartSeedBookingDraftWithoutUsingSystemDefault() {
        InboundHarness harness = runInboundSequence(List.of(
                "Which doctors are available tomorrow evening?", "I want an appointment with Dr Amit Verma"));
        SessionProjection booking = harness.sessions().find("VOICE|" + TENANT + "|"
                + harness.result().callId()).orElseThrow();
        assertThat(booking.activeDraft()).isNotNull();
        assertThat(booking.activeDraft().preferredDate())
                .isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1));
        assertThat(booking.activeDraft().preferredTimeWindow()).isEqualTo("evening");
        assertThat(booking.activeDraft().selectedProvider()).isNotNull();
        assertThat(booking.activeDraft().selectedProvider().doctorId()).isEqualTo(DOCTOR.toString());
        verify(harness.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
    }

    @Test
    void hindiSpokenEveningTimeSelectsOnlyTheSecondDisplayedPage() {
        List<LocalTime> offered = List.of(LocalTime.of(16, 30), LocalTime.of(17, 0), LocalTime.of(17, 30),
                LocalTime.of(18, 30), LocalTime.of(19, 0), LocalTime.of(19, 30));
        for (String phrase : List.of("सात तीस PM बुक कर दो", "साढ़े सात वाला बुक करो", "सात बजकर तीस वाला")) {
            InboundHarness harness = runInboundSequence(List.of(
                    "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल शाम", "और स्लॉट", phrase),
                    false, true, offered);
            SessionProjection session = harness.sessions().find("VOICE|" + TENANT + "|"
                    + harness.result().callId()).orElseThrow();
            assertThat(session.activeDraft().exactTime()).as(phrase).isEqualTo(LocalTime.of(19, 30));
            assertThat(session.activeDraft().selectedSlotReference()).as(phrase)
                    .isEqualTo(slotReference(LocalTime.of(19, 30), LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1)));
            assertThat(session.latestAvailabilityResult().displayOffset()).isEqualTo(3);
            assertThat(harness.result().conversationTurns().get(3).toolResultSummary()).as(phrase)
                    .isEqualTo("BOOKING_CONFIRMATION_PENDING");
            verify(harness.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
            verifyNoSemanticAi(harness);
        }
    }

    @Test
    void confirmationAcceptsNaturalAffirmativesOnlyAtThePreparedConfirmationBoundary() {
        for (String answer : List.of("Yes, book.", "yes please", "yes book", "book it", "confirm it", "go ahead",
                "जी हाँ", "हाँ बुक कर दो", "कर दीजिए", "बुक कर दीजिए", "पक्का कर दो", "haan book karo",
                "yes book kar do", "confirm kar do", "book it please")) {
            InboundHarness harness = runInboundSequence(List.of(
                    "I want to book an appointment with Dr Amit Verma", "tomorrow", "10 AM", answer));
            assertThat(harness.result().conversationTurns().getLast().toolResultSummary()).as(answer)
                    .isEqualTo("BOOKING_CONFIRMED");
            verify(harness.appointments(), times(1))
                    .createScheduled(eq(TENANT), any(), eq(null), eq(false), any(), any());
            verifyNoSemanticAi(harness);
        }
    }

    @Test
    void hindiAndHinglishGenericDoctorAvailabilityReturnsAvailableProviderNames() {
        for (String utterance : List.of("कल कौन से डॉक्टर उपलब्ध हैं?", "कल कौन सा डॉक्टर उपलब्ध है?",
                "kal kaunse doctor available hain?", "कल बाल रोग विशेषज्ञ उपलब्ध है?",
                "कल pediatric doctor available hai?")) {
            InboundHarness harness = runInboundSequence(List.of(utterance), false, true, null,
                    List.of(new PatientPortalCareAiDoctorOption(UUID.randomUUID().toString(), "Dr Pooja Rao",
                            "Pediatrics", UUID.randomUUID(), CLINIC, TENANT, "clinic", "Clinic")));
            var turn = harness.result().conversationTurns().getLast();
            assertThat(turn.selectedTool()).as(utterance).isEqualTo("FIND_DOCTOR");
            String expectedHindiName = utterance.toLowerCase(Locale.ROOT).contains("pediatric")
                    ? "पूजा राव" : utterance.contains("बाल रोग") ? "पूजा राव" : "अमित वर्मा";
            String expectedLatinName = utterance.toLowerCase(Locale.ROOT).contains("pediatric")
                    ? "Pooja Rao" : "Amit Verma";
            assertThat(turn.aivaResponseText()).as(utterance).satisfiesAnyOf(
                    value -> assertThat(value).contains(expectedHindiName),
                    value -> assertThat(value).contains(expectedLatinName));
            if (utterance.contains("कल") || utterance.toLowerCase(Locale.ROOT).contains("kal")) {
                String response = turn.aivaResponseText();
                assertThat(response).as(utterance).satisfiesAnyOf(
                        value -> assertThat(value).containsAnyOf("कल", "kal", "tomorrow"),
                        value -> assertThat(value).containsPattern("\\d{1,2} .*\\d{4}"));
            }
            assertThat(turn.aivaResponseText()).as(utterance).doesNotContain("which doctor");
            verifyNoSemanticAi(harness);
            verify(harness.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
        }
    }

    @Test
    void genericConsultationAsksTenantDerivedSpecialtyBeforeChoosingDoctor() {
        var pediatrics = new PatientPortalCareAiDoctorOption(UUID.randomUUID().toString(), "Dr Ravi Mehta",
                "Pediatrics", UUID.randomUUID(), CLINIC, TENANT, "clinic", "Clinic");
        var harness = runInboundSequence(List.of("I need to consult a doctor"), false, true, null,
                List.of(pediatrics));
        var turn = harness.result().conversationTurns().getLast();
        assertThat(turn.selectedTool()).isEqualTo("FIND_DOCTOR");
        assertThat(turn.aivaResponseText().toLowerCase(Locale.ROOT)).contains("general medicine", "pediatrics")
                .contains("which type of doctor");
        assertThat(turn.aivaResponseText()).doesNotContain("Amit Verma", "Ravi Mehta");
        assertThat(harness.sessions().find("VOICE|" + TENANT + "|" + harness.result().callId())
                .orElseThrow().voiceClarificationState().pendingAction()).isEqualTo("CONSULTATION_SPECIALTY");
        verify(harness.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
        verifyNoSemanticAi(harness);
    }

    @Test
    void consultationSpecialtyChoiceRetainsRequestedTomorrowAndDaypart() {
        var pediatrics = new PatientPortalCareAiDoctorOption(UUID.randomUUID().toString(), "Dr Ravi Mehta",
                "Pediatrics", UUID.randomUUID(), CLINIC, TENANT, "clinic", "Clinic");
        InboundHarness harness = runInboundSequence(List.of("kal evening doctor se consult karna hai", "Pediatrics"),
                true, true, null, List.of(pediatrics));
        var turns = harness.result().conversationTurns();
        assertThat(turns).hasSize(2);
        assertThat(turns.getFirst().selectedTool()).isEqualTo("FIND_DOCTOR");
        assertThat(turns.getFirst().aivaResponseText()).contains("Pediatrics");
        assertThat(turns.getLast().selectedTool()).isEqualTo("FIND_DOCTOR");
        assertThat(turns.getLast().toolInputSpeciality()).isEqualTo("Pediatrics");
        assertThat(turns.getLast().aivaResponseText()).contains("Ravi Mehta");
        assertThat(turns.getLast().aivaResponseText()).containsAnyOf("kal", "tomorrow", "2026");
        SessionProjection session = harness.sessions().find("VOICE|" + TENANT + "|" + harness.result().callId())
                .orElseThrow();
        assertThat(session.latestAvailabilityResult().date())
                .isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1));
        assertThat(session.latestAvailabilityResult().timeWindow()).isEqualTo("EVENING");
        assertThat(session.latestAvailabilityResult().cursor()).isEqualTo("VOICE_CONFIRM_SCHEDULE");
        verify(harness.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
        verifyNoSemanticAi(harness);
    }

    @Test
    void hindiAndHinglishGenericConsultationClarificationsStayReadOnly() {
        var pediatrics = new PatientPortalCareAiDoctorOption(UUID.randomUUID().toString(), "Dr Ravi Mehta",
                "Pediatrics", UUID.randomUUID(), CLINIC, TENANT, "clinic", "Clinic");
        for (String phrase : List.of("मुझे डॉक्टर से मिलना है", "doctor se consult karna hai")) {
            InboundHarness harness = runInboundSequence(List.of(phrase), false, true, null, List.of(pediatrics));
            var turn = harness.result().conversationTurns().getLast();
            assertThat(turn.selectedTool()).as(phrase).isEqualTo("FIND_DOCTOR");
            assertThat(turn.aivaResponseText()).as(phrase).containsAnyOf("General Medicine", "जनरल मेडिसिन", "Pediatrics");
            assertThat(turn.aivaResponseText()).as(phrase).containsAnyOf("specialty", "specialty", "किस प्रकार");
            verify(harness.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
            verifyNoSemanticAi(harness);
        }
    }

    @Test
    void oneDoctorConsultationOffersSlotsAndDoesNotAutoBook() {
        InboundHarness harness = runInboundSequence(List.of("I need to consult a doctor"));
        var turn = harness.result().conversationTurns().getLast();
        assertThat(turn.selectedTool()).isEqualTo("FIND_DOCTOR");
        assertThat(turn.aivaResponseText()).contains("Dr. Amit Verma", "Would you like me to check");
        assertThat(harness.sessions().find("VOICE|" + TENANT + "|" + harness.result().callId())
                .orElseThrow().latestAvailabilityResult().cursor()).isEqualTo("VOICE_CONFIRM_SCHEDULE");
        verify(harness.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
    }

    @Test
    void activeProviderPronounsAndSameProviderBookingRequestRetainCanonicalProvider() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "शाम",
                "उनका appointment book कर दीजिए", "अमित वर्मा का appointment चाहिए"),
                false, true, List.of(LocalTime.of(17, 0), LocalTime.of(17, 30)));
        SessionProjection before = harness.sessionSnapshots().get(2);
        SessionProjection pronoun = harness.sessionSnapshots().get(3);
        SessionProjection reaffirmed = harness.sessionSnapshots().get(4);
        assertThat(pronoun.voiceClarificationState() == null
                || !"PROVIDER_SWITCH_CONFIRMATION".equals(pronoun.voiceClarificationState().pendingAction())).isTrue();
        assertThat(pronoun.activeDraft().selectedProvider().doctorId()).isEqualTo(DOCTOR.toString());
        assertThat(pronoun.activeDraft().preferredDate()).isEqualTo(before.activeDraft().preferredDate());
        assertThat(reaffirmed.voiceClarificationState() == null
                || !"PROVIDER_SWITCH_CONFIRMATION".equals(reaffirmed.voiceClarificationState().pendingAction())).isTrue();
        assertThat(reaffirmed.activeDraft().selectedProvider().doctorId()).isEqualTo(DOCTOR.toString());
        assertThat(reaffirmed.activeDraft().preferredTimeWindow()).isEqualTo("evening");
        verify(harness.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
    }

    @Test
    void providerSwitchDeclineConsumesSameTurnCurrentProviderAndDaypart() {
        PatientPortalCareAiDoctorOption ravi = additionalDoctor("Dr Ravi Mehta");
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "सुबह",
                "Ravi Mehta appointment chahiye", "नहीं, अमित वर्मा का appointment शाम का दे दीजिए"),
                false, true, List.of(LocalTime.of(9, 0), LocalTime.of(9, 30)), List.of(ravi));
        SessionProjection before = harness.sessionSnapshots().get(2);
        SessionProjection pending = harness.sessionSnapshots().get(3);
        SessionProjection after = harness.sessionSnapshots().get(4);
        assertThat(pending.voiceClarificationState().pendingAction()).isEqualTo("PROVIDER_SWITCH_CONFIRMATION");
        assertThat(after.voiceClarificationState()).isNull();
        assertThat(after.activeDraft().selectedProvider().doctorId()).isEqualTo(DOCTOR.toString());
        assertThat(after.activeDraft().preferredDate()).isEqualTo(before.activeDraft().preferredDate());
        assertThat(after.activeDraft().preferredTimeWindow()).isEqualTo("evening");
        verify(harness.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
    }

    @Test
    void providerClarificationAcceptsCanonicalSpecialtyAndRetainsRequestedDate() {
        var secondGeneral = new PatientPortalCareAiDoctorOption(UUID.randomUUID().toString(), "Dr Nisha Shah",
                "General Medicine", UUID.randomUUID(), CLINIC, TENANT, "clinic", "Clinic");
        InboundHarness harness = runInboundSequence(List.of("कल doctor appointment चाहिए", "General Medicine"),
                true, true, null, List.of(secondGeneral));
        assertThat(harness.result().conversationTurns().getFirst().selectedTool()).isEqualTo("FIND_DOCTOR");
        assertThat(harness.result().conversationTurns().getLast().selectedTool()).isEqualTo("FIND_DOCTOR");
        assertThat(harness.result().conversationTurns().getLast().toolInputSpeciality()).isEqualTo("General Medicine");
        assertThat(harness.result().conversationTurns().getLast().aivaResponseText()).contains("Amit Verma", "Nisha Shah");
        verify(harness.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
        verifyNoSemanticAi(harness);
    }

    @Test
    void consultationWithSeveralDoctorsInOneSpecialtyAsksWhichDoctor() {
        var secondGeneral = new PatientPortalCareAiDoctorOption(UUID.randomUUID().toString(), "Dr Nisha Shah",
                "General Medicine", UUID.randomUUID(), CLINIC, TENANT, "clinic", "Clinic");
        InboundHarness harness = runInboundSequence(List.of("I need to consult a doctor"), false, true, null,
                List.of(secondGeneral));
        var turn = harness.result().conversationTurns().getLast();
        assertThat(turn.selectedTool()).isEqualTo("FIND_DOCTOR");
        assertThat(turn.aivaResponseText()).contains("Amit Verma", "Nisha Shah", "Which doctor");
        var session = harness.sessions().find("VOICE|" + TENANT + "|" + harness.result().callId()).orElseThrow();
        assertThat(session.voiceClarificationState().pendingAction()).isEqualTo("CONSULTATION_PROVIDER");
        assertThat(session.activeVoiceDoctor()).isNull();
        verify(harness.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
        verifyNoSemanticAi(harness);
    }

    @Test
    void needAppointmentStartsConsultationButExistingAppointmentQuestionsStayUnsupported() {
        for (String phrase : List.of("I need an appointment", "मुझे अपॉइंटमेंट चाहिए")) {
            InboundHarness harness = runInboundSequence(List.of(phrase));
            var turn = harness.result().conversationTurns().getLast();
            assertThat(turn.selectedTool()).as(phrase).isEqualTo("FIND_DOCTOR");
            verify(harness.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
        }
        for (String phrase : List.of("Do I have an appointment?", "meri appointment check karo")) {
            InboundHarness harness = runInboundSequence(List.of(phrase));
            assertThat(harness.result().conversationTurns().getLast().detectedIntent())
                    .contains("PATIENT_DATA_NOT_SUPPORTED");
            verify(harness.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
        }
    }

    @Test
    void knownGeneralMedicineAndPediatricPhrasesAreSpecialtyDiscoveryNotDoctorNames() {
        var general = List.of(new PatientPortalCareAiDoctorOption(UUID.randomUUID().toString(), "Dr Amit Verma",
                "General Medicine", UUID.randomUUID(), CLINIC, TENANT, "clinic", "Clinic"));
        for (String phrase : List.of("जनरल फिजिशियन का नाम बताइए", "general physician ka naam batao")) {
            var harness = runInboundSequence(List.of(phrase), false, true, null, general);
            var turn = harness.result().conversationTurns().getLast();
            assertThat(turn.selectedTool()).as(phrase).isEqualTo("FIND_DOCTOR");
            assertThat(turn.toolInputSpeciality()).as(phrase).isEqualTo("General Medicine");
            assertThat(turn.aivaResponseText()).as(phrase).contains(phrase.contains("जनरल")
                    ? "अमित वर्मा" : "Amit Verma");
            verifyNoSemanticAi(harness);
        }

        var pediatric = List.of(new PatientPortalCareAiDoctorOption(UUID.randomUUID().toString(), "Dr Pooja Rao",
                "Pediatrics", UUID.randomUUID(), CLINIC, TENANT, "clinic", "Clinic"));
        for (String phrase : List.of("pediatric doctor ka naam batao", "बच्चों का डॉक्टर कौन है")) {
            var harness = runInboundSequence(List.of(phrase), false, true, null, pediatric);
            var turn = harness.result().conversationTurns().getLast();
            assertThat(turn.selectedTool()).as(phrase).isEqualTo("FIND_DOCTOR");
            assertThat(turn.toolInputSpeciality()).as(phrase).isEqualTo("Pediatrics");
            assertThat(turn.aivaResponseText()).as(phrase).contains(phrase.contains("बच्चों")
                    ? "पूजा राव" : "Pooja Rao");
            verifyNoSemanticAi(harness);
        }
    }

    @Test
    void hindiMorningShowMoreNaturalTimeAndConfirmationBookExactlyOnce() {
        List<LocalTime> offered = List.of(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0),
                LocalTime.of(10, 30), LocalTime.of(11, 0), LocalTime.of(11, 30));
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "सुबह", "और स्लॉट बताओ",
                "ग्यारह तीस वाला बुक करो", "हाँ बुक कर दो", "हाँ बुक कर दो"), false, true, offered);
        SessionProjection session = harness.sessions().find("VOICE|" + TENANT + "|" + harness.result().callId())
                .orElseThrow();
        LocalDate tomorrow = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
        assertThat(harness.result().conversationTurns().get(3).toolResultSummary()).isEqualTo("SLOT_CHOICES");
        assertThat(harness.result().conversationTurns().get(4).toolResultSummary()).isEqualTo("BOOKING_CONFIRMATION_PENDING");
        assertThat(harness.result().conversationTurns().get(5).toolResultSummary()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(session.completedBookingOutcome()).isNotNull();
        assertThat(session.completedBookingOutcome().date()).isEqualTo(tomorrow);
        assertThat(session.completedBookingOutcome().time()).isEqualTo(LocalTime.of(11, 30));
        assertThat(harness.availabilityCallTranscripts()).containsExactly("कल", "सुबह", "ग्यारह तीस वाला बुक करो");
        verify(harness.appointments(), times(1)).createScheduled(eq(TENANT), any(), eq(null), eq(false), any(), any());
        verifyNoInteractions(harness.otpService());
        verifyNoSemanticAi(harness);
    }

    @Test
    void hinglishMorningShowMoreNaturalTimeAndConfirmationBookExactlyOnce() {
        List<LocalTime> offered = List.of(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0),
                LocalTime.of(10, 30), LocalTime.of(11, 0), LocalTime.of(11, 30));
        InboundHarness harness = runInboundSequence(List.of(
                "Doctor Amit Verma ke saath appointment book karna hai", "kal", "morning slot dikhao",
                "aur slots batao", "11:30 wala book karo", "haan book kar do", "haan book kar do"),
                false, true, offered);
        SessionProjection session = harness.sessions().find("VOICE|" + TENANT + "|" + harness.result().callId())
                .orElseThrow();
        LocalDate tomorrow = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
        assertThat(harness.result().conversationTurns().get(3).toolResultSummary()).isEqualTo("SLOT_CHOICES");
        assertThat(harness.result().conversationTurns().get(4).toolResultSummary()).isEqualTo("BOOKING_CONFIRMATION_PENDING");
        assertThat(harness.result().conversationTurns().get(5).toolResultSummary()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(session.completedBookingOutcome()).isNotNull();
        assertThat(session.completedBookingOutcome().date()).isEqualTo(tomorrow);
        assertThat(session.completedBookingOutcome().time()).isEqualTo(LocalTime.of(11, 30));
        assertThat(harness.availabilityCallTranscripts()).containsExactly("kal", "morning slot dikhao",
                "11:30 wala book karo");
        verify(harness.appointments(), times(1)).createScheduled(eq(TENANT), any(), eq(null), eq(false), any(), any());
        verifyNoInteractions(harness.otpService());
        verifyNoSemanticAi(harness);
    }

    @Test
    void specialtyOnlyPediatricQueryReturnsProviderNames() {
        List<PatientPortalCareAiDoctorOption> pediatric = List.of(new PatientPortalCareAiDoctorOption(
                UUID.randomUUID().toString(), "Dr Pooja Rao", "Pediatrics", UUID.randomUUID(), CLINIC,
                TENANT, "clinic", "Clinic"));
        InboundHarness hindi = runInboundSequence(List.of("पीडियाट्रिक"), false, true, null, pediatric);
        assertThat(hindi.result().conversationTurns().getLast().selectedTool()).isEqualTo("FIND_DOCTOR");
        assertThat(hindi.result().conversationTurns().getLast().toolInputSpeciality()).isEqualTo("Pediatrics");
        assertThat(hindi.result().conversationTurns().getLast().aivaResponseText()).contains("पूजा राव");

        InboundHarness hinglish = runInboundSequence(List.of("pediatric doctor ka naam batao"), false, true,
                null, pediatric);
        assertThat(hinglish.result().conversationTurns().getLast().selectedTool()).isEqualTo("FIND_DOCTOR");
        assertThat(hinglish.result().conversationTurns().getLast().toolInputSpeciality()).isEqualTo("Pediatrics");
        assertThat(hinglish.result().conversationTurns().getLast().aivaResponseText()).contains("Pooja Rao");
    }

    @Test
    void existingAppointmentQuestionsStayUnsupportedAndThankYouClosesInSessionLanguage() {
        for (String phrase : List.of("Do you have an appointment of Amit Verma?", "मेरी पुरानी अपॉइंटमेंट बताओ",
                "meri existing appointment check karo")) {
            InboundHarness harness = runInboundSequence(List.of(phrase));
            assertThat(harness.result().conversationTurns().getLast().detectedIntent()).contains("PATIENT_DATA_NOT_SUPPORTED");
            verifyNoSemanticAi(harness);
            verify(harness.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
        }

        InboundHarness english = runInboundSequence(List.of("I want to book an appointment", "Thank you"));
        assertThat(english.result().conversationTurns().getLast().aivaResponseText()).contains("Thank you").doesNotContain("धन्यवाद");
        assertThat(english.result().conversationTurns().getLast().detectedIntent()).isEqualTo("END_CONVERSATION");
        verify(english.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
        InboundHarness hindi = runInboundSequence(List.of("मुझे डॉक्टर अमित वर्मा के साथ अपॉइंटमेंट बुक करना है", "धन्यवाद"));
        assertThat(hindi.result().conversationTurns().getLast().aivaResponseText()).contains("धन्यवाद");
        assertThat(hindi.result().conversationTurns().getLast().detectedIntent()).isEqualTo("END_CONVERSATION");
        verify(hindi.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
        InboundHarness hinglish = runInboundSequence(List.of("Doctor Amit Verma available hain?", "thank you"));
        assertThat(hinglish.result().conversationTurns().getLast().aivaResponseText()).contains("Thank you", "Apna dhyan");
        assertThat(hinglish.result().conversationTurns().getLast().detectedIntent()).isEqualTo("END_CONVERSATION");
        verify(hinglish.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
    }

    @Test
    void identifiedCallerPatientBindingSurvivesMultipleDotVoiceTurns() {
        InboundHarness harness = runInboundSequence(List.of("Which doctors are available?", "tomorrow"));

        assertThat(harness.result().patientIdentificationStatus()).isEqualTo("SINGLE_MATCH");
        assertThat(harness.result().conversationPartyType()).isEqualTo("IDENTIFIED_PATIENT");
        SessionProjection session = harness.sessions().find(
                "VOICE|" + TENANT + "|" + harness.result().callId()).orElseThrow();
        assertThat(session.patientId()).isEqualTo(PATIENT);
        assertThat(session.activeWorkflow()).isEqualTo("NONE");
        verifyNoSemanticAi(harness);
    }

    @Test
    void courtesyCloseVariantsWinOverBookingContextAndRepeatedCloseDoesNotMutate() {
        for (String phrase : List.of("Okay. Thank you.", "No, thank you.", "that's all thank you",
                "I'm done thanks", "ठीक है धन्यवाद", "बस धन्यवाद", "नहीं धन्यवाद",
                "मुझे मदद नहीं चाहिए धन्यवाद", "thik hai thanks", "bas thank you", "nahi thanks",
                "mujhe aur help nahi chahiye thanks")) {
            InboundHarness harness = runInboundSequence(List.of(
                    "I want to book an appointment", phrase, phrase));
            var turns = harness.result().conversationTurns();
            assertThat(turns.stream().map(turn -> turn.detectedIntent()).toList())
                    .as("phrase=%s", phrase).contains("END_CONVERSATION");
            assertThat(turns.getLast().detectedIntent()).as("last turn for=%s", phrase)
                    .isEqualTo("END_CONVERSATION");
            verify(harness.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
            verifyNoSemanticAi(harness);
        }
    }

    @Test
    void invalidAndOutOfContextSlotLikeTurnsDoNotSelectAnything() {
        InboundHarness invalid = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का अपॉइंटमेंट चाहिए।", "कल।", "11"));
        SessionProjection invalidSession = invalid.sessions().find(
                "VOICE|" + TENANT + "|" + invalid.result().callId()).orElseThrow();
        assertThat(invalidSession.activeDraft().selectedSlotReference()).isNull();
        assertThat(invalidSession.latestAvailabilityResult().slots())
                .extracting(slot -> slot.slotReference())
                .containsExactly(slotReference(LocalTime.of(9, 0), LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1)),
                        slotReference(LocalTime.of(9, 30), LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1)),
                        slotReference(LocalTime.of(10, 0), LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1)));
        assertThat(invalid.sessionSnapshots().get(2).activeDraft())
                .isEqualTo(invalid.sessionSnapshots().get(1).activeDraft());
        assertThat(invalid.sessionSnapshots().get(2).latestAvailabilityResult())
                .isEqualTo(invalid.sessionSnapshots().get(1).latestAvailabilityResult());
        assertThat(invalid.result().conversationTurns().getLast().toolResultSummary())
                .isNotEqualTo("GUEST_PREPARATION_DENIED");

        InboundHarness outside = runInboundSequence(List.of("10 works"));
        SessionProjection outsideSession = outside.sessions().find(
                "VOICE|" + TENANT + "|" + outside.result().callId()).orElseThrow();
        assertThat(outsideSession.activeDraft()).isNull();
        assertThat(outside.result().conversationTurns().getFirst().detectedIntent())
                .isNotEqualTo("SELECT_SLOT");
    }

    private void assertSelectedCanonicalSlot(String selection, String expectedReference) {
        LocalDate expectedDate = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
        LocalTime expectedTime = switch (expectedReference) {
            case "slot-0900" -> LocalTime.of(9, 0);
            case "slot-0930" -> LocalTime.of(9, 30);
            default -> LocalTime.of(10, 0);
        };
        String canonicalReference = slotReference(expectedTime, expectedDate);
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का अपॉइंटमेंट चाहिए।", "कल।", selection));
        InboundVoiceResult result = harness.result();
        SessionProjection session = harness.sessions().find("VOICE|" + TENANT + "|" + result.callId()).orElseThrow();
        assertThat(result.conversationTurns()).hasSize(3);
        assertThat(result.conversationTurns().get(0).toolResultSummary()).isEqualTo("NEED_DATE");
        assertThat(result.conversationTurns().get(1).toolResultSummary()).isEqualTo("SLOT_CHOICES");
        assertThat(result.conversationTurns().getLast().detectedIntent()).as(selection).isEqualTo("BOOK_APPOINTMENT");
        assertThat(harness.sessionSnapshots()).hasSize(3);
        UUID draftId = harness.sessionSnapshots().getFirst().activeDraft().draftId();
        assertThat(harness.sessionSnapshots()).allSatisfy(snapshot ->
                assertThat(snapshot.activeDraft().draftId()).isEqualTo(draftId));
        assertThat(session.activeWorkflow()).isIn("BOOKING", "BOOKING_CONFIRMATION");
        assertThat(session.activeDraft().selectedProvider().displayName()).contains("Amit Verma");
        assertThat(session.activeDraft().preferredDate())
                .isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1));
        assertThat(session.activeDraft().selectedSlotReference()).isEqualTo(canonicalReference);
        assertThat(session.latestAvailabilityResult().slots()).extracting(slot -> slot.slotReference())
                .contains(canonicalReference);
        assertThat(harness.gatewayTurns()).extracting(NormalizedUserTurn::rawText).doesNotContain(selection);
        verify(harness.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
    }

    @Test
    void finalConfirmationUsesSharedBookingBoundaryAndCreatesAppointmentOnce() {
        assertThat(new VoiceTestProperties().isAivaV2Enabled())
                .as("alternate shared Voice defaults off while authorized DotVoice uses VoiceReadOnlyAivaService directly")
                .isFalse();
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का अपॉइंटमेंट चाहिए।", "कल।", "10 works",
                "हाँ बुक कर दीजिए।", "हाँ बुक कर दीजिए।"));
        InboundVoiceResult result = harness.result();
        assertThat(result.conversationTurns()).hasSize(5);
        assertThat(result.conversationTurns().get(2).toolResultSummary()).isEqualTo("BOOKING_CONFIRMATION_PENDING");
        assertThat(result.conversationTurns().get(3).toolResultSummary()).isEqualTo("BOOKING_CONFIRMED");
        SessionProjection preparedSession = harness.sessionSnapshots().get(2);
        assertThat(preparedSession.activeDraft().selectedProvider().displayName()).contains("Amit Verma");
        assertThat(preparedSession.activeDraft().preferredDate()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1));
        assertThat(preparedSession.activeDraft().selectedSlotReference()).isEqualTo(slotReference(LocalTime.of(10, 0),
                LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1)));
        assertThat(preparedSession.activeDraft().draftId()).isEqualTo(harness.sessionSnapshots().get(1).activeDraft().draftId());
        assertThat(result.conversationTurns().get(3).runtimeDiagnostics().toString())
                .contains("executionDecision=BOOKED");
        assertThat(result.conversationTurns().get(4).toolResultSummary()).isNotEqualTo("BOOKING_CONFIRMED");
        verify(harness.appointments()).createScheduled(eq(TENANT), any(), eq(null), eq(false), any(), any());
        verify(harness.idempotency()).storeResponse(any(), anyString(), anyString(), anyString());
        verify(harness.appointments(), org.mockito.Mockito.times(1))
                .createScheduled(eq(TENANT), any(), eq(null), eq(false), any(), any());
    }

    @Test
    void hinglishYesBookKarDoConfirmsOnlyThePreparedBooking() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए।", "कल।", "10 works", "yes book kar do"));
        assertThat(harness.result().conversationTurns().get(2).toolResultSummary())
                .isEqualTo("BOOKING_CONFIRMATION_PENDING");
        assertThat(harness.result().conversationTurns().get(3).toolResultSummary())
                .isEqualTo("BOOKING_CONFIRMED");
        verify(harness.appointments(), org.mockito.Mockito.times(1))
                .createScheduled(eq(TENANT), any(), eq(null), eq(false), any(), any());
        verify(harness.orchestration(), never()).complete(any());
    }

    @Test
    void ambiguousConfirmationRepeatsBoundSummaryWithoutExecutingOrDroppingPreparedState() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का अपॉइंटमेंट चाहिए।", "कल।", "10 works", "शायद"));
        SessionProjection prepared = harness.sessionSnapshots().get(2);
        SessionProjection after = harness.sessionSnapshots().get(3);
        assertThat(harness.result().conversationTurns().getLast().toolResultSummary())
                .isEqualTo("BOOKING_CONFIRMATION_PENDING");
        assertThat(harness.result().conversationTurns().getLast().aivaResponseText())
                .contains("अमित वर्मा", "दस बजे");
        assertThat(after.activeDraft().draftId()).isEqualTo(prepared.activeDraft().draftId());
        assertThat(after.activeDraft().selectedProvider()).isEqualTo(prepared.activeDraft().selectedProvider());
        assertThat(after.activeDraft().preferredDate()).isEqualTo(prepared.activeDraft().preferredDate());
        assertThat(after.activeDraft().selectedSlotReference()).isEqualTo(prepared.activeDraft().selectedSlotReference());
        assertThat(after.pendingConfirmation()).isEqualTo(prepared.pendingConfirmation());
        verify(harness.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
        assertThat(harness.availabilityCallCounts().get(3)).isEqualTo(harness.availabilityCallCounts().get(2));
        org.mockito.Mockito.verifyNoInteractions(harness.orchestration());
    }

    @Test
    void decliningConfirmationRetainsBookingContextAndReturnsToSlotSelection() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का अपॉइंटमेंट चाहिए।", "कल।", "10 works", "नहीं"));
        SessionProjection before = harness.sessionSnapshots().get(2);
        SessionProjection after = harness.sessionSnapshots().get(3);
        assertThat(harness.result().conversationTurns().getLast().toolResultSummary())
                .isEqualTo("BOOKING_CONFIRMATION_DECLINED");
        assertThat(after.activeDraft().draftId()).isEqualTo(before.activeDraft().draftId());
        assertThat(after.activeDraft().selectedProvider()).isEqualTo(before.activeDraft().selectedProvider());
        assertThat(after.activeDraft().preferredDate()).isEqualTo(before.activeDraft().preferredDate());
        assertThat(after.latestAvailabilityResult()).isEqualTo(before.latestAvailabilityResult());
        assertThat(after.activeDraft().selectedSlotReference()).isNull();
        assertThat(after.pendingConfirmation()).isNull();
        assertThat(after.activeWorkflow()).isEqualTo("BOOKING");
        verify(harness.appointments(), never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
        assertThat(harness.availabilityCallCounts().get(3)).isEqualTo(harness.availabilityCallCounts().get(2));
    }

    @Test
    void changingDisplayedSlotDuringConfirmationInvalidatesOldBindingAndPreparesNewOne() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "first one", "second one"));
        SessionProjection firstConfirmation = harness.sessionSnapshots().get(2);
        SessionProjection changed = harness.sessionSnapshots().get(3);
        assertThat(firstConfirmation.pendingConfirmation()).isNotNull();
        assertThat(changed.activeDraft().selectedProvider()).isEqualTo(firstConfirmation.activeDraft().selectedProvider());
        assertThat(changed.activeDraft().preferredDate()).isEqualTo(firstConfirmation.activeDraft().preferredDate());
        assertThat(changed.activeDraft().selectedSlotReference())
                .isEqualTo(slotReference(LocalTime.of(9, 30), LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1)))
                .isNotEqualTo(firstConfirmation.activeDraft().selectedSlotReference());
        assertThat(changed.pendingConfirmation()).isNotNull();
        assertThat(changed.pendingConfirmation().confirmationRef())
                .isNotEqualTo(firstConfirmation.pendingConfirmation().confirmationRef());
        assertThat(changed.latestAvailabilityResult().requestId())
                .isEqualTo(firstConfirmation.latestAvailabilityResult().requestId());
        assertThat(harness.availabilityCallCounts().get(3)).isEqualTo(harness.availabilityCallCounts().get(2) + 1);
        assertThat(harness.providerDiscoveryCallCounts().get(3)).as(harness.providerDiscoveryCallTranscripts().toString())
                .isEqualTo(harness.providerDiscoveryCallCounts().get(2));
        verify(harness.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
        verifyNoSemanticAi(harness);
    }

    @Test
    void changingDateDuringConfirmationDropsOldBindingAndLoadsOnlyNewDateAvailability() {
        LocalDate newDate = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(3);
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "10 works", newDate.toString()));
        SessionProjection before = harness.sessionSnapshots().get(2);
        SessionProjection after = harness.sessionSnapshots().get(3);
        assertThat(after.activeDraft().selectedProvider()).isEqualTo(before.activeDraft().selectedProvider());
        assertThat(after.activeDraft().preferredDate()).isEqualTo(newDate);
        assertThat(after.activeDraft().selectedSlotReference()).isNull();
        assertThat(after.pendingConfirmation()).isNull();
        assertThat(after.latestAvailabilityResult().date()).isEqualTo(newDate);
        assertThat(after.latestAvailabilityResult().requestId()).isNotEqualTo(before.latestAvailabilityResult().requestId());
        assertThat(harness.availabilityCallCounts().get(3)).isGreaterThan(harness.availabilityCallCounts().get(2));
        verify(harness.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void changingDaypartDuringConfirmationKeepsProviderAndDateAndDropsSelection() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "10 works", "शाम के बजाय सुबह"),
                false, true, List.of(LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(17, 0)));
        SessionProjection before = harness.sessionSnapshots().get(2);
        SessionProjection after = harness.sessionSnapshots().get(3);
        assertThat(after.activeDraft().selectedProvider()).isEqualTo(before.activeDraft().selectedProvider());
        assertThat(after.activeDraft().preferredDate()).isEqualTo(before.activeDraft().preferredDate());
        assertThat(after.activeDraft().preferredTimeWindow()).isEqualToIgnoringCase("morning");
        assertThat(after.activeDraft().selectedSlotReference()).isNull();
        assertThat(after.pendingConfirmation()).isNull();
        assertThat(after.latestAvailabilityResult().requestId()).isNotEqualTo(before.latestAvailabilityResult().requestId());
        assertThat(harness.availabilityCallCounts().get(3)).isGreaterThan(harness.availabilityCallCounts().get(2));
        verify(harness.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void repeatDuringConfirmationReturnsSameSummaryAndBindingWithoutToolsOrAi() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "10 works", "फिर से बताओ"));
        SessionProjection before = harness.sessionSnapshots().get(2);
        SessionProjection after = harness.sessionSnapshots().get(3);
        assertThat(harness.result().conversationTurns().getLast().aivaResponseText())
                .contains("Amit Verma", "10 baje");
        assertThat(after.activeDraft()).isEqualTo(before.activeDraft());
        assertThat(after.pendingConfirmation()).isEqualTo(before.pendingConfirmation());
        assertThat(after.latestAvailabilityResult()).isEqualTo(before.latestAvailabilityResult());
        assertThat(harness.availabilityCallCounts().get(3)).as(harness.availabilityCallTranscripts().toString())
                .isEqualTo(harness.availabilityCallCounts().get(2));
        assertThat(harness.providerDiscoveryCallCounts().get(3)).as(harness.providerDiscoveryCallTranscripts().toString())
                .isEqualTo(harness.providerDiscoveryCallCounts().get(2));
        verify(harness.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
        verifyNoSemanticAi(harness);
    }

    @Test
    void unavailableCanonicalSlotAtFinalYesUsesStaleRecoveryWithoutBookingRetry() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "10 works", "हाँ"),
                false, true, null, List.of(), true, null);
        SessionProjection prepared = harness.sessionSnapshots().get(2);
        SessionProjection recovered = harness.sessionSnapshots().get(3);
        assertThat(harness.result().conversationTurns().getLast().toolResultSummary()).isEqualTo("STALE_SLOT");
        assertThat(harness.result().conversationTurns().getLast().aivaResponseText())
                .contains("slot ab available nahi hai");
        assertThat(recovered.activeDraft().selectedProvider()).isEqualTo(prepared.activeDraft().selectedProvider());
        assertThat(recovered.activeDraft().preferredDate()).isEqualTo(prepared.activeDraft().preferredDate());
        assertThat(recovered.activeDraft().selectedSlotReference()).isNull();
        assertThat(recovered.pendingConfirmation()).isNull();
        assertThat(recovered.latestAvailabilityResult().requestId())
                .isNotEqualTo(prepared.latestAvailabilityResult().requestId());
        assertThat(harness.availabilityCallCounts().get(3)).isGreaterThan(harness.availabilityCallCounts().get(2));
        verify(harness.appointments(), never()).createScheduled(any(), any(), any(), anyBoolean(), any(), any());
        verify(harness.appointments(), times(1)).listSlots(eq(TENANT), eq(DOCTOR), any(LocalDate.class), any());
    }

    @Test
    void genericExecutionFailureIsNotTreatedAsStaleAndDoesNotReloadAvailability() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "10 works", "हाँ"),
                false, true, null, List.of(), false, new IllegalStateException("execution unavailable"));
        SessionProjection prepared = harness.sessionSnapshots().get(2);
        SessionProjection failed = harness.sessionSnapshots().get(3);
        assertThat(harness.result().conversationTurns().getLast().toolResultSummary()).isEqualTo("BOOKING_FAILED");
        assertThat(failed.activeDraft().selectedSlotReference()).isEqualTo(prepared.activeDraft().selectedSlotReference());
        assertThat(failed.pendingConfirmation()).isEqualTo(prepared.pendingConfirmation());
        assertThat(failed.latestAvailabilityResult()).isEqualTo(prepared.latestAvailabilityResult());
        assertThat(harness.availabilityCallCounts().get(3)).isEqualTo(harness.availabilityCallCounts().get(2));
        verify(harness.appointments(), times(1)).createScheduled(eq(TENANT), any(), eq(null), eq(false), any(), any());
        verify(harness.appointments(), times(1)).listSlots(eq(TENANT), eq(DOCTOR), any(LocalDate.class), any());
    }

    @Test
    void completedBookingRepeatStatusAndDuplicateYesNeverCreateAnotherAppointment() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "10 works", "हाँ",
                "repeat", "booking ho gayi?", "हाँ"));
        SessionProjection completed = harness.sessionSnapshots().get(3);
        assertThat(completed.completedBookingOutcome()).isNotNull();
        assertThat(completed.completedBookingOutcome().status()).isEqualTo("CONFIRMED");
        assertThat(harness.sessionSnapshots().get(4).completedBookingOutcome()).isEqualTo(completed.completedBookingOutcome());
        assertThat(harness.sessionSnapshots().get(5).completedBookingOutcome()).isEqualTo(completed.completedBookingOutcome());
        assertThat(harness.sessionSnapshots().get(6).pendingConfirmation()).isNull();
        assertThat(harness.result().conversationTurns().get(4).aivaResponseText()).contains("Amit Verma", "10 baje");
        assertThat(harness.result().conversationTurns().get(5).toolResultSummary()).isEqualTo("COMPLETED_BOOKING_REPEAT");
        assertThat(harness.result().conversationTurns().get(6).toolResultSummary()).isEqualTo("COMPLETED_BOOKING_REPEAT");
        assertThat(harness.availabilityCallCounts().get(6)).isEqualTo(harness.availabilityCallCounts().get(3));
        verify(harness.appointments(), times(1)).createScheduled(eq(TENANT), any(), eq(null), eq(false), any(), any());
        verifyNoSemanticAi(harness);
    }

    @Test
    void postCommitSessionFinalizationFailureReconcilesRepeatFromExistingReceipt() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "10 works", "हाँ", "repeat"),
                false, true, null, List.of(), false, null, true);
        SessionProjection afterFailedFinalization = harness.sessionSnapshots().get(3);
        SessionProjection reconciled = harness.sessionSnapshots().get(4);
        assertThat(afterFailedFinalization.pendingConfirmation()).isNotNull();
        assertThat(afterFailedFinalization.completedBookingOutcome()).isNull();
        assertThat(reconciled.pendingConfirmation()).isNull();
        assertThat(reconciled.completedBookingOutcome()).isNotNull();
        assertThat(reconciled.completedBookingOutcome().executionReference())
                .isEqualTo(afterFailedFinalization.pendingConfirmation().idempotencyKey());
        assertThat(harness.result().conversationTurns().getLast().toolResultSummary()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(harness.result().conversationTurns().getLast().aivaResponseText()).contains("book ho gaya");
        verify(harness.appointments(), times(1)).createScheduled(eq(TENANT), any(), eq(null), eq(false), any(), any());
        verify(harness.idempotency(), times(1)).storeResponse(any(), anyString(), anyString(), anyString());
        verifyNoSemanticAi(harness);
    }

    @Test
    void duplicateYesAfterPostCommitSessionFailureUsesSameReceiptAndDoesNotMutateAgain() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "10 works", "हाँ", "हाँ"),
                false, true, null, List.of(), false, null, true);
        assertThat(harness.sessionSnapshots().get(4).completedBookingOutcome()).isNotNull();
        assertThat(harness.sessionSnapshots().get(4).pendingConfirmation()).isNull();
        assertThat(harness.result().conversationTurns().getLast().toolResultSummary()).isEqualTo("BOOKING_CONFIRMED");
        verify(harness.appointments(), times(1)).createScheduled(eq(TENANT), any(), eq(null), eq(false), any(), any());
        verify(harness.idempotency(), times(1)).storeResponse(any(), anyString(), anyString(), anyString());
    }

    @Test
    void explicitAdditionalBookingStartsFreshDraftWithoutReplacingCompletedOutcome() {
        InboundHarness harness = runInboundSequence(List.of(
                "मुझे डॉक्टर अमित वर्मा का appointment चाहिए", "कल", "10 works", "हाँ",
                "ek aur appointment book karna hai"));
        SessionProjection completed = harness.sessionSnapshots().get(3);
        SessionProjection next = harness.sessionSnapshots().get(4);
        assertThat(completed.completedBookingOutcome()).isNotNull();
        assertThat(next.completedBookingOutcome()).isEqualTo(completed.completedBookingOutcome());
        assertThat(next.activeDraft()).isNotNull();
        assertThat(next.activeDraft().draftId()).isNotEqualTo(harness.sessionSnapshots().get(2).activeDraft().draftId());
        assertThat(next.pendingConfirmation()).isNull();
        verify(harness.appointments(), times(1)).createScheduled(eq(TENANT), any(), eq(null), eq(false), any(), any());
    }

    private void verifyNoSemanticAi(InboundHarness harness) {
        verifyNoInteractions(harness.orchestration());
    }

    private InboundVoiceResult runInbound(String phrase) {
        return runInboundSequence(List.of(phrase)).result();
    }

    private InboundHarness runInboundSequence(List<String> phrases) {
        return runInboundSequence(phrases, false);
    }

    private InboundHarness runInboundSequence(List<String> phrases, boolean expandedAvailability) {
        return runInboundSequence(phrases, expandedAvailability, true);
    }

    private InboundHarness runInboundSequence(List<String> phrases, boolean expandedAvailability,
                                               boolean publicDoctorCanBookOnline) {
        return runInboundSequence(phrases, expandedAvailability, publicDoctorCanBookOnline, null);
    }

    private InboundHarness runInboundSequence(List<String> phrases, boolean expandedAvailability,
                                               boolean publicDoctorCanBookOnline, List<LocalTime> offeredTimes) {
        return runInboundSequence(phrases, expandedAvailability, publicDoctorCanBookOnline, offeredTimes, List.of());
    }

    private InboundHarness runInboundSequence(List<String> phrases, boolean expandedAvailability,
                                               boolean publicDoctorCanBookOnline, List<LocalTime> offeredTimes,
                                               List<PatientPortalCareAiDoctorOption> additionalDoctors) {
        return runInboundSequence(phrases, expandedAvailability, publicDoctorCanBookOnline,
                offeredTimes, additionalDoctors, false, null);
    }

    private InboundHarness runInboundSequence(List<String> phrases, boolean expandedAvailability,
                                               boolean publicDoctorCanBookOnline, List<LocalTime> offeredTimes,
                                               List<PatientPortalCareAiDoctorOption> additionalDoctors,
                                               boolean slotUnavailableAtConfirmation,
                                               RuntimeException bookingFailure) {
        return runInboundSequence(phrases, expandedAvailability, publicDoctorCanBookOnline, offeredTimes,
                additionalDoctors, slotUnavailableAtConfirmation, bookingFailure, false);
    }

    private InboundHarness runInboundSequence(List<String> phrases, boolean expandedAvailability,
                                               boolean publicDoctorCanBookOnline, List<LocalTime> offeredTimes,
                                               List<PatientPortalCareAiDoctorOption> additionalDoctors,
                                               boolean slotUnavailableAtConfirmation,
                                               RuntimeException bookingFailure,
                                               boolean failAfterCompletedOutcomeWrite) {
        return runInboundSequence(phrases, expandedAvailability, publicDoctorCanBookOnline, offeredTimes,
                additionalDoctors, slotUnavailableAtConfirmation, bookingFailure, failAfterCompletedOutcomeWrite,
                TENANT, PATIENT, CLINIC, DOCTOR, null, null);
    }

    private InboundHarness runInboundSequence(List<String> phrases, boolean expandedAvailability,
                                               boolean publicDoctorCanBookOnline, List<LocalTime> offeredTimes,
                                               List<PatientPortalCareAiDoctorOption> additionalDoctors,
                                               boolean slotUnavailableAtConfirmation,
                                               RuntimeException bookingFailure,
                                               boolean failAfterCompletedOutcomeWrite,
                                               UUID tenantId, UUID patientId, UUID clinicId, UUID doctorId,
                                               String fixedCallId, AivaV2SessionStore sharedSessions) {
        UUID callInteraction = UUID.randomUUID();
        java.util.concurrent.atomic.AtomicBoolean canonicalSlotUnavailable =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicReference<String> currentTranscript =
                new java.util.concurrent.atomic.AtomicReference<>();
        List<String> availabilityCallTranscripts = new java.util.ArrayList<>();
        List<String> providerDiscoveryCallTranscripts = new java.util.ArrayList<>();
        var stt = mock(VoiceOrchestratorService.class);
        org.mockito.stubbing.OngoingStubbing<VoiceTranscriptionResult> transcription =
                when(stt.transcribeBufferedAudio(any(), any(byte[].class), anyString(), anyString(), anyString()));
        for (String phrase : phrases) {
            transcription = transcription.thenAnswer(invocation -> {
                currentTranscript.set(phrase);
                if (slotUnavailableAtConfirmation && "हाँ".equals(phrase)) canonicalSlotUnavailable.set(true);
                return new VoiceTranscriptionResult(phrase, "sarvam", null);
            });
        }

        PatientService patients = mock(PatientService.class);
        when(patients.findActiveByTenantAndMobile(tenantId, "0000000000"))
                .thenReturn(List.of(patient(tenantId, patientId)));
        when(patients.findById(tenantId, patientId)).thenReturn(Optional.of(patient(tenantId, patientId)));
        CommunicationChannelResolver routes = mock(CommunicationChannelResolver.class);
        when(routes.resolveInboundVoiceByDidWithFallback("+919999000001")).thenReturn(java.util.Optional.of(
                new Resolution(UUID.randomUUID(), tenantId, clinicId, null, "DOTVOICE",
                        "+919999000001", null, null)));

        PatientPortalService portal = mock(PatientPortalService.class);
        List<PatientPortalCareAiDoctorOption> doctors = new java.util.ArrayList<>(List.of(
                new PatientPortalCareAiDoctorOption(doctorId.toString(), "Dr Amit Verma", "General Medicine",
                        UUID.randomUUID(), clinicId, tenantId, "clinic", "Clinic")));
        doctors.addAll(additionalDoctors);
        when(portal.careAiDoctorsForTenant(tenantId)).thenAnswer(invocation -> {
            providerDiscoveryCallTranscripts.add(currentTranscript.get());
            return List.copyOf(doctors);
        });
        when(portal.careAiDoctorsAcrossAuthorizedClinics()).thenAnswer(invocation -> {
            providerDiscoveryCallTranscripts.add(currentTranscript.get());
            return List.copyOf(doctors);
        });
        PublicCatalogFacade publicCatalog = mock(PublicCatalogFacade.class);
        AivaV2BookingTools bookingTools = new AivaV2BookingTools(portal, publicCatalog,
                Clock.systemUTC());
        when(portal.doctorSlotsForTenant(eq(tenantId), anyString(), nullable(String.class), any(LocalDate.class)))
                .thenAnswer(invocation -> {
                    availabilityCallTranscripts.add(currentTranscript.get());
                    LocalDate requestedDate = invocation.getArgument(3);
                    String resolvedDoctorId = invocation.getArgument(1);
                    List<LocalTime> times = offeredTimes != null ? offeredTimes : expandedAvailability
                            ? List.of(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(14, 0),
                            LocalTime.of(16, 30), LocalTime.of(17, 0), LocalTime.of(18, 0))
                            : List.of(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0));
                    return times.stream().map(time -> new PatientPortalDoctorSlotResponse(
                            slotReferenceForDoctor(resolvedDoctorId, time, requestedDate), requestedDate, time, time.plusMinutes(30),
                            "AVAILABLE", true)).toList();
                });

        AivaV2SessionStore sessions = sharedSessions != null ? sharedSessions
                : failAfterCompletedOutcomeWrite ? new FailCompletedOutcomeSessionStore()
                : new AivaV2SessionStore(Clock.systemUTC());
        AiOrchestrationService orchestration = mock(AiOrchestrationService.class);
        AivaV2ConversationDecisionGateway decisions = org.mockito.Mockito.spy(new AiAivaV2ConversationDecisionGateway(
                orchestration, new ObjectMapper(), mock(ClinicTimeZoneResolver.class),
                Clock.systemUTC(),
                LanguageAdapterRegistry.defaults(), bookingTools));
        List<NormalizedUserTurn> gatewayTurns = new java.util.ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            gatewayTurns.add(invocation.getArgument(0));
            return invocation.callRealMethod();
        }).when(decisions).decide(any(NormalizedUserTurn.class), any(SessionProjection.class), any(AivaExecutionContext.class));

        AivaV2TransactionalKernel kernel = org.mockito.Mockito.spy(new AivaV2TransactionalKernel(bookingTools,
                Clock.systemUTC()));
        List<ConversationDecision> kernelDecisions = new java.util.ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            kernelDecisions.add(invocation.getArgument(1));
            return invocation.callRealMethod();
        }).when(kernel).prepareOnly(any(), any(), any(), any(), anyString(), anyBoolean());
        AivaTransactionCapabilityGate gate = new AivaTransactionCapabilityGate();
        AivaV2TransactionalExecutionBoundary boundary = new AivaV2TransactionalExecutionBoundary(kernel, gate);
        AppointmentService appointments = mock(AppointmentService.class);
        when(appointments.listSlots(eq(tenantId), eq(doctorId), any(LocalDate.class), any()))
                .thenAnswer(invocation -> {
                    if (canonicalSlotUnavailable.get()) return List.of();
                    LocalDate date = invocation.getArgument(2);
                    List<LocalTime> times = offeredTimes != null ? offeredTimes
                            : List.of(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0));
                    return times.stream().map(time -> availabilitySlot(date, time)).toList();
                });
        when(appointments.createScheduled(eq(tenantId), any(), eq(null), eq(false), any(), any()))
                .thenAnswer(invocation -> {
                    if (bookingFailure != null) throw bookingFailure;
                    var command = invocation.<com.deepthoughtnet.clinic.appointment.service.model.AppointmentUpsertCommand>getArgument(1);
                    return new AppointmentRecord(UUID.randomUUID(), tenantId, patientId, "SYNTHETIC", "Synthetic Caller",
                            null, doctorId, "Dr Amit Verma", null, command.appointmentDate(), command.appointmentTime(),
                            null, "AIVA-TEST-APT", command.reason(), AppointmentType.SCHEDULED,
                            AppointmentPriority.NORMAL, AppointmentStatus.BOOKED,
                            java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now());
                });
        IdempotencyService idempotency = mock(IdempotencyService.class);
        java.util.concurrent.atomic.AtomicReference<String> receiptKey = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> receiptFingerprint = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> receiptJson = new java.util.concurrent.atomic.AtomicReference<>();
        when(idempotency.findCachedResponse(any(), anyString(), anyString())).thenAnswer(invocation ->
                java.util.Objects.equals(receiptKey.get(), invocation.getArgument(1))
                        && java.util.Objects.equals(receiptFingerprint.get(), invocation.getArgument(2))
                        && receiptJson.get() != null ? Optional.of(receiptJson.get()) : Optional.empty());
        org.mockito.Mockito.doAnswer(invocation -> {
            receiptKey.set(invocation.getArgument(1));
            receiptFingerprint.set(invocation.getArgument(2));
            receiptJson.set(invocation.getArgument(3));
            return null;
        }).when(idempotency).storeResponse(any(), anyString(), anyString(), anyString());
        AppointmentBookingApplicationService applicationService = new AppointmentBookingApplicationService(
                appointments, patients, null, idempotency, new ObjectMapper().findAndRegisterModules());
        VoiceBookingExecutionService bookingExecution = new VoiceBookingExecutionService(applicationService);
        AivaV2VoiceConversationAdapter voiceAdapter = new AivaV2VoiceConversationAdapter(sessions, decisions,
                LanguageAdapterRegistry.defaults(), gate, boundary, bookingExecution);

        VoiceOperatorProperties operator = new VoiceOperatorProperties();
        operator.setBookingEnabled(true);
        operator.setOtpRequired(true);
        operator.setGuestBookingEnabled(true);
        VoiceTransactionPolicyResolver policies = new VoiceTransactionPolicyResolver(
                new VoiceTransactionAuthProperties(), operator);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("bookingExecutionContextFactory", new BookingExecutionContextFactory());
        HealthcareAiReadOnlyToolGateway readOnlyTools = mock(HealthcareAiReadOnlyToolGateway.class);
        List<PublicDoctorSummaryResponse> publicDoctors = new java.util.ArrayList<>(List.of(
                new PublicDoctorSummaryResponse(doctorId.toString(), "amit-verma", "/doctors/amit-verma",
                        "Amit Verma", null, null, "General Medicine", null, null, List.of(), null, null,
                        publicDoctorCanBookOnline ? "ONLINE" : "INQUIRY", null, null, "Clinic", "clinic",
                        true, null, null, doctorId.toString(), publicDoctorCanBookOnline)));
        for (PatientPortalCareAiDoctorOption doctor : additionalDoctors) {
            String slug = doctor.doctorName().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
            publicDoctors.add(new PublicDoctorSummaryResponse(doctor.publicDoctorId(), slug,
                    "/doctors/" + slug, doctor.doctorName(), null, null, doctor.specialization(),
                    null, null, List.of(), null, null, "ONLINE", null, null, doctor.clinicName(),
                    doctor.clinicSlug(), true, null, null, doctor.publicDoctorId(), true));
        }
        when(readOnlyTools.execute(eq("FIND_DOCTOR"), any(), any())).thenAnswer(invocation -> {
            HealthcareAiReadOnlyToolGateway.ToolInput input = invocation.getArgument(2);
            String query = input == null || input.query() == null ? "" : input.query()
                    .toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
            String requestedSpecialty = input == null || input.speciality() == null ? ""
                    : input.speciality().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
            List<PublicDoctorSummaryResponse> matches = publicDoctors.stream()
                    .filter(doctor -> query.isBlank() || doctor.doctorDisplayName().toLowerCase(java.util.Locale.ROOT)
                            .replaceAll("[^a-z0-9]+", " ").contains(query))
                    .filter(doctor -> requestedSpecialty.isBlank() || doctor.speciality() != null
                            && doctor.speciality().toLowerCase(java.util.Locale.ROOT)
                            .replaceAll("[^a-z0-9]+", " ").contains(requestedSpecialty))
                    .toList();
            return new HealthcareAiReadOnlyToolGateway.ToolResult("FIND_DOCTOR", true, true,
                    matches, matches.size(), matches.size(), "sourceCount=" + matches.size());
        });
        VoiceTestProperties voiceProperties = new VoiceTestProperties();
        voiceProperties.setAivaV2Enabled(false);
        PatientPortalOtpService otpService = mock(PatientPortalOtpService.class);
        VoiceReadOnlyAivaService aiva = new VoiceReadOnlyAivaService(
                readOnlyTools, voiceAdapter, voiceProperties,
                mock(ClinicTimeZoneResolver.class), otpService, policies, patients,
                beanFactory.getBeanProvider(BookingExecutionContextFactory.class));

        ReminderVoiceRenderer renderer = mock(ReminderVoiceRenderer.class);
        when(renderer.render(anyString(), anyString())).thenReturn(new ReminderVoiceRenderer.RenderedReminder(
                new SpeechSynthesisResult(testWav(), "audio/wav", 1L), null, "test"));
        DotVoiceMediaStreamClient stream = mock(DotVoiceMediaStreamClient.class);
        when(stream.startPlayback(anyString(), any())).thenReturn(true);
        CommunicationInteractionService interactions = mock(CommunicationInteractionService.class);
        when(interactions.startInbound(anyString(), nullable(UUID.class), nullable(UUID.class), nullable(UUID.class),
                nullable(UUID.class), nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class), nullable(UUID.class), nullable(
                        com.deepthoughtnet.clinic.communication.model.ConversationPartyType.class),
                org.mockito.ArgumentMatchers.anyInt(), nullable(String.class), nullable(java.time.OffsetDateTime.class)))
                .thenReturn(callInteraction);
        CommunicationTestService inbound = new CommunicationTestService(
                mock(com.deepthoughtnet.clinic.api.carepilot.CarePilotMessagingStatusService.class),
                mock(MessageOrchestratorService.class), List.of(), List.of(), null, renderer, stream, stt,
                new VoiceConversationContextRegistry(), new VoiceResponseInterpreter(), routes, patients,
                interactions, aiva);

        inbound.registerInboundCall(fixedCallId == null ? "voice-routing-test-" + UUID.randomUUID() : fixedCallId,
                "0000000000", "+919999000001");
        var registered = inbound.latestInboundVoiceResult();
        List<SessionProjection> snapshots = new java.util.ArrayList<>();
        List<Long> availabilityCallCounts = new java.util.ArrayList<>();
        List<Long> providerDiscoveryCallCounts = new java.util.ArrayList<>();
        for (int i = 0; i < phrases.size(); i++) {
            inbound.processInboundFirstUtterance(registered.callId(), testWav());
            sessions.find("VOICE|" + tenantId + "|" + registered.callId()).ifPresent(snapshots::add);
            availabilityCallCounts.add(org.mockito.Mockito.mockingDetails(portal).getInvocations().stream()
                    .filter(invocation -> invocation.getMethod().getName().equals("doctorSlotsForTenant")).count());
            providerDiscoveryCallCounts.add(org.mockito.Mockito.mockingDetails(portal).getInvocations().stream()
                    .filter(invocation -> invocation.getMethod().getName().equals("careAiDoctorsForTenant")
                            || invocation.getMethod().getName().equals("careAiDoctorsAcrossAuthorizedClinics")).count());
        }
        return new InboundHarness(inbound.inboundVoiceResult(registered.callId()), sessions, orchestration, portal,
                bookingExecution, appointments, idempotency, List.copyOf(snapshots), List.copyOf(kernelDecisions),
                List.copyOf(availabilityCallCounts), List.copyOf(providerDiscoveryCallCounts),
                List.copyOf(availabilityCallTranscripts),
                List.copyOf(providerDiscoveryCallTranscripts),
                readOnlyTools, List.copyOf(gatewayTurns), renderer, otpService);
    }

    private record InboundHarness(InboundVoiceResult result, AivaV2SessionStore sessions,
                                  AiOrchestrationService orchestration,
                                  PatientPortalService portal,
                                  VoiceBookingExecutionService bookingExecution,
                                  AppointmentService appointments, IdempotencyService idempotency,
                                  List<SessionProjection> sessionSnapshots, List<ConversationDecision> kernelDecisions,
                                  List<Long> availabilityCallCounts, List<Long> providerDiscoveryCallCounts,
                                  List<String> availabilityCallTranscripts,
                                  List<String> providerDiscoveryCallTranscripts,
                                  HealthcareAiReadOnlyToolGateway readOnlyTools,
                                  List<NormalizedUserTurn> gatewayTurns,
                                  ReminderVoiceRenderer renderer,
                                  PatientPortalOtpService otpService) { }

    private static final class FailCompletedOutcomeSessionStore extends AivaV2SessionStore {
        private final java.util.concurrent.atomic.AtomicBoolean failOnce =
                new java.util.concurrent.atomic.AtomicBoolean(true);

        private FailCompletedOutcomeSessionStore() { super(Clock.systemUTC()); }

        @Override
        SessionProjection update(String key,
                java.util.function.Function<SessionProjection, SessionProjection> updater) {
            SessionProjection current = find(key).orElse(null);
            SessionProjection updated = updater.apply(current);
            if (updated != null && updated.completedBookingOutcome() != null && failOnce.compareAndSet(true, false)) {
                throw new IllegalStateException("injected post-commit Voice session finalization failure");
            }
            return super.update(key, ignored -> updated);
        }
    }

    private record ExpectedDateTurn(String utterance, LocalDate expectedDate) { }

    private PatientRecord patient() {
        return patient(TENANT, PATIENT);
    }

    private PatientRecord patient(UUID tenantId, UUID patientId) {
        return new PatientRecord(patientId, tenantId, "SYNTHETIC", "Synthetic", "Caller", null, null, null,
                "0000000000", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, true, null, null);
    }

    private String slotReference(LocalTime time, LocalDate date) {
        return slotReferenceForDoctor(DOCTOR.toString(), time, date);
    }

    private String slotReferenceForDoctor(String doctorId, LocalTime time, LocalDate date) {
        String source = String.join("|", "care-slot", TENANT.toString(), doctorId, date.toString(), time.toString());
        return "care-slot-" + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    private DoctorAvailabilitySlotRecord availabilitySlot(LocalDate date, LocalTime time) {
        return new DoctorAvailabilitySlotRecord(DOCTOR, "Dr Amit Verma", date, time, time.plusMinutes(30),
                null, 0, 1, true, null, false, false, true, null, null, null, null, null, null, null, null);
    }

    private byte[] testWav() {
        byte[] wav = new byte[44 + 320];
        ByteBuffer bytes = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(wav.length - 8)
                .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(8_000).putInt(16_000).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(320);
        return wav;
    }
}
