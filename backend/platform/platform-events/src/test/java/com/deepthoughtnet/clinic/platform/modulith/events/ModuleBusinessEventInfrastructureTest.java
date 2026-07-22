package com.deepthoughtnet.clinic.platform.modulith.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deepthoughtnet.clinic.platform.modulith.events.db.ModuleBusinessEventEntity;
import com.deepthoughtnet.clinic.platform.modulith.events.db.ModuleBusinessEventListenerExecutionEntity;
import com.deepthoughtnet.clinic.platform.modulith.events.db.ModuleBusinessEventListenerExecutionRepository;
import com.deepthoughtnet.clinic.platform.modulith.events.db.ModuleBusinessEventRepository;
import com.deepthoughtnet.clinic.platform.modulith.events.config.PlatformEventsPersistenceConfiguration;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventStatus;
import com.deepthoughtnet.clinic.platform.modulith.events.model.AppointmentBookedEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.model.LabReportPublishedEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.model.LeadConvertedEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListenerStatus;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventProcessingException;
import com.deepthoughtnet.clinic.platform.modulith.events.service.ModuleBusinessEventListenerRegistry;
import com.deepthoughtnet.clinic.platform.modulith.events.service.ModuleBusinessEventMetrics;
import com.deepthoughtnet.clinic.platform.modulith.events.service.ModuleBusinessEventProcessingService;
import com.deepthoughtnet.clinic.platform.modulith.events.service.ModuleBusinessEventPublisherService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        classes = ModuleBusinessEventInfrastructureTest.TestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:module_events;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.show-sql=false",
                "spring.flyway.enabled=false",
                "jeevanam.platform.events.dispatch-fixed-delay=PT1S",
                "jeevanam.platform.events.retry-backoff=PT1S",
                "jeevanam.platform.events.stale-processing-timeout=PT1S",
                "jeevanam.platform.events.max-attempts=3"
        }
)
@ActiveProfiles("test")
class ModuleBusinessEventInfrastructureTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID APPOINTMENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LAB_ORDER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID LEAD_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID PATIENT_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Autowired
    private ModuleBusinessEventPublisherService publisher;
    @Autowired
    private ModuleBusinessEventProcessingService processingService;
    @Autowired
    private ModuleBusinessEventRepository eventRepository;
    @Autowired
    private ModuleBusinessEventListenerExecutionRepository listenerRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private RollbackingPublisher rollbackingPublisher;
    @Autowired
    private SuccessfulAppointmentBookedListener appointmentBookedListener;
    @Autowired
    private SuccessfulLabReportPublishedListener labReportPublishedListener;
    @Autowired
    private SuccessfulLeadConvertedListener leadConvertedListener;
    @Autowired
    private FailingLeadConvertedListener failingLeadConvertedListener;
    @Autowired
    private ModuleBusinessEventProperties properties;

    @BeforeEach
    void resetState() {
        MDC.clear();
        appointmentBookedListener.reset();
        labReportPublishedListener.reset();
        leadConvertedListener.reset();
        failingLeadConvertedListener.reset();
        jdbcTemplate.update("delete from module_business_event_listener_jobs");
        jdbcTemplate.update("delete from module_business_events");
        properties.setMaxAttempts(3);
        properties.setRetryBackoff("PT1S");
        properties.setStaleProcessingTimeout("PT1S");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void publishCreatesDurableEventAndListenerJobs() {
        MDC.put("correlationId", "corr-commit");
        UUID eventId = publisher.publish(ModuleBusinessEvents.appointmentBooked(
                TENANT_ID,
                APPOINTMENT_ID,
                PATIENT_ID,
                UUID.randomUUID(),
                LocalDate.of(2026, 7, 22),
                LocalTime.of(11, 0),
                "BOOKED",
                "SCHEDULED",
                ACTOR_ID
        ));

        ModuleBusinessEventEntity saved = eventRepository.findById(eventId).orElseThrow();
        assertThat(saved.getEventType()).isEqualTo("APPOINTMENT_BOOKED");
        assertThat(saved.getEventVersion()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(ModuleBusinessEventStatus.PENDING);
        assertThat(saved.getPayloadJson()).contains("\"appointmentId\":\"" + APPOINTMENT_ID + "\"");
        assertThat(listenerRepository.findByTenantIdAndEventIdOrderByCreatedAtAsc(TENANT_ID, eventId)).hasSize(1);
        assertThat(appointmentBookedListener.handledCount()).isZero();
    }

    @Test
    void rollbackDoesNotPersistEventOrDispatchListenerJobs() {
        assertThatThrownBy(() -> rollbackingPublisher.publishThenFail(ModuleBusinessEvents.labReportPublished(
                TENANT_ID,
                LAB_ORDER_ID,
                PATIENT_ID,
                UUID.randomUUID(),
                "report.pdf",
                "DELIVERED",
                ACTOR_ID
        ))).isInstanceOf(IllegalStateException.class).hasMessageContaining("rollback");

        assertThat(eventRepository.count()).isZero();
        assertThat(listenerRepository.count()).isZero();
        assertThat(labReportPublishedListener.handledCount()).isZero();
    }

    @Test
    void committedPublishAndDispatchAreIdempotent() {
        MDC.put("correlationId", "corr-lead");
        UUID eventId = publisher.publish(ModuleBusinessEvents.leadConverted(
                TENANT_ID,
                LEAD_ID,
                PATIENT_ID,
                true,
                null,
                ACTOR_ID
        ));

        UUID duplicatePublish = publisher.publish(ModuleBusinessEvents.leadConverted(
                TENANT_ID,
                LEAD_ID,
                UUID.randomUUID(),
                false,
                UUID.randomUUID(),
                UUID.randomUUID()
        ));

        assertThat(duplicatePublish).isEqualTo(eventId);
        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(listenerRepository.count()).isEqualTo(2);

        int processed = processingService.dispatchRunnable(Integer.MAX_VALUE);
        assertThat(processed).isEqualTo(2);
        assertThat(leadConvertedListener.handledCount()).isEqualTo(1);
        assertThat(failingLeadConvertedListener.handledCount()).isEqualTo(1);

        List<ModuleBusinessEventListenerExecutionEntity> rows = listenerRepository.findByTenantIdAndEventIdOrderByCreatedAtAsc(TENANT_ID, eventId);
        assertThat(rows).hasSize(2);
        assertThat(rows.stream()
                .filter(row -> row.getListenerName().equals("leadConvertedDiagnostics"))
                .findFirst()
                .orElseThrow()
                .getStatus()).isEqualTo(ModuleBusinessEventListenerStatus.SUCCEEDED.name());
        assertThat(rows.stream()
                .filter(row -> row.getListenerName().equals("leadConvertedRetryDiagnostics"))
                .findFirst()
                .orElseThrow()
                .getStatus()).isEqualTo(ModuleBusinessEventListenerStatus.RETRY_SCHEDULED.name());

        processingService.processOne(rows.get(0).getId());
        assertThat(leadConvertedListener.handledCount()).isEqualTo(1);
    }

    @Test
    void oneFailedListenerDoesNotBlockAnotherAndRetryThenDeadLetterAreTracked() {
        MDC.put("correlationId", "corr-retry");
        UUID eventId = publisher.publish(ModuleBusinessEvents.leadConverted(
                TENANT_ID,
                LEAD_ID,
                PATIENT_ID,
                true,
                null,
                ACTOR_ID
        ));

        processingService.dispatchRunnable(Integer.MAX_VALUE);

        List<ModuleBusinessEventListenerExecutionEntity> rows = listenerRepository.findByTenantIdAndEventIdOrderByCreatedAtAsc(TENANT_ID, eventId);
        assertThat(rows).hasSize(2);
        ModuleBusinessEventListenerExecutionEntity successRow = rows.stream()
                .filter(row -> row.getListenerName().equals("leadConvertedDiagnostics"))
                .findFirst()
                .orElseThrow();
        ModuleBusinessEventListenerExecutionEntity failingRow = rows.stream()
                .filter(row -> row.getListenerName().equals("leadConvertedRetryDiagnostics"))
                .findFirst()
                .orElseThrow();
        assertThat(successRow.getStatus()).isEqualTo(ModuleBusinessEventListenerStatus.SUCCEEDED.name());
        assertThat(failingRow.getStatus()).isEqualTo(ModuleBusinessEventListenerStatus.RETRY_SCHEDULED.name());
        assertThat(failingRow.getAttemptCount()).isEqualTo(1);
        assertThat(failingRow.getNextAttemptAt()).isNotNull();

        properties.setMaxAttempts(1);
        UUID deadLetterEventId = publisher.publish(ModuleBusinessEvents.leadConverted(
                TENANT_ID,
                UUID.randomUUID(),
                PATIENT_ID,
                false,
                null,
                ACTOR_ID
        ));
        processingService.dispatchRunnable(Integer.MAX_VALUE);

        ModuleBusinessEventListenerExecutionEntity deadLetterRow = listenerRepository
                .findByTenantIdAndEventIdOrderByCreatedAtAsc(TENANT_ID, deadLetterEventId)
                .stream()
                .filter(row -> row.getListenerName().equals("leadConvertedRetryDiagnostics"))
                .findFirst()
                .orElseThrow();
        assertThat(deadLetterRow.getStatus()).isEqualTo(ModuleBusinessEventListenerStatus.DEAD_LETTERED.name());
    }

    @Test
    void staleProcessingRowsAreRecoveredOnRestart() throws Exception {
        UUID eventId = publisher.publish(ModuleBusinessEvents.appointmentBooked(
                TENANT_ID,
                APPOINTMENT_ID,
                PATIENT_ID,
                UUID.randomUUID(),
                LocalDate.of(2026, 7, 22),
                LocalTime.of(11, 0),
                "BOOKED",
                "SCHEDULED",
                ACTOR_ID
        ));

        ModuleBusinessEventListenerExecutionEntity row = listenerRepository.findByTenantIdAndEventIdOrderByCreatedAtAsc(TENANT_ID, eventId).get(0);
        row.markProcessing();
        listenerRepository.saveAndFlush(row);
        jdbcTemplate.update(
                "update module_business_event_listener_jobs set updated_at = ? where id = ?",
                Timestamp.from(OffsetDateTime.now().minusMinutes(10).toInstant()),
                row.getId()
        );

        int reclaimed = processingService.recoverStaleProcessing();
        assertThat(reclaimed).isEqualTo(1);
        ModuleBusinessEventListenerExecutionEntity recovered = listenerRepository.findById(row.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(ModuleBusinessEventListenerStatus.PENDING.name());
    }

    @Test
    void minimalDiagnosticPayloadDoesNotExposeBusinessNames() {
        UUID eventId = publisher.publish(ModuleBusinessEvents.labReportPublished(
                TENANT_ID,
                LAB_ORDER_ID,
                PATIENT_ID,
                UUID.randomUUID(),
                "report.pdf",
                "DELIVERED",
                ACTOR_ID
        ));

        String payloadJson = eventRepository.findById(eventId).orElseThrow().getPayloadJson();
        assertThat(payloadJson).contains("\"reportFilename\":\"report.pdf\"");
        assertThat(payloadJson).doesNotContain("Sharma");
        assertThat(payloadJson).doesNotContain("987");
    }

    @Test
    void tenantScopedLookupDoesNotLeakCrossTenantRecords() {
        UUID eventId = publisher.publish(ModuleBusinessEvents.appointmentBooked(
                TENANT_ID,
                APPOINTMENT_ID,
                PATIENT_ID,
                UUID.randomUUID(),
                LocalDate.of(2026, 7, 22),
                LocalTime.of(11, 0),
                "BOOKED",
                "SCHEDULED",
                ACTOR_ID
        ));

        assertThat(eventRepository.findByTenantIdAndId(TENANT_ID, eventId)).isPresent();
        assertThat(eventRepository.findByTenantIdAndId(UUID.randomUUID(), eventId)).isEmpty();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
            PlatformEventsPersistenceConfiguration.class,
            ModuleBusinessEventProperties.class,
            ModuleBusinessEventMetrics.class,
            ModuleBusinessEventListenerRegistry.class,
            ModuleBusinessEventPublisherService.class,
            ModuleBusinessEventProcessingService.class,
            RollbackingPublisher.class,
            TestListenersConfig.class
    })
    static class TestApplication {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }

    @Service
    static class RollbackingPublisher {
        private final ModuleBusinessEventPublisherService publisher;

        RollbackingPublisher(ModuleBusinessEventPublisherService publisher) {
            this.publisher = publisher;
        }

        @Transactional
        public void publishThenFail(ModuleBusinessEvent event) {
            publisher.publish(event);
            throw new IllegalStateException("rollback requested");
        }
    }

    @TestConfiguration
    static class TestListenersConfig {
        @Bean
        SuccessfulAppointmentBookedListener appointmentBookedListener() {
            return new SuccessfulAppointmentBookedListener();
        }

        @Bean
        SuccessfulLabReportPublishedListener labReportPublishedListener() {
            return new SuccessfulLabReportPublishedListener();
        }

        @Bean
        SuccessfulLeadConvertedListener leadConvertedListener() {
            return new SuccessfulLeadConvertedListener();
        }

        @Bean
        FailingLeadConvertedListener failingLeadConvertedListener() {
            return new FailingLeadConvertedListener();
        }
    }

    static final class SuccessfulAppointmentBookedListener implements ModuleBusinessEventListener<AppointmentBookedEvent> {
        private final AtomicInteger handled = new AtomicInteger();

        @Override
        public String listenerName() {
            return "appointmentBookedDiagnostics";
        }

        @Override
        public String listenerModule() {
            return "APPOINTMENT";
        }

        @Override
        public String eventType() {
            return "APPOINTMENT_BOOKED";
        }

        @Override
        public Class<AppointmentBookedEvent> eventClass() {
            return AppointmentBookedEvent.class;
        }

        @Override
        public void handle(AppointmentBookedEvent event) {
            handled.incrementAndGet();
        }

        int handledCount() {
            return handled.get();
        }

        void reset() {
            handled.set(0);
        }
    }

    static final class SuccessfulLabReportPublishedListener implements ModuleBusinessEventListener<LabReportPublishedEvent> {
        private final AtomicInteger handled = new AtomicInteger();

        @Override
        public String listenerName() {
            return "labReportPublishedDiagnostics";
        }

        @Override
        public String listenerModule() {
            return "LAB";
        }

        @Override
        public String eventType() {
            return "LAB_REPORT_PUBLISHED";
        }

        @Override
        public Class<LabReportPublishedEvent> eventClass() {
            return LabReportPublishedEvent.class;
        }

        @Override
        public void handle(LabReportPublishedEvent event) {
            handled.incrementAndGet();
        }

        int handledCount() {
            return handled.get();
        }

        void reset() {
            handled.set(0);
        }
    }

    static final class SuccessfulLeadConvertedListener implements ModuleBusinessEventListener<LeadConvertedEvent> {
        private final AtomicInteger handled = new AtomicInteger();

        @Override
        public String listenerName() {
            return "leadConvertedDiagnostics";
        }

        @Override
        public String listenerModule() {
            return "CAREPILOT";
        }

        @Override
        public String eventType() {
            return "LEAD_CONVERTED";
        }

        @Override
        public Class<LeadConvertedEvent> eventClass() {
            return LeadConvertedEvent.class;
        }

        @Override
        public void handle(LeadConvertedEvent event) {
            handled.incrementAndGet();
        }

        int handledCount() {
            return handled.get();
        }

        void reset() {
            handled.set(0);
        }
    }

    static final class FailingLeadConvertedListener implements ModuleBusinessEventListener<LeadConvertedEvent> {
        private final AtomicInteger handled = new AtomicInteger();

        @Override
        public String listenerName() {
            return "leadConvertedRetryDiagnostics";
        }

        @Override
        public String listenerModule() {
            return "CAREPILOT";
        }

        @Override
        public String eventType() {
            return "LEAD_CONVERTED";
        }

        @Override
        public Class<LeadConvertedEvent> eventClass() {
            return LeadConvertedEvent.class;
        }

        @Override
        public void handle(LeadConvertedEvent event) {
            handled.incrementAndGet();
            throw ModuleBusinessEventProcessingException.retryable("transient listener failure", new IllegalStateException("boom"));
        }

        int handledCount() {
            return handled.get();
        }

        void reset() {
            handled.set(0);
        }
    }

}
