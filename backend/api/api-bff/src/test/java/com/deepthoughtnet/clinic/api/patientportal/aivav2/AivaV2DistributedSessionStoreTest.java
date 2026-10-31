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
    void activeVoiceDoctorSurvivesRedisSessionRoundTrip() {
        try (RedisResources resources = resources()) {
            AivaV2SessionStore instanceA = resources.store();
            AivaV2SessionStore instanceB = resources.store();
            String key = "VOICE|tenant-a|provider-call-a";
            var doctor = new com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate(
                    "doctor-ravi", "doctor-ravi", "doctor-ravi", null, "tenant-a", null, null,
                    "Ravi Mehta", "Pediatrics", null,
                    com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource.CARE_PRIVATE,
                    new com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCapabilities(
                            false, true, false, false, false, false, true));

            instanceA.update(key, ignored -> session(1).withActiveVoiceDoctor(doctor));

            assertThat(instanceB.find(key).orElseThrow().activeVoiceDoctor()).isEqualTo(doctor);
        }
    }

    @Test
    void sameDotVoiceCallIdentifierRemainsTenantIsolatedAcrossRedisRoundTrip() {
        try (RedisResources resources = resources()) {
            AivaV2SessionStore writer = resources.store();
            AivaV2SessionStore reader = resources.store();
            String tenantAKey = "VOICE|tenant-a|same-dotvoice-call";
            String tenantBKey = "VOICE|tenant-b|same-dotvoice-call";
            var providerA = new com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate(
                    "provider-a", "provider-a", "doctor-a", null, "tenant-a", null, null,
                    "Dr Same Name", "General Medicine", null,
                    com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource.CARE_PRIVATE,
                    new com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCapabilities(
                            false, true, false, false, false, false, true));
            var providerB = new com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate(
                    "provider-b", "provider-b", "doctor-b", null, "tenant-b", null, null,
                    "Dr Same Name", "General Medicine", null,
                    com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource.CARE_PRIVATE,
                    new com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCapabilities(
                            false, true, false, false, false, false, true));

            writer.update(tenantAKey, ignored -> sessionForTenant("tenant-a", 1).withActiveVoiceDoctor(providerA));
            writer.update(tenantBKey, ignored -> sessionForTenant("tenant-b", 1).withActiveVoiceDoctor(providerB));

            SessionProjection restoredA = reader.find(tenantAKey).orElseThrow();
            SessionProjection restoredB = reader.find(tenantBKey).orElseThrow();
            assertThat(restoredA.activeVoiceDoctor().doctorId()).isEqualTo("doctor-a");
            assertThat(restoredB.activeVoiceDoctor().doctorId()).isEqualTo("doctor-b");
            assertThat(restoredA.tenantId()).isEqualTo("tenant-a");
            assertThat(restoredB.tenantId()).isEqualTo("tenant-b");
            assertThat(resources.redis().hasKey("aiva:v2:session:" + tenantAKey)).isTrue();
            assertThat(resources.redis().hasKey("aiva:v2:session:" + tenantBKey)).isTrue();
        }
    }

    @Test
    void conversationPresentationModeSurvivesRedisSessionRoundTripAndWorkflowClearing() {
        try (RedisResources resources = resources()) {
            AivaV2SessionStore instanceA = resources.store();
            AivaV2SessionStore instanceB = resources.store();
            String key = "VOICE|tenant-a|provider-call-presentation";
            instanceA.update(key, ignored -> session(1)
                    .withPresentationMode(AivaV2Models.VoicePresentationMode.HINGLISH));

            SessionProjection restored = instanceB.find(key).orElseThrow();
            assertThat(restored.presentationMode()).isEqualTo(AivaV2Models.VoicePresentationMode.HINGLISH);
            instanceB.update(key, SessionProjection::clearTransientWorkflow);
            assertThat(instanceA.find(key).orElseThrow().presentationMode())
                    .isEqualTo(AivaV2Models.VoicePresentationMode.HINGLISH);
        }
    }

    @Test
    void voiceClarificationStateSurvivesRedisSessionRoundTrip() {
        try (RedisResources resources = resources()) {
            AivaV2SessionStore instanceA = resources.store();
            AivaV2SessionStore instanceB = resources.store();
            String key = "VOICE|tenant-a|provider-call-clarification";
            AivaV2Models.VoiceClarificationState clarification = new AivaV2Models.VoiceClarificationState(
                    AivaV2Models.VoiceClarificationKind.DOCTOR, 2,
                    AivaV2Models.VoiceUnresolvedReason.AMBIGUOUS_DOCTOR,
                    List.of("Ravi Mehta", "Amit Verma"), false);

            instanceA.update(key, ignored -> session(1).withVoiceClarificationState(clarification));

            assertThat(instanceB.find(key).orElseThrow().voiceClarificationState()).isEqualTo(clarification);
        }
    }

    @Test
    void voiceAuthenticationGrantSurvivesRedisRoundTripAndIsClearedOnClose() {
        try (RedisResources resources = resources()) {
            AivaV2SessionStore instanceA = resources.store();
            AivaV2SessionStore instanceB = resources.store();
            String key = "VOICE|tenant-a|provider-call-auth";
            var tenant = java.util.UUID.randomUUID();
            var patient = java.util.UUID.randomUUID();
            var auth = new AivaV2Models.VoiceAuthenticationState("VERIFIED", java.util.UUID.randomUUID(),
                    tenant, patient, "provider-call-auth", java.util.UUID.randomUUID(), Instant.now().plusSeconds(300),
                    1, new AivaTransactionAuthorization(AivaTransactionAuthorization.IdentityState.AUTHENTICATED,
                    patient, AivaTransactionAuthorization.ActorType.VOICE_PATIENT, "app-user-real",
                    java.util.Set.of(AivaTransactionAuthorization.Capability.BOOK_APPOINTMENT), "VOICE_OTP", "OTP",
                    Instant.now().plusSeconds(300), tenant, "provider-call-auth"));
            instanceA.update(key, ignored -> session(1).withVoiceAuthenticationState(auth));

            assertThat(instanceB.find(key).orElseThrow().voiceAuthenticationState()).isEqualTo(auth);
            instanceB.update(key, AivaV2Models.SessionProjection::close);
            assertThat(instanceA.find(key).orElseThrow().voiceAuthenticationState()).isNull();
        }
    }

    @Test
    void guestRegistrationCapabilitySurvivesRedisRoundTripAndIsClearedOnSessionEnd() {
        try (RedisResources resources = resources()) {
            AivaV2SessionStore instanceA = resources.store();
            AivaV2SessionStore instanceB = resources.store();
            String key = "VOICE|tenant-guest|provider-call-guest";
            var state = new AivaV2Models.VoiceGuestRegistrationState(
                    AivaV2Models.VoiceGuestRegistrationStatus.CAPABILITY_GRANTED,
                    java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "provider-call-guest",
                    java.util.UUID.randomUUID(), Instant.now().plusSeconds(600), true);
            instanceA.update(key, ignored -> session(1).withVoiceGuestRegistrationState(state));

            assertThat(instanceB.find(key).orElseThrow().voiceGuestRegistrationState()).isEqualTo(state);
            instanceB.update(key, AivaV2Models.SessionProjection::close);
            assertThat(instanceA.find(key).orElseThrow().voiceGuestRegistrationState()).isNull();
        }
    }

    @Test
    void quickRegistrationConsentNameAndAgeSurviveRedisRoundTripWithTenantScope() {
        try (RedisResources resources = resources()) {
            AivaV2SessionStore instanceA = resources.store();
            AivaV2SessionStore instanceB = resources.store();
            String key = "VOICE|tenant-quick-registration|provider-call-quick-registration";
            var tenant = java.util.UUID.randomUUID();
            var state = new AivaV2Models.VoiceGuestRegistrationState(
                    AivaV2Models.VoiceGuestRegistrationStatus.CAPABILITY_GRANTED, tenant, java.util.UUID.randomUUID(),
                    "provider-call-quick-registration", java.util.UUID.randomUUID(), Instant.now().plusSeconds(600), true,
                    "Asha", 42, Instant.parse("2026-10-30T02:00:00Z"));
            var provider = new com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate(
                    "quick-provider", "provider-handle", "doctor-quick", "clinic-quick", tenant.toString(),
                    "clinic", "booking-ref", "Dr. Ravi Mehta", "General Medicine", "Clinic",
                    com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource.CARE_PRIVATE,
                    new com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCapabilities(
                            true, true, false, false, false, true, false));
            var date = java.time.LocalDate.of(2026, 11, 2);
            var draft = new com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingDraft(
                    java.util.UUID.randomUUID(), state.patientId(), tenant.toString(), provider, "General Medicine",
                    date, "EVENING", null, null, null,
                    com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.DraftStatus.COLLECTING, 2,
                    Instant.now().plusSeconds(600), null);
            var availability = new com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult(
                    java.util.UUID.randomUUID(), draft.draftId(), draft.revision(), "criteria",
                    provider.providerHandle(), provider.doctorId(), provider.clinicId(), date, "EVENING",
                    "Asia/Kolkata", java.util.List.of(new com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilitySlot(
                            "displayed-only", java.time.LocalTime.of(20, 0), java.time.LocalTime.of(20, 30), "8 PM")),
                    null, false, Instant.now(), Instant.now().plusSeconds(300));
            instanceA.update(key, ignored -> sessionForTenant(tenant.toString(), 1).withPatientId(state.patientId())
                    .withBookingDraft(draft).withVoiceGuestRegistrationState(state).withActiveVoiceDoctor(provider)
                    .withVoiceAvailabilityResult(availability)
                    .withPresentationMode(com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoicePresentationMode.HINDI));

            var restored = instanceB.find(key).orElseThrow();
            assertThat(restored.voiceGuestRegistrationState()).isEqualTo(state);
            assertThat(restored.patientId()).isEqualTo(state.patientId());
            assertThat(restored.activeDraft()).isEqualTo(draft);
            assertThat(restored.activeDraft().selectedProvider()).isEqualTo(provider);
            assertThat(restored.activeDraft().specialtyFilter()).isEqualTo("General Medicine");
            assertThat(restored.activeDraft().preferredDate()).isEqualTo(date);
            assertThat(restored.activeDraft().preferredTimeWindow()).isEqualTo("EVENING");
            assertThat(restored.activeDraft().selectedSlotReference()).isNull();
            assertThat(restored.activeVoiceDoctor()).isEqualTo(provider);
            assertThat(restored.latestAvailabilityResult()).isEqualTo(availability);
            assertThat(restored.presentationMode()).isEqualTo(
                    com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoicePresentationMode.HINDI);
            assertThat(restored.tenantId()).isEqualTo(tenant.toString());
            assertThat(instanceB.find("VOICE|other-tenant|provider-call-quick-registration")).isEmpty();
        }
    }

    @Test
    void preparedGuestBookingDraftAndCanonicalSlotReferenceSurviveRedisRoundTrip() {
        try (RedisResources resources = resources()) {
            AivaV2SessionStore instanceA = resources.store();
            AivaV2SessionStore instanceB = resources.store();
            String key = "VOICE|tenant-guest|provider-call-prepared";
            var tenant = java.util.UUID.randomUUID();
            var patient = java.util.UUID.randomUUID();
            var provider = new com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate(
                    "canonical-provider", "provider-handle", "doctor-id", "clinic-id", tenant.toString(),
                    "clinic", "canonical-booking-reference", "Dr. Ravi Mehta", "Pediatrics", "Clinic",
                    com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource.CARE_PRIVATE,
                    new com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCapabilities(
                            true, true, false, false, false, true, false));
            var draftId = java.util.UUID.randomUUID();
            var requestId = java.util.UUID.randomUUID();
            var draft = new com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingDraft(
                    draftId, patient, tenant.toString(), provider, null, LocalDate.of(2026, 11, 5), null,
                    LocalTime.of(16, 0), null, requestId, "canonical-slot-reference",
                    com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.DraftStatus.PREPARED,
                    4, NOW.plusSeconds(1800), null);
            var availability = new com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult(
                    requestId, draftId, 4, "criteria-fingerprint", provider.providerHandle(), provider.doctorId(),
                    provider.clinicId(), LocalDate.of(2026, 11, 5), null, "Asia/Kolkata",
                    List.of(new com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilitySlot(
                            "canonical-slot-reference", LocalTime.of(16, 0), LocalTime.of(16, 30), "16:00")),
                    null, false, NOW, NOW.plusSeconds(300));
            var guest = new com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceGuestRegistrationState(
                    com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceGuestRegistrationStatus.CAPABILITY_GRANTED,
                    tenant, patient, "provider-call-prepared", java.util.UUID.randomUUID(), NOW.plusSeconds(600), true);
            SessionProjection prepared = session(1).withPatientId(patient).withBookingDraft(draft)
                    .withVoiceAvailabilityResult(availability).withVoiceGuestRegistrationState(guest);

            instanceA.update(key, ignored -> prepared);

            SessionProjection restored = instanceB.find(key).orElseThrow();
            assertThat(restored.activeDraft()).isEqualTo(draft);
            assertThat(restored.activeDraft().selectedProvider().doctorId()).isEqualTo(provider.doctorId());
            assertThat(restored.activeDraft().selectedSlotReference()).isEqualTo("canonical-slot-reference");
            assertThat(restored.latestAvailabilityResult()).isEqualTo(availability);
            assertThat(restored.latestAvailabilityResult().doctorId()).isEqualTo(provider.doctorId());
            assertThat(restored.voiceGuestRegistrationState()).isEqualTo(guest);
            instanceB.update(key, SessionProjection::close);
            SessionProjection closed = instanceA.find(key).orElseThrow();
            assertThat(closed.activeDraft()).isNull();
            assertThat(closed.voiceGuestRegistrationState()).isNull();
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

            assertThatThrownBy(() -> resources.store().find(key))
                    .isInstanceOf(AivaV2SessionStore.SessionStoreCorruptException.class);
            assertThat(resources.store().find("tenant-b|patient-b|valid" )).isEmpty();
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

    private static SessionProjection sessionForTenant(String tenant, long version) {
        return new SessionProjection("same-dotvoice-call", java.util.UUID.randomUUID(), tenant,
                null, null, null, null, null, null, null, null, null, null, false, version, NOW.plusSeconds(1800),
                null, null, null, null, null, null);
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
