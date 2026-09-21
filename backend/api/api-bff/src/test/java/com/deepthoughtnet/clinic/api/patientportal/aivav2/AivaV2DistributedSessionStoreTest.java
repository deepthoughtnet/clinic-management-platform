package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageResponse;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Testcontainers(disabledWithoutDocker = true)
class AivaV2DistributedSessionStoreTest {
    private static final Instant NOW = Instant.parse("2026-09-19T00:00:00Z");

    @Container
    static final GenericContainer<?> redisContainer = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Test
    void instanceBContinuesInstanceAAndReplaysCompletedDuplicate() {
        try (RedisResources resources = resources()) {
            AivaV2SessionStore instanceA = resources.store();
            AivaV2SessionStore instanceB = resources.store();
            String key = "tenant-a|patient-a|conversation-a";
            MessageResponse response = response("first");

            instanceA.updateTurn(key, "turn-1", 1L,
                    ignored -> new AivaV2SessionStore.TurnExecution(session(1), response));
            AivaV2SessionStore.TurnExchange duplicate = instanceB.updateTurn(key, "turn-1", 1L,
                    ignored -> { throw new AssertionError("duplicate must not execute"); });
            AivaV2SessionStore.TurnExchange next = instanceB.updateTurn(key, "turn-2", 2L,
                    ignored -> new AivaV2SessionStore.TurnExecution(session(2), response("second")));

            assertThat(duplicate.disposition()).isEqualTo(AivaV2SessionStore.TurnDisposition.DUPLICATE_REPLAY);
            assertThat(duplicate.response()).isEqualTo(response);
            assertThat(next.disposition()).isEqualTo(AivaV2SessionStore.TurnDisposition.ACCEPTED);
            assertThat(instanceA.find(key).orElseThrow().version()).isEqualTo(2L);
        }
    }

    @Test
    void concurrentCrossInstanceTurnIsReservedOnlyOnce() throws Exception {
        try (RedisResources resources = resources()) {
            AivaV2SessionStore instanceA = resources.store();
            AivaV2SessionStore instanceB = resources.store();
            String key = "tenant-a|patient-a|conversation-b";
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            var executor = Executors.newSingleThreadExecutor();
            try {
                var accepted = executor.submit(() -> instanceA.updateTurn(key, "turn-1", 1L, ignored -> {
                    entered.countDown();
                    try { assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new RuntimeException(ex); }
                    return new AivaV2SessionStore.TurnExecution(session(1), response("accepted"));
                }));
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                AivaV2SessionStore.TurnExchange inFlight = instanceB.updateTurn(key, "turn-1", 1L,
                        ignored -> { throw new AssertionError("in-flight duplicate must not execute"); });
                assertThat(inFlight.disposition()).isEqualTo(AivaV2SessionStore.TurnDisposition.IN_FLIGHT);
                release.countDown();
                assertThat(accepted.get(5, TimeUnit.SECONDS).disposition())
                        .isEqualTo(AivaV2SessionStore.TurnDisposition.ACCEPTED);
            } finally {
                release.countDown();
                executor.shutdownNow();
            }
        }
    }

    @Test
    void staleAndGapTurnsAreRejectedAcrossInstances() {
        try (RedisResources resources = resources()) {
            AivaV2SessionStore instanceA = resources.store();
            AivaV2SessionStore instanceB = resources.store();
            String key = "tenant-a|patient-a|conversation-c";
            instanceA.updateTurn(key, "turn-1", 1L,
                    ignored -> new AivaV2SessionStore.TurnExecution(session(1), response("one")));

            assertThat(instanceB.updateTurn(key, "turn-3", 3L, ignored -> {
                throw new AssertionError("gap must not execute");
            }).disposition()).isEqualTo(AivaV2SessionStore.TurnDisposition.SEQUENCE_GAP);
            assertThat(instanceB.updateTurn(key, "turn-0", 0L, ignored -> {
                throw new AssertionError("stale must not execute");
            }).disposition()).isEqualTo(AivaV2SessionStore.TurnDisposition.STALE_TURN);
        }
    }

    @Test
    void expiredReservationCanBeRecoveredByTheSameTurn() throws Exception {
        try (RedisResources resources = resources()) {
            AivaV2SessionStore instanceA = resources.store();
            AivaV2SessionStore instanceB = resources.store();
            String key = "tenant-a|patient-a|conversation-recovery";
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            var executor = Executors.newSingleThreadExecutor();
            try {
                var abandoned = executor.submit(() -> instanceA.updateTurn(key, "turn-1", 1L, ignored -> {
                    entered.countDown();
                    try { assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new RuntimeException(ex); }
                    return new AivaV2SessionStore.TurnExecution(session(1), response("abandoned"));
                }));
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                resources.redis.opsForHash().put("aiva:v2:session:" + key,
                        "reservationLeaseUntil", Long.toString(NOW.minusSeconds(1).toEpochMilli()));

                assertThat(instanceB.updateTurn(key, "turn-1", 1L,
                        ignored -> new AivaV2SessionStore.TurnExecution(session(1), response("recovered")))
                        .disposition()).isEqualTo(AivaV2SessionStore.TurnDisposition.ACCEPTED);
                release.countDown();
                assertThatThrownBy(() -> abandoned.get(5, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(IllegalStateException.class);
            } finally {
                release.countDown();
                executor.shutdownNow();
            }
        }
    }

    @Test
    void corruptPayloadIsRejectedWithoutCrossKeyLeakage() {
        try (RedisResources resources = resources()) {
            String key = "tenant-a|patient-a|corrupt";
            resources.redis.opsForHash().put("aiva:v2:session:" + key, "expiresAt", Long.toString(NOW.plusSeconds(600).toEpochMilli()));
            resources.redis.opsForHash().put("aiva:v2:session:" + key, "state", "not-json");

            assertThat(resources.store().find(key)).isEmpty();
        }
    }

    @Test
    void appointmentReferentRoundTripsThroughRedisSessionState() {
        try (RedisResources resources = resources()) {
            String key = "tenant-a|patient-a|referent";
            AivaV2Models.AppointmentSummary referent = new AivaV2Models.AppointmentSummary(
                    "appointment-1", "Dr. Test", "Clinic", LocalDate.of(2026, 9, 25),
                    LocalTime.of(9, 30), "UPCOMING", null, "doctor-1", "clinic-1",
                    "tenant-a", "clinic-slug");

            resources.store().update(key, ignored -> session(1).withAppointmentReferent(referent));

            assertThat(resources.store().find(key).orElseThrow().activeAppointmentReferent())
                    .isEqualTo(referent);
        }
    }

    private RedisResources resources() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redisContainer.getHost(), redisContainer.getMappedPort(6379)));
        factory.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        return new RedisResources(factory, redis);
    }

    private static SessionProjection session(long version) {
        return new SessionProjection("conversation", java.util.UUID.randomUUID(), "tenant",
                null, null, null, null, null, null, null, null, null, version, NOW.plusSeconds(1800));
    }

    private static MessageResponse response(String text) {
        return new MessageResponse("conversation", "turn", text, "TEST", null,
                "TEST", false, List.of());
    }

    private record RedisResources(LettuceConnectionFactory factory, StringRedisTemplate redis)
            implements AutoCloseable {
        AivaV2SessionStore store() {
            return new AivaV2SessionStore(Clock.fixed(NOW, ZoneOffset.UTC), redis,
                    new ObjectMapper().findAndRegisterModules(), "redis");
        }

        @Override
        public void close() {
            factory.destroy();
        }
    }
}
