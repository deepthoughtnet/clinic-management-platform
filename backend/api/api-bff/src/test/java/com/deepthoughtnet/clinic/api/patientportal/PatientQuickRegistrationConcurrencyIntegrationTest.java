package com.deepthoughtnet.clinic.api.patientportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.deepthoughtnet.clinic.api.config.PersistenceScanConfig;
import com.deepthoughtnet.clinic.api.support.AbstractPostgresDataJpaTest;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientConflictException;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.patient.service.model.PatientUpsertCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({PersistenceScanConfig.class, PatientService.class,
        PatientQuickRegistrationConcurrencyIntegrationTest.TestBeans.class})
class PatientQuickRegistrationConcurrencyIntegrationTest extends AbstractPostgresDataJpaTest {
    private static final Duration WAIT = Duration.ofSeconds(10);

    @Autowired private PatientService patientService;
    @Autowired private PatientRepository patientRepository;
    @MockBean private AuditEventPublisher auditEventPublisher;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutDownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void concurrentSameTenantSameMobileSerializesRecheckAndCreatesExactlyOnePatient() throws Exception {
        UUID tenant = UUID.randomUUID();
        String mobile = "9876543210";
        CountDownLatch firstSavedInsideTransaction = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertAdvisoryLockHeldByServiceTransaction(tenant, mobile);
            firstSavedInsideTransaction.countDown();
            assertThat(allowFirstCommit.await(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            return UUID.randomUUID();
        }).when(auditEventPublisher).record(any(AuditEventCommand.class));

        CountDownLatch start = new CountDownLatch(1);
        Future<String> first = executor.submit(() -> createOrReuse(start, tenant, mobile, "First"));
        Future<String> second = executor.submit(() -> createOrReuse(start, tenant, mobile, "Second"));
        start.countDown();

        assertThat(firstSavedInsideTransaction.await(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS))
                .isInstanceOf(java.util.concurrent.TimeoutException.class);
        allowFirstCommit.countDown();

        List<String> results = List.of(first.get(WAIT.toSeconds(), TimeUnit.SECONDS),
                second.get(WAIT.toSeconds(), TimeUnit.SECONDS));
        assertThat(results).containsExactlyInAnyOrder("CREATED", "REUSED");
        assertThat(patientRepository.findByTenantIdAndMobileIgnoreCaseAndActiveTrue(tenant, mobile)).hasSize(1);
    }

    @Test
    void sameMobileAcrossTenantsAndDifferentMobilesWithinTenantProceedIndependently() throws Exception {
        assertIndependentCreates(UUID.randomUUID(), "9876543211", UUID.randomUUID(), "9876543211");
        UUID tenant = UUID.randomUUID();
        assertIndependentCreates(tenant, "9876543212", tenant, "9876543213");
    }

    @Test
    void transactionRollbackRemovesPatientAndReleasesAdvisoryLockForRetry() {
        UUID tenant = UUID.randomUUID();
        String mobile = "9876543214";
        doThrow(new IllegalStateException("simulated audit failure"))
                .when(auditEventPublisher).record(any(AuditEventCommand.class));

        assertThatThrownBy(() -> patientService.createVoiceQuickRegistration(tenant, command("Rollback", mobile),
                UUID.randomUUID(), java.time.OffsetDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated audit failure");
        assertThat(patientRepository.findByTenantIdAndMobileIgnoreCaseAndActiveTrue(tenant, mobile)).isEmpty();

        reset(auditEventPublisher);
        PatientRecord retry = patientService.createVoiceQuickRegistration(tenant, command("Retry", mobile),
                UUID.randomUUID(), java.time.OffsetDateTime.now());
        assertThat(retry.mobile()).isEqualTo(mobile);
        assertThat(patientRepository.findByTenantIdAndMobileIgnoreCaseAndActiveTrue(tenant, mobile)).hasSize(1);
    }

    private void assertIndependentCreates(UUID firstTenant, String firstMobile,
            UUID secondTenant, String secondMobile) throws Exception {
        CountDownLatch bothSavedInsideTransactions = new CountDownLatch(2);
        CountDownLatch allowCommit = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            bothSavedInsideTransactions.countDown();
            assertThat(allowCommit.await(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            return UUID.randomUUID();
        }).when(auditEventPublisher).record(any(AuditEventCommand.class));
        CountDownLatch start = new CountDownLatch(1);
        Future<PatientRecord> first = executor.submit(() -> {
            start.await();
            return patientService.createVoiceQuickRegistration(firstTenant, command("First", firstMobile),
                    UUID.randomUUID(), java.time.OffsetDateTime.now());
        });
        Future<PatientRecord> second = executor.submit(() -> {
            start.await();
            return patientService.createVoiceQuickRegistration(secondTenant, command("Second", secondMobile),
                    UUID.randomUUID(), java.time.OffsetDateTime.now());
        });
        start.countDown();

        boolean overlapped = bothSavedInsideTransactions.await(WAIT.toSeconds(), TimeUnit.SECONDS);
        allowCommit.countDown();
        assertThat(overlapped).as("both independent creates reached the save/audit point concurrently").isTrue();
        PatientRecord firstRecord = first.get(WAIT.toSeconds(), TimeUnit.SECONDS);
        PatientRecord secondRecord = second.get(WAIT.toSeconds(), TimeUnit.SECONDS);
        assertThat(firstRecord.id()).isNotEqualTo(secondRecord.id());
        assertThat(firstRecord.tenantId()).isEqualTo(firstTenant);
        assertThat(secondRecord.tenantId()).isEqualTo(secondTenant);
        assertThat(patientRepository.findByTenantIdAndMobileIgnoreCaseAndActiveTrue(firstTenant, firstMobile))
                .hasSize(1);
        assertThat(patientRepository.findByTenantIdAndMobileIgnoreCaseAndActiveTrue(secondTenant, secondMobile))
                .hasSize(1);
        reset(auditEventPublisher);
    }

    private String createOrReuse(CountDownLatch start, UUID tenant, String mobile, String name)
            throws InterruptedException, ExecutionException {
        start.await();
        try {
            patientService.createVoiceQuickRegistration(tenant, command(name, mobile), UUID.randomUUID(),
                    java.time.OffsetDateTime.now());
            return "CREATED";
        } catch (PatientConflictException conflict) {
            List<PatientRecord> matches = patientService.findActiveByTenantAndMobile(tenant, mobile);
            if (matches.size() == 1) return "REUSED";
            throw conflict;
        }
    }

    private PatientUpsertCommand command(String name, String mobile) {
        return new PatientUpsertCommand(name, null, PatientGender.UNKNOWN, null, 42, mobile, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, true);
    }

    private void assertAdvisoryLockHeldByServiceTransaction(UUID tenant, String mobile) throws Exception {
        try (Connection connection = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
             PreparedStatement statement = connection.prepareStatement(
                     "select pg_try_advisory_xact_lock(hashtextextended(?, 0))")) {
            statement.setString(1, tenant + ":" + mobile);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBoolean(1)).as("service transaction retains its xact lock through save").isFalse();
            }
        }
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
