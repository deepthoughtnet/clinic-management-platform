package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingDraft;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.CancellationConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageResponse;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.RescheduleConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AivaV2SessionStoreTest {
    private static final Instant NOW = Instant.parse("2026-09-18T12:00:00Z");
    private static final UUID PATIENT_ID = UUID.randomUUID();

    @Test
    void expiredSessionReadIsAbsentAndRemovesPresentation() {
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.fixed(NOW, ZoneId.of("UTC")));
        String key = "tenant|patient|conversation";
        store.update(key, ignored -> session(NOW.minusSeconds(1)));
        store.updatePresentation(key, turn("hi", ResponseStyle.STANDARD), NOW.minusSeconds(1));

        assertThat(store.find(key)).isEmpty();
        assertThat(store.updatePresentation(key, turn("en", ResponseStyle.HINGLISH), NOW.plusSeconds(60))
                .responseLanguage()).isEqualTo("en");
    }

    @Test
    void expiredSessionUpdateReceivesFreshStateAndCannotReviveCapabilities() {
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.fixed(NOW, ZoneId.of("UTC")));
        String key = "tenant|patient|conversation";
        store.update(key, ignored -> session(NOW.minusSeconds(1)));
        AtomicBoolean sawFreshInput = new AtomicBoolean();

        store.update(key, current -> {
            sawFreshInput.set(current == null);
            return new SessionProjection("conversation", PATIENT_ID, "tenant", null, null, null, null,
                    null, null, null, null, null, 1, NOW.plusSeconds(1800));
        });

        assertThat(sawFreshInput).isTrue();
        assertThat(store.find(key).orElseThrow())
                .extracting(SessionProjection::pendingConfirmation, SessionProjection::pendingCancellation,
                        SessionProjection::pendingRescheduleConfirmation)
                .containsOnlyNulls();
    }

    @Test
    void unexpiredSessionUpdateRetainsState() {
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.fixed(NOW, ZoneId.of("UTC")));
        String key = "tenant|patient|conversation";
        SessionProjection original = session(NOW.plusSeconds(60));
        store.update(key, ignored -> original);

        assertThat(store.update(key, current -> current).activeDraft()).isEqualTo(original.activeDraft());
    }

    @Test
    void sessionKeysIsolateConversationPatientAndTenant() {
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.fixed(NOW, ZoneId.of("UTC")));
        store.update("tenant-a|patient-a|conversation", ignored -> session(NOW.plusSeconds(60)));
        store.update("tenant-a|patient-a|other-conversation", ignored -> session(NOW.plusSeconds(60)));
        store.update("tenant-b|patient-a|conversation", ignored -> session(NOW.plusSeconds(60)));
        store.update("tenant-a|patient-b|conversation", ignored -> session(NOW.plusSeconds(60)));

        assertThat(store.find("tenant-a|patient-a|conversation")).isPresent();
        assertThat(store.find("tenant-a|patient-a|missing")).isEmpty();
        assertThat(store.find("tenant-a|patient-b|conversation")).isPresent();
        assertThat(store.find("tenant-b|patient-a|conversation")).isPresent();
    }

    @Test
    void presentationCannotResurrectAfterSessionExpiry() {
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.fixed(NOW, ZoneId.of("UTC")));
        String key = "tenant|patient|conversation";
        store.update(key, ignored -> session(NOW.minusSeconds(1)));
        store.updatePresentation(key, turn("hi", ResponseStyle.HINGLISH), NOW.minusSeconds(1));

        store.update(key, ignored -> new SessionProjection("conversation", PATIENT_ID, "tenant", null, null,
                null, null, null, null, null, null, null, 1, NOW.plusSeconds(1800)));
        assertThat(store.updatePresentation(key, new NormalizedUserTurn("123", "123", "", "en", null, false,
                        List.of(), "en", ResponseStyle.STANDARD), NOW.plusSeconds(1800))
                .responseLanguage()).isEqualTo("en");
    }

    @Test
    void sameKeyUpdatesAreAtomic() throws Exception {
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.fixed(NOW, ZoneId.of("UTC")));
        String key = "tenant|patient|conversation";
        store.update(key, ignored -> new SessionProjection("conversation", PATIENT_ID, "tenant", null, null,
                null, null, null, null, null, null, null, 0, NOW.plusSeconds(1800)));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> atomicIncrement(store, key, ready, start));
            var second = executor.submit(() -> atomicIncrement(store, key, ready, start));
            ready.await();
            start.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }

        assertThat(store.find(key).orElseThrow().version()).isEqualTo(2);
    }

    @Test
    void duplicateTurnReplaysResponseWithoutRunningProcessor() {
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.fixed(NOW, ZoneId.of("UTC")));
        AtomicInteger executions = new AtomicInteger();
        MessageResponse response = response("accepted");

        var first = store.updateTurn("tenant|patient|conversation", "turn-a", 1L,
                current -> {
                    executions.incrementAndGet();
                    return new AivaV2SessionStore.TurnExecution(freshSession(), response);
                });
        var duplicate = store.updateTurn("tenant|patient|conversation", "turn-a", 1L,
                current -> {
                    executions.incrementAndGet();
                    return new AivaV2SessionStore.TurnExecution(freshSession(), response("wrong"));
                });

        assertThat(first.disposition()).isEqualTo(AivaV2SessionStore.TurnDisposition.ACCEPTED);
        assertThat(duplicate.disposition()).isEqualTo(AivaV2SessionStore.TurnDisposition.DUPLICATE_REPLAY);
        assertThat(duplicate.response()).isSameAs(response);
        assertThat(executions).hasValue(1);
    }

    @Test
    void concurrentDuplicateTurnExecutesProcessorOnce() throws Exception {
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.fixed(NOW, ZoneId.of("UTC")));
        AtomicInteger executions = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> store.updateTurn("tenant|patient|conversation", "turn-a", 1L,
                    current -> {
                        executions.incrementAndGet();
                        return new AivaV2SessionStore.TurnExecution(freshSession(), response("accepted"));
                    }));
            var second = executor.submit(() -> store.updateTurn("tenant|patient|conversation", "turn-a", 1L,
                    current -> {
                        executions.incrementAndGet();
                        return new AivaV2SessionStore.TurnExecution(freshSession(), response("wrong"));
                    }));
            var dispositions = List.of(first.get().disposition(), second.get().disposition());
            assertThat(dispositions).containsExactlyInAnyOrder(
                    AivaV2SessionStore.TurnDisposition.ACCEPTED,
                    AivaV2SessionStore.TurnDisposition.DUPLICATE_REPLAY);
        } finally {
            executor.shutdownNow();
        }
        assertThat(executions).hasValue(1);
    }

    @Test
    void differentTurnIdWithSameTextSemanticsIsAcceptedAsNewSequence() {
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.fixed(NOW, ZoneId.of("UTC")));
        AtomicInteger executions = new AtomicInteger();
        var processor = (java.util.function.Function<com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection,
                AivaV2SessionStore.TurnExecution>) current -> {
            executions.incrementAndGet();
            return new AivaV2SessionStore.TurnExecution(freshSession(), response("show appointments"));
        };

        store.updateTurn("tenant|patient|conversation", "turn-a", 1L, processor);
        var second = store.updateTurn("tenant|patient|conversation", "turn-b", 2L, processor);

        assertThat(second.disposition()).isEqualTo(AivaV2SessionStore.TurnDisposition.ACCEPTED);
        assertThat(executions).hasValue(2);
    }

    @Test
    void staleAndGapSequencesDoNotRunProcessor() {
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.fixed(NOW, ZoneId.of("UTC")));
        AtomicInteger executions = new AtomicInteger();
        var processor = (java.util.function.Function<com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection,
                AivaV2SessionStore.TurnExecution>) current -> {
            executions.incrementAndGet();
            return new AivaV2SessionStore.TurnExecution(freshSession(), response("accepted"));
        };
        store.updateTurn("tenant|patient|conversation", "turn-a", 1L, processor);

        var gap = store.updateTurn("tenant|patient|conversation", "turn-c", 3L, processor);
        var stale = store.updateTurn("tenant|patient|conversation", "turn-old", 1L, processor);

        assertThat(gap.disposition()).isEqualTo(AivaV2SessionStore.TurnDisposition.SEQUENCE_GAP);
        assertThat(stale.disposition()).isEqualTo(AivaV2SessionStore.TurnDisposition.STALE_TURN);
        assertThat(executions).hasValue(1);
    }

    @Test
    void expiredConversationRejectsProtocolTurnAndDoesNotReviveState() {
        MutableClock clock = new MutableClock(NOW);
        AivaV2SessionStore store = new AivaV2SessionStore(clock);
        store.update("tenant|patient|conversation", ignored -> session(NOW.plusSeconds(1)));
        clock.current = NOW.plusSeconds(2);
        AtomicInteger executions = new AtomicInteger();

        var result = store.updateTurn("tenant|patient|conversation", "old-confirm", 1L, current -> {
            executions.incrementAndGet();
            return new AivaV2SessionStore.TurnExecution(freshSession(), response("wrong"));
        });

        assertThat(result.disposition()).isEqualTo(AivaV2SessionStore.TurnDisposition.CONVERSATION_EXPIRED);
        assertThat(executions).hasValue(0);
        assertThat(store.find("tenant|patient|conversation")).isEmpty();
    }

    private void atomicIncrement(AivaV2SessionStore store, String key, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
        store.update(key, current -> new SessionProjection(current.conversationId(), current.patientId(),
                current.tenantId(), current.activeDraft(), current.suspendedDraft(), current.latestProviderResult(),
                current.latestAvailabilityResult(), current.pendingConfirmation(), current.pendingCancellation(),
                current.pendingCancellationResolution(), current.pendingReschedule(),
                current.pendingRescheduleConfirmation(), current.version() + 1, current.expiresAt()));
    }

    private SessionProjection session(Instant expiresAt) {
        Instant capabilityExpiry = NOW.plusSeconds(600);
        return new SessionProjection("conversation", PATIENT_ID, "tenant",
                BookingDraft.create(PATIENT_ID, "tenant", NOW), null, null, null,
                new BookingConfirmation("booking", UUID.randomUUID(), 1, "provider", "doctor", "clinic",
                        UUID.randomUUID(), "slot", null, null, capabilityExpiry, "booking-command"),
                new CancellationConfirmation("cancel", UUID.randomUUID(), "appointment", "doctor", "clinic",
                        null, null, capabilityExpiry, "cancel-command", UUID.randomUUID()), null,
                null,
                new RescheduleConfirmation("reschedule", UUID.randomUUID().toString(), "provider", "doctor", "clinic",
                        "tenant", UUID.randomUUID(), "slot", null, null, 1, NOW, capabilityExpiry, "reschedule-command"),
                7, expiresAt);
    }

    private SessionProjection freshSession() {
        return new SessionProjection("conversation", PATIENT_ID, "tenant", null, null, null, null,
                null, 1, NOW.plusSeconds(1800));
    }

    private MessageResponse response(String message) {
        return new MessageResponse("conversation", "server-turn", message, "ACCEPTED", null,
                "TEST", false, List.of());
    }

    private NormalizedUserTurn turn(String language, ResponseStyle style) {
        return new NormalizedUserTurn("text", "text", "", language, null, false, List.of(), language, style);
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
    }
}
