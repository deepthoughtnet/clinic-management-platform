package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConversationDecision;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.DialogAct;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageRequest;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageResponse;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Operation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.TopicAction;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Certification coverage for failure modes above the Redis store primitives.
 * These tests deliberately use independent service, kernel, gateway and store
 * objects; only Redis is shared between the simulated instances.
 */
@Testcontainers(disabledWithoutDocker = true)
class AivaV2RemainingReliabilityCertificationTest {
    private static final Instant START = Instant.parse("2026-09-19T00:00:00Z");
    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();

    @Container
    static final GenericContainer<?> redisContainer = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @AfterEach
    void clearContext() {
        RequestContextHolder.clear();
    }

    @Test
    void independentConversationServicesAlternateAcrossInstances() {
        try (RedisResources resources = resources()) {
            setContext();
            AivaV2ConversationService instanceA = conversationService(resources.store(), unknownDecision());
            AivaV2ConversationService instanceB = conversationService(resources.store(), unknownDecision());
            String conversation = "cross-instance";

            MessageResponse first = instanceA.message(request(conversation, "turn-a", 1));
            MessageResponse second = instanceB.message(request(conversation, "turn-b", 2));
            MessageResponse third = instanceA.message(request(conversation, "turn-c", 3));

            assertThat(first.responseCategory()).isEqualTo("CLARIFICATION");
            assertThat(second.responseCategory()).isEqualTo("CLARIFICATION");
            assertThat(third.responseCategory()).isEqualTo("CLARIFICATION");
            assertThat(resources.redis.opsForHash().get("aiva:v2:session:" + redisKey(conversation), "lastSequence"))
                    .isEqualTo("3");
        }
    }

