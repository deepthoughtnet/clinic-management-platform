package com.deepthoughtnet.clinic.api.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiInvocationLogEntity;
import com.deepthoughtnet.clinic.api.config.db.DatabaseSchemaIntegrityReport;
import com.deepthoughtnet.clinic.api.config.db.DatabaseSchemaIntegrityService;
import com.deepthoughtnet.clinic.api.carepilot.CarePilotAiCallSchedulerMonitor;
import com.deepthoughtnet.clinic.api.carepilot.CarePilotRuntimeSchedulerMonitor;
import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiInvocationLogRepository;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentRepository;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.ComponentHealthResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.ComponentHealthStatus;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformOperationsOverviewResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformOperationsReleaseResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformOperationsRuntimeResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformOperationsOverviewSummaryResponse;
import com.deepthoughtnet.clinic.storage.minio.MinioStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import com.deepthoughtnet.clinic.llm.spi.LlmClient;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

class PlatformOperationsOverviewServiceTest {
    @Test
    void overviewReturnsHealthyMatrixAndLeavesUnsupportedTelemetryUnknown() throws Exception {
        PlatformOperationsOverviewService service = newService(
                redisConnectionFactory(true),
                true,
                false,
                0
        );
        installKeycloakProbe(service, 200);

        PlatformOperationsOverviewResponse response = service.overview();
        Map<String, ComponentHealthResponse> components = indexByComponent(response);

        assertThat(response.summary().overallStatus()).isEqualTo(ComponentHealthStatus.HEALTHY);
        assertThat(component(components, "API").status()).isEqualTo(ComponentHealthStatus.HEALTHY);
        assertThat(component(components, "DB").status()).isEqualTo(ComponentHealthStatus.HEALTHY);
        assertThat(component(components, "Redis").status()).isEqualTo(ComponentHealthStatus.HEALTHY);
        assertThat(component(components, "Keycloak").status()).isEqualTo(ComponentHealthStatus.HEALTHY);
        assertThat(component(components, "MinIO").status()).isEqualTo(ComponentHealthStatus.HEALTHY);
        assertThat(component(components, "Scheduler").status()).isEqualTo(ComponentHealthStatus.HEALTHY);
        assertThat(component(components, "Backups").status()).isEqualTo(ComponentHealthStatus.UNKNOWN);
        assertThat(component(components, "Gemini").status()).isEqualTo(ComponentHealthStatus.UNKNOWN);
        assertThat(component(components, "Groq").status()).isEqualTo(ComponentHealthStatus.UNKNOWN);
        assertThat(component(components, "Document AI").status()).isEqualTo(ComponentHealthStatus.UNKNOWN);
        assertThat(component(components, "Release").status()).isEqualTo(ComponentHealthStatus.HEALTHY);
        assertThat(response.release().releaseTag()).isEqualTo("jeevanam-prod-2026-09-05-01");
        assertThat(response.release().gitCommit()).isEqualTo("6f79bc84");
        assertThat(response.release().environment()).isEqualTo("PRODUCTION");
        assertThat(response.runtime().applicationUp()).isTrue();
        assertThat(response.runtime().applicationStatus()).isEqualTo("UP");
        assertThat(response.aiSummary().totalCalls()).isZero();
    }

    @Test
    void overviewIsolatedComponentFailuresStillReturnResponse() throws Exception {
        PlatformOperationsOverviewService service = newService(
                redisConnectionFactory(false),
                false,
                true,
                0
        );
        installKeycloakProbe(service, 500);

        PlatformOperationsOverviewResponse response = service.overview();
        Map<String, ComponentHealthResponse> components = indexByComponent(response);

        assertThat(response.summary().overallStatus()).isEqualTo(ComponentHealthStatus.CRITICAL);
        assertThat(component(components, "API").status()).isEqualTo(ComponentHealthStatus.HEALTHY);
        assertThat(component(components, "DB").status()).isEqualTo(ComponentHealthStatus.HEALTHY);
        assertThat(component(components, "Redis").status()).isEqualTo(ComponentHealthStatus.CRITICAL);
        assertThat(component(components, "Keycloak").status()).isEqualTo(ComponentHealthStatus.CRITICAL);
        assertThat(component(components, "MinIO").status()).isEqualTo(ComponentHealthStatus.WARNING);
        assertThat(component(components, "Backups").status()).isEqualTo(ComponentHealthStatus.UNKNOWN);
        assertThat(component(components, "Release").status()).isEqualTo(ComponentHealthStatus.HEALTHY);
    }

