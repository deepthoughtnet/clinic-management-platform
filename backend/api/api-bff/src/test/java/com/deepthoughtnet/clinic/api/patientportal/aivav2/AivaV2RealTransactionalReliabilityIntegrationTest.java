package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.deepthoughtnet.clinic.api.ApiBffApplication;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.clinic.db.ClinicProfileEntity;
import com.deepthoughtnet.clinic.clinic.db.ClinicProfileRepository;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AppointmentLookupFilter;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AppointmentSummary;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilitySlot;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingDraft;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.CancellationConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConversationDecision;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.DraftStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageRequest;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageResponse;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Operation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCapabilities;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.RescheduleConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.RescheduleResolution;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.RescheduleStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ToolResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConfirmationPolarity;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentBookingRequest;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiAppointmentOption;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiDoctorOption;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn;
import com.deepthoughtnet.clinic.api.support.PostgresTestContainerSupport;
import com.deepthoughtnet.clinic.appointment.db.AppointmentEntity;
import com.deepthoughtnet.clinic.appointment.db.AppointmentRepository;
import com.deepthoughtnet.clinic.appointment.db.DoctorAvailabilityEntity;
import com.deepthoughtnet.clinic.appointment.db.DoctorAvailabilityRepository;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.identity.db.TenantEntity;
import com.deepthoughtnet.clinic.identity.db.TenantMembershipEntity;
import com.deepthoughtnet.clinic.identity.db.TenantMembershipRepository;
import com.deepthoughtnet.clinic.api.reliability.db.IdempotencyKeyRepository;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.service.PatientPortalAccessRequestService;
import com.deepthoughtnet.clinic.patient.service.model.PatientPortalAuthorizedClinicRecord;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real PostgreSQL + Redis AIVA mutation/retry harness. */
@Testcontainers
@SpringBootTest(classes = ApiBffApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/.well-known/jwks.json",
                "clinic.ai.enabled=false", "clinic.ocr.enabled=false",
                "clinic.notifications.scheduler.enabled=false", "clinic.notifications.dispatcher.enabled=false",
                "clinic.carepilot.scheduler.enabled=false", "voice.stt.provider-order=mock",
                "voice.tts.provider-order=mock", "spring.flyway.enabled=true"
        })
class AivaV2RealTransactionalReliabilityIntegrationTest extends PostgresTestContainerSupport {
    private static final Instant NOW = Instant.parse("2026-09-19T00:00:00Z");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired private PatientPortalService patientPortalService;
    @Autowired private ClinicTimeZoneResolver clinicTimeZoneResolver;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private DoctorAvailabilityRepository availabilityRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private TenantMembershipRepository membershipRepository;
    @Autowired private ClinicProfileRepository clinicProfileRepository;
    @Autowired private IdempotencyKeyRepository idempotencyKeyRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockBean private PatientPortalAccessRequestService accessRequestService;
    @MockBean private AppUserProvisioner appUserProvisioner;

    @AfterEach
    void clearContext() {
        RequestContextHolder.clear();
    }

