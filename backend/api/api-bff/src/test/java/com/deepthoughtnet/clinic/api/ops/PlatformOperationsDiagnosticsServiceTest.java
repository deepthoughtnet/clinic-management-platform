package com.deepthoughtnet.clinic.api.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.api.carepilot.CarePilotAiCallSchedulerMonitor;
import com.deepthoughtnet.clinic.api.carepilot.CarePilotRuntimeSchedulerMonitor;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalDocumentTextExtractionResult;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.service.ClinicalDocumentTextExtractionService;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.service.DeterministicLabFactParser;
import com.deepthoughtnet.clinic.api.config.db.DatabaseSchemaIntegrityReport;
import com.deepthoughtnet.clinic.api.config.db.DatabaseSchemaIntegrityService;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.ComponentHealthStatus;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformOperationsDiagnosticResponse;
import com.deepthoughtnet.clinic.llm.spi.LlmClient;
import com.deepthoughtnet.clinic.llm.spi.LlmResponse;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.storage.minio.MinioStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

class PlatformOperationsDiagnosticsServiceTest {

    @Test
    void invalidComponentIsRejected() {
        PlatformOperationsDiagnosticsService service = newService();

        assertThatThrownBy(() -> service.test("nope"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Unsupported health component: nope");
    }

    @Test
    void cooldownReturnsCachedResponseWithoutRerunningProbe() {
        PlatformOperationsDiagnosticsService service = newService();
        LlmClient client = mock(LlmClient.class);
        ReflectionTestUtils.setField(service, "geminiLlmClient", geminiProvider(client));
        PlatformOperationsDiagnosticStateStore stateStore = stateStore(service);
        PlatformOperationsDiagnosticResponse cached = new PlatformOperationsDiagnosticResponse(
                "Gemini",
                true,
                ComponentHealthStatus.HEALTHY,
                "Manual diagnostic succeeded",
                12L,
                Instant.now().minusSeconds(5),
                Map.of("model", "gemini-2.5-flash")
        );
        stateStore.record(cached);

        PlatformOperationsDiagnosticResponse response = service.test("gemini");

        assertThat(response).isSameAs(cached);
        verify(client, never()).generate(any());
    }

    @Test
    void databaseProbeReportsSuccessAndFailure() {
        PlatformOperationsDiagnosticsService healthyService = newService();
        PlatformOperationsDiagnosticResponse success = healthyService.test("database");
        assertThat(success.success()).isTrue();
        assertThat(success.status()).isEqualTo(ComponentHealthStatus.HEALTHY);
        assertThat(success.details()).containsEntry("validationQuery", 1);

        PlatformOperationsDiagnosticsService failingService = newService();
        JdbcTemplate jdbcTemplate = jdbcTemplate(failingService);
        when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenThrow(new IllegalStateException("db down"));
        PlatformOperationsDiagnosticResponse failure = failingService.test("database");
        assertThat(failure.success()).isFalse();
        assertThat(failure.status()).isEqualTo(ComponentHealthStatus.CRITICAL);
    }

    @Test
    void redisProbeReportsSuccessAndFailure() {
        PlatformOperationsDiagnosticsService healthyService = newService();
        PlatformOperationsDiagnosticResponse success = healthyService.test("redis");
        assertThat(success.success()).isTrue();
        assertThat(success.status()).isEqualTo(ComponentHealthStatus.HEALTHY);

        PlatformOperationsDiagnosticsService failingService = newService();
        ObjectProvider<RedisConnectionFactory> provider = redisProviderOf(failingService);
        RedisConnectionFactory failureFactory = redisFactoryFailure();
        when(provider.getIfAvailable()).thenReturn(failureFactory);
        PlatformOperationsDiagnosticResponse failure = failingService.test("redis");
        assertThat(failure.success()).isFalse();
        assertThat(failure.status()).isEqualTo(ComponentHealthStatus.CRITICAL);
    }

    @Test
    void keycloakProbeHandlesSuccessTimeoutAndFailure() throws Exception {
        PlatformOperationsDiagnosticsService service = newService();
        installKeycloakProbe(service, 200, """
                {"issuer":"https://keycloak.example/auth/realms/clinic-management","authorization_endpoint":"https://keycloak.example/auth/protocol/openid-connect/auth","token_endpoint":"https://keycloak.example/auth/protocol/openid-connect/token"}
                """);
        PlatformOperationsDiagnosticResponse success = service.test("keycloak");
        assertThat(success.success()).isTrue();
        assertThat(success.status()).isEqualTo(ComponentHealthStatus.HEALTHY);

        PlatformOperationsDiagnosticsService failingService = newService();
        installKeycloakProbe(failingService, 503, "{}");
        PlatformOperationsDiagnosticResponse failure = failingService.test("keycloak");
        assertThat(failure.success()).isFalse();
        assertThat(failure.status()).isEqualTo(ComponentHealthStatus.CRITICAL);

        PlatformOperationsDiagnosticsService timeoutService = newService();
        installKeycloakProbeTimeout(timeoutService);
        PlatformOperationsDiagnosticResponse timeout = timeoutService.test("keycloak");
        assertThat(timeout.success()).isFalse();
        assertThat(timeout.status()).isEqualTo(ComponentHealthStatus.CRITICAL);
    }

    @Test
    void minioProbeWritesReadsAndCleansUp() throws Exception {
        PlatformOperationsDiagnosticsService service = newService();
        MinioClient client = minioClient(service);
        GetObjectResponse getObjectResponse = mock(GetObjectResponse.class);
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(getObjectResponse.readAllBytes()).thenReturn("JEEVANAM_PLATFORM_OPS_HEALTH_CHECK".getBytes());
        when(client.getObject(any(GetObjectArgs.class))).thenReturn(getObjectResponse);

        PlatformOperationsDiagnosticResponse success = service.test("minio");
        assertThat(success.success()).isTrue();
        assertThat(success.status()).isEqualTo(ComponentHealthStatus.HEALTHY);
        verify(client).putObject(any(PutObjectArgs.class));
        verify(client).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void minioCleanupFailureDowngradesToWarning() throws Exception {
        PlatformOperationsDiagnosticsService service = newService();
        MinioClient client = minioClient(service);
        GetObjectResponse getObjectResponse = mock(GetObjectResponse.class);
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(getObjectResponse.readAllBytes()).thenReturn("JEEVANAM_PLATFORM_OPS_HEALTH_CHECK".getBytes());
        when(client.getObject(any(GetObjectArgs.class))).thenReturn(getObjectResponse);
        Mockito.doThrow(new RuntimeException("cleanup failed")).when(client).removeObject(any(RemoveObjectArgs.class));

        PlatformOperationsDiagnosticResponse response = service.test("minio");
        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo(ComponentHealthStatus.WARNING);
        assertThat(response.message()).contains("cleanup failed");
    }

    @Test
    void geminiProbeUsesConfiguredClient() {
        PlatformOperationsDiagnosticsService service = newService();
        LlmClient client = mock(LlmClient.class);
        ReflectionTestUtils.setField(service, "geminiLlmClient", geminiProvider(client));
        when(client.generate(any())).thenReturn(new LlmResponse("GEMINI", "gemini-2.5-flash", "OK", null, "STOP"));

        PlatformOperationsDiagnosticResponse response = service.test("gemini");

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo(ComponentHealthStatus.HEALTHY);
        assertThat(response.details()).containsEntry("model", "gemini-2.5-flash");
    }

    @Test
    void documentAiProbeExercisesSyntheticExtractionPipeline() {
        PlatformOperationsDiagnosticsService service = newService();
        LlmClient client = mock(LlmClient.class);
        when(client.isAvailable()).thenReturn(true);
        ReflectionTestUtils.setField(service, "geminiLlmClient", geminiProvider(client));
        ClinicalDocumentTextExtractionService textExtractionService = mock(ClinicalDocumentTextExtractionService.class);
        when(textExtractionService.extract(any(), any())).thenReturn(new ClinicalDocumentTextExtractionResult(
                "PDFBOX",
                "COMPLETED",
                "Diagnostic Test Document\nPatient: TEST ONLY\nHemoglobin 14.2 g/dL"
        ));
        ReflectionTestUtils.setField(service, "textExtractionService", textExtractionService);
        when(client.generate(any())).thenReturn(new LlmResponse(
                "GEMINI",
                "gemini-2.5-flash",
                """
                        {"labResults":[{"testName":"Hemoglobin","value":"14.2","unit":"g/dL"}],"summary":"ok","parseStatus":"OK"}
                        """.trim(),
                null,
                "STOP"
        ));

        PlatformOperationsDiagnosticResponse response = service.test("document-ai");

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo(ComponentHealthStatus.HEALTHY);
        assertThat(response.details()).containsEntry("hemoglobinMatched", true);
    }

    @Test
    void schedulerProbeUsesCurrentTelemetry() {
        PlatformOperationsDiagnosticsService service = newService();
        PlatformOperationsDiagnosticResponse response = service.test("scheduler");

        assertThat(response.status()).isEqualTo(ComponentHealthStatus.HEALTHY);
        assertThat(response.details()).containsKeys("reminderSchedulerEnabled", "aiCallSchedulerEnabled", "aiNextRunAt");
    }

    @Test
    void backupProbeReturnsUnknownUntilStructuredTelemetryExists() {
        PlatformOperationsDiagnosticsService service = newService();

        PlatformOperationsDiagnosticResponse response = service.test("backups");

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(ComponentHealthStatus.UNKNOWN);
    }

    private PlatformOperationsDiagnosticsService newService() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenReturn(1);

        HikariDataSource dataSource = mock(HikariDataSource.class);
        HikariPoolMXBean pool = mock(HikariPoolMXBean.class);
        when(dataSource.getHikariPoolMXBean()).thenReturn(pool);
        when(pool.getActiveConnections()).thenReturn(2);
        when(pool.getIdleConnections()).thenReturn(8);
        when(dataSource.getMaximumPoolSize()).thenReturn(20);

        DatabaseSchemaIntegrityService schemaService = mock(DatabaseSchemaIntegrityService.class);
        when(schemaService.inspect(true)).thenReturn(new DatabaseSchemaIntegrityReport(true, false, 123, 1L, 1L, 1L, List.of(), Map.of()));

        MinioClient minioClient = mock(MinioClient.class);
        try {
            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
            GetObjectResponse getObjectResponse = mock(GetObjectResponse.class);
            when(getObjectResponse.readAllBytes()).thenReturn("JEEVANAM_PLATFORM_OPS_HEALTH_CHECK".getBytes());
            when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(getObjectResponse);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }

        MinioStorageProperties minioProperties = new MinioStorageProperties();
        minioProperties.setEndpoint("http://localhost:9000");
        minioProperties.setBucket("clinic-documents");
        minioProperties.setAutoCreateBucket(true);

        CarePilotRuntimeSchedulerMonitor reminderMonitor = new CarePilotRuntimeSchedulerMonitor(true);
        reminderMonitor.markGlobalReminderScan(OffsetDateTime.now().minusMinutes(2));

        CarePilotAiCallSchedulerMonitor aiMonitor = new CarePilotAiCallSchedulerMonitor(true, Duration.ofMinutes(5));
        aiMonitor.markRun(OffsetDateTime.now().minusMinutes(1), 3, 3, 0, 0, 10L);

        Environment environment = mock(Environment.class);
        when(environment.getProperty("clinic.keycloak.admin.serverUrl")).thenReturn("https://keycloak.example/auth");
        when(environment.getProperty("clinic.keycloak.admin.targetRealm")).thenReturn("clinic-management");
        when(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri")).thenReturn("https://keycloak.example/auth/realms/clinic-management");
        when(environment.getProperty("jeevanam.api-version")).thenReturn("1.0.0");

        PlatformOpsReleaseProperties releaseProperties = new PlatformOpsReleaseProperties(
                "jeevanam-prod-2026-09-05-01",
                "6f79bc84",
                "2026-09-05T12:00:00Z",
                "2026-09-05T12:05:00Z",
                "PRODUCTION"
        );

        SchedulerLockMonitor schedulerLockMonitor = new SchedulerLockMonitor();
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        ClinicalDocumentTextExtractionService textExtractionService = new ClinicalDocumentTextExtractionService(mock(ObjectProvider.class));
        DeterministicLabFactParser parser = new DeterministicLabFactParser();
        PlatformOperationsDiagnosticsService service = new PlatformOperationsDiagnosticsService(
                new PlatformOperationsDiagnosticStateStore(),
                jdbcTemplate,
                dataSource,
                redisProvider(redisFactorySuccess()),
                minioClient,
                minioProperties,
                schemaService,
                reminderMonitor,
                aiMonitor,
                schedulerLockMonitor,
                geminiProvider(null),
                groqProvider(null),
                textExtractionService,
                parser,
                auditEventPublisher,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                environment
        );
        ReflectionTestUtils.setField(service, "httpClient", mock(HttpClient.class));
        return service;
    }

    private void installKeycloakProbe(PlatformOperationsDiagnosticsService service, int statusCode, String body) throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        ReflectionTestUtils.setField(service, "httpClient", httpClient);
    }

    private void installKeycloakProbeTimeout(PlatformOperationsDiagnosticsService service) throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new HttpTimeoutException("timeout"));
        ReflectionTestUtils.setField(service, "httpClient", httpClient);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<RedisConnectionFactory> redisProvider(RedisConnectionFactory factory) {
        ObjectProvider<RedisConnectionFactory> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(factory);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<RedisConnectionFactory> redisProviderOf(PlatformOperationsDiagnosticsService service) {
        return (ObjectProvider<RedisConnectionFactory>) ReflectionTestUtils.getField(service, "redisConnectionFactoryProvider");
    }

    private RedisConnectionFactory redisFactorySuccess() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        try {
            when(connection.ping()).thenReturn("PONG");
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        when(factory.getConnection()).thenReturn(connection);
        return factory;
    }

    private RedisConnectionFactory redisFactoryFailure() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenThrow(new IllegalStateException("redis down"));
        return factory;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<LlmClient> geminiProvider(LlmClient client) {
        ObjectProvider<LlmClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<LlmClient> groqProvider(LlmClient client) {
        ObjectProvider<LlmClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        return provider;
    }

    private PlatformOperationsDiagnosticStateStore stateStore(PlatformOperationsDiagnosticsService service) {
        return (PlatformOperationsDiagnosticStateStore) ReflectionTestUtils.getField(service, "stateStore");
    }

    private JdbcTemplate jdbcTemplate(PlatformOperationsDiagnosticsService service) {
        return (JdbcTemplate) ReflectionTestUtils.getField(service, "jdbcTemplate");
    }

    private MinioClient minioClient(PlatformOperationsDiagnosticsService service) {
        return (MinioClient) ReflectionTestUtils.getField(service, "minioClient");
    }
}