    @Test
    void keycloakTimeoutFallsBackToCriticalWithoutFailingOverview() throws Exception {
        PlatformOperationsOverviewService service = newService(
                redisConnectionFactory(true),
                true,
                true,
                0
        );
        installKeycloakProbeTimeout(service);

        PlatformOperationsOverviewResponse response = service.overview();
        ComponentHealthResponse keycloakComponent = component(indexByComponent(response), "Keycloak");

        assertThat(keycloakComponent.status()).isEqualTo(ComponentHealthStatus.CRITICAL);
        assertThat(keycloakComponent.reason()).contains("probe failed");
    }

    @Test
    void schedulerStaleHeartbeatBecomesCritical() throws Exception {
        PlatformOperationsOverviewService service = newServiceWithTelemetry(
                redisConnectionFactory(true),
                true,
                true,
                true,
                true,
                45,
                0,
                List.of(),
                List.of()
        );
        installKeycloakProbe(service, 200);

        PlatformOperationsOverviewResponse response = service.overview();
        ComponentHealthResponse scheduler = component(indexByComponent(response), "Scheduler");

        assertThat(scheduler.status()).isEqualTo(ComponentHealthStatus.CRITICAL);
        assertThat(scheduler.reason()).contains("stale");
    }

    @Test
    void documentAiRecentTelemetryPreventsUnknown() throws Exception {
        ClinicalDocumentEntity document = mock(ClinicalDocumentEntity.class);
        when(document.getCreatedAt()).thenReturn(OffsetDateTime.now().minusHours(1));
        when(document.getUpdatedAt()).thenReturn(OffsetDateTime.now().minusMinutes(50));
        when(document.getAiExtractionStatus()).thenReturn("COMPLETED");
        when(document.getAiExtractionProvider()).thenReturn("GEMINI");
        when(document.getAiExtractionModel()).thenReturn("gemini-1.5-flash");
        when(document.getAiExtractionSummary()).thenReturn("Synthetic extraction");

        PlatformOperationsOverviewService service = newServiceWithTelemetry(
                redisConnectionFactory(true),
                true,
                true,
                true,
                true,
                0,
                0,
                List.of(),
                List.of(document)
        );
        installKeycloakProbe(service, 200);

        PlatformOperationsOverviewResponse response = service.overview();
        ComponentHealthResponse documentAi = component(indexByComponent(response), "Document AI");

        assertThat(documentAi.status()).isEqualTo(ComponentHealthStatus.HEALTHY);
        assertThat(documentAi.reason()).contains("successful processing");
    }

    @Test
    void documentAiNoTelemetryUsesWindowAwareReason() throws Exception {
        PlatformOperationsOverviewService service = newServiceWithTelemetry(
                redisConnectionFactory(true),
                true,
                true,
                true,
                true,
                0,
                0,
                List.of(),
                List.of()
        );
        installKeycloakProbe(service, 200);

        PlatformOperationsOverviewResponse response = service.overview();
        ComponentHealthResponse documentAi = component(indexByComponent(response), "Document AI");

        assertThat(documentAi.status()).isEqualTo(ComponentHealthStatus.UNKNOWN);
        assertThat(documentAi.reason()).contains("No document extraction activity in the last 24h");
    }

    private PlatformOperationsOverviewService newService(
            ObjectProvider<RedisConnectionFactory> redisProvider,
            boolean minioBucketExists,
            boolean minioAutoCreateBucket,
            long reminderHeartbeatAgeMinutes
    ) {
        return newServiceWithTelemetry(
                redisProvider,
                minioBucketExists,
                minioAutoCreateBucket,
                true,
                true,
                reminderHeartbeatAgeMinutes,
                0,
                List.of(),
                List.of()
        );
    }