    @Test
    void redisUnavailableBeforeAdmissionFailsWithoutMemoryFallback() {
        LettuceConnectionFactory unavailableFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("127.0.0.1", 1));
        unavailableFactory.afterPropertiesSet();
        StringRedisTemplate unavailable = new StringRedisTemplate(unavailableFactory);
        unavailable.afterPropertiesSet();
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.fixed(START, ZoneOffset.UTC), unavailable,
                new ObjectMapper().findAndRegisterModules(), "redis");
        AtomicBoolean updaterCalled = new AtomicBoolean();

        assertThatThrownBy(() -> store.updateTurn("tenant|patient|unavailable", "turn-1", 1L, ignored -> {
            updaterCalled.set(true);
            return new AivaV2SessionStore.TurnExecution(null, response("must-not-run"));
        })).isInstanceOf(RuntimeException.class);

        assertThat(updaterCalled).isFalse();
        unavailableFactory.destroy();
    }

    @Test
    void sessionExpiryDuringProcessingRejectsFinalizationAndDoesNotResurrectState() {
        MutableClock clock = new MutableClock(START);
        try (RedisResources resources = resources(clock)) {
            AivaV2SessionStore store = resources.store(clock);
            String key = "tenant|patient|expiry-in-flight";
            AtomicReference<MessageResponse> produced = new AtomicReference<>();

            assertThatThrownBy(() -> store.updateTurn(key, "turn-1", 1L, ignored -> {
                clock.advanceSeconds(30 * 60 + 1);
                MessageResponse response = response("stale-result");
                produced.set(response);
                return new AivaV2SessionStore.TurnExecution(session(1, clock.instant().minusSeconds(1)), response);
            })).hasMessageContaining("session finalization failed");

            assertThat(produced).hasValue(response("stale-result"));
            assertThat(store.find(key)).isEmpty();
            assertThat(resources.redis.opsForHash().entries("aiva:v2:session:" + key)).isEmpty();
        }
    }

    @Test
    void leaseReclaimKeepsDomainAtMostOnceButAllowsProviderWorkAgain() throws Exception {
        try (RedisResources resources = resources()) {
            AivaV2SessionStore instanceA = resources.store();
            AivaV2SessionStore instanceB = resources.store();
            String key = "tenant|patient|lease-reclaim";
            AtomicBoolean secondWorkerRan = new AtomicBoolean();
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
            try {
                var first = executor.submit(() -> instanceA.updateTurn(key, "turn-1", 1L, ignored -> {
                    entered.countDown();
                    try {
                        assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ex);
                    }
                    return new AivaV2SessionStore.TurnExecution(session(1, START.plusSeconds(1800)), response("worker-a"));
                }));
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                resources.redis.opsForHash().put("aiva:v2:session:" + key, "reservationLeaseUntil",
                        Long.toString(START.minusSeconds(1).toEpochMilli()));
                assertThat(instanceB.updateTurn(key, "turn-1", 1L, ignored -> {
                    secondWorkerRan.set(true);
                    return new AivaV2SessionStore.TurnExecution(session(1, START.plusSeconds(1800)), response("worker-b"));
                }).disposition()).isEqualTo(AivaV2SessionStore.TurnDisposition.ACCEPTED);
                assertThat(secondWorkerRan).isTrue();
                release.countDown();
                assertThatThrownBy(() -> first.get()).hasCauseInstanceOf(IllegalStateException.class);
            } finally {
                release.countDown();
                executor.shutdownNow();
            }
        }
    }

    @Test
    void redisRestartWithoutPersistentVolumeLosesSessionAndResetsSafely() {
        String key = "tenant|patient|redis-restart";
        RedisResources resources = resources();
        try {
            resources.store().updateTurn(key, "turn-1", 1L,
                    ignored -> new AivaV2SessionStore.TurnExecution(session(1, START.plusSeconds(1800)), response("active")));
            resources.close();
            redisContainer.stop();
            redisContainer.start();
            try (RedisResources restarted = resources(false)) {
                assertThat(restarted.store().find(key)).isEmpty();
            }
        } finally {
            if (redisContainer.isRunning()) redisContainer.stop();
        }
    }

    private AivaV2ConversationService conversationService(AivaV2SessionStore store, ConversationDecision decision) {
        PatientPortalService patientPortal = mock(PatientPortalService.class);
        when(patientPortal.currentPatientId()).thenReturn(PATIENT);
        AivaV2ConversationDecisionGateway gateway = (text, language, context) -> decision;
        return new AivaV2ConversationService(gateway,
                new AivaV2TransactionalKernel(mock(AivaV2BookingTools.class), Clock.fixed(START, ZoneOffset.UTC)),
                store, patientPortal, Clock.fixed(START, ZoneOffset.UTC));
    }

    private void setContext() {
        RequestContextHolder.set(new RequestContext(new TenantId(TENANT), PATIENT, "patient",
                Set.of("PATIENT"), "PATIENT", "reliability-cert"));
    }

    private MessageRequest request(String conversation, String turnId, long sequence) {
        return new MessageRequest(conversation, "status", "en", null, turnId, sequence);
    }

    private ConversationDecision unknownDecision() {
        return new ConversationDecision("1.0", DialogAct.UNKNOWN, Operation.UNKNOWN,
                null, null, null, TopicAction.CONTINUE, "en", 1.0, "TEST", false, 0);
    }

    private String redisKey(String conversation) {
        return TENANT + "|" + PATIENT + "|" + conversation;
    }

    private static MessageResponse response(String text) {
        return new MessageResponse("conversation", "turn", text, "TEST", null,
                "TEST", false, List.of());
    }

    private static AivaV2Models.SessionProjection session(long version, Instant expiresAt) {
        return new AivaV2Models.SessionProjection("conversation", PATIENT, TENANT.toString(),
                null, null, null, null, null, version, expiresAt);
    }

    private RedisResources resources() {
        return resources(Clock.fixed(START, ZoneOffset.UTC));
    }

    private RedisResources resources(Clock clock) {
        return resources(clock, true);
    }

    private RedisResources resources(boolean flush) {
        return resources(Clock.fixed(START, ZoneOffset.UTC), flush);
    }

    private RedisResources resources(Clock clock, boolean flush) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redisContainer.getHost(), redisContainer.getMappedPort(6379)));
        factory.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        if (flush) redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        return new RedisResources(factory, redis, clock);
    }

    private record RedisResources(LettuceConnectionFactory factory, StringRedisTemplate redis, Clock clock)
            implements AutoCloseable {
        AivaV2SessionStore store() {
            return store(clock);
        }

        AivaV2SessionStore store(Clock requestedClock) {
            return new AivaV2SessionStore(requestedClock, redis,
                    new ObjectMapper().findAndRegisterModules(), "redis");
        }

        @Override
        public void close() {
            factory.destroy();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advanceSeconds(long seconds) {
            current = current.plusSeconds(seconds);
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
    }
}
