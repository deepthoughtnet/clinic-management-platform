package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.LanguageAdapterRegistry;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2VoiceToolExecutor.QueryType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AivaV2VoiceConversationAdapterTest {
    private static final UUID TENANT = UUID.randomUUID();

    @Test
    void usesV2SessionForReadOnlyVoiceTurnsAndCarriesProviderContextForward() {
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.fixed(
                java.time.Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        AivaV2VoiceConversationAdapter adapter = new AivaV2VoiceConversationAdapter(
                store, mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        AivaExecutionContext context = context("call-a");
        var calls = new java.util.ArrayList<String>();

        var first = adapter.continueTurn(context, "मुझे डॉक्टर चाहिए", (tool, execution, input) -> {
            calls.add(tool + ":" + execution.tenantId());
            return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1,
                    "sourceCount=1", List.of(new AivaV2VoiceToolExecutor.ProviderOption(
                            "doctor-1", "Ravi Mehta", "Pediatrics", null, TENANT.toString(), null, null)));
        });
        var second = adapter.continueTurn(context, "कल स्लॉट बताइए", (tool, execution, input) -> {
            calls.add(tool + ":" + input.doctorId());
            return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 2, 2,
                    "sourceCount=2", List.of());
        });

        assertThat(first.tool()).isEqualTo("FIND_DOCTOR");
        assertThat(second.tool()).isEqualTo("FIND_SLOTS");
        assertThat(calls).containsExactly("FIND_DOCTOR:" + TENANT, "FIND_SLOTS:doctor-1");
        var persisted = store.find("VOICE|" + TENANT + "|call-a").orElseThrow();
        assertThat(persisted.activeVoiceDoctor()).isNotNull();
        assertThat(persisted.activeVoiceDoctor().displayName()).isEqualTo("Ravi Mehta");
        assertThat(persisted.latestAvailabilityResult()).isNull();
    }

    @Test
    void activeDoctorSurvivesClarificationAndBlockedMutationUntilCallCloses() {
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.systemUTC());
        AivaV2VoiceConversationAdapter adapter = new AivaV2VoiceConversationAdapter(
                store, mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        AivaExecutionContext context = context("active-doctor-lifecycle");

        adapter.continueTurn(context, "बच्चों का डॉक्टर अवेलेबल है?", (tool, execution, input) ->
                new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 2, "one result", List.of(
                        new AivaV2VoiceToolExecutor.ProviderOption("doctor-ravi", "Ravi Mehta", "Pediatrics",
                                null, TENANT.toString(), null, null))));
        var datePrompt = adapter.continueTurn(context, "Ravi ke slots dikhao",
                (tool, execution, input) -> { throw new AssertionError("missing date should clarify"); });
        assertThat(datePrompt.intent()).isEqualTo("CLARIFY_DATE");
        assertThat(store.find("VOICE|" + TENANT + "|active-doctor-lifecycle").orElseThrow()
                .activeVoiceDoctor().displayName()).isEqualTo("Ravi Mehta");

        var blocked = adapter.continueTurn(context, "unka appointment chahiye",
                (tool, execution, input) -> { throw new AssertionError("mutation must remain blocked"); });
        assertThat(blocked.intent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(blocked.policyDecision()).isEqualTo("BLOCKED_READ_ONLY");
        assertThat(store.find("VOICE|" + TENANT + "|active-doctor-lifecycle").orElseThrow()
                .activeVoiceDoctor().displayName()).isEqualTo("Ravi Mehta");

        adapter.continueTurn(context, "Thank you", (tool, execution, input) -> {
            throw new AssertionError("conversation close must not execute a tool");
        });
        assertThat(store.find("VOICE|" + TENANT + "|active-doctor-lifecycle").orElseThrow()
                .activeVoiceDoctor()).isNull();
    }

    @Test
    void plainBlockedBookingUsesAndPreservesActiveDoctorForNextAvailabilityTurn() {
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.systemUTC());
        AivaV2VoiceConversationAdapter adapter = new AivaV2VoiceConversationAdapter(
                store, mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        AivaExecutionContext context = context("active-doctor-plain-booking");

        var discovery = adapter.continueTurn(context, "बच्चों का डॉक्टर अवेलेबल है।",
                (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
        var booking = adapter.continueTurn(context, "अपॉइंटमेंट बुक करना है.",
                (tool, execution, input) -> { throw new AssertionError("blocked booking must not execute"); });
        var slotInput = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
        var slots = adapter.continueTurn(context, "कल के slots दिखाओ", (tool, execution, input) -> {
            slotInput.set(input);
            return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 0, 0,
                    "no slots", List.of());
        });

        assertThat(discovery.tool()).isEqualTo("FIND_DOCTOR");
        assertThat(booking.intent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(booking.policyDecision()).isEqualTo("BLOCKED_READ_ONLY");
        assertThat(booking.toolExecuted()).isFalse();
        assertThat(booking.response()).contains("Ravi Mehta", "अपॉइंटमेंट बुक करना")
                .doesNotContain("किस डॉक्टर");
        assertThat(store.find("VOICE|" + TENANT + "|active-doctor-plain-booking").orElseThrow()
                .activeVoiceDoctor().displayName()).isEqualTo("Ravi Mehta");
        assertThat(slots.tool()).isEqualTo("FIND_SLOTS");
        assertThat(slotInput.get().doctorId()).isEqualTo("doctor-ravi");
        assertThat(slotInput.get().date()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1));
        assertThat(store.find("VOICE|" + TENANT + "|active-doctor-plain-booking").orElseThrow()
                .activeVoiceDoctor().displayName()).isEqualTo("Ravi Mehta");
    }

    @Test
    void streamEndRemovesTheCallScopedActiveDoctorSession() {
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.systemUTC());
        AivaV2VoiceConversationAdapter adapter = new AivaV2VoiceConversationAdapter(
                store, mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        AivaExecutionContext context = context("active-doctor-stream-end");
        adapter.continueTurn(context, "बच्चों का डॉक्टर अवेलेबल है?", (tool, execution, input) ->
                new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 2, "one result", List.of(
                        new AivaV2VoiceToolExecutor.ProviderOption("doctor-ravi", "Ravi Mehta", "Pediatrics",
                                null, TENANT.toString(), null, null))));

        adapter.endSession(context);

        assertThat(store.find("VOICE|" + TENANT + "|active-doctor-stream-end")).isEmpty();
    }

    @Test
    void blocksTransactionalOperationsBeforeTheVoiceExecutor() {
        AivaV2VoiceConversationAdapter adapter = new AivaV2VoiceConversationAdapter(
                new AivaV2SessionStore(Clock.systemUTC()), mock(AivaV2ConversationDecisionGateway.class),
                LanguageAdapterRegistry.defaults());

        var result = adapter.continueTurn(context("call-b"), "मेरी appointment book कर दीजिए", (tool, context, input) -> {
            throw new AssertionError("voice transaction must not execute");
        });

        assertThat(result.mutationBlocked()).isTrue();
        assertThat(result.intent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(result.tool()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(result.response()).contains("बुकिंग");
    }

    @Test
    void carriesHindiPediatricsAliasIntoTheV2ReadOnlyToolInput() {
        AivaV2VoiceConversationAdapter adapter = new AivaV2VoiceConversationAdapter(
                new AivaV2SessionStore(Clock.systemUTC()), mock(AivaV2ConversationDecisionGateway.class),
                LanguageAdapterRegistry.defaults());
        var speciality = new java.util.concurrent.atomic.AtomicReference<String>();
        var query = new java.util.concurrent.atomic.AtomicReference<String>("sentinel");

        var result = adapter.continueTurn(context("call-pediatrics"), "बच्चों का डॉक्टर।", (tool, execution, input) -> {
            speciality.set(input.speciality());
            query.set(input.query());
            return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 2,
                    "sourceCount=2; filteredCount=1", List.of());
        });

        assertThat(result.tool()).isEqualTo("FIND_DOCTOR");
        assertThat(speciality).hasValue("Pediatrics");
        assertThat(query).hasValue(null);
    }

    @Test
    void resolvesVoiceSpecialtyPhrasesBeforeSelectingFindDoctor() {
        for (String phrase : List.of(
                "कोई पेडियाट्रिशियन अवेलेबल है?",
                "पेडिट्रिशियन का चाहिए",
                "मुझे जनरल फिजिशियन चाहिए",
                "General physician, tell me."
        )) {
            var input = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
            var result = new AivaV2VoiceConversationAdapter(
                    new AivaV2SessionStore(Clock.systemUTC()), mock(AivaV2ConversationDecisionGateway.class),
                    LanguageAdapterRegistry.defaults()).continueTurn(context("specialty-" + phrase.hashCode()), phrase,
                    (tool, execution, toolInput) -> {
                        input.set(toolInput);
                        return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 2,
                                "sourceCount=2; filteredCount=1", List.of());
                    });

            assertThat(result.intent()).as("phrase=%s", phrase).isEqualTo("FIND_DOCTOR");
            assertThat(result.tool()).as("phrase=%s", phrase).isEqualTo("FIND_DOCTOR");
            assertThat(input.get().query()).as("phrase=%s", phrase).isNull();
            assertThat(input.get().speciality()).as("phrase=%s", phrase)
                    .isEqualTo(phrase.toLowerCase().contains("जनरल") || phrase.toLowerCase().contains("general")
                            ? "General Medicine" : "Pediatrics");
        }
    }

    @Test
    void carriesHindiHinglishAndEnglishDoctorContextAcrossTurns() {
        for (String firstPhrase : List.of(
                "कोई बच्चों का डॉक्टर अवेलेबल है?",
                "Kids ka doctor available hai?",
                "Is a pediatrician available?")) {
            var adapter = new AivaV2VoiceConversationAdapter(
                    new AivaV2SessionStore(Clock.systemUTC()), mock(AivaV2ConversationDecisionGateway.class),
                    LanguageAdapterRegistry.defaults());
            String callId = "language-context-" + firstPhrase.hashCode();

            var first = adapter.continueTurn(context(callId), firstPhrase,
                    (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
            var second = adapter.continueTurn(context(callId),
                    firstPhrase.startsWith("Is") ? "I want an appointment with the same doctor."
                            : firstPhrase.startsWith("Kids") ? "unka appointment chahiye" : "उनका अपॉइंटमेंट चाहिए.",
                    (tool, execution, input) -> { throw new AssertionError("booking must remain blocked"); });

            assertThat(first.tool()).isEqualTo("FIND_DOCTOR");
            assertThat(second.intent()).isEqualTo("BOOK_APPOINTMENT");
            assertThat(second.mutationBlocked()).isTrue();
        }
    }

    @Test
    void sendsOnlyExplicitDoctorNameAsDoctorQuery() {
        var adapter = new AivaV2VoiceConversationAdapter(
                new AivaV2SessionStore(Clock.systemUTC()), mock(AivaV2ConversationDecisionGateway.class),
                LanguageAdapterRegistry.defaults());
        var input = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();

        var result = adapter.continueTurn(context("explicit-doctor"), "Ravi Mehta available हैं?",
                (tool, execution, toolInput) -> {
                    input.set(toolInput);
                    return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 2,
                            "sourceCount=2; filteredCount=1", List.of());
                });

        assertThat(result.tool()).isEqualTo("FIND_DOCTOR");
        assertThat(input.get().query()).isEqualTo("Ravi Mehta");
        assertThat(input.get().speciality()).isNull();
    }

    @Test
    void extractsDevanagariDoctorNamesWithAndWithoutDoctorPrefix() {
        var adapter = new AivaV2VoiceConversationAdapter(
                new AivaV2SessionStore(Clock.systemUTC()), mock(AivaV2ConversationDecisionGateway.class),
                LanguageAdapterRegistry.defaults());

        for (String phrase : List.of("रवि मेहता अवेलेबल हैं।", "क्या डॉक्टर रवि मेहता अवेलेबल हैं?")) {
            var input = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
            var result = adapter.continueTurn(context("hindi-doctor-" + phrase.hashCode()), phrase,
                    (tool, execution, toolInput) -> {
                        input.set(toolInput);
                        return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 2,
                                "sourceCount=2; filteredCount=1", List.of());
                    });

            assertThat(result.tool()).isEqualTo("FIND_DOCTOR");
            assertThat(input.get().query()).isEqualTo("Ravi Mehta");
        }
    }

    @Test
    void genericDoctorPhrasesDoNotBecomeDoctorNameQueries() {
        var adapter = new AivaV2VoiceConversationAdapter(
                new AivaV2SessionStore(Clock.systemUTC()), mock(AivaV2ConversationDecisionGateway.class),
                LanguageAdapterRegistry.defaults());

        for (String phrase : List.of(
                "कौन सा डॉक्टर अवेलेबल है?",
                "कौन से डॉक्टर हैं?",
                "doctor available है?",
                "available doctors बताइए"
        )) {
            var input = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
            var result = adapter.continueTurn(context("generic-" + phrase.hashCode()), phrase,
                    (tool, execution, toolInput) -> {
                        input.set(toolInput);
                        return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 2, 2,
                                "sourceCount=2; filteredCount=2", List.of());
                    });

            assertThat(result.tool()).as("phrase=%s", phrase).isEqualTo("FIND_DOCTOR");
            assertThat(input.get().query()).as("phrase=%s", phrase).isNull();
            assertThat(input.get().speciality()).as("phrase=%s", phrase).isNull();
        }
    }

    @Test
    void endsConversationWithoutSelectingDoctorTool() {
        var adapter = new AivaV2VoiceConversationAdapter(
                new AivaV2SessionStore(Clock.systemUTC()), mock(AivaV2ConversationDecisionGateway.class),
                LanguageAdapterRegistry.defaults());

        var result = adapter.continueTurn(context("close"), "Thank you.",
                (tool, execution, input) -> { throw new AssertionError("closing turn must not execute a tool"); });

        assertThat(result.intent()).isEqualTo("END_CONVERSATION");
        assertThat(result.tool()).isNull();
        assertThat(result.closing()).isTrue();
    }

    @Test
    void leavesGenericDoctorDiscoveryWithoutASpecialtyFilter() {
        AivaV2VoiceConversationAdapter adapter = new AivaV2VoiceConversationAdapter(
                new AivaV2SessionStore(Clock.systemUTC()), mock(AivaV2ConversationDecisionGateway.class),
                LanguageAdapterRegistry.defaults());
        var speciality = new java.util.concurrent.atomic.AtomicReference<String>("sentinel");

        var result = adapter.continueTurn(context("call-generic-doctors"), "कौन सा डॉक्टर अवेलेबल है?", (tool, execution, input) -> {
            speciality.set(input.speciality());
            return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 2, 2,
                    "sourceCount=2; filteredCount=2", List.of());
        });

        assertThat(result.tool()).isEqualTo("FIND_DOCTOR");
        assertThat(speciality).hasValue(null);
    }

    @Test
    void resolvesHindiPronounToTheSingleDoctorFoundOnThePreviousTurnAndBlocksBooking() {
        var store = new AivaV2SessionStore(Clock.systemUTC());
        var adapter = new AivaV2VoiceConversationAdapter(
                store, mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());

        var first = adapter.continueTurn(context("cross-turn-hi"), "कोई बच्चों का डॉक्टर अवेलेबल है?",
                (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
        var second = adapter.continueTurn(context("cross-turn-hi"), "उनका अपॉइंटमेंट चाहिए.",
                (tool, execution, input) -> { throw new AssertionError("booking must remain blocked"); });

        assertThat(first.tool()).isEqualTo("FIND_DOCTOR");
        assertThat(second.intent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(second.mutationBlocked()).isTrue();
        assertThat(second.response()).contains("Ravi Mehta", "अपॉइंटमेंट बुक करना")
                .doesNotContain("किस डॉक्टर");
        assertThat(store.find("VOICE|" + TENANT + "|cross-turn-hi")).get()
                .extracting(session -> session.latestProviderResult().resolvedProvider().displayName())
                .isEqualTo("Ravi Mehta");
    }

    @Test
    void doesNotTreatAmbiguousAvailabilityAsAnImplicitSlotLookup() {
        var adapter = new AivaV2VoiceConversationAdapter(
                new AivaV2SessionStore(Clock.systemUTC()), mock(AivaV2ConversationDecisionGateway.class),
                LanguageAdapterRegistry.defaults());

        adapter.continueTurn(context("cross-turn-availability"), "क्या Ravi Mehta available हैं?",
                (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
        var second = adapter.continueTurn(context("cross-turn-availability"), "उनकी availability बताइए.",
                (tool, execution, input) -> { throw new AssertionError("ambiguous availability must clarify"); });

        assertThat(second.tool()).isNull();
        assertThat(second.intent()).isEqualTo("UNKNOWN");
        assertThat(second.response()).contains("डॉक्टर");
    }

    @Test
    void distinguishesDoctorAvailabilityFromSlotAvailabilityAfterDoctorContextExists() {
        var adapter = new AivaV2VoiceConversationAdapter(
                new AivaV2SessionStore(Clock.systemUTC()), mock(AivaV2ConversationDecisionGateway.class),
                LanguageAdapterRegistry.defaults());

        adapter.continueTurn(context("routing-matrix"), "रवि मेहता अवेलेबल हैं।",
                (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));

        for (String phrase : List.of(
                "क्या डॉक्टर रवि मेहता अवेलेबल हैं?",
                "Kids's doctor available?",
                "General physician available hai.",
                "Is a pediatrician available?",
                "Is a general physician available?",
                "Which doctors are available?")) {
            var input = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
            var result = adapter.continueTurn(context("routing-matrix"), phrase,
                    (tool, execution, toolInput) -> {
                        input.set(toolInput);
                        return twoDoctors();
                    });
            assertThat(result.tool()).as("phrase=%s", phrase).isEqualTo("FIND_DOCTOR");
            assertThat(result.intent()).as("phrase=%s", phrase).isEqualTo("FIND_DOCTOR");
            if (phrase.contains("Kids") || phrase.contains("pediatrician")) {
                assertThat(input.get().speciality()).as("phrase=%s", phrase).isEqualTo("Pediatrics");
            } else if (phrase.contains("General") || phrase.contains("general")) {
                assertThat(input.get().speciality()).as("phrase=%s", phrase).isEqualTo("General Medicine");
            } else if (phrase.contains("Which")) {
                assertThat(input.get().speciality()).as("phrase=%s", phrase).isNull();
            }
        }
    }

    @Test
    void routesExplicitSlotEvidenceToFindSlotsButGenericAvailabilityToFindDoctor() {
        var adapter = new AivaV2VoiceConversationAdapter(
                new AivaV2SessionStore(Clock.systemUTC()), mock(AivaV2ConversationDecisionGateway.class),
                LanguageAdapterRegistry.defaults());

        adapter.continueTurn(context("slot-routing"), "रवि मेहता अवेलेबल हैं।",
                (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));

        for (String phrase : List.of("रवि मेहता के स्लॉट बताइए", "Ravi Mehta ke slots dikhao",
                "Show me Ravi Mehta's available slots", "कल Ravi Mehta ke slots available hain?")) {
            var result = adapter.continueTurn(context("slot-routing"), phrase,
                    (tool, execution, input) -> new AivaV2VoiceToolExecutor.ToolResult(
                            true, true, List.of(), 0, 0, "slots", List.of()));
            if (phrase.contains("कल")) {
                assertThat(result.tool()).as("phrase=%s", phrase).isEqualTo("FIND_SLOTS");
            } else {
                assertThat(result.intent()).as("phrase=%s", phrase).isEqualTo("CLARIFY_DATE");
                assertThat(result.tool()).as("phrase=%s", phrase).isNull();
                assertThat(result.response()).contains(phrase.startsWith("रवि") ? "किस दिन"
                        : phrase.startsWith("Show") ? "Which day" : "Aap kis din");
            }
        }

        var generic = adapter.continueTurn(context("slot-routing"), "Which doctors are available?",
                (tool, execution, input) -> twoDoctors());
        assertThat(generic.tool()).isEqualTo("FIND_DOCTOR");
    }

    @Test
    void blocksAppointmentRequestWithExplicitDoctorWithoutExecutingMutation() {
        var adapter = new AivaV2VoiceConversationAdapter(
                new AivaV2SessionStore(Clock.systemUTC()), mock(AivaV2ConversationDecisionGateway.class),
                LanguageAdapterRegistry.defaults());

        var result = adapter.continueTurn(context("appointment-intent"),
                "डॉक्टर रवि मेहता का अपॉइंटमेंट चाहिए।",
                (tool, execution, input) -> { throw new AssertionError("voice booking must remain blocked"); });

        assertThat(result.intent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(result.tool()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(result.mutationBlocked()).isTrue();
    }

    @Test
    void routesRecurringDoctorSittingQuestionsToTheScheduleToolInThreeLanguages() {
        for (String[] sample : new String[][]{
                {"hi-IN", "रवि मेहता कब बैठते हैं?"},
                {"hi-IN", "Ravi Mehta ka sitting time kya hai?"},
                {"en-IN", "What are Ravi Mehta's consultation hours?"},
                {"hi-IN", "Ravi Mehta शाम में consult करते हैं?"},
                {"hi-IN", "Doctor kab available hote hain?"},
                {"hi-IN", "Doctor ka evening timing kya hai?"}}) {
            var input = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
            var result = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                    mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
            String callId = "schedule-" + sample[1].hashCode();
            if (!sample[1].contains("Ravi Mehta")) {
                result.continueTurn(context(callId, sample[0]), "Ravi Mehta available हैं?",
                        (tool, execution, toolInput) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
            }
            var turn = result.continueTurn(context(callId, sample[0]), sample[1], (tool, execution, toolInput) -> {
                        input.set(toolInput);
                        return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1,
                                "scheduleWindows=1", List.of());
                    });

            assertThat(turn.tool()).as(sample[1]).isEqualTo("FIND_DOCTOR_SCHEDULE");
            if (sample[1].contains("Ravi Mehta")) {
                assertThat(input.get().query()).as(sample[1]).contains("Ravi Mehta");
                assertThat(input.get().queryType()).as(sample[1])
                        .isEqualTo(AivaV2VoiceToolExecutor.QueryType.RECURRING_SCHEDULE);
            } else {
                assertThat(input.get().doctorId()).as(sample[1]).isEqualTo("doctor-ravi");
            }
        }
    }

    @Test
    void carriesCanonicalDaypartIntoSlotToolForHindiHinglishAndEnglish() {
        for (String[] sample : new String[][]{
                {"hi-IN", "कल शाम के slots दिखाइए"},
                {"hi-IN", "kal shaam ke slots dikhao"},
                {"en-IN", "tomorrow evening slots"}}) {
            var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                    mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
            String call = "daypart-" + sample[1].hashCode();
            adapter.continueTurn(context(call, sample[0]), "Ravi Mehta available हैं?",
                    (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
            var captured = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
            var result = adapter.continueTurn(context(call, sample[0]), sample[1], (tool, execution, input) -> {
                captured.set(input);
                return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1,
                        "slotCount=1", List.of());
            });
            assertThat(result.tool()).as(sample[1]).isEqualTo("FIND_SLOTS");
            assertThat(captured.get().daypart()).as(sample[1]).isEqualTo(NormalizedUserTurn.Daypart.EVENING);
            assertThat(captured.get().queryType()).isEqualTo(AivaV2VoiceToolExecutor.QueryType.SLOT_AVAILABILITY);
            assertThat(captured.get().doctorId()).isEqualTo("doctor-ravi");
        }
    }

    @Test
    void asksForDateInsteadOfSilentlyAssumingTomorrowAndCarriesDoctorContext() {
        var store = new AivaV2SessionStore(Clock.systemUTC());
        var adapter = new AivaV2VoiceConversationAdapter(store,
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        String call = "date-clarification";
        adapter.continueTurn(context(call), "Ravi Mehta available हैं?",
                (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));

        var missedToolExecution = new java.util.concurrent.atomic.AtomicBoolean();
        var askDate = adapter.continueTurn(context(call), "Ravi Mehta ke slots dikhao", (tool, execution, input) -> {
            missedToolExecution.set(true);
            return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 0, 0, null, List.of());
        });

        assertThat(askDate.intent()).isEqualTo("CLARIFY_DATE");
        assertThat(askDate.response()).contains("Aap kis din");
        assertThat(missedToolExecution).isFalse();
        assertThat(store.find("VOICE|" + TENANT + "|" + call).orElseThrow()
                .latestAvailabilityResult().cursor()).isEqualTo("VOICE_PENDING_DATE");

        var captured = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
        var lookup = adapter.continueTurn(context(call), "परसों सुबह", (tool, execution, input) -> {
            captured.set(input);
            return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 2, 4, "slots", List.of());
        });
        assertThat(lookup.tool()).isEqualTo("FIND_SLOTS");
        assertThat(captured.get().query()).isNull();
        assertThat(captured.get().date()).isEqualTo(LocalDate.now(context(call).timezone()).plusDays(2));
        assertThat(captured.get().daypart()).isEqualTo(NormalizedUserTurn.Daypart.MORNING);
        assertThat(captured.get().doctorId()).isEqualTo("doctor-ravi");
        assertThat(captured.get().query()).isNull();
    }

    @Test
    void carriesDateAndDaypartThroughShowMoreWithoutResettingPaging() {
        var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        String call = "carry-page";
        adapter.continueTurn(context(call), "Ravi Mehta available हैं?",
                (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
        var clarification = adapter.continueTurn(context(call), "Ravi ke slots dikhao",
                (tool, execution, input) -> { throw new AssertionError("date must be clarified first"); });
        assertThat(clarification.intent()).isEqualTo("CLARIFY_DATE");

        LocalDate tomorrow = LocalDate.now(context(call).timezone()).plusDays(1);
        var dateInput = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
        var dateResult = adapter.continueTurn(context(call), "kal", (tool, execution, input) -> {
            dateInput.set(input);
            return slotPageResult(input, List.of(NormalizedUserTurn.Daypart.MORNING,
                    NormalizedUserTurn.Daypart.EVENING), List.of(slot(tomorrow, 9), slot(tomorrow, 9),
                    slot(tomorrow, 17), slot(tomorrow, 17), slot(tomorrow, 17), slot(tomorrow, 18)),
                    "VOICE_PENDING_DAYPART", 0);
        });
        assertThat(dateInput.get().date()).isEqualTo(tomorrow);

        var eveningInput = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
        adapter.continueTurn(context(call), "शाम", (tool, execution, input) -> {
            eveningInput.set(input);
            return slotPageResult(input, List.of(NormalizedUserTurn.Daypart.EVENING),
                    List.of(slot(tomorrow, 17), slot(tomorrow, 17), slot(tomorrow, 17), slot(tomorrow, 18)),
                    "VOICE_DAYPART:EVENING", 0);
        });
        assertThat(eveningInput.get().date()).isEqualTo(tomorrow);
        assertThat(eveningInput.get().doctorId()).isEqualTo("doctor-ravi");
        assertThat(eveningInput.get().daypart()).isEqualTo(NormalizedUserTurn.Daypart.EVENING);

        var moreInput = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
        adapter.continueTurn(context(call), "और बताइए", (tool, execution, input) -> {
            moreInput.set(input);
            return slotPageResult(input, List.of(NormalizedUserTurn.Daypart.EVENING),
                    List.of(slot(tomorrow, 17), slot(tomorrow, 17), slot(tomorrow, 17), slot(tomorrow, 18)),
                    "VOICE_DAYPART:EVENING", input.pageOffset());
        });
        assertThat(moreInput.get().doctorId()).isEqualTo("doctor-ravi");
        assertThat(moreInput.get().date()).isEqualTo(tomorrow);
        assertThat(moreInput.get().daypart()).isEqualTo(NormalizedUserTurn.Daypart.EVENING);
        assertThat(moreInput.get().pageOffset()).isEqualTo(3);
    }

    @Test
    void dateOnlyContextOverridesPriorDateAndScheduleYesUsesExistingSessionCursor() {
        var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        String call = "date-override";
        adapter.continueTurn(context(call), "Ravi Mehta उपलब्ध हैं?",
                (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
        adapter.continueTurn(context(call), "Ravi के slots", (tool, execution, input) -> {
            throw new AssertionError("date clarification should happen");
        });
        var replacement = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
        adapter.continueTurn(context(call), "Actually परसों सुबह", (tool, execution, input) -> {
            replacement.set(input);
            return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 0, 0, "", List.of());
        });
        assertThat(replacement.get().date()).isEqualTo(LocalDate.now(context(call).timezone()).plusDays(2));
        assertThat(replacement.get().daypart()).isEqualTo(NormalizedUserTurn.Daypart.MORNING);

        var scheduleAdapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        String scheduleCall = "schedule-yes";
        scheduleAdapter.continueTurn(context(scheduleCall), "Ravi Mehta available हैं?",
                (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
        scheduleAdapter.continueTurn(context(scheduleCall), "क्या Ravi शाम में बैठते हैं?", (tool, execution, input) ->
                new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1, "schedule", List.of(
                        new AivaV2VoiceToolExecutor.ProviderOption("doctor-ravi", "Ravi Mehta", "Pediatrics",
                                null, TENANT.toString(), null, null)),
                        scheduleState("doctor-ravi", null, "EVENING", "VOICE_CONFIRM_SCHEDULE_DAYPART:EVENING")));
        var followUp = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
        var yes = scheduleAdapter.continueTurn(context(scheduleCall), "हाँ", (tool, execution, input) -> {
            followUp.set(input);
            return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 0, 0, "", List.of());
        });
        assertThat(yes.intent()).isEqualTo("CLARIFY_DATE");
        assertThat(followUp.get()).isNull();
        var dateFollowUp = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
        scheduleAdapter.continueTurn(context(scheduleCall), "कल", (tool, execution, input) -> {
            dateFollowUp.set(input);
            return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 0, 0, "", List.of());
        });
        assertThat(dateFollowUp.get().doctorId()).isEqualTo("doctor-ravi");
        assertThat(dateFollowUp.get().date()).isEqualTo(LocalDate.now(context(scheduleCall).timezone()).plusDays(1));
        assertThat(dateFollowUp.get().daypart()).isEqualTo(NormalizedUserTurn.Daypart.EVENING);
    }

    @Test
    void resolvesTomorrowEveningAsEffectiveDateAvailabilityAndRetainsDoctorContext() {
        var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        String call = "tomorrow-effective";
        adapter.continueTurn(context(call), "Ravi Mehta available हैं?",
                (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
        var captured = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();

        var result = adapter.continueTurn(context(call), "Kal Ravi evening mein available hain?", (tool, execution, input) -> {
            captured.set(input);
            return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1,
                    "effective availability", List.of());
        });

        assertThat(result.tool()).isEqualTo("FIND_SLOTS");
        assertThat(captured.get().queryType()).isEqualTo(AivaV2VoiceToolExecutor.QueryType.EFFECTIVE_DATE_AVAILABILITY);
        assertThat(captured.get().date()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1));
        assertThat(captured.get().daypart()).isEqualTo(NormalizedUserTurn.Daypart.EVENING);
        assertThat(captured.get().doctorId()).isEqualTo("doctor-ravi");
    }

    @Test
    void explicitCurrentDoctorOverridesThePreviousSingleDoctor() {
        var adapter = new AivaV2VoiceConversationAdapter(
                new AivaV2SessionStore(Clock.systemUTC()), mock(AivaV2ConversationDecisionGateway.class),
                LanguageAdapterRegistry.defaults());
        var queries = new java.util.ArrayList<String>();

        adapter.continueTurn(context("cross-turn-override"), "Pediatrician available है?",
                (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
        var second = adapter.continueTurn(context("cross-turn-override"), "Actually Amit Verma available हैं?",
                (tool, execution, input) -> {
                    queries.add(input.query());
                    return singleDoctor("doctor-amit", "Amit Verma", "General Medicine");
                });

        assertThat(second.tool()).isEqualTo("FIND_DOCTOR");
        assertThat(queries).containsExactly("Amit Verma");
    }

    @Test
    void ambiguousPreviousDoctorsRequireClarificationForPronounBooking() {
        var adapter = new AivaV2VoiceConversationAdapter(
                new AivaV2SessionStore(Clock.systemUTC()), mock(AivaV2ConversationDecisionGateway.class),
                LanguageAdapterRegistry.defaults());

        adapter.continueTurn(context("cross-turn-ambiguous"), "कौन सा डॉक्टर अवेलेबल है?",
                (tool, execution, input) -> twoDoctors());
        var second = adapter.continueTurn(context("cross-turn-ambiguous"), "उनका appointment चाहिए.",
                (tool, execution, input) -> { throw new AssertionError("ambiguous booking must not execute"); });

        assertThat(second.intent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(second.mutationBlocked()).isTrue();
        assertThat(second.response()).contains("Ravi Mehta", "Amit Verma");
    }

    @Test
    void pronounBookingWithoutPriorDoctorAsksForDoctor() {
        var adapter = new AivaV2VoiceConversationAdapter(
                new AivaV2SessionStore(Clock.systemUTC()), mock(AivaV2ConversationDecisionGateway.class),
                LanguageAdapterRegistry.defaults());

        var result = adapter.continueTurn(context("cross-turn-none"), "उनका appointment चाहिए.",
                (tool, execution, input) -> { throw new AssertionError("booking must not execute"); });

        assertThat(result.intent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(result.response()).contains("किस डॉक्टर");
        assertThat(result.mutationBlocked()).isTrue();
    }

    @Test
    void extractsEnglishExplicitDoctorNamesWithoutInterrogativeOrDiscoursePrefixes() {
        for (String phrase : List.of("Is Amit Verma available?", "Actually, is Amit Verma available?",
                "Is Ravi Mehta available?", "Is Dr Ravi Mehta available?")) {
            var input = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
            var result = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                    mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults())
                    .continueTurn(context("english-name-" + phrase.hashCode()), phrase,
                            (tool, execution, toolInput) -> {
                                input.set(toolInput);
                                return singleDoctor(phrase.contains("Amit") ? "doctor-amit" : "doctor-ravi",
                                        phrase.contains("Amit") ? "Amit Verma" : "Ravi Mehta", "Pediatrics");
                            });
            assertThat(result.intent()).as(phrase).isEqualTo("FIND_DOCTOR");
            assertThat(input.get().query()).as(phrase).isEqualTo(phrase.contains("Amit") ? "Amit Verma" : "Ravi Mehta");
        }
    }

    @Test
    void sittingTimePhrasesIncludingDevanagariTransliterationRouteToSchedule() {
        for (String phrase : List.of("रवि मेहता की सिटिंग टाइम क्या है?", "रवि मेहता का सिटिंग टाइम बताओ.",
                "Ravi Mehta ka sitting time kya hai?", "What are Ravi Mehta's consultation hours?")) {
            var input = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
            var result = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                    mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults())
                    .continueTurn(context("schedule-phrase-" + phrase.hashCode()), phrase,
                            (tool, execution, toolInput) -> {
                                input.set(toolInput);
                                return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1,
                                        "schedule", List.of());
                            });
            assertThat(result.tool()).as(phrase).isEqualTo("FIND_DOCTOR_SCHEDULE");
            assertThat(input.get().queryType()).as(phrase).isEqualTo(QueryType.RECURRING_SCHEDULE);
        }
    }

    @Test
    void englishScheduleUtteranceOverridesHindiSessionLocaleForResponseLanguage() {
        var result = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults())
                .continueTurn(context("english-language-hi-session"), "What are Ravi Mehta's consultation hours?",
                        (tool, execution, input) -> new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(),
                                1, 1, "schedule", List.of()));
        assertThat(result.tool()).isEqualTo("FIND_DOCTOR_SCHEDULE");
        assertThat(result.normalizedTurn().responseLanguage()).isEqualTo("en");
        assertThat(result.normalizedTurn().responseStyle()).isEqualTo(
                com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.STANDARD);
    }

    @Test
    void exactMissingDatePhrasesClarifyWithoutExecutingSlots() {
        for (String phrase : List.of("Ravi ke slots dikhao.", "रवि का स्लॉट दिखाओ.", "Show Ravi's slots.")) {
            String callId = "missing-date-exact-" + phrase.hashCode();
            var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                    mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
            adapter.continueTurn(context(callId), "Is Ravi Mehta available?",
                    (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
            var result = adapter.continueTurn(context(callId), phrase,
                    (tool, execution, input) -> { throw new AssertionError("missing date must not query slots"); });
            assertThat(result.intent()).as(phrase).isEqualTo("CLARIFY_DATE");
            assertThat(result.tool()).as(phrase).isNull();
        }
    }

    @Test
    void devanagariDaypartVariantsResumePendingSlotChoice() {
        for (String phrase : List.of("इवनिंग का बताइए.", "मॉर्निंग का बताइए.", "आफ्टरनून का बताइए.")) {
            String callId = "devanagari-daypart-" + phrase.hashCode();
            var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                    mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
            adapter.continueTurn(context(callId), "Ravi Mehta available हैं?",
                    (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
            adapter.continueTurn(context(callId), "कल availability बताइए", (tool, execution, input) ->
                    new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 2, 2, "slots", List.of(),
                            new AivaV2Models.AvailabilityResult(UUID.randomUUID(), null, 0, "voice-read-only",
                                    "doctor-ravi", "doctor-ravi", null, LocalDate.now().plusDays(1), null, null,
                                    "Asia/Kolkata", List.of(slot(LocalDate.now().plusDays(1), 9),
                                    slot(LocalDate.now().plusDays(1), 18)), "VOICE_PENDING_DAYPART", true,
                                    java.time.Instant.now(), java.time.Instant.now().plusSeconds(300), 0, 3)));
            var input = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
            var result = adapter.continueTurn(context(callId), phrase, (tool, execution, toolInput) -> {
                input.set(toolInput);
                return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1, "slots", List.of());
            });
            assertThat(result.tool()).as(phrase).isEqualTo("FIND_SLOTS");
            assertThat(input.get().daypart()).as(phrase).isEqualTo(phrase.startsWith("इवनिंग")
                    ? NormalizedUserTurn.Daypart.EVENING : phrase.startsWith("मॉर्निंग")
                    ? NormalizedUserTurn.Daypart.MORNING : NormalizedUserTurn.Daypart.AFTERNOON);
            assertThat(input.get().doctorId()).as(phrase).isEqualTo("doctor-ravi");
            assertThat(input.get().date()).as(phrase).isEqualTo(LocalDate.now().plusDays(1));
        }
    }

    @Test
    void showMoreWhileDaypartChoiceIsPendingRepeatsChoiceWithoutPaging() {
        String callId = "show-more-pending-daypart";
        var store = new AivaV2SessionStore(Clock.systemUTC());
        var adapter = new AivaV2VoiceConversationAdapter(store, mock(AivaV2ConversationDecisionGateway.class),
                LanguageAdapterRegistry.defaults());
        adapter.continueTurn(context(callId), "Ravi Mehta available?", (tool, execution, input) ->
                singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
        LocalDate date = LocalDate.now().plusDays(1);
        adapter.continueTurn(context(callId), "tomorrow availability", (tool, execution, input) ->
                new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 6, 6, "slots", List.of(),
                        new AivaV2Models.AvailabilityResult(UUID.randomUUID(), null, 0, "voice-read-only",
                                "doctor-ravi", "doctor-ravi", null, date, null, null, "Asia/Kolkata",
                                List.of(slot(date, 9), slot(date, 10), slot(date, 11), slot(date, 17), slot(date, 18), slot(date, 19)),
                                "VOICE_PENDING_DAYPART", true, java.time.Instant.now(),
                                java.time.Instant.now().plusSeconds(300), 0, 3)));

        var result = adapter.continueTurn(context(callId), "और बताइए",
                (tool, execution, input) -> { throw new AssertionError("must choose a daypart before paging"); });
        assertThat(result.intent()).isEqualTo("CLARIFY_DAYPART");
        assertThat(result.response()).contains("सुबह", "शाम");
        assertThat(store.find("VOICE|" + TENANT + "|" + callId).orElseThrow()
                .latestAvailabilityResult().cursor()).isEqualTo("VOICE_PENDING_DAYPART");
    }

    @Test
    void resolvesShortDoctorNameOnlyAgainstUniqueCurrentCallCandidates() {
        var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        String callId = "short-doctor-ref";
        adapter.continueTurn(context(callId), "Which doctors are available?", (tool, execution, input) ->
                singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
        var input = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
        var result = adapter.continueTurn(context(callId), "कल Ravi सुबह का slot बताइए", (tool, execution, toolInput) -> {
            input.set(toolInput);
            return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1, "slots", List.of());
        });
        assertThat(result.tool()).isEqualTo("FIND_SLOTS");
        assertThat(input.get().doctorId()).isEqualTo("doctor-ravi");
    }

    @Test
    void ambiguousShortDoctorNameClarifiesWithinCurrentCallCandidates() {
        String callId = "short-doctor-ambiguous";
        var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        adapter.continueTurn(context(callId), "Which doctors are available?", (tool, execution, input) ->
                new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 2, 2, "doctors", List.of(
                        new AivaV2VoiceToolExecutor.ProviderOption("ravi-one", "Ravi Mehta", "Pediatrics", null, TENANT.toString(), null, null),
                        new AivaV2VoiceToolExecutor.ProviderOption("ravi-two", "Ravi Sharma", "General Medicine", null, TENANT.toString(), null, null))));
        var result = adapter.continueTurn(context(callId), "Ravi ke slots", (tool, execution, input) -> {
            throw new AssertionError("ambiguous first name must clarify");
        });
        assertThat(result.intent()).isEqualTo("CLARIFY_DOCTOR");
    }

    @Test
    void tomorrowAvailabilityUsesSelectedDoctorAndClarifiesWhenNoDoctorIsSelected() {
        String callId = "tomorrow-availability-selected";
        var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        adapter.continueTurn(context(callId), "Is Ravi Mehta available?",
                (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
        var input = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
        var result = adapter.continueTurn(context(callId), "कल availability बताइए", (tool, execution, toolInput) -> {
            input.set(toolInput);
            return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1, "availability", List.of());
        });
        assertThat(result.tool()).isEqualTo("FIND_SLOTS");
        assertThat(input.get().queryType()).isEqualTo(QueryType.EFFECTIVE_DATE_AVAILABILITY);
        assertThat(input.get().doctorId()).isEqualTo("doctor-ravi");
        assertThat(input.get().date()).isEqualTo(LocalDate.now().plusDays(1));

        var noContext = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults())
                .continueTurn(context("tomorrow-availability-none"), "tomorrow availability",
                        (tool, execution, toolInput) -> { throw new AssertionError("must ask for a doctor"); });
        assertThat(noContext.intent()).isEqualTo("CLARIFY_DOCTOR");
    }

    @Test
    void newProviderAndScheduleIntentsClearStaleAvailabilityCursor() {
        String callId = "clear-stale-cursor";
        var store = new AivaV2SessionStore(Clock.systemUTC());
        var adapter = new AivaV2VoiceConversationAdapter(store, mock(AivaV2ConversationDecisionGateway.class),
                LanguageAdapterRegistry.defaults());
        adapter.continueTurn(context(callId), "Ravi Mehta available?",
                (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        adapter.continueTurn(context(callId), "tomorrow availability", (tool, execution, input) ->
                new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 2, 2, "slots", List.of(),
                        new AivaV2Models.AvailabilityResult(UUID.randomUUID(), null, 0, "voice", "doctor-ravi",
                                "doctor-ravi", null, tomorrow, null, null, "Asia/Kolkata",
                                List.of(slot(tomorrow, 9), slot(tomorrow, 17)), "VOICE_PENDING_DAYPART", true,
                                java.time.Instant.now(), java.time.Instant.now().plusSeconds(300), 0, 3)));
        var generic = adapter.continueTurn(context(callId), "Which doctors are available?",
                (tool, execution, input) -> twoDoctors());
        assertThat(generic.tool()).isEqualTo("FIND_DOCTOR");
        assertThat(store.find("VOICE|" + TENANT + "|" + callId).orElseThrow().latestAvailabilityResult()).isNull();

        String scheduleCall = "clear-stale-date";
        var scheduleAdapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        scheduleAdapter.continueTurn(context(scheduleCall), "Ravi Mehta available?",
                (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
        scheduleAdapter.continueTurn(context(scheduleCall), "Ravi ke slots dikhao",
                (tool, execution, input) -> { throw new AssertionError("date clarification expected"); });
        var schedule = scheduleAdapter.continueTurn(context(scheduleCall), "Ravi ka sitting time kya hai?",
                (tool, execution, input) -> new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1,
                        "schedule", List.of()));
        assertThat(schedule.tool()).isEqualTo("FIND_DOCTOR_SCHEDULE");
    }

    @Test
    void ambiguousPronounSittingTimeClarifiesDoctorWithoutAskingForDate() {
        for (String phrase : List.of("उनका sitting time क्या है?", "उनका सिटिंग टाइम क्या है?")) {
            String callId = "ambiguous-schedule-" + phrase.hashCode();
            var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                    mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
            adapter.continueTurn(context(callId), "Which doctors are available?", (tool, execution, input) -> twoDoctors());
            var result = adapter.continueTurn(context(callId), phrase,
                    (tool, execution, input) -> { throw new AssertionError("ambiguous schedule must clarify"); });
            assertThat(result.intent()).isEqualTo("CLARIFY_DOCTOR");
            assertThat(result.response()).contains("Amit Verma", "Ravi Mehta").doesNotContain("किस दिन");
        }
    }

    @Test
    void blocksCancelAndRescheduleWithIntentSpecificResponsesAcrossLanguages() {
        for (String[] sample : new String[][]{
                {"hi-IN", "मेरा अपॉइंटमेंट कैंसिल करना है", "CANCEL_APPOINTMENT", "कैंसिल"},
                {"hi-IN", "मेरा appointment cancel करना है", "CANCEL_APPOINTMENT", "कैंसिल"},
                {"hi-IN", "appointment रद्द करना है", "CANCEL_APPOINTMENT", "कैंसिल"},
                {"hi-IN", "appointment cancel karna hai", "CANCEL_APPOINTMENT", "cancel"},
                {"hi-IN", "meri booking cancel karni hai", "CANCEL_APPOINTMENT", "cancel"},
                {"en-IN", "cancel my appointment", "CANCEL_APPOINTMENT", "cancellation"},
                {"en-IN", "I want to cancel an appointment", "CANCEL_APPOINTMENT", "cancellation"},
                {"hi-IN", "Appointment reschedule karna hai.", "RESCHEDULE_APPOINTMENT", "reschedule"}}) {
            var result = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                    mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults())
                    .continueTurn(context("blocked-intent-" + sample[1].hashCode(), sample[0]), sample[1],
                            (tool, execution, input) -> { throw new AssertionError("mutation must not execute"); });
            assertThat(result.intent()).as(sample[1]).isEqualTo(sample[2]);
            assertThat(result.policyDecision()).as(sample[1]).isEqualTo("BLOCKED_READ_ONLY");
            assertThat(result.mutationBlocked()).as(sample[1]).isTrue();
            assertThat(result.response()).as(sample[1]).doesNotContain("किस डॉक्टर", "Which doctor");
            assertThat(result.response().toLowerCase()).as(sample[1]).contains(sample[3]);
        }
    }

    @Test
    void blockedAppointmentLookupHasItsOwnReadOnlyResponse() {
        var result = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults())
                .continueTurn(context("blocked-appointment-lookup", "en-IN"), "Show my appointments",
                        (tool, execution, input) -> { throw new AssertionError("appointment lookup stays blocked"); });
        assertThat(result.intent()).isEqualTo("FIND_APPOINTMENTS");
        assertThat(result.mutationBlocked()).isTrue();
        assertThat(result.response()).contains("Appointment lookup").doesNotContain("booking");
    }

    @Test
    void appointmentPossibilityWithKnownDoctorRoutesToDateSpecificAvailability() {
        for (String phrase : List.of("कल इनका appointment मिल सकता है क्या?",
                "kal appointment mil sakta hai?", "Can I get an appointment tomorrow?")) {
            var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                    mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
            String call = "appointment-possibility-" + phrase.hashCode();
            adapter.continueTurn(context(call), "Is Amit Verma available?",
                    (tool, execution, input) -> singleDoctor("doctor-amit", "Amit Verma", "General Medicine"));
            var captured = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
            var result = adapter.continueTurn(context(call), phrase, (tool, execution, input) -> {
                captured.set(input);
                return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1,
                        "availability", List.of());
            });
            assertThat(result.intent()).as(phrase).isEqualTo("FIND_SLOTS");
            assertThat(result.tool()).as(phrase).isEqualTo("FIND_SLOTS");
            assertThat(result.mutationBlocked()).as(phrase).isFalse();
            assertThat(captured.get().doctorId()).as(phrase).isEqualTo("doctor-amit");
            assertThat(captured.get().date()).as(phrase)
                    .isEqualTo(LocalDate.now(context(call).timezone()).plusDays(1));
        }
    }

    @Test
    void contextualTomorrowAvailabilityUsesOnlySingleResolvedDoctor() {
        for (String phrase : List.of("कल available हैं क्या?", "कल available हैं?", "kal available hain?",
                "available tomorrow?", "tomorrow available?", "कल समय है क्या?")) {
            var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                    mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
            String call = "contextual-availability-" + phrase.hashCode();
            adapter.continueTurn(context(call), "Is Ravi Mehta available?",
                    (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
            var input = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
            var result = adapter.continueTurn(context(call), phrase, (tool, execution, toolInput) -> {
                input.set(toolInput);
                return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1,
                        "availability", List.of());
            });
            assertThat(result.tool()).as(phrase).isEqualTo("FIND_SLOTS");
            assertThat(input.get().doctorId()).as(phrase).isEqualTo("doctor-ravi");
            assertThat(input.get().date()).as(phrase).isEqualTo(LocalDate.now(context(call).timezone()).plusDays(1));
        }

        var ambiguous = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        ambiguous.continueTurn(context("ambiguous-tomorrow-available"), "Which doctors are available?",
                (tool, execution, input) -> twoDoctors());
        var clarify = ambiguous.continueTurn(context("ambiguous-tomorrow-available"), "कल available हैं क्या?",
                (tool, execution, input) -> { throw new AssertionError("ambiguous doctor must clarify"); });
        assertThat(clarify.intent()).isEqualTo("CLARIFY_DOCTOR");
    }

    @Test
    void explicitShortDoctorAndTomorrowAvailabilityResolvesAgainstCurrentCallCandidates() {
        for (String phrase : List.of("Doctor Ravi kal available hain?", "Ravi kal available hai?",
                "Is Ravi available tomorrow?")) {
            var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                    mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
            String call = "short-name-date-availability-" + phrase.hashCode();
            adapter.continueTurn(context(call), "Which doctors are available?", (tool, execution, input) -> twoDoctors());
            var input = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
            var result = adapter.continueTurn(context(call), phrase, (tool, execution, toolInput) -> {
                input.set(toolInput);
                return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1, "availability", List.of());
            });
            assertThat(result.tool()).as(phrase).isEqualTo("FIND_SLOTS");
            assertThat(input.get().doctorId()).as(phrase).isEqualTo("doctor-ravi");
            assertThat(input.get().date()).as(phrase).isEqualTo(LocalDate.now(context(call).timezone()).plusDays(1));
        }
    }

    @Test
    void bareDoctorAppointmentReferenceAsksBoundedClarificationWithoutToolExecution() {
        for (String[] sample : new String[][]{{"en-IN", "Doctor Ravi Mehta's appointment."},
                {"hi-IN", "Doctor Ravi Mehta's appointment."}}) {
            var result = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                    mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults())
                    .continueTurn(context("bare-appointment-" + sample[0], sample[0]), sample[1],
                            (tool, execution, input) -> { throw new AssertionError("ambiguous appointment noun must clarify"); });
            assertThat(result.intent()).isEqualTo("CLARIFY_APPOINTMENT_INTENT");
            assertThat(result.tool()).isNull();
            assertThat(result.response()).contains("Ravi Mehta", "available slots").contains("existing appointment");
        }
    }

    @Test
    void blockedBookingRetainsExplicitDoctorForSubsequentSlotDateClarification() {
        var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        String call = "blocked-booking-then-slots";
        var blocked = adapter.continueTurn(context(call), "Doctor Ravi Mehta ka appointment chahiye.",
                (tool, execution, input) -> { throw new AssertionError("booking must remain blocked"); });
        assertThat(blocked.intent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(blocked.policyDecision()).isEqualTo("BLOCKED_READ_ONLY");

        var dateQuestion = adapter.continueTurn(context(call), "slot bata dijiye",
                (tool, execution, input) -> { throw new AssertionError("date must be clarified first"); });
        assertThat(dateQuestion.intent()).isEqualTo("CLARIFY_DATE");

        var input = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
        var resumed = adapter.continueTurn(context(call), "22nd date", (tool, execution, toolInput) -> {
            input.set(toolInput);
            return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1, "slots", List.of());
        });
        assertThat(resumed.tool()).isEqualTo("FIND_SLOTS");
        assertThat(input.get().doctorId()).isNull();
        assertThat(input.get().query()).isEqualTo("Ravi Mehta");
        assertThat(input.get().date()).isEqualTo(LocalDate.of(2026, 10, 22));
    }

    @Test
    void pendingDateConsumesAbsoluteHindiAndEnglishDatesButExplicitScheduleOverridesIt() {
        for (String phrase : List.of("22 अक्टूबर 2026 का", "22nd October")) {
            var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                    mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
            String call = "absolute-date-continuation-" + phrase.hashCode();
            adapter.continueTurn(context(call), "Ravi Mehta available?",
                    (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
            adapter.continueTurn(context(call), "Ravi ke slots dikhao",
                    (tool, execution, input) -> { throw new AssertionError("date must be asked first"); });
            var input = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
            var resumed = adapter.continueTurn(context(call), phrase, (tool, execution, toolInput) -> {
                input.set(toolInput);
                return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1, "slots", List.of());
            });
            assertThat(resumed.tool()).as(phrase).isEqualTo("FIND_SLOTS");
            assertThat(input.get().doctorId()).as(phrase).isEqualTo("doctor-ravi");
            assertThat(input.get().date()).as(phrase).isEqualTo(LocalDate.of(2026, 10, 22));
        }

        var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        String call = "schedule-over-pending-date";
        adapter.continueTurn(context(call), "Ravi Mehta available?",
                (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
        adapter.continueTurn(context(call), "Ravi ke slots dikhao",
                (tool, execution, input) -> { throw new AssertionError("date clarification expected"); });
        var schedule = adapter.continueTurn(context(call), "Actually Ravi ka sitting time kya hai?",
                (tool, execution, input) -> new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1,
                        "schedule", List.of()));
        assertThat(schedule.tool()).isEqualTo("FIND_DOCTOR_SCHEDULE");
        assertThat(schedule.normalizedTurn().temporal().rawSpan()).isNull();
    }

    @Test
    void explicitDoctorAndDateAvailabilityUsesSlotsWhileUndatedAvailabilityRemainsDiscovery() {
        for (String phrase : List.of("कल रवि मेहता available हैं क्या?",
                "Ravi Mehta kal available hain?", "Is Ravi Mehta available tomorrow?")) {
            var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                    mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
            String call = "named-date-availability-" + phrase.hashCode();
            adapter.continueTurn(context(call), "Which doctors are available?",
                    (tool, execution, input) -> twoDoctors());
            var captured = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
            var result = adapter.continueTurn(context(call), phrase, (tool, execution, input) -> {
                captured.set(input);
                return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1,
                        "availability", List.of());
            });
            assertThat(result.tool()).as(phrase).isEqualTo("FIND_SLOTS");
            assertThat(captured.get().doctorId()).as(phrase).isEqualTo("doctor-ravi");
            assertThat(captured.get().date()).as(phrase).isEqualTo(LocalDate.now(context(call).timezone()).plusDays(1));
        }

        var plain = new java.util.concurrent.atomic.AtomicReference<String>();
        var result = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults())
                .continueTurn(context("undated-doctor-availability"), "Is Ravi Mehta available?",
                        (tool, execution, input) -> {
                            plain.set(tool);
                            return singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics");
                        });
        assertThat(result.tool()).isEqualTo("FIND_DOCTOR");
        assertThat(plain).hasValue("FIND_DOCTOR");

        var withoutPriorCandidates = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
        new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults())
                .continueTurn(context("date-doctor-without-prior-search"), "कल रवि मेहता available हैं क्या?",
                        (tool, execution, input) -> {
                            withoutPriorCandidates.set(input);
                            return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 0, 0,
                                    "availability", List.of());
                        });
        assertThat(withoutPriorCandidates.get().query()).isEqualTo("Ravi Mehta");
    }

    @Test
    void absoluteDateAnswerRetainsDoctorForHindiEnglishAndHinglishSlotClarifications() {
        for (String[] sample : new String[][]{
                {"hi-IN", "डॉक्टर रवि मेहता का स्लॉट बताइए", "26 अक्टूबर 2026"},
                {"en-IN", "Show Ravi Mehta's slots", "October 26, 2026"},
                {"hi-IN", "Ravi ke slots dikhao", "26 October"}}) {
            var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                    mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
            String call = "absolute-date-keeps-doctor-" + sample[1].hashCode();
            adapter.continueTurn(context(call, sample[0]), "Is Ravi Mehta available?",
                    (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
            var clarification = adapter.continueTurn(context(call, sample[0]), sample[1],
                    (tool, execution, input) -> { throw new AssertionError("missing date must clarify"); });
            assertThat(clarification.intent()).isEqualTo("CLARIFY_DATE");
            var captured = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
            var resumed = adapter.continueTurn(context(call, sample[0]), sample[2], (tool, execution, input) -> {
                captured.set(input);
                return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1,
                        "availability", List.of());
            });
            assertThat(resumed.tool()).as(sample[2]).isEqualTo("FIND_SLOTS");
            assertThat(captured.get().doctorId()).as(sample[2]).isEqualTo("doctor-ravi");
            assertThat(captured.get().date()).as(sample[2]).isEqualTo(LocalDate.of(2026, 10, 26));
        }
    }

    @Test
    void actionableDateOrDaypartOverridesNegativePendingAvailabilityContinuation() {
        for (String[] sample : new String[][]{
                {"नहीं, आप परसों का बताइए", "PARSON", "null"},
                {"कल नहीं, 22 October का बताइए", "2026-10-22", "null"},
                {"नहीं, शाम का बताइए", null, "EVENING"}}) {
            var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                    mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
            String call = "negative-actionable-continuation-" + sample[0].hashCode();
            adapter.continueTurn(context(call), "Is Ravi Mehta available?",
                    (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
            LocalDate initialDate = LocalDate.now(context(call).timezone()).plusDays(1);
            adapter.continueTurn(context(call), "kal Ravi ke slots", (tool, execution, input) ->
                    slotPageResult(input, List.of(NormalizedUserTurn.Daypart.MORNING,
                                    NormalizedUserTurn.Daypart.EVENING),
                            List.of(slot(initialDate, 9), slot(initialDate, 17)), "VOICE_PENDING_DAYPART", 0));
            var captured = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
            var result = adapter.continueTurn(context(call), sample[0], (tool, execution, input) -> {
                captured.set(input);
                return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1,
                        "availability", List.of());
            });
            assertThat(result.tool()).as(sample[0]).isEqualTo("FIND_SLOTS");
            assertThat(captured.get().doctorId()).as(sample[0]).isEqualTo("doctor-ravi");
            if (sample[1] != null) {
                LocalDate expectedDate = "PARSON".equals(sample[1])
                        ? LocalDate.now(context(call).timezone()).plusDays(2) : LocalDate.parse(sample[1]);
                assertThat(captured.get().date()).as(sample[0]).isEqualTo(expectedDate);
                assertThat(captured.get().daypart()).as(sample[0]).isNull();
            } else {
                assertThat(captured.get().date()).as(sample[0]).isEqualTo(initialDate);
                assertThat(captured.get().daypart()).as(sample[0]).isEqualTo(NormalizedUserTurn.Daypart.EVENING);
            }
            assertThat(captured.get().pageOffset()).as(sample[0]).isZero();
        }
    }

    @Test
    void specialtySingleResultFromRealFindDoctorTurnBecomesContextualAvailabilityDoctor() {
        var store = new AivaV2SessionStore(Clock.systemUTC());
        var adapter = new AivaV2VoiceConversationAdapter(store,
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        String call = "specialty-result-context-continuity";
        var first = adapter.continueTurn(context(call), "कोई बच्चों का डॉक्टर available है?",
                (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
        assertThat(first.tool()).isEqualTo("FIND_DOCTOR");
        assertThat(store.find("VOICE|" + TENANT + "|" + call).orElseThrow()
                .latestProviderResult().resolvedProvider().displayName()).isEqualTo("Ravi Mehta");

        var nextInput = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
        var next = adapter.continueTurn(context(call), "कल available है क्या?", (tool, execution, input) -> {
            nextInput.set(input);
            return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 2, 2,
                    "availability", List.of());
        });

        assertThat(next.tool()).isEqualTo("FIND_SLOTS");
        assertThat(nextInput.get().doctorId()).isEqualTo("doctor-ravi");
        assertThat(nextInput.get().date()).isEqualTo(LocalDate.now(context(call).timezone()).plusDays(1));
    }

    @Test
    void explicitDoctorDateAvailabilityIsNotReducedToProviderDiscovery() {
        for (String phrase : List.of("कल डॉक्टर रवि मेहता available हैं.",
                "कल डॉक्टर रवि मेहता available हैं क्या?", "Ravi Mehta tomorrow available?",
                "Is Ravi Mehta available tomorrow?")) {
            var input = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
            var result = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                    mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults())
                    .continueTurn(context("explicit-doctor-date-control-" + phrase.hashCode()), phrase,
                            (tool, execution, toolInput) -> {
                                input.set(toolInput);
                                return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1,
                                        "availability", List.of());
                            });
            assertThat(result.tool()).as(phrase).isEqualTo("FIND_SLOTS");
            assertThat(input.get().date()).as(phrase)
                    .isEqualTo(LocalDate.now(context("date-control").timezone()).plusDays(1));
            assertThat(input.get().query()).as(phrase).contains("Ravi");
        }

        var plain = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults())
                .continueTurn(context("undated-doctor-availability-control"), "Is Ravi Mehta available?",
                        (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
        assertThat(plain.tool()).isEqualTo("FIND_DOCTOR");
    }

    @Test
    void devanagariAvailableDeclarativeWithDateUsesSlotsButWithoutDateUsesDiscovery() {
        var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        var datedInput = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
        var dated = adapter.continueTurn(context("devanagari-available-date"),
                "कल डॉक्टर रवि मेहता अवेलेबल हैं।", (tool, execution, input) -> {
                    datedInput.set(input);
                    return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1,
                            "availability", List.of());
                });
        assertThat(dated.tool()).isEqualTo("FIND_SLOTS");
        assertThat(datedInput.get().query()).contains("Ravi Mehta");
        assertThat(datedInput.get().date()).isEqualTo(LocalDate.now(context("date").timezone()).plusDays(1));

        var undated = adapter.continueTurn(context("devanagari-available-undated"),
                "डॉक्टर रवि मेहता अवेलेबल हैं।", (tool, execution, input) ->
                        singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
        assertThat(undated.tool()).isEqualTo("FIND_DOCTOR");
    }

    @Test
    void replacementDateAfterExecutedDaypartSlotsRetainsDoctorAndClearsOldSlotPage() {
        var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        String call = "real-slot-page-date-replacement";
        adapter.continueTurn(context(call), "कोई बच्चों का डॉक्टर available है?",
                (tool, execution, input) -> singleDoctor("doctor-ravi", "Ravi Mehta", "Pediatrics"));
        LocalDate dateA = LocalDate.of(2026, 10, 26);
        LocalDate dateB = LocalDate.now(context(call).timezone()).plusDays(2);
        var dateAInput = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
        adapter.continueTurn(context(call), "Ravi Mehta के slots 26 October 2026 बताइए", (tool, execution, input) -> {
            dateAInput.set(input);
            return availabilityResult(input, dateA, null, List.of(slot(dateA, 9), slot(dateA, 17)),
                    "VOICE_PENDING_DAYPART", 0);
        });
        assertThat(dateAInput.get().doctorId()).isEqualTo("doctor-ravi");
        assertThat(dateAInput.get().date()).isEqualTo(dateA);

        var eveningInput = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
        adapter.continueTurn(context(call), "Evening slot", (tool, execution, input) -> {
            eveningInput.set(input);
            return availabilityResult(input, dateA, NormalizedUserTurn.Daypart.EVENING,
                    List.of(slot(dateA, 17), slot(dateA, 18)), "VOICE_DAYPART:EVENING", 0);
        });
        assertThat(eveningInput.get().doctorId()).isEqualTo("doctor-ravi");
        assertThat(eveningInput.get().daypart()).isEqualTo(NormalizedUserTurn.Daypart.EVENING);

        var replacement = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
        adapter.continueTurn(context(call), "नहीं, परसों का बताइए", (tool, execution, input) -> {
            replacement.set(input);
            return availabilityResult(input, dateB, null, List.of(slot(dateB, 9)),
                    "VOICE_CONFIRM_DAYPART:MORNING", 0);
        });
        assertThat(replacement.get().doctorId()).isEqualTo("doctor-ravi");
        assertThat(replacement.get().date()).isEqualTo(dateB);
        assertThat(replacement.get().daypart()).isNull();
        assertThat(replacement.get().pageOffset()).isZero();
    }

    @Test
    void appointmentSlotWordingRoutesToSlotsButBareAppointmentRemainsClarification() {
        for (String phrase : List.of("डॉक्टर रवि मेहता, परसों का अपॉइंटमेंट स्लॉट बताइए",
                "Ravi Mehta ka appointment slot parso bataiye",
                "Ravi Mehta appointment slot day after tomorrow",
                "Ravi Mehta's available appointment slot tomorrow")) {
            var result = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                    mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults())
                    .continueTurn(context("appointment-slot-wording-" + phrase.hashCode()), phrase,
                            (tool, execution, input) -> new AivaV2VoiceToolExecutor.ToolResult(true, true,
                                    List.of(), 1, 1, "slots", List.of()));
            assertThat(result.tool()).as(phrase).isEqualTo("FIND_SLOTS");
        }

        var ambiguous = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults())
                .continueTurn(context("bare-doctor-appointment"), "Doctor Ravi Mehta's appointment",
                        (tool, execution, input) -> { throw new AssertionError("ambiguous appointment must clarify"); });
        assertThat(ambiguous.intent()).isEqualTo("CLARIFY_APPOINTMENT_INTENT");
    }

    @Test
    void appointmentIntentClarificationRetainsDoctorAndDateForSlotsOrBlockedBooking() {
        var adapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        String call = "appointment-clarification-retains-context";
        var clarification = adapter.continueTurn(context(call), "Doctor Ravi Mehta's appointment on October 26, 2026",
                (tool, execution, input) -> { throw new AssertionError("bounded clarification must not execute a tool"); });
        assertThat(clarification.intent()).isEqualTo("CLARIFY_APPOINTMENT_INTENT");

        var slotsInput = new java.util.concurrent.atomic.AtomicReference<AivaV2VoiceToolExecutor.ToolInput>();
        var slots = adapter.continueTurn(context(call), "I want slots for the day after tomorrow",
                (tool, execution, input) -> {
                    slotsInput.set(input);
                    return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1, "slots", List.of());
                });
        assertThat(slots.tool()).isEqualTo("FIND_SLOTS");
        assertThat(slotsInput.get().query()).contains("Ravi Mehta");
        assertThat(slotsInput.get().date()).isEqualTo(LocalDate.now(context(call).timezone()).plusDays(2));

        var bookingAdapter = new AivaV2VoiceConversationAdapter(new AivaV2SessionStore(Clock.systemUTC()),
                mock(AivaV2ConversationDecisionGateway.class), LanguageAdapterRegistry.defaults());
        String bookingCall = "appointment-clarification-blocked-booking";
        bookingAdapter.continueTurn(context(bookingCall), "Doctor Ravi Mehta's appointment on October 26, 2026",
                (tool, execution, input) -> { throw new AssertionError("clarification must not execute a tool"); });
        var blocked = bookingAdapter.continueTurn(context(bookingCall), "book करना है",
                (tool, execution, input) -> { throw new AssertionError("booking remains blocked"); });
        assertThat(blocked.intent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(blocked.policyDecision()).isEqualTo("BLOCKED_READ_ONLY");
        var next = bookingAdapter.continueTurn(context(bookingCall), "slots बताइए", (tool, execution, input) -> {
            assertThat(input.query()).contains("Ravi Mehta");
            assertThat(input.date()).isEqualTo(LocalDate.of(2026, 10, 26));
            return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1, "slots", List.of());
        });
        assertThat(next.intent()).isEqualTo("FIND_SLOTS");
    }

    private static AivaV2VoiceToolExecutor.ToolResult availabilityResult(AivaV2VoiceToolExecutor.ToolInput input,
            LocalDate date, NormalizedUserTurn.Daypart daypart, List<AivaV2Models.AvailabilitySlot> slots,
            String cursor, int offset) {
        return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), slots.size(), slots.size(), "slots",
                List.of(), new AivaV2Models.AvailabilityResult(UUID.randomUUID(), null, 0, "voice", "Ravi Mehta",
                        "doctor-ravi", null, date, daypart == null ? null : daypart.name(), null,
                        input.timezone().getId(), slots, cursor, false, java.time.Instant.now(),
                        java.time.Instant.now().plusSeconds(300), offset, 3));
    }

    private static AivaV2VoiceToolExecutor.ToolResult singleDoctor(String id, String name, String specialty) {
        return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 1, 1, "sourceCount=1",
                List.of(new AivaV2VoiceToolExecutor.ProviderOption(
                        id, name, specialty, null, TENANT.toString(), null, null)));
    }

    private static AivaV2VoiceToolExecutor.ToolResult twoDoctors() {
        return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), 2, 2, "sourceCount=2",
                List.of(
                        new AivaV2VoiceToolExecutor.ProviderOption(
                                "doctor-ravi", "Ravi Mehta", "Pediatrics", null, TENANT.toString(), null, null),
                        new AivaV2VoiceToolExecutor.ProviderOption(
                                "doctor-amit", "Amit Verma", "General Medicine", null, TENANT.toString(), null, null)));
    }

    private static AivaV2VoiceToolExecutor.ToolResult slotPageResult(AivaV2VoiceToolExecutor.ToolInput input,
            List<NormalizedUserTurn.Daypart> dayparts, List<AivaV2Models.AvailabilitySlot> slots,
            String cursor, int offset) {
        return new AivaV2VoiceToolExecutor.ToolResult(true, true, List.of(), slots.size(), slots.size(), "slots",
                List.of(new AivaV2VoiceToolExecutor.ProviderOption("doctor-ravi", "Ravi Mehta", "Pediatrics",
                        null, TENANT.toString(), null, null)),
                new AivaV2Models.AvailabilityResult(UUID.randomUUID(), null, 0, "voice", "doctor-ravi",
                        "doctor-ravi", null, input.date(), input.daypart() == null ? null : input.daypart().name(),
                        null, input.timezone().getId(), slots, cursor, offset + 3 < slots.size(),
                        java.time.Instant.now(), java.time.Instant.now().plusSeconds(300), offset, 3));
    }

    private static AivaV2Models.AvailabilityResult scheduleState(String doctorId, LocalDate date, String daypart,
                                                                  String cursor) {
        return new AivaV2Models.AvailabilityResult(UUID.randomUUID(), null, 0, "voice", doctorId, doctorId,
                null, date, daypart, null, "Asia/Kolkata", List.of(), cursor, false,
                java.time.Instant.now(), java.time.Instant.now().plusSeconds(300), 0, 3);
    }

    private static AivaV2Models.AvailabilitySlot slot(LocalDate date, int hour) {
        return new AivaV2Models.AvailabilitySlot(null, java.time.LocalTime.of(hour, 0),
                java.time.LocalTime.of(hour, 30), hour + ":00");
    }

    private static AivaExecutionContext context(String callId) {
        return context(callId, "hi-IN");
    }

    private static AivaExecutionContext context(String callId, String language) {
        return new AivaExecutionContext(TENANT, null, "VOICE", language, ZoneId.of("Asia/Kolkata"),
                callId, null, null, null, Set.of("FIND_DOCTOR", "FIND_DOCTOR_SCHEDULE", "FIND_SLOTS"));
    }
}