    private PlatformOperationsOverviewService newServiceWithTelemetry(
            ObjectProvider<RedisConnectionFactory> redisProvider,
            boolean minioBucketExists,
            boolean minioAutoCreateBucket,
            boolean reminderSchedulerEnabled,
            boolean aiSchedulerEnabled,
            long reminderHeartbeatAgeMinutes,
            int aiFailedCount,
            List<AiInvocationLogEntity> aiLogs,
            List<ClinicalDocumentEntity> clinicalDocuments
    ) {
        AiInvocationLogRepository aiInvocationLogRepository = mock(AiInvocationLogRepository.class);
        when(aiInvocationLogRepository.findByCreatedAtBetween(any(), any())).thenReturn(aiLogs);
        when(aiInvocationLogRepository.findByProviderNameAndCreatedAtBetween(any(), any(), any())).thenReturn(aiLogs);

        ClinicalDocumentRepository clinicalDocumentRepository = mock(ClinicalDocumentRepository.class);
        when(clinicalDocumentRepository.findByCreatedAtBetween(any(), any())).thenReturn(clinicalDocuments);

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenReturn(1);

        HikariDataSource dataSource = mock(HikariDataSource.class);
        HikariPoolMXBean pool = mock(HikariPoolMXBean.class);
        when(dataSource.getHikariPoolMXBean()).thenReturn(pool);
        when(pool.getActiveConnections()).thenReturn(2);
        when(pool.getIdleConnections()).thenReturn(8);
        when(pool.getTotalConnections()).thenReturn(10);
        when(pool.getThreadsAwaitingConnection()).thenReturn(0);
        when(dataSource.getMaximumPoolSize()).thenReturn(20);

        DatabaseSchemaIntegrityService schemaService = mock(DatabaseSchemaIntegrityService.class);
        when(schemaService.inspect(true)).thenReturn(new DatabaseSchemaIntegrityReport(
                true,
                false,
                123,
                1L,
                1L,
                1L,
                List.of(),
                Map.of()
        ));

        MinioClient minioClient = mock(MinioClient.class);
        try {
            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(minioBucketExists);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }

        MinioStorageProperties minioProperties = new MinioStorageProperties();
        minioProperties.setEndpoint("http://localhost:9000");
        minioProperties.setBucket("clinic-documents");
        minioProperties.setAutoCreateBucket(minioAutoCreateBucket);

        CarePilotRuntimeSchedulerMonitor reminderMonitor = new CarePilotRuntimeSchedulerMonitor(reminderSchedulerEnabled);
        if (reminderHeartbeatAgeMinutes >= 0) {
            reminderMonitor.markGlobalReminderScan(OffsetDateTime.now().minusMinutes(reminderHeartbeatAgeMinutes));
        }

        CarePilotAiCallSchedulerMonitor aiMonitor = new CarePilotAiCallSchedulerMonitor(aiSchedulerEnabled, Duration.ofMinutes(5));
        aiMonitor.markRun(OffsetDateTime.now().minusMinutes(2), 5, 5, aiFailedCount, 0, 15L);

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

        ObjectProvider<LlmClient> geminiProvider = mock(ObjectProvider.class);
        when(geminiProvider.getIfAvailable()).thenReturn(null);
        ObjectProvider<LlmClient> groqProvider = mock(ObjectProvider.class);
        when(groqProvider.getIfAvailable()).thenReturn(null);

        return new PlatformOperationsOverviewService(
                releaseProperties,
                new PlatformOperationsDiagnosticStateStore(),
                aiInvocationLogRepository,
                clinicalDocumentRepository,
                jdbcTemplate,
                dataSource,
                redisProvider,
                minioClient,
                minioProperties,
                schemaService,
                reminderMonitor,
                aiMonitor,
                schedulerLockMonitor,
                geminiProvider,
                groqProvider,
                environment
        );
    }

    private void installKeycloakProbe(PlatformOperationsOverviewService service, int statusCode) {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<Void> response = mock(HttpResponse.class);
        try {
            when(response.statusCode()).thenReturn(statusCode);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        ReflectionTestUtils.setField(service, "httpClient", httpClient);
    }

    private void installKeycloakProbeTimeout(PlatformOperationsOverviewService service) {
        HttpClient httpClient = mock(HttpClient.class);
        try {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new HttpTimeoutException("timeout"));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        ReflectionTestUtils.setField(service, "httpClient", httpClient);
    }

    private ObjectProvider<RedisConnectionFactory> redisConnectionFactory(boolean healthy) {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        if (healthy) {
            RedisConnection connection = mock(RedisConnection.class);
            try {
                when(connection.ping()).thenReturn("PONG");
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            when(factory.getConnection()).thenReturn(connection);
        } else {
            when(factory.getConnection()).thenThrow(new IllegalStateException("redis down"));
        }
        ObjectProvider<RedisConnectionFactory> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(factory);
        return provider;
    }

    private Map<String, ComponentHealthResponse> indexByComponent(PlatformOperationsOverviewResponse response) {
        return response.healthMatrix().stream().collect(java.util.stream.Collectors.toMap(
                ComponentHealthResponse::component,
                component -> component
        ));
    }

    private ComponentHealthResponse component(Map<String, ComponentHealthResponse> components, String name) {
        ComponentHealthResponse component = components.get(name);
        assertThat(component).as("component %s", name).isNotNull();
        return component;
    }
}