    @Test
    void crossInstanceCancellationCommitsOnceAndRetriesThroughDomainIdempotency() {
        Fixture fixture = fixture(true, false, false);
        String conversationId = "cancel-" + UUID.randomUUID();
        InstanceGraphs graphs = instanceGraphs(false);
        AppointmentSummary summary = summary(fixture);
        CancellationConfirmation confirmation = graphs.a.cancellationTool.prepare(summary, Instant.now()).value();
        seed(graphs.a.store, conversationId, session(conversationId, fixture,
                null, confirmation, null, null, null));

        setContext(fixture);
        MessageResponse response = graphs.b.service.message(confirm(conversationId, "cancel-turn", 1, "CONFIRM_CANCELLATION"));
        assertThat(response.responseCategory()).isEqualTo("CANCELLATION_CONFIRMED");

        entityManager.clear();
        AppointmentEntity persisted = freshAppointment(fixture.ownerTenant, fixture.appointmentId);
        assertThat(persisted.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(freshUpcoming()).noneMatch(row -> fixture.appointmentId.equals(row.appointmentId()));

        MessageResponse replay = graphs.a.service.message(confirm(conversationId, "cancel-turn", 1, "CONFIRM_CANCELLATION"));
        assertThat(replay.assistantMessage()).isEqualTo(response.assistantMessage());
    }

    @Test
    void crossInstanceRescheduleCommitsOnceAndRetriesThroughDomainIdempotency() {
        Fixture fixture = fixture(true, true, false);
        InstanceGraphs graphs = instanceGraphs(false);
        String conversationId = "reschedule-" + UUID.randomUUID();
        AppointmentSummary summary = summary(fixture);
        ProviderCandidate provider = provider(fixture);
        RescheduleResolution state = new RescheduleResolution(
                summary.appointmentReference(), AppointmentLookupFilter.empty(), List.of(summary),
                "Owner Doctor", provider.providerHandle(), provider.doctorId(), provider.clinicId(),
                provider.tenantId(), provider.clinicSlug(), fixture.sourceDate, fixture.sourceTime,
                fixture.targetDate, null, null, null, fixture.targetSlot.slotReference(), fixture.targetSlot.startsAt(),
                RescheduleStatus.CONFIRMATION_PENDING, 1, Instant.now(), Instant.now().plusSeconds(300));
        RescheduleConfirmation confirmation = graphs.a.rescheduleTools.prepare(state, fixture.targetAvailability, fixture.targetSlot).value();
        seed(graphs.a.store, conversationId, session(conversationId, fixture,
                null, null, state, confirmation, null));

        setContext(fixture);
        MessageResponse response = graphs.b.service.message(confirm(conversationId, "reschedule-turn", 1, "CONFIRM_RESCHEDULE"));
        assertThat(response.responseCategory()).isEqualTo("RESCHEDULE_CONFIRMED");

        entityManager.clear();
        AppointmentEntity persisted = freshAppointment(fixture.ownerTenant, fixture.appointmentId);
        assertThat(persisted.getAppointmentDate()).isEqualTo(fixture.targetDate);
        assertThat(persisted.getAppointmentTime()).isEqualTo(fixture.targetTime);
        assertThat(freshUpcoming()).noneMatch(row -> fixture.appointmentId.equals(row.appointmentId())
                && fixture.sourceDate.equals(row.appointmentDate()));
        assertThat(freshUpcoming()).anyMatch(row -> fixture.appointmentId.equals(row.appointmentId())
                && fixture.targetDate.equals(row.appointmentDate()) && fixture.targetTime.equals(row.appointmentTime()));

        assertThat(graphs.a.service.message(confirm(conversationId, "reschedule-turn", 1, "CONFIRM_RESCHEDULE")).assistantMessage())
                .isEqualTo(response.assistantMessage());
    }

    @Test
    void crossInstanceBookingCommitsOnceAndRetriesThroughDomainIdempotency() {
        Fixture fixture = fixture(false, true, true);
        InstanceGraphs graphs = instanceGraphs(false);
        String conversationId = "booking-" + UUID.randomUUID();
        ProviderCandidate provider = provider(fixture);
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), fixture.currentPatientId,
                fixture.currentTenant.toString(), provider, null, fixture.targetDate, null,
                fixture.targetSlot.startsAt(), fixture.targetAvailability.requestId(), fixture.targetSlot.slotReference(),
                DraftStatus.READY_FOR_CONFIRMATION, 1, Instant.now().plusSeconds(1800), null);
        BookingConfirmation confirmation = new BookingConfirmation("booking-confirmation-" + UUID.randomUUID(),
                draft.draftId(), draft.revision(), provider.providerHandle(), provider.doctorId(), provider.clinicId(),
                fixture.targetAvailability.requestId(), fixture.targetSlot.slotReference(), fixture.targetDate,
                fixture.targetSlot.startsAt(), Instant.now().plusSeconds(300),
                "booking-command-" + UUID.randomUUID());
        seed(graphs.a.store, conversationId, session(conversationId, fixture,
                draft, null, null, null, confirmation));

