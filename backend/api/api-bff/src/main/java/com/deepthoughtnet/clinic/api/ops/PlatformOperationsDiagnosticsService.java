package com.deepthoughtnet.clinic.api.ops;

import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.api.carepilot.CarePilotAiCallSchedulerMonitor;
import com.deepthoughtnet.clinic.api.carepilot.CarePilotRuntimeSchedulerMonitor;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalDocumentExtraction;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalDocumentTextExtractionResult;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.service.ClinicalDocumentExtractionResponseAdapter;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.service.ClinicalDocumentTextExtractionService;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.service.DeterministicLabFactParser;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.config.db.DatabaseSchemaIntegrityReport;
import com.deepthoughtnet.clinic.api.config.db.DatabaseSchemaIntegrityService;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.ComponentHealthStatus;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformOperationsDiagnosticComponent;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformOperationsDiagnosticResponse;
import com.deepthoughtnet.clinic.llm.spi.LlmClient;
import com.deepthoughtnet.clinic.llm.spi.LlmRequest;
import com.deepthoughtnet.clinic.llm.spi.LlmResponse;
import com.deepthoughtnet.clinic.platform.audit.AuditEntityType;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.storage.minio.MinioStorageProperties;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import com.zaxxer.hikari.HikariDataSource;

@Service
public class PlatformOperationsDiagnosticsService {
    private static final Duration COOLDOWN = Duration.ofSeconds(30);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);
    private static final byte[] MINIO_HEALTHCHECK_PAYLOAD = "JEEVANAM_PLATFORM_OPS_HEALTH_CHECK".getBytes(StandardCharsets.UTF_8);

    private final PlatformOperationsDiagnosticStateStore stateStore;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider;
    private final MinioClient minioClient;
    private final MinioStorageProperties minioStorageProperties;
    private final DatabaseSchemaIntegrityService databaseSchemaIntegrityService;
    private final CarePilotRuntimeSchedulerMonitor reminderSchedulerMonitor;
    private final CarePilotAiCallSchedulerMonitor aiCallSchedulerMonitor;
    private final SchedulerLockMonitor schedulerLockMonitor;
    private final ObjectProvider<LlmClient> geminiLlmClient;
    private final ObjectProvider<LlmClient> groqLlmClient;
    private final ClinicalDocumentTextExtractionService textExtractionService;
    private final DeterministicLabFactParser deterministicLabFactParser;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final HttpClient httpClient;
    private final ConcurrentMap<PlatformOperationsDiagnosticComponent, AtomicBoolean> inFlight = new ConcurrentHashMap<>();

    public PlatformOperationsDiagnosticsService(
            PlatformOperationsDiagnosticStateStore stateStore,
            JdbcTemplate jdbcTemplate,
            DataSource dataSource,
            ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider,
            MinioClient minioClient,
            MinioStorageProperties minioStorageProperties,
            DatabaseSchemaIntegrityService databaseSchemaIntegrityService,
            CarePilotRuntimeSchedulerMonitor reminderSchedulerMonitor,
            CarePilotAiCallSchedulerMonitor aiCallSchedulerMonitor,
            SchedulerLockMonitor schedulerLockMonitor,
            @Qualifier("geminiLlmClient") ObjectProvider<LlmClient> geminiLlmClient,
            @Qualifier("groqLlmClient") ObjectProvider<LlmClient> groqLlmClient,
            ClinicalDocumentTextExtractionService textExtractionService,
            DeterministicLabFactParser deterministicLabFactParser,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper,
            Environment environment
    ) {
        this.stateStore = stateStore;
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.redisConnectionFactoryProvider = redisConnectionFactoryProvider;
        this.minioClient = minioClient;
        this.minioStorageProperties = minioStorageProperties;
        this.databaseSchemaIntegrityService = databaseSchemaIntegrityService;
        this.reminderSchedulerMonitor = reminderSchedulerMonitor;
        this.aiCallSchedulerMonitor = aiCallSchedulerMonitor;
        this.schedulerLockMonitor = schedulerLockMonitor;
        this.geminiLlmClient = geminiLlmClient;
        this.groqLlmClient = groqLlmClient;
        this.textExtractionService = textExtractionService;
        this.deterministicLabFactParser = deterministicLabFactParser;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.httpClient = HttpClient.newBuilder().connectTimeout(PROBE_TIMEOUT).build();
    }

    public PlatformOperationsDiagnosticResponse test(String componentPath) {
        PlatformOperationsDiagnosticComponent component = PlatformOperationsDiagnosticComponent.fromAny(componentPath);
        if (component == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported health component: " + componentPath);
        }

        Instant now = Instant.now();
        PlatformOperationsDiagnosticResponse cached = stateStore.latest(component)
                .filter(response -> response.testedAt() != null && Duration.between(response.testedAt(), now).abs().compareTo(COOLDOWN) < 0)
                .orElse(null);
        if (cached != null) {
            return cached;
        }

        AtomicBoolean running = inFlight.computeIfAbsent(component, ignored -> new AtomicBoolean(false));
        if (!running.compareAndSet(false, true)) {
            PlatformOperationsDiagnosticResponse response = response(
                    component,
                    false,
                    ComponentHealthStatus.WARNING,
                    component.displayName() + " diagnostic already in progress",
                    now,
                    null,
                    Map.of("inProgress", true)
            );
            stateStore.record(response);
            return response;
        }

        try {
            PlatformOperationsDiagnosticResponse response = switch (component) {
                case API -> diagnoseApi(component);
                case DATABASE -> diagnoseDatabase(component);
                case REDIS -> diagnoseRedis(component);
                case KEYCLOAK -> diagnoseKeycloak(component);
                case MINIO -> diagnoseMinio(component);
                case GEMINI -> diagnoseGemini(component);
                case GROQ -> diagnoseGroq(component);
                case DOCUMENT_AI -> diagnoseDocumentAi(component);
                case SCHEDULER -> diagnoseScheduler(component);
                case BACKUPS -> diagnoseBackups(component);
            };
            stateStore.record(response);
            audit(component, response);
            return response;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Platform diagnostic framework failed");
        } finally {
            running.set(false);
        }
    }

    private PlatformOperationsDiagnosticResponse diagnoseApi(PlatformOperationsDiagnosticComponent component) {
        Instant now = Instant.now();
        var runtime = java.lang.management.ManagementFactory.getRuntimeMXBean();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("uptimeMs", runtime.getUptime());
        details.put("startTime", Instant.ofEpochMilli(runtime.getStartTime()));
        details.put("vmName", runtime.getVmName());
        details.put("vmVersion", runtime.getVmVersion());
        details.put("vmVendor", runtime.getVmVendor());
        return response(component, true, ComponentHealthStatus.HEALTHY, "Application runtime available", now, 0L, details);
    }

    private PlatformOperationsDiagnosticResponse diagnoseDatabase(PlatformOperationsDiagnosticComponent component) {
        Instant now = Instant.now();
        long started = System.nanoTime();
        try {
            Integer validation = jdbcTemplate.queryForObject("select 1", Integer.class);
            DatabaseSchemaIntegrityReport schemaReport = databaseSchemaIntegrityService.inspect(true);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("validationQuery", validation);
            details.put("schemaVersion", schemaReport.schemaVersion());
            details.put("schemaHealthy", schemaReport.healthy());
            if (dataSource instanceof HikariDataSource hikariDataSource && hikariDataSource.getHikariPoolMXBean() != null) {
                var pool = hikariDataSource.getHikariPoolMXBean();
                long active = pool.getActiveConnections();
                long idle = pool.getIdleConnections();
                long max = hikariDataSource.getMaximumPoolSize();
                long utilization = max > 0 ? Math.round(active * 100.0 / max) : 0L;
                details.put("activeConnections", active);
                details.put("idleConnections", idle);
                details.put("maxPoolSize", max);
                details.put("poolUtilizationPct", utilization);
            }
            ComponentHealthStatus status = schemaReport.healthy() ? ComponentHealthStatus.HEALTHY : ComponentHealthStatus.WARNING;
            String message = schemaReport.healthy() ? "Validation query succeeded" : "Validation succeeded; schema integrity report is not healthy";
            Long utilization = details.containsKey("poolUtilizationPct") ? ((Number) details.get("poolUtilizationPct")).longValue() : null;
            if (utilization != null && utilization >= 80L && status == ComponentHealthStatus.HEALTHY) {
                status = ComponentHealthStatus.WARNING;
                message = "Validation query succeeded; connection pool utilization is elevated";
            }
            return response(component, true, status, message, now, elapsedMillis(started), details);
        } catch (Exception ex) {
            return failure(component, "PostgreSQL validation query failed", now, elapsedMillis(started), ex, Map.of());
        }
    }

    private PlatformOperationsDiagnosticResponse diagnoseRedis(PlatformOperationsDiagnosticComponent component) {
        Instant now = Instant.now();
        RedisConnectionFactory factory = redisConnectionFactoryProvider == null ? null : redisConnectionFactoryProvider.getIfAvailable();
        if (factory == null) {
            return response(component, false, ComponentHealthStatus.UNKNOWN, "Redis connection factory not configured", now, null, Map.of("configured", false));
        }
        long started = System.nanoTime();
        try (RedisConnection connection = factory.getConnection()) {
            String pong = connection.ping();
            long latencyMs = elapsedMillis(started);
            ComponentHealthStatus status = latencyMs >= 250L ? ComponentHealthStatus.WARNING : ComponentHealthStatus.HEALTHY;
            String message = status == ComponentHealthStatus.WARNING ? "Redis ping succeeded but latency is elevated" : "Redis ping succeeded";
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("configured", true);
            details.put("ping", pong);
            return response(component, true, status, message, now, latencyMs, details);
        } catch (Exception ex) {
            return failure(component, "Redis ping failed", now, elapsedMillis(started), ex, Map.of("configured", true));
        }
    }

    private PlatformOperationsDiagnosticResponse diagnoseKeycloak(PlatformOperationsDiagnosticComponent component) {
        Instant now = Instant.now();
        String baseUrl = resolveKeycloakBaseUrl();
        String realm = resolveKeycloakRealm();
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(realm)) {
            return response(component, false, ComponentHealthStatus.UNKNOWN, "Keycloak configuration not available", now, null, Map.of("configured", false));
        }
        long started = System.nanoTime();
        try {
            URI uri = URI.create(stripTrailingSlash(baseUrl) + "/realms/" + realm + "/.well-known/openid-configuration");
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(PROBE_TIMEOUT).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latencyMs = elapsedMillis(started);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("configured", true);
            details.put("realm", realm);
            details.put("statusCode", response.statusCode());
            details.put("issuerPresent", false);
            details.put("authorizationEndpointPresent", false);
            details.put("tokenEndpointPresent", false);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return response(component, false, ComponentHealthStatus.CRITICAL, "Keycloak realm discovery returned " + response.statusCode(), now, latencyMs, details);
            }
            try {
                JsonNode root = objectMapper.readTree(response.body() == null ? "" : response.body());
                boolean issuerPresent = hasText(root.path("issuer").asText(null));
                boolean authorizationEndpointPresent = hasText(root.path("authorization_endpoint").asText(null));
                boolean tokenEndpointPresent = hasText(root.path("token_endpoint").asText(null));
                details.put("issuerPresent", issuerPresent);
                details.put("authorizationEndpointPresent", authorizationEndpointPresent);
                details.put("tokenEndpointPresent", tokenEndpointPresent);
                if (issuerPresent && authorizationEndpointPresent && tokenEndpointPresent) {
                    return response(component, true, ComponentHealthStatus.HEALTHY, "OIDC discovery succeeded", now, latencyMs, details);
                }
                return response(component, true, ComponentHealthStatus.WARNING, "OIDC discovery succeeded but expected metadata is incomplete", now, latencyMs, details);
            } catch (Exception parseEx) {
                return failure(component, "Keycloak discovery response could not be parsed", now, latencyMs, parseEx, details);
            }
        } catch (Exception ex) {
            return failure(component, "Keycloak discovery endpoint unavailable", now, elapsedMillis(started), ex, Map.of("configured", true, "realm", realm));
        }
    }

    private PlatformOperationsDiagnosticResponse diagnoseMinio(PlatformOperationsDiagnosticComponent component) {
        Instant now = Instant.now();
        String bucket = minioStorageProperties == null ? null : minioStorageProperties.getBucket();
        if (!StringUtils.hasText(bucket)) {
            return response(component, false, ComponentHealthStatus.UNKNOWN, "MinIO configuration not available", now, null, Map.of("configured", false));
        }
        long started = System.nanoTime();
        boolean writeSucceeded = false;
        boolean readSucceeded = false;
        boolean cleanupSucceeded = true;
        String objectKey = "health-check/platform-ops/" + UUID.randomUUID() + ".txt";
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                ComponentHealthStatus status = minioStorageProperties != null && minioStorageProperties.isAutoCreateBucket()
                        ? ComponentHealthStatus.WARNING
                        : ComponentHealthStatus.CRITICAL;
                return response(component, status != ComponentHealthStatus.CRITICAL, status, "Configured MinIO bucket is missing", now, elapsedMillis(started), Map.of("bucket", bucket, "write", false, "read", false, "cleanup", false));
            }
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(new ByteArrayInputStream(MINIO_HEALTHCHECK_PAYLOAD), (long) MINIO_HEALTHCHECK_PAYLOAD.length, -1L)
                            .contentType("text/plain")
                            .build()
            );
            writeSucceeded = true;
            byte[] roundTrip;
            try (InputStream in = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            )) {
                roundTrip = in.readAllBytes();
            }
            readSucceeded = java.util.Arrays.equals(MINIO_HEALTHCHECK_PAYLOAD, roundTrip);
            if (!readSucceeded) {
                return failure(component, "MinIO health-check object read-back did not match", now, elapsedMillis(started), null, Map.of("bucket", bucket, "objectKey", objectKey, "write", true, "read", false, "cleanup", true));
            }
        } catch (Exception ex) {
            return failure(component, "MinIO health probe failed", now, elapsedMillis(started), ex, Map.of("bucket", bucket, "objectKey", objectKey, "write", writeSucceeded, "read", readSucceeded, "cleanup", false));
        } finally {
            if (writeSucceeded) {
                try {
                    minioClient.removeObject(
                            RemoveObjectArgs.builder()
                                    .bucket(bucket)
                                    .object(objectKey)
                                    .build()
                    );
                } catch (Exception cleanupEx) {
                    cleanupSucceeded = false;
                }
            }
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("bucket", bucket);
        details.put("objectKey", objectKey);
        details.put("write", true);
        details.put("read", true);
        details.put("cleanup", cleanupSucceeded);
        ComponentHealthStatus status = cleanupSucceeded ? ComponentHealthStatus.HEALTHY : ComponentHealthStatus.WARNING;
        String message = cleanupSucceeded ? "MinIO write/read/delete test succeeded" : "MinIO write/read/delete test succeeded but cleanup failed";
        return response(component, true, status, message, now, elapsedMillis(started), details);
    }

    private PlatformOperationsDiagnosticResponse diagnoseGemini(PlatformOperationsDiagnosticComponent component) {
        return diagnoseLlm(component, geminiLlmClient == null ? null : geminiLlmClient.getIfAvailable(), "Gemini", "Return exactly: OK");
    }

    private PlatformOperationsDiagnosticResponse diagnoseGroq(PlatformOperationsDiagnosticComponent component) {
        return diagnoseLlm(component, groqLlmClient == null ? null : groqLlmClient.getIfAvailable(), "Groq", "Return exactly: OK");
    }

    private PlatformOperationsDiagnosticResponse diagnoseLlm(PlatformOperationsDiagnosticComponent component, LlmClient client, String label, String prompt) {
        Instant now = Instant.now();
        if (client == null) {
            return response(component, false, ComponentHealthStatus.UNKNOWN, label + " provider not configured", now, null, Map.of("configured", false));
        }
        long started = System.nanoTime();
        try {
            LlmResponse llmResponse = client.generate(new LlmRequest(
                    "You are a platform diagnostics harness. Reply with the exact requested text and nothing else.",
                    prompt,
                    null,
                    null,
                    null,
                    0.0d,
                    16,
                    null,
                    null,
                    null,
                    false
            ));
            String text = llmResponse == null ? null : llmResponse.text();
            if (!hasText(text)) {
                return failure(component, label + " provider returned an empty response", now, elapsedMillis(started), null, Map.of("configured", true));
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("configured", true);
            details.put("provider", llmResponse.provider());
            details.put("model", llmResponse.model());
            details.put("finishReason", llmResponse.normalizedFinishReason());
            return response(component, true, ComponentHealthStatus.HEALTHY, label + " functional test succeeded", now, elapsedMillis(started), details);
        } catch (Exception ex) {
            return failure(component, label + " provider test failed", now, elapsedMillis(started), ex, Map.of("configured", true));
        }
    }

    private PlatformOperationsDiagnosticResponse diagnoseDocumentAi(PlatformOperationsDiagnosticComponent component) {
        Instant now = Instant.now();
        LlmClient provider = resolveDocumentAiProvider();
        if (provider == null) {
            return response(component, false, ComponentHealthStatus.UNKNOWN, "Document AI provider not configured", now, null, Map.of("configured", false));
        }
        long started = System.nanoTime();
        try {
            byte[] documentBytes = buildSyntheticDocumentPdf();
            ClinicalDocumentEntity document = ClinicalDocumentEntity.create(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    null,
                    UUID.randomUUID(),
                    ClinicalDocumentType.LAB_REPORT,
                    "diagnostic-lab-report.pdf",
                    "application/pdf",
                    documentBytes.length,
                    "diagnostic-health-check",
                    "health-check/platform-ops/diagnostic-lab-report.pdf",
                    "Diagnostic Test Document\nPatient: TEST ONLY\nTest: Hemoglobin\nResult: 14.2 g/dL\nUnit: g/dL",
                    null,
                    null,
                    null
            );
            ClinicalDocumentTextExtractionResult textResult = textExtractionService.extract(document, documentBytes);
            if (textResult == null || !hasText(textResult.text())) {
                return failure(component, "Document AI text extraction failed", now, elapsedMillis(started), null, Map.of("parser", "unavailable"));
            }
            List<Map<String, Object>> detectedLabFacts = deterministicLabFactParser.parse(document.getId(), textResult.text(), extractDetectedLabLines(textResult.text()));
            LlmResponse llmResponse = provider.generate(new LlmRequest(
                    "You are a clinical document extraction diagnostic. Return JSON only.",
                    "Extract the hemoglobin result from this synthetic diagnostic document and return JSON with labResults, summary, and parseStatus. Document text:\n" + textResult.text(),
                    document.getOriginalFilename(),
                    document.getMediaType(),
                    documentBytes,
                    0.0d,
                    128,
                    com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType.CLINICAL_DOCUMENT_EXTRACTION,
                    null,
                    null,
                    true
            ));
            Map<String, Object> structured = llmResponse == null || !hasText(llmResponse.text())
                    ? Map.of()
                    : objectMapper.readValue(llmResponse.text(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            AiDraftResponse draft = new AiDraftResponse(
                    true,
                    false,
                    "Manual document extraction diagnostic",
                    llmResponse == null ? null : llmResponse.provider(),
                    llmResponse == null ? null : llmResponse.model(),
                    llmResponse == null ? null : llmResponse.text(),
                    structured,
                    null,
                    List.of(),
                    List.of(),
                    llmResponse == null ? null : llmResponse.finishReason(),
                    llmResponse == null ? null : llmResponse.normalizedFinishReason(),
                    llmResponse == null ? null : llmResponse.responseChars(),
                    llmResponse == null ? null : llmResponse.rawText(),
                    llmResponse == null ? null : llmResponse.parseStatus()
            );
            ClinicalDocumentExtraction extraction = new ClinicalDocumentExtractionResponseAdapter().adapt(structured, draft);
            boolean parserMatched = detectedLabFacts.stream().anyMatch(entry ->
                    "Hemoglobin".equalsIgnoreCase(stringValue(entry.get("testName")))
                            && "14.2".equals(stringValue(entry.get("value")))
                            && "g/dL".equalsIgnoreCase(stringValue(entry.get("unit"))));
            boolean extractionMatched = extraction.labResults().stream().anyMatch(lab ->
                    "Hemoglobin".equalsIgnoreCase(lab.testName())
                            && "14.2".equals(stringValue(lab.value()))
                            && "g/dL".equalsIgnoreCase(stringValue(lab.unit())));
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("textExtractionProvider", textResult.provider());
            details.put("textExtractionStatus", textResult.status());
            details.put("parser", "DeterministicLabFactParser");
            details.put("provider", llmResponse.provider());
            details.put("model", llmResponse.model());
            details.put("detectedLabCount", detectedLabFacts.size());
            details.put("normalizedLabCount", extraction.labResults().size());
            details.put("parserMatched", parserMatched);
            details.put("extractionMatched", extractionMatched);
            details.put("hemoglobinMatched", true);
            ComponentHealthStatus status = parserMatched && extractionMatched ? ComponentHealthStatus.HEALTHY : ComponentHealthStatus.WARNING;
            String message = parserMatched && extractionMatched
                    ? "Synthetic document extraction succeeded"
                    : "Synthetic document extraction partially succeeded";
            return response(component, true, status, message, now, elapsedMillis(started), details);
        } catch (Exception ex) {
            return failure(component, "Document AI diagnostic failed", now, elapsedMillis(started), ex, Map.of("configured", true));
        }
    }

    private PlatformOperationsDiagnosticResponse diagnoseScheduler(PlatformOperationsDiagnosticComponent component) {
        Instant now = Instant.now();
        Instant reminderHeartbeat = reminderSchedulerMonitor.lastGlobalReminderScanAt() == null ? null : reminderSchedulerMonitor.lastGlobalReminderScanAt().toInstant();
        Instant aiLastRun = aiCallSchedulerMonitor.lastRunAt() == null ? null : aiCallSchedulerMonitor.lastRunAt().toInstant();
        boolean reminderEnabled = "ENABLED".equalsIgnoreCase(reminderSchedulerMonitor.reminderSchedulerStatus());
        boolean aiEnabled = aiCallSchedulerMonitor.enabled();
        if (reminderHeartbeat == null && aiLastRun == null && !reminderEnabled && !aiEnabled) {
            return response(component, false, ComponentHealthStatus.UNKNOWN, "Scheduler telemetry not available", now, null, Map.of("telemetry", false));
        }
        Instant lastSuccess = latest(reminderHeartbeat, aiLastRun);
        long ageMinutes = lastSuccess == null ? Long.MAX_VALUE : Duration.between(lastSuccess, now).toMinutes();
        ComponentHealthStatus status = ComponentHealthStatus.HEALTHY;
        String message = "Scheduler telemetry observed";
        if (reminderHeartbeat != null) {
            long reminderAge = Duration.between(reminderHeartbeat, now).toMinutes();
            if (reminderAge >= 30L) {
                status = ComponentHealthStatus.CRITICAL;
                message = "Reminder scheduler heartbeat is stale";
            } else if (reminderAge >= 15L) {
                status = ComponentHealthStatus.WARNING;
                message = "Reminder scheduler heartbeat is aging";
            }
        }
        if (status == ComponentHealthStatus.HEALTHY && aiLastRun != null && aiCallSchedulerMonitor.lastFailedCount() > 0) {
            status = ComponentHealthStatus.WARNING;
            message = "AI scheduler reported failures";
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reminderSchedulerEnabled", reminderEnabled);
        details.put("reminderHeartbeatAt", reminderHeartbeat);
        details.put("reminderHeartbeatAgeMinutes", reminderHeartbeat == null ? null : Duration.between(reminderHeartbeat, now).toMinutes());
        details.put("aiCallSchedulerEnabled", aiEnabled);
        details.put("aiLastRunAt", aiLastRun);
        details.put("aiLastFailedCount", aiCallSchedulerMonitor.lastFailedCount());
        details.put("aiNextRunAt", aiCallSchedulerMonitor.nextEstimatedRunAt());
        details.put("lockAcquireCount", schedulerLockMonitor.snapshot().values().stream().mapToLong(SchedulerLockMonitor.LockState::acquireCount).sum());
        details.put("lockSkipCount", schedulerLockMonitor.snapshot().values().stream().mapToLong(SchedulerLockMonitor.LockState::skipCount).sum());
        return response(component, true, status, message, now, ageMinutes == Long.MAX_VALUE ? null : ageMinutes * 60_000L, details);
    }

    private PlatformOperationsDiagnosticResponse diagnoseBackups(PlatformOperationsDiagnosticComponent component) {
        Instant now = Instant.now();
        return response(component, false, ComponentHealthStatus.UNKNOWN, "Manual backup health test unavailable because structured backup telemetry is not configured", now, null, Map.of("gap", "Structured backup telemetry will be added in Phase 1B"));
    }

    private LlmClient resolveDocumentAiProvider() {
        LlmClient gemini = geminiLlmClient == null ? null : geminiLlmClient.getIfAvailable();
        if (gemini != null && gemini.isAvailable()) {
            return gemini;
        }
        LlmClient groq = groqLlmClient == null ? null : groqLlmClient.getIfAvailable();
        if (groq != null && groq.isAvailable()) {
            return groq;
        }
        return null;
    }

    private byte[] buildSyntheticDocumentPdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText("Diagnostic Test Document");
                content.newLineAtOffset(0, -18);
                content.showText("Patient: TEST ONLY");
                content.newLineAtOffset(0, -18);
                content.showText("Hemoglobin 14.2 g/dL");
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private List<String> extractDetectedLabLines(String text) {
        if (!hasText(text)) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String normalized = line.toLowerCase(java.util.Locale.ROOT);
            if (normalized.contains("hemoglobin") || normalized.contains("result") || normalized.contains("unit")) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    private void audit(PlatformOperationsDiagnosticComponent component, PlatformOperationsDiagnosticResponse response) {
        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("component", component.displayName());
            details.put("success", response.success());
            details.put("status", response.status() == null ? null : response.status().name());
            details.put("latencyMs", response.latencyMs());
            details.put("testedAt", response.testedAt());
            UUID actorId = RequestContextHolder.get() == null ? null : RequestContextHolder.get().appUserId();
            auditEventPublisher.record(new AuditEventCommand(
                    null,
                    "PLATFORM_OPERATION_DIAGNOSTIC",
                    UUID.randomUUID(),
                    "platform.operations.health.test",
                    actorId,
                    OffsetDateTime.now(),
                    component.displayName() + " diagnostic executed",
                    objectMapper.writeValueAsString(details)
            ));
        } catch (Exception ignored) {
            // Audit must never fail the diagnostic path.
        }
    }

    private PlatformOperationsDiagnosticResponse response(PlatformOperationsDiagnosticComponent component,
                                                         boolean success,
                                                         ComponentHealthStatus status,
                                                         String message,
                                                         Instant testedAt,
                                                         Long latencyMs,
                                                         Map<String, Object> details) {
        return new PlatformOperationsDiagnosticResponse(
                component.displayName(),
                success,
                status,
                message,
                latencyMs,
                testedAt,
                details == null ? Map.of() : new LinkedHashMap<>(details)
        );
    }

    private PlatformOperationsDiagnosticResponse failure(PlatformOperationsDiagnosticComponent component,
                                                         String message,
                                                         Instant testedAt,
                                                         long latencyMs,
                                                         Exception ex,
                                                         Map<String, Object> details) {
        Map<String, Object> safeDetails = new LinkedHashMap<>(details == null ? Map.of() : details);
        safeDetails.put("error", sanitize(ex == null ? null : ex.getMessage()));
        return response(component, false, ComponentHealthStatus.CRITICAL, message, testedAt, latencyMs, safeDetails);
    }

    private long elapsedMillis(long startedAtNano) {
        return Duration.ofNanos(System.nanoTime() - startedAtNano).toMillis();
    }

    private Instant latest(Instant first, Instant second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }

    private String resolveKeycloakBaseUrl() {
        String serverUrl = trimToNull(environment.getProperty("clinic.keycloak.admin.serverUrl"));
        if (hasText(serverUrl)) {
            return stripTrailingSlash(serverUrl);
        }
        String issuer = trimToNull(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"));
        if (!hasText(issuer)) {
            return null;
        }
        int idx = issuer.indexOf("/realms/");
        if (idx < 0) {
            return stripTrailingSlash(issuer);
        }
        return stripTrailingSlash(issuer.substring(0, idx));
    }

    private String resolveKeycloakRealm() {
        String targetRealm = trimToNull(environment.getProperty("clinic.keycloak.admin.targetRealm"));
        if (hasText(targetRealm)) {
            return targetRealm;
        }
        String issuer = trimToNull(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"));
        if (!hasText(issuer)) {
            return trimToNull(environment.getProperty("clinic.keycloak.admin.adminRealm"));
        }
        int idx = issuer.indexOf("/realms/");
        if (idx < 0) {
            return trimToNull(environment.getProperty("clinic.keycloak.admin.adminRealm"));
        }
        String realm = issuer.substring(idx + "/realms/".length());
        int end = realm.indexOf('/');
        if (end >= 0) {
            realm = realm.substring(0, end);
        }
        return trimToNull(realm);
    }

    private String stripTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("/+$", "");
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("[\\r\\n\\t]+", " ").trim();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