        setContext(fixture);
        MessageResponse response = graphs.b.service.message(confirm(conversationId, "booking-turn", 1, "CONFIRM_BOOKING"));
        assertThat(response.responseCategory()).isEqualTo("BOOKING_CONFIRMED");
        entityManager.clear();
        assertThat(appointmentRepository.findAll().stream()
                .filter(row -> fixture.currentPatientId.equals(row.getPatientId())).count()).isEqualTo(1);
        assertThat(idempotencyExists(fixture.currentTenant, confirmation.idempotencyKey())).isTrue();
        assertThat(graphs.a.service.message(confirm(conversationId, "booking-turn", 1, "CONFIRM_BOOKING")).assistantMessage())
                .isEqualTo(response.assistantMessage());
    }

    @Test
    void commitBeforeRedisFinalizeFailureStillProducesOneCancellationAndSafeRetry() {
        Fixture fixture = fixture(true, false, false);
        String conversationId = "cancel-finalize-failure-" + UUID.randomUUID();
        InstanceGraphs graphs = instanceGraphs(true);
        CancellationConfirmation confirmation = graphs.a.cancellationTool.prepare(summary(fixture), Instant.now()).value();
        seed(instanceGraphs(false).a.store, conversationId, session(conversationId, fixture, null, confirmation, null, null, null));

        setContext(fixture);
        assertThatThrownBy(() -> graphs.a.service.message(confirm(conversationId, "cancel-finalize", 1, "CONFIRM_CANCELLATION")))
                .isInstanceOf(RuntimeException.class);
        entityManager.clear();
        assertThat(freshAppointment(fixture.ownerTenant, fixture.appointmentId).getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(idempotencyExists(fixture.ownerTenant, confirmation.idempotencyKey())).isTrue();

        MessageResponse retry = graphs.b.service.message(confirm(conversationId, "cancel-finalize", 1, "CONFIRM_CANCELLATION"));
        assertThat(retry.responseCategory()).isEqualTo("CANCELLATION_CONFIRMED");
        assertThat(freshAppointment(fixture.ownerTenant, fixture.appointmentId).getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(freshUpcoming()).noneMatch(row -> fixture.appointmentId.equals(row.appointmentId()));
    }

    @Test
    void bookingCommitBeforeRedisFinalizeFailureDoesNotCreateDuplicate() {
        Fixture fixture = fixture(false, true, true);
        String conversationId = "booking-finalize-failure-" + UUID.randomUUID();
        InstanceGraphs graphs = instanceGraphs(true);
        ProviderCandidate provider = provider(fixture);
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), fixture.currentPatientId,
                fixture.currentTenant.toString(), provider, null, fixture.targetDate, null,
                fixture.targetSlot.startsAt(), fixture.targetAvailability.requestId(), fixture.targetSlot.slotReference(),
                DraftStatus.READY_FOR_CONFIRMATION, 1, Instant.now().plusSeconds(1800), null);
        BookingConfirmation confirmation = new BookingConfirmation("booking-confirmation-" + UUID.randomUUID(),
                draft.draftId(), draft.revision(), provider.providerHandle(), provider.doctorId(), provider.clinicId(),
                fixture.targetAvailability.requestId(), fixture.targetSlot.slotReference(), fixture.targetDate,
                fixture.targetSlot.startsAt(), Instant.now().plusSeconds(300), "booking-command-" + UUID.randomUUID());
        seed(instanceGraphs(false).a.store, conversationId, session(conversationId, fixture, draft, null, null, null, confirmation));

        setContext(fixture);
        assertThatThrownBy(() -> graphs.a.service.message(confirm(conversationId, "booking-finalize", 1, "CONFIRM_BOOKING")))
                .isInstanceOf(RuntimeException.class);
        entityManager.clear();
        assertThat(appointmentRepository.findAll().stream()
                .filter(row -> fixture.currentPatientId.equals(row.getPatientId())).count()).isEqualTo(1);
        assertThat(idempotencyExists(fixture.currentTenant, confirmation.idempotencyKey())).isTrue();

        MessageResponse retry = graphs.b.service.message(confirm(conversationId, "booking-finalize", 1, "CONFIRM_BOOKING"));
        assertThat(retry.responseCategory()).isEqualTo("BOOKING_CONFIRMED");
        entityManager.clear();
        assertThat(appointmentRepository.findAll().stream()
                .filter(row -> fixture.currentPatientId.equals(row.getPatientId())).count()).isEqualTo(1);
    }

    @Test
    void rescheduleCommitBeforeRedisFinalizeFailureDoesNotRepeatMutation() {
        Fixture fixture = fixture(true, true, false);
        String conversationId = "reschedule-finalize-failure-" + UUID.randomUUID();
        InstanceGraphs graphs = instanceGraphs(true);
        AppointmentSummary summary = summary(fixture);
        ProviderCandidate provider = provider(fixture);
        RescheduleResolution state = new RescheduleResolution(
                summary.appointmentReference(), AppointmentLookupFilter.empty(), List.of(summary),
                "Owner Doctor", provider.providerHandle(), provider.doctorId(), provider.clinicId(), provider.tenantId(),
                provider.clinicSlug(), fixture.sourceDate, fixture.sourceTime, fixture.targetDate, null, null, null,
                fixture.targetSlot.slotReference(), fixture.targetSlot.startsAt(), RescheduleStatus.CONFIRMATION_PENDING,
                1, Instant.now(), Instant.now().plusSeconds(300));
        RescheduleConfirmation confirmation = graphs.a.rescheduleTools.prepare(state, fixture.targetAvailability, fixture.targetSlot).value();
        seed(instanceGraphs(false).a.store, conversationId, session(conversationId, fixture, null, null, state, confirmation, null));

        setContext(fixture);
        assertThatThrownBy(() -> graphs.a.service.message(confirm(conversationId, "reschedule-finalize", 1, "CONFIRM_RESCHEDULE")))
                .isInstanceOf(RuntimeException.class);
        entityManager.clear();
        AppointmentEntity committed = freshAppointment(fixture.ownerTenant, fixture.appointmentId);
        assertThat(committed.getAppointmentDate()).isEqualTo(fixture.targetDate);
        assertThat(committed.getAppointmentTime()).isEqualTo(fixture.targetTime);

        MessageResponse retry = graphs.b.service.message(confirm(conversationId, "reschedule-finalize", 1, "CONFIRM_RESCHEDULE"));
        assertThat(retry.responseCategory()).isEqualTo("RESCHEDULE_CONFIRMED");
        entityManager.clear();
        assertThat(freshAppointment(fixture.ownerTenant, fixture.appointmentId).getAppointmentDate()).isEqualTo(fixture.targetDate);
        assertThat(idempotencyExists(fixture.ownerTenant, confirmation.commandId())).isTrue();
        assertThat(freshUpcoming()).anyMatch(row -> fixture.appointmentId.equals(row.appointmentId())
                && fixture.targetDate.equals(row.appointmentDate()) && fixture.targetTime.equals(row.appointmentTime()));
    }

    @Test
    void redisUnavailableAfterAdmissionDoesNotDuplicateBooking() {
        Fixture fixture = fixture(false, true, true);
        String conversationId = "booking-redis-outage-" + UUID.randomUUID();
        InstanceGraphs graphs = instanceGraphs(false);
        ProviderCandidate provider = provider(fixture);
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), fixture.currentPatientId,
                fixture.currentTenant.toString(), provider, null, fixture.targetDate, null,
                fixture.targetSlot.startsAt(), fixture.targetAvailability.requestId(), fixture.targetSlot.slotReference(),
                DraftStatus.READY_FOR_CONFIRMATION, 1, Instant.now().plusSeconds(1800), null);
        BookingConfirmation confirmation = new BookingConfirmation("booking-confirmation-" + UUID.randomUUID(),
                draft.draftId(), draft.revision(), provider.providerHandle(), provider.doctorId(), provider.clinicId(),
                fixture.targetAvailability.requestId(), fixture.targetSlot.slotReference(), fixture.targetDate,
                fixture.targetSlot.startsAt(), Instant.now().plusSeconds(300), "booking-outage-command-" + UUID.randomUUID());
        seed(graphs.a.store, conversationId, session(conversationId, fixture, draft, null, null, null, confirmation));

        setContext(fixture);
        Graphs outage = graphsWithRedisUnavailableAfterAdmission();
        assertThatThrownBy(() -> outage.service.message(confirm(conversationId, "booking-redis-outage", 1, "CONFIRM_BOOKING")))
                .isInstanceOf(RuntimeException.class);
        entityManager.clear();
        assertThat(appointmentRepository.findAll().stream()
                .filter(row -> fixture.currentPatientId.equals(row.getPatientId())).count()).isEqualTo(1);

        String redisKey = "aiva:v2:session:" + key(fixture.currentTenant, fixture.currentPatientId, conversationId);
        redisTemplate().opsForHash().put(redisKey, "reservationLeaseUntil", "0");
        MessageResponse retry = graphs.b.service.message(confirm(conversationId, "booking-redis-outage", 1, "CONFIRM_BOOKING"));
        assertThat(retry.responseCategory()).isEqualTo("BOOKING_CONFIRMED");
        entityManager.clear();
        assertThat(appointmentRepository.findAll().stream()
                .filter(row -> fixture.currentPatientId.equals(row.getPatientId())).count()).isEqualTo(1);
        assertThat(idempotencyExists(fixture.currentTenant, confirmation.idempotencyKey())).isTrue();
    }

    @Test
    void transactionalFailurePathsDoNotLogSyntheticSensitiveValues() {
        Fixture fixture = fixture(true, false, false);
        String conversationId = "privacy-finalize-" + UUID.randomUUID();
        InstanceGraphs graphs = instanceGraphs(true);
        CancellationConfirmation confirmation = graphs.a.cancellationTool.prepare(summary(fixture), Instant.now()).value();
        seed(instanceGraphs(false).a.store, conversationId, session(conversationId, fixture, null, confirmation, null, null, null));
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            setContext(fixture);
            assertThatThrownBy(() -> graphs.a.service.message(new MessageRequest(conversationId,
                    "PATIENT_SECRET_123 DOCTOR_SECRET_456 APPOINTMENT_SECRET_789 CONFIRMATION_SECRET_ABC", "en",
                    new AivaV2Models.InteractiveActionRequest("CONFIRM_CANCELLATION", null, null),
                    "privacy-turn", 1L))).isInstanceOf(RuntimeException.class);
        } finally {
            root.detachAppender(appender);
            appender.stop();
        }
        String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
        assertThat(logs).doesNotContain("PATIENT_SECRET_123", "DOCTOR_SECRET_456", "APPOINTMENT_SECRET_789",
                "CONFIRMATION_SECRET_ABC", confirmation.appointmentId().toString());
    }

    @Test
    void structuredPayloadsRoundTripWithExplicitSubtypeMetadata() throws Exception {
        List<AivaStructuredResponse.Payload> payloads = List.of(
                new AivaStructuredResponse.BookingStatePayload("READY_FOR_CONFIRMATION"),
                new AivaStructuredResponse.ContextualInformationPayload("TODAY", LocalDate.of(2026, 9, 19), "Doctor"),
                new AivaStructuredResponse.ClarificationPayload("AMBIGUOUS_DATE", List.of("date")),
                new AivaStructuredResponse.ProviderNotFoundPayload("Doctor", null),
                new AivaStructuredResponse.AvailabilityPayload("Doctor", LocalDate.of(2026, 9, 20), List.of(), false, null),
                new AivaStructuredResponse.BookingConfirmationPayload("Doctor", LocalDate.of(2026, 9, 20), LocalTime.of(9, 30)),
                new AivaStructuredResponse.CancellationSuccessPayload(null),
                new AivaStructuredResponse.RescheduleSuccessPayload("Doctor", LocalDate.of(2026, 9, 20),
                        LocalTime.of(9, 0), LocalDate.of(2026, 9, 21), LocalTime.of(9, 30)));
        for (AivaStructuredResponse.Payload payload : payloads) {
            AivaStructuredResponse response = AivaStructuredResponse.of(AivaStructuredResponse.ResponseType.CLARIFICATION,
                    payload, List.of());
            AivaStructuredResponse roundTrip = objectMapper.readValue(objectMapper.writeValueAsString(response), AivaStructuredResponse.class);
            assertThat(roundTrip.payload()).isEqualTo(payload);
        }
        String unknown = "{\"type\":\"CLARIFICATION\",\"payload\":{\"payloadType\":\"not-allowed\"}}";
        assertThatThrownBy(() -> objectMapper.readValue(unknown, AivaStructuredResponse.class))
                .isInstanceOf(Exception.class);
    }

    private Graphs graphs(boolean failFinalize) {
        StringRedisTemplate redis = redisTemplate();
        if (failFinalize) redis = failingFinalizeRedis(redis);
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.fixed(NOW, ZoneOffset.UTC), redis, objectMapper, "redis");
        AivaV2BookingTools booking = new AivaV2BookingTools(patientPortalService, null, clinicTimeZoneResolver, Clock.fixed(NOW, ZoneOffset.UTC));
        AivaV2CancellationTool cancellation = new AivaV2CancellationTool(patientPortalService);
        AivaV2RescheduleTools reschedule = new AivaV2RescheduleTools(patientPortalService, booking, Clock.fixed(NOW, ZoneOffset.UTC));
        AivaV2AppointmentLookupTool lookup = new AivaV2AppointmentLookupTool(patientPortalService);
        AivaV2TransactionalKernel kernel = new AivaV2TransactionalKernel(booking, lookup, cancellation, reschedule, Clock.fixed(NOW, ZoneOffset.UTC));
        AivaV2ConversationDecisionGateway gateway = (text, language, context) -> new ConversationDecision(
                "1.0", AivaV2Models.DialogAct.CONFIRM, Operation.UNKNOWN, null, null,
                ConfirmationPolarity.NONE, AivaV2Models.TopicAction.CONTINUE, language, 1, "TEST", false, 0);
        AivaV2ConversationService service = new AivaV2ConversationService(gateway, kernel, store,
                patientPortalService, clinicTimeZoneResolver, Clock.fixed(NOW, ZoneOffset.UTC), new AivaResponseRenderer());
        return new Graphs(service, store, booking, cancellation, reschedule);
    }

    private InstanceGraphs instanceGraphs(boolean failFinalize) {
        return new InstanceGraphs(graphs(failFinalize), graphs(false));
    }

    private StringRedisTemplate redisTemplate() {
        var factory = new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }

    private StringRedisTemplate failingFinalizeRedis(StringRedisTemplate delegate) {
        StringRedisTemplate spy = org.mockito.Mockito.spy(delegate);
        AtomicInteger executeCount = new AtomicInteger();
        AtomicBoolean failed = new AtomicBoolean();
        org.mockito.Mockito.doAnswer(invocation -> {
            if (executeCount.incrementAndGet() == 2 && failed.compareAndSet(false, true)) {
                throw new IllegalStateException("test Redis finalization failure");
            }
            return invocation.callRealMethod();
        }).when(spy).execute(any(RedisScript.class), any(List.class), any(Object[].class));
        return spy;
    }

    private Graphs graphsWithRedisUnavailableAfterAdmission() {
        StringRedisTemplate unavailableAfterAdmission = redisUnavailableAfterAdmission(redisTemplate());
        AivaV2SessionStore store = new AivaV2SessionStore(Clock.fixed(NOW, ZoneOffset.UTC), unavailableAfterAdmission,
                objectMapper, "redis");
        AivaV2BookingTools booking = new AivaV2BookingTools(patientPortalService, null, clinicTimeZoneResolver,
                Clock.fixed(NOW, ZoneOffset.UTC));
        AivaV2CancellationTool cancellation = new AivaV2CancellationTool(patientPortalService);
        AivaV2RescheduleTools reschedule = new AivaV2RescheduleTools(patientPortalService, booking,
                Clock.fixed(NOW, ZoneOffset.UTC));
        AivaV2AppointmentLookupTool lookup = new AivaV2AppointmentLookupTool(patientPortalService);
        AivaV2TransactionalKernel kernel = new AivaV2TransactionalKernel(booking, lookup, cancellation, reschedule,
                Clock.fixed(NOW, ZoneOffset.UTC));
        AivaV2ConversationDecisionGateway gateway = (text, language, context) -> new ConversationDecision(
                "1.0", AivaV2Models.DialogAct.CONFIRM, Operation.UNKNOWN, null, null,
                ConfirmationPolarity.NONE, AivaV2Models.TopicAction.CONTINUE, language, 1, "TEST", false, 0);
        AivaV2ConversationService service = new AivaV2ConversationService(gateway, kernel, store,
                patientPortalService, clinicTimeZoneResolver, Clock.fixed(NOW, ZoneOffset.UTC), new AivaResponseRenderer());
        return new Graphs(service, store, booking, cancellation, reschedule);
    }

    private StringRedisTemplate redisUnavailableAfterAdmission(StringRedisTemplate delegate) {
        StringRedisTemplate spy = org.mockito.Mockito.spy(delegate);
        AtomicInteger executeCount = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            if (executeCount.incrementAndGet() > 1) {
                throw new IllegalStateException("test Redis unavailable after admission");
            }
            return invocation.callRealMethod();
        }).when(spy).execute(any(RedisScript.class), any(List.class), any(Object[].class));
        return spy;
    }

    private void seed(AivaV2SessionStore store, String conversationId, SessionProjection state) {
        setContext(UUID.fromString(state.tenantId()), state.patientId());
        store.update(key(UUID.fromString(state.tenantId()), state.patientId(), conversationId), ignored -> state);
    }

    private SessionProjection session(String conversationId, Fixture fixture, BookingDraft draft,
                                      CancellationConfirmation cancellation, RescheduleResolution reschedule,
                                      RescheduleConfirmation rescheduleConfirmation, BookingConfirmation booking) {
        return new SessionProjection(conversationId, fixture.currentPatientId, fixture.currentTenant.toString(),
                draft, null, null, fixture.targetAvailability, booking, cancellation, null, reschedule,
                rescheduleConfirmation, null, 1, Instant.now().plusSeconds(1800));
    }

    private MessageRequest confirm(String conversationId, String turn, long sequence, String action) {
        return new MessageRequest(conversationId, "confirm", "en",
                new AivaV2Models.InteractiveActionRequest(action, null, null), turn, sequence);
    }

    private AppointmentSummary summary(Fixture fixture) {
        return new AppointmentSummary(fixture.appointmentId.toString(), "Owner Doctor", "Owner Clinic",
                fixture.sourceDate, fixture.sourceTime, "BOOKED", null, fixture.doctorId.toString(), null,
                fixture.ownerTenant.toString(), null);
    }

    private ProviderCandidate provider(Fixture fixture) {
        return fixture.provider;
    }

    private String key(UUID tenant, UUID patient, String conversation) {
        return tenant + "|" + patient + "|" + conversation;
    }

    private void setContext(Fixture fixture) { setContext(fixture.currentTenant, fixture.currentUserId); }

    private void setContext(UUID tenant, UUID patient) {
        RequestContextHolder.set(new RequestContext(TenantId.of(tenant), patient, "portal-sub",
                Set.of("PATIENT"), "PATIENT", "aiva-real-transactional-test"));
    }

    private AppointmentEntity freshAppointment(UUID tenant, UUID appointmentId) {
        return transactionTemplate.execute(status -> appointmentRepository.findByTenantIdAndId(tenant, appointmentId).orElseThrow());
    }

    private boolean idempotencyExists(UUID tenant, String key) {
        return transactionTemplate.execute(status -> idempotencyKeyRepository
                .findByTenantIdAndIdempotencyKey(tenant, key).isPresent());
    }

    private List<PatientPortalCareAiAppointmentOption> freshUpcoming() {
        return transactionTemplate.execute(status -> patientPortalService.careAiUpcomingAppointmentsAcrossAuthorizedClinics());
    }

    private Fixture fixture(boolean appointment, boolean availability, boolean bookingCurrentTenant) {
        TenantEntity current = TenantEntity.create("current-" + UUID.randomUUID(), "Current Clinic", "TRIAL");
        TenantEntity owner = TenantEntity.create("owner-" + UUID.randomUUID(), "Owner Clinic", "TRIAL");
        transactionTemplate.executeWithoutResult(status -> { entityManager.persist(current); entityManager.persist(owner); });
        PatientEntity currentPatient = PatientEntity.create(current.getId(), "CURRENT-" + UUID.randomUUID());
        PatientEntity ownerPatient = PatientEntity.create(owner.getId(), "OWNER-" + UUID.randomUUID());
        populate(currentPatient, "999" + Math.abs(current.getId().getLeastSignificantBits()));
        populate(ownerPatient, currentPatient.getMobile());
        AppUserEntity currentUser = AppUserEntity.create(current.getId(), "portal-sub", "portal@example.com", "Portal Patient");
        UUID doctorTenant = bookingCurrentTenant ? current.getId() : owner.getId();
        AppUserEntity doctor = AppUserEntity.create(doctorTenant, "doctor-" + UUID.randomUUID(), "doctor@example.com", "Owner Doctor");
        currentUser.setPatientId(currentPatient.getId());
        UUID appointmentId = UUID.randomUUID();
        LocalDate sourceDate = LocalDate.now().plusDays(3);
        LocalDate targetDate = sourceDate.plusDays(1);
        LocalTime sourceTime = LocalTime.of(20, 0);
        LocalTime targetTime = LocalTime.of(9, 30);
        transactionTemplate.executeWithoutResult(status -> {
            patientRepository.saveAll(List.of(currentPatient, ownerPatient));
            appUserRepository.saveAll(List.of(currentUser, doctor));
            ClinicProfileEntity currentProfile = ClinicProfileEntity.create(current.getId());
            currentProfile.update(current.getName(), current.getName(), null, null, "Test Address", null,
                    "Test City", "Test State", "IN", "000000", null, null, null, true, true,
                    "current-" + current.getId());
            ClinicProfileEntity ownerProfile = ClinicProfileEntity.create(owner.getId());
            ownerProfile.update(owner.getName(), owner.getName(), null, null, "Test Address", null,
                    "Test City", "Test State", "IN", "000000", null, null, null, true, true,
                    "owner-" + owner.getId());
            clinicProfileRepository.saveAll(List.of(currentProfile, ownerProfile));
            membershipRepository.save(TenantMembershipEntity.create(doctorTenant, doctor.getId(), "DOCTOR"));
            if (availability) {
                DoctorAvailabilityEntity schedule = DoctorAvailabilityEntity.create(doctorTenant, doctor.getId());
                schedule.update(targetDate.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(10, 0), null, null, 30, 1, true);
                availabilityRepository.save(schedule);
            }
            if (appointment) {
                AppointmentEntity row = AppointmentEntity.create(owner.getId(), ownerPatient.getId(), doctor.getId());
                row.update(sourceDate, sourceTime, null, "Review", AppointmentType.SCHEDULED, AppointmentStatus.BOOKED, null);
                appointmentRepository.save(row);
            }
        });
        when(accessRequestService.listAuthorizedClinics(any())).thenReturn(List.of(
                new PatientPortalAuthorizedClinicRecord(owner.getId(), "owner", "Owner Clinic", ownerPatient.getId(),
                        "Portal Patient", true, "PATIENT_PORTAL_ACCESS_REQUEST")));
        when(appUserProvisioner.upsertAndReturnId(any(), any(), any(), any())).thenReturn(UUID.randomUUID());
        ProviderCandidate provider = new ProviderCandidate("provider-" + doctor.getId(), "provider-" + doctor.getId(),
                doctor.getId().toString(), null, doctorTenant.toString(), null, null, "Owner Doctor", null,
                "Owner Clinic", ProviderSource.CARE_PRIVATE, new ProviderCapabilities(true, true, false, false, false, true, false));
        AvailabilityResult targetAvailability = null;
        AvailabilitySlot targetSlot = null;
        if (availability) {
            String slotSource = String.join("|", "care-slot", doctorTenant.toString(), doctor.getId().toString(),
                    targetDate.toString(), targetTime.toString());
            targetSlot = new AvailabilitySlot("care-slot-" + UUID.nameUUIDFromBytes(slotSource.getBytes()), targetTime,
                    targetTime.plusMinutes(30), targetTime.toString());
            targetAvailability = new AvailabilityResult(UUID.randomUUID(), UUID.randomUUID(), 1,
                    "real-schedule", provider.providerHandle(), provider.doctorId(), provider.clinicId(),
                    targetDate, null, null, "Asia/Kolkata", List.of(targetSlot), null, false,
                    Instant.now(), Instant.now().plusSeconds(300), 0, 50);
        }
        UUID aid = appointment ? transactionTemplate.execute(status -> appointmentRepository.findAll().stream()
                .filter(row -> owner.getId().equals(row.getTenantId()) && ownerPatient.getId().equals(row.getPatientId()))
                .reduce((first, second) -> second).orElseThrow().getId()) : appointmentId;
        return new Fixture(current.getId(), owner.getId(), currentPatient.getId(), ownerPatient.getId(), currentUser.getId(),
                doctor.getId(), aid, sourceDate, sourceTime, targetDate, targetTime, provider, targetAvailability, targetSlot);
    }

    private void populate(PatientEntity patient, String mobile) {
        patient.update("Portal", "Patient", com.deepthoughtnet.clinic.patient.service.model.PatientGender.UNKNOWN,
                null, null, mobile, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
    }

    private record Graphs(AivaV2ConversationService service, AivaV2SessionStore store,
                          AivaV2BookingTools bookingTools, AivaV2CancellationTool cancellationTool,
                          AivaV2RescheduleTools rescheduleTools) { }

    private record InstanceGraphs(Graphs a, Graphs b) { }

    private record Fixture(UUID currentTenant, UUID ownerTenant, UUID currentPatientId, UUID ownerPatientId,
                           UUID currentUserId, UUID doctorId, UUID appointmentId, LocalDate sourceDate, LocalTime sourceTime,
                           LocalDate targetDate, LocalTime targetTime, ProviderCandidate provider,
                           AvailabilityResult targetAvailability, AvailabilitySlot targetSlot) { }
}
