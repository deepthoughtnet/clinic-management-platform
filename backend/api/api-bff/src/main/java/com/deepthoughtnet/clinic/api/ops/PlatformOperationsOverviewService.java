package com.deepthoughtnet.clinic.api.ops;

import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiInvocationLogEntity;
import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiInvocationLogRepository;
import com.deepthoughtnet.clinic.api.carepilot.CarePilotAiCallSchedulerMonitor;
import com.deepthoughtnet.clinic.api.carepilot.CarePilotRuntimeSchedulerMonitor;
import com.deepthoughtnet.clinic.api.config.db.DatabaseSchemaIntegrityReport;
import com.deepthoughtnet.clinic.api.config.db.DatabaseSchemaIntegrityService;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentRepository;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.ComponentHealthResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.ComponentHealthStatus;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformOperationsAiSummaryResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformOperationsOverviewResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformOperationsOverviewSummaryResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformOperationsReleaseResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformOperationsRuntimeResponse;
import com.deepthoughtnet.clinic.storage.minio.MinioStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.deepthoughtnet.clinic.llm.spi.LlmClient;
import com.zaxxer.hikari.HikariDataSource;

/** Builds the tenantless platform operations overview and component health matrix. */
@Service
public class PlatformOperationsOverviewService {
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration AI_RECENT_WINDOW = Duration.ofHours(24);
    private static final long REDIS_WARNING_LATENCY_MS = 250L;
    private static final long DB_WARNING_UTILIZATION_PCT = 80L;
    private static final long SCHEDULER_WARNING_AGE_MINUTES = 15L;
    private static final long SCHEDULER_CRITICAL_AGE_MINUTES = 30L;
    private static final long AI_WARNING_LATENCY_MS = 2_500L;

    private final PlatformOpsReleaseProperties releaseProperties;
    private final PlatformOperationsDiagnosticStateStore diagnosticStateStore;
    private final AiInvocationLogRepository aiInvocationLogRepository;
    private final ClinicalDocumentRepository clinicalDocumentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final MinioClient minioClient;
    private final MinioStorageProperties minioStorageProperties;
    private final DatabaseSchemaIntegrityService databaseSchemaIntegrityService;
    private final CarePilotRuntimeSchedulerMonitor reminderSchedulerMonitor;
    private final CarePilotAiCallSchedulerMonitor aiCallSchedulerMonitor;
    private final SchedulerLockMonitor schedulerLockMonitor;
    private final ObjectProvider<LlmClient> geminiLlmClient;
    private final ObjectProvider<LlmClient> groqLlmClient;
    private final HttpClient httpClient;
    private final Environment environment;

    public PlatformOperationsOverviewService(
            PlatformOpsReleaseProperties releaseProperties,
            PlatformOperationsDiagnosticStateStore diagnosticStateStore,
            AiInvocationLogRepository aiInvocationLogRepository,
            ClinicalDocumentRepository clinicalDocumentRepository,
            JdbcTemplate jdbcTemplate,
            DataSource dataSource,
            ObjectProvider<RedisConnectionFactory> redisConnectionFactory,
            MinioClient minioClient,
            MinioStorageProperties minioStorageProperties,
            DatabaseSchemaIntegrityService databaseSchemaIntegrityService,
            CarePilotRuntimeSchedulerMonitor reminderSchedulerMonitor,
            CarePilotAiCallSchedulerMonitor aiCallSchedulerMonitor,
            SchedulerLockMonitor schedulerLockMonitor,
            @Qualifier("geminiLlmClient") ObjectProvider<LlmClient> geminiLlmClient,
            @Qualifier("groqLlmClient") ObjectProvider<LlmClient> groqLlmClient,
            Environment environment
    ) {
        this.releaseProperties = releaseProperties;
        this.diagnosticStateStore = diagnosticStateStore;
        this.aiInvocationLogRepository = aiInvocationLogRepository;
        this.clinicalDocumentRepository = clinicalDocumentRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory.getIfAvailable();
        this.minioClient = minioClient;
        this.minioStorageProperties = minioStorageProperties;
        this.databaseSchemaIntegrityService = databaseSchemaIntegrityService;
        this.reminderSchedulerMonitor = reminderSchedulerMonitor;
        this.aiCallSchedulerMonitor = aiCallSchedulerMonitor;
        this.schedulerLockMonitor = schedulerLockMonitor;
        this.geminiLlmClient = geminiLlmClient;
        this.groqLlmClient = groqLlmClient;
        this.environment = environment;
        this.httpClient = HttpClient.newBuilder().connectTimeout(PROBE_TIMEOUT).build();
    }

    public PlatformOperationsOverviewResponse overview() {
        Instant now = Instant.now();
        List<ComponentHealthResponse> healthMatrix = List.of(
                apiHealth(now),
                databaseHealth(now),
                keycloakHealth(now),
                redisHealth(now),
                minioHealth(now),
                geminiHealth(now),
                groqHealth(now),
                documentAiHealth(now),
                schedulerHealth(now),
                backupHealth(now),
                releaseHealth(now)
        );
        healthMatrix = healthMatrix.stream().map(diagnosticStateStore::overlay).toList();

        PlatformOperationsOverviewSummaryResponse summary = summarize(now, healthMatrix);
        PlatformOperationsAiSummaryResponse aiSummary = aiSummary(now);
        PlatformOperationsReleaseResponse release = releaseInfo(now);
        PlatformOperationsRuntimeResponse runtime = runtimeInfo(now);
        return new PlatformOperationsOverviewResponse(summary, healthMatrix, aiSummary, release, runtime);
    }

    private PlatformOperationsOverviewSummaryResponse summarize(Instant now, List<ComponentHealthResponse> components) {
        long healthy = components.stream().filter(component -> component.status() == ComponentHealthStatus.HEALTHY).count();
        long warning = components.stream().filter(component -> component.status() == ComponentHealthStatus.WARNING).count();
        long critical = components.stream().filter(component -> component.status() == ComponentHealthStatus.CRITICAL).count();
        long unknown = components.stream().filter(component -> component.status() == ComponentHealthStatus.UNKNOWN).count();
        ComponentHealthStatus overall = overallStatus(components);
        return new PlatformOperationsOverviewSummaryResponse(overall, healthy, warning, critical, unknown, now);
    }

    private ComponentHealthStatus overallStatus(List<ComponentHealthResponse> components) {
        List<ComponentHealthResponse> required = components.stream()
                .filter(component -> isRequiredCoreComponent(component.component()))
                .toList();

        if (components.stream().anyMatch(component -> component.status() == ComponentHealthStatus.CRITICAL)) {
            return ComponentHealthStatus.CRITICAL;
        }
        if (components.stream().anyMatch(component -> component.status() == ComponentHealthStatus.WARNING)) {
            return ComponentHealthStatus.WARNING;
        }
        if (required.stream().anyMatch(component -> component.status() == ComponentHealthStatus.UNKNOWN)) {
            return ComponentHealthStatus.UNKNOWN;
        }
        return ComponentHealthStatus.HEALTHY;
    }

    private ComponentHealthStatus worse(ComponentHealthStatus left, ComponentHealthStatus right) {
        return severityRank(right) > severityRank(left) ? right : left;
    }

    private int severityRank(ComponentHealthStatus status) {
        return switch (status) {
            case CRITICAL -> 3;
            case WARNING -> 2;
            case UNKNOWN -> 1;
            case HEALTHY -> 0;
        };
    }

    private ComponentHealthResponse apiHealth(Instant now) {
        var runtime = ManagementFactory.getRuntimeMXBean();
        long uptimeMs = runtime.getUptime();
        Instant startTime = Instant.ofEpochMilli(runtime.getStartTime());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("applicationName", safe(environment.getProperty("spring.application.name")));
        details.put("jvmName", safe(runtime.getVmName()));
        details.put("jvmVersion", safe(runtime.getVmVersion()));
        details.put("vmVendor", safe(runtime.getVmVendor()));
        details.put("uptimeMs", uptimeMs);
        details.put("startTime", startTime);
        return new ComponentHealthResponse(
                "API",
                ComponentHealthStatus.HEALTHY,
                "Application responding; uptime " + formatDuration(Duration.ofMillis(uptimeMs)),
                now,
                now,
                null,
                0L,
                details
        );
    }

    private ComponentHealthResponse databaseHealth(Instant now) {
        long startedAt = System.nanoTime();
        try {
            Integer validation = jdbcTemplate.queryForObject("select 1", Integer.class);
            DatabaseSchemaIntegrityReport schemaReport = databaseSchemaIntegrityService.inspect(true);
            long latencyMs = elapsedMillis(startedAt);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("validationQuery", validation);
            details.put("schemaVersion", schemaReport.schemaVersion());
            details.put("schemaHealthy", schemaReport.healthy());

            if (dataSource instanceof HikariDataSource hikariDataSource && hikariDataSource.getHikariPoolMXBean() != null) {
                var pool = hikariDataSource.getHikariPoolMXBean();
                long active = pool.getActiveConnections();
                long idle = pool.getIdleConnections();
                long total = pool.getTotalConnections();
                long waiting = pool.getThreadsAwaitingConnection();
                long max = hikariDataSource.getMaximumPoolSize();
                long utilization = max > 0 ? Math.round(active * 100.0 / max) : 0L;
                details.put("activeConnections", active);
                details.put("idleConnections", idle);
                details.put("totalConnections", total);
                details.put("maxPoolSize", max);
                details.put("threadsAwaitingConnection", waiting);
                details.put("poolUtilizationPct", utilization);
            }

            ComponentHealthStatus status = ComponentHealthStatus.HEALTHY;
            List<String> reasons = new ArrayList<>();
            reasons.add("Validation query succeeded");
            if (!schemaReport.healthy()) {
                status = ComponentHealthStatus.WARNING;
                reasons.add("schema integrity report is not healthy");
            }
            Long utilizationPct = details.containsKey("poolUtilizationPct") ? ((Number) details.get("poolUtilizationPct")).longValue() : null;
            Long waiting = details.containsKey("threadsAwaitingConnection") ? ((Number) details.get("threadsAwaitingConnection")).longValue() : 0L;
            if ((utilizationPct != null && utilizationPct >= DB_WARNING_UTILIZATION_PCT) || waiting > 0) {
                status = status == ComponentHealthStatus.HEALTHY ? ComponentHealthStatus.WARNING : status;
                reasons.add("connection pool utilization is elevated");
            }

            return new ComponentHealthResponse(
                    "DB",
                    status,
                    String.join("; ", reasons),
                    now,
                    now,
                    null,
                    latencyMs,
                    details
            );
        } catch (Exception ex) {
            return failureComponent("DB", "PostgreSQL validation query failed", now, elapsedMillis(startedAt), ex, Map.of());
        }
    }

    private ComponentHealthResponse redisHealth(Instant now) {
        if (redisConnectionFactory == null) {
            return unknownComponent("Redis", "Redis connection factory not configured");
        }

        long startedAt = System.nanoTime();
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            long latencyMs = elapsedMillis(startedAt);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("ping", pong);
            details.put("configured", true);
            ComponentHealthStatus status = latencyMs >= REDIS_WARNING_LATENCY_MS ? ComponentHealthStatus.WARNING : ComponentHealthStatus.HEALTHY;
            String reason = latencyMs >= REDIS_WARNING_LATENCY_MS
                    ? "Redis ping succeeded but latency is elevated"
                    : "Redis ping succeeded";
            return new ComponentHealthResponse("Redis", status, reason, now, now, null, latencyMs, details);
        } catch (Exception ex) {
            return failureComponent("Redis", "Redis ping failed", now, elapsedMillis(startedAt), ex, Map.of("configured", true));
        }
    }

    private ComponentHealthResponse keycloakHealth(Instant now) {
        String baseUrl = resolveKeycloakBaseUrl();
        String realm = resolveKeycloakRealm();
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(realm)) {
            return unknownComponent("Keycloak", "Keycloak configuration not available");
        }

        long startedAt = System.nanoTime();
        try {
            URI uri = URI.create(stripTrailingSlash(baseUrl) + "/realms/" + realm + "/.well-known/openid-configuration");
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(PROBE_TIMEOUT).GET().build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            long latencyMs = elapsedMillis(startedAt);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("configured", true);
            details.put("realm", realm);
            details.put("statusCode", response.statusCode());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new ComponentHealthResponse("Keycloak", ComponentHealthStatus.HEALTHY, "Realm discovery succeeded", now, now, null, latencyMs, details);
            }
            return new ComponentHealthResponse(
                    "Keycloak",
                    ComponentHealthStatus.CRITICAL,
                    "Keycloak realm discovery returned " + response.statusCode(),
                    now,
                    null,
                    now,
                    latencyMs,
                    details
            );
        } catch (Exception ex) {
            return failureComponent("Keycloak", "Keycloak health probe failed", now, elapsedMillis(startedAt), ex, Map.of("configured", true, "realm", safe(resolveKeycloakRealm())));
        }
    }

    private ComponentHealthResponse minioHealth(Instant now) {
        String bucket = safe(minioStorageProperties == null ? null : minioStorageProperties.getBucket());
        String endpoint = safe(minioStorageProperties == null ? null : minioStorageProperties.getEndpoint());
        boolean autoCreateBucket = minioStorageProperties != null && minioStorageProperties.isAutoCreateBucket();
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(endpoint)) {
            return unknownComponent("MinIO", "MinIO configuration not available");
        }

        long startedAt = System.nanoTime();
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            long latencyMs = elapsedMillis(startedAt);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("bucket", bucket);
            details.put("autoCreateBucket", autoCreateBucket);
            details.put("configured", true);
            ComponentHealthStatus status = exists ? ComponentHealthStatus.HEALTHY : (autoCreateBucket ? ComponentHealthStatus.WARNING : ComponentHealthStatus.CRITICAL);
            String reason = exists ? "Configured bucket is reachable" : "Configured bucket is missing";
            return new ComponentHealthResponse("MinIO", status, reason, now, exists ? now : null, exists ? null : now, latencyMs, details);
        } catch (Exception ex) {
            return failureComponent("MinIO", "MinIO health probe failed", now, elapsedMillis(startedAt), ex, Map.of("configured", true, "bucket", bucket));
        }
    }

    private ComponentHealthResponse schedulerHealth(Instant now) {
        OffsetDateTime reminderHeartbeat = reminderSchedulerMonitor.lastGlobalReminderScanAt();
        OffsetDateTime aiLastRun = aiCallSchedulerMonitor.lastRunAt();
        boolean reminderEnabled = "ENABLED".equalsIgnoreCase(reminderSchedulerMonitor.reminderSchedulerStatus());
        boolean aiEnabled = aiCallSchedulerMonitor.enabled();

        if (reminderHeartbeat == null && aiLastRun == null && !reminderEnabled && !aiEnabled) {
            return unknownComponent("Scheduler", "Scheduler telemetry not available");
        }

        Instant reminderInstant = reminderHeartbeat == null ? null : reminderHeartbeat.toInstant();
        Instant aiInstant = aiLastRun == null ? null : aiLastRun.toInstant();
        Instant lastSuccess = latest(reminderInstant, aiInstant);
        Instant lastFailure = aiCallSchedulerMonitor.lastFailedCount() > 0 && aiInstant != null ? aiInstant : null;
        Long latencyMs = lastSuccess == null ? null : Duration.between(lastSuccess, now).toMillis();

        List<String> reasons = new ArrayList<>();
        ComponentHealthStatus status = ComponentHealthStatus.UNKNOWN;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reminderSchedulerEnabled", reminderEnabled);
        details.put("reminderHeartbeatAt", reminderHeartbeat);
        details.put("aiCallSchedulerEnabled", aiEnabled);
        details.put("aiLastRunAt", aiLastRun);
        details.put("aiNextRunAt", aiCallSchedulerMonitor.nextEstimatedRunAt());
        details.put("aiLastFailedCount", aiCallSchedulerMonitor.lastFailedCount());
        details.put("lockAcquireCount", schedulerLockMonitor.snapshot().values().stream().mapToLong(SchedulerLockMonitor.LockState::acquireCount).sum());
        details.put("lockSkipCount", schedulerLockMonitor.snapshot().values().stream().mapToLong(SchedulerLockMonitor.LockState::skipCount).sum());

        if (reminderHeartbeat != null) {
            long ageMinutes = Duration.between(reminderHeartbeat.toInstant(), now).toMinutes();
            details.put("reminderHeartbeatAgeMinutes", ageMinutes);
            reasons.add("Reminder heartbeat " + ageMinutes + "m ago");
            if (ageMinutes >= SCHEDULER_CRITICAL_AGE_MINUTES) {
                status = ComponentHealthStatus.CRITICAL;
                reasons.add("reminder scheduler heartbeat is stale");
            } else if (ageMinutes >= SCHEDULER_WARNING_AGE_MINUTES) {
                status = ComponentHealthStatus.WARNING;
            } else {
                status = ComponentHealthStatus.HEALTHY;
            }
        }

        if (aiLastRun != null) {
            long ageMinutes = Duration.between(aiLastRun.toInstant(), now).toMinutes();
            details.put("aiLastRunAgeMinutes", ageMinutes);
            reasons.add("AI scheduler last run " + ageMinutes + "m ago");
            if (aiCallSchedulerMonitor.lastFailedCount() > 0) {
                status = worse(status, ComponentHealthStatus.WARNING);
                reasons.add("last AI scheduler run reported failures");
                lastFailure = aiInstant;
            }
        }

        if (status == ComponentHealthStatus.UNKNOWN) {
            if (reminderHeartbeat == null && aiLastRun == null) {
                return unknownComponent("Scheduler", "Scheduler telemetry not available");
            }
            status = ComponentHealthStatus.WARNING;
            if (reasons.isEmpty()) {
                reasons.add("Scheduler telemetry is incomplete");
            }
        }

        return new ComponentHealthResponse(
                "Scheduler",
                status,
                String.join("; ", reasons),
                now,
                lastSuccess,
                lastFailure,
                latencyMs,
                details
        );
    }

    private ComponentHealthResponse geminiHealth(Instant now) {
        return aiProviderHealth("Gemini", "GEMINI", geminiLlmClient, now);
    }

    private ComponentHealthResponse groqHealth(Instant now) {
        return aiProviderHealth("Groq", "GROQ", groqLlmClient, now);
    }

    private ComponentHealthResponse documentAiHealth(Instant now) {
        Instant from = now.minus(AI_RECENT_WINDOW);
        OffsetDateTime fromTs = OffsetDateTime.ofInstant(from, ZoneOffset.UTC);
        OffsetDateTime toTs = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        List<ClinicalDocumentEntity> recentDocuments = clinicalDocumentRepository.findByCreatedAtBetween(fromTs, toTs);
        if (recentDocuments.isEmpty()) {
            return unknownComponent("Document AI", "No document extraction activity in the last " + AI_RECENT_WINDOW.toHours() + "h");
        }

        long successful = recentDocuments.stream().filter(this::isDocumentAiSuccess).count();
        long failed = recentDocuments.stream().filter(this::isDocumentAiFailure).count();
        long pending = recentDocuments.stream().filter(this::isDocumentAiPending).count();
        long total = recentDocuments.size();
        ClinicalDocumentEntity latest = recentDocuments.stream()
                .filter(document -> document.getCreatedAt() != null)
                .max(Comparator.comparing(ClinicalDocumentEntity::getCreatedAt))
                .orElse(null);
        ClinicalDocumentEntity lastSuccess = recentDocuments.stream()
                .filter(this::isDocumentAiSuccess)
                .filter(document -> document.getCreatedAt() != null)
                .max(Comparator.comparing(ClinicalDocumentEntity::getCreatedAt))
                .orElse(null);
        ClinicalDocumentEntity lastFailure = recentDocuments.stream()
                .filter(this::isDocumentAiFailure)
                .filter(document -> document.getCreatedAt() != null)
                .max(Comparator.comparing(ClinicalDocumentEntity::getCreatedAt))
                .orElse(null);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("windowHours", AI_RECENT_WINDOW.toHours());
        details.put("totalDocuments", total);
        details.put("successfulExtractions", successful);
        details.put("failedExtractions", failed);
        details.put("pendingExtractions", pending);
        details.put("lastDocumentProcessedAt", latest == null ? null : latest.getCreatedAt());
        details.put("provider", latest == null ? null : safe(latest.getAiExtractionProvider()));
        details.put("model", latest == null ? null : safe(latest.getAiExtractionModel()));
        details.put("lastStatus", latest == null ? null : latest.getAiExtractionStatus());
        details.put("lastSummary", latest == null ? null : sanitizeExtractionSummary(latest.getAiExtractionSummary()));

        if (latest == null) {
            return unknownComponent("Document AI", "Document extraction telemetry not available");
        }

        ComponentHealthStatus status;
        String reason;
        if (successful > 0 && failed == 0) {
            status = ComponentHealthStatus.HEALTHY;
            reason = "Recent extraction telemetry shows successful processing";
        } else if (successful > 0) {
            status = ComponentHealthStatus.WARNING;
            reason = "Recent extraction telemetry shows mixed results";
        } else if (failed > 0) {
            status = ComponentHealthStatus.CRITICAL;
            reason = "Recent extraction telemetry shows failures";
        } else {
            status = ComponentHealthStatus.UNKNOWN;
            reason = "Configured, but no recent functional evidence";
        }

        return new ComponentHealthResponse(
                "Document AI",
                status,
                reason,
                now,
                lastSuccess == null || lastSuccess.getCreatedAt() == null ? null : lastSuccess.getCreatedAt().toInstant(),
                lastFailure == null || lastFailure.getCreatedAt() == null ? null : lastFailure.getCreatedAt().toInstant(),
                null,
                details
        );
    }

    private ComponentHealthResponse backupHealth(Instant now) {
        return new ComponentHealthResponse(
                "Backups",
                ComponentHealthStatus.UNKNOWN,
                "Structured backup telemetry not available",
                now,
                null,
                null,
                null,
                Map.of("gap", "Structured backup status will be added in Phase 1B")
        );
    }

    private ComponentHealthResponse releaseHealth(Instant now) {
        PlatformOperationsReleaseResponse release = releaseInfo(now);
        int present = 0;
        if (StringUtils.hasText(release.releaseTag())) present++;
        if (StringUtils.hasText(release.gitCommit())) present++;
        if (StringUtils.hasText(release.environment())) present++;
        if (release.buildTimestamp() != null) present++;
        if (release.deploymentTimestamp() != null) present++;
        if (StringUtils.hasText(release.apiVersion())) present++;

        ComponentHealthStatus status;
        String reason;
        if (present == 0) {
            status = ComponentHealthStatus.UNKNOWN;
            reason = "Build metadata not injected";
        } else if (present < 6) {
            status = ComponentHealthStatus.WARNING;
            reason = buildReleaseReason(release);
        } else {
            status = ComponentHealthStatus.HEALTHY;
            reason = buildReleaseReason(release);
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("releaseTag", release.releaseTag());
        details.put("gitCommit", release.gitCommit());
        details.put("environment", release.environment());
        details.put("buildTimestamp", release.buildTimestamp());
        details.put("deploymentTimestamp", release.deploymentTimestamp());
        details.put("apiVersion", release.apiVersion());

        return new ComponentHealthResponse("Release", status, reason, now, now, null, 0L, details);
    }

    private ComponentHealthResponse aiProviderHealth(String component, String providerName, ObjectProvider<LlmClient> provider, Instant now) {
        Instant from = now.minus(AI_RECENT_WINDOW);
        OffsetDateTime fromTs = OffsetDateTime.ofInstant(from, ZoneOffset.UTC);
        OffsetDateTime toTs = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        List<AiInvocationLogEntity> recentLogs = aiInvocationLogRepository.findByProviderNameAndCreatedAtBetween(providerName, fromTs, toTs);
        LlmClient client = provider == null ? null : provider.getIfAvailable();
        boolean configured = client != null || !recentLogs.isEmpty();
        boolean fallbackEnabled = client != null;
        AiInvocationLogEntity latest = recentLogs.stream()
                .filter(log -> log.getCreatedAt() != null)
                .max(Comparator.comparing(AiInvocationLogEntity::getCreatedAt))
                .orElse(null);
        AiInvocationLogEntity lastSuccessLog = recentLogs.stream()
                .filter(this::isAiSuccess)
                .filter(log -> log.getCreatedAt() != null)
                .max(Comparator.comparing(AiInvocationLogEntity::getCreatedAt))
                .orElse(null);
        AiInvocationLogEntity lastFailureLog = recentLogs.stream()
                .filter(log -> !isAiSuccess(log))
                .filter(log -> log.getCreatedAt() != null)
                .max(Comparator.comparing(AiInvocationLogEntity::getCreatedAt))
                .orElse(null);

        if (!configured && recentLogs.isEmpty()) {
            return unknownComponent(component, "Telemetry not available");
        }
        if (latest == null) {
            return unknownComponent(component, configured ? "Configured, but no recent functional evidence" : "Telemetry not available");
        }

        long total = recentLogs.size();
        long successful = recentLogs.stream().filter(this::isAiSuccess).count();
        long failed = total - successful;
        double successRate = total == 0 ? 0.0 : (successful * 100.0 / total);
        double failureRate = total == 0 ? 0.0 : (failed * 100.0 / total);
        long latencyMs = Math.round(recentLogs.stream()
                .map(AiInvocationLogEntity::getLatencyMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(-1.0));
        String model = resolveAiModel(providerName, client, latest);
        boolean providerReady = isProviderReady(client);
        ComponentHealthStatus status;
        String reason;
        if (successful > 0 && failed == 0) {
            status = ComponentHealthStatus.HEALTHY;
            reason = lastSuccessLog == null ? "Recent successful invocation" : "Recent successful invocation at " + lastSuccessLog.getCreatedAt();
        } else if (successful > 0) {
            status = ComponentHealthStatus.WARNING;
            reason = "Recent telemetry shows mixed results";
        } else {
            status = ComponentHealthStatus.CRITICAL;
            reason = "Recent telemetry shows repeated failures";
        }
        if (latencyMs >= AI_WARNING_LATENCY_MS && status == ComponentHealthStatus.HEALTHY) {
            status = ComponentHealthStatus.WARNING;
            reason = "Recent telemetry is healthy but latency is elevated";
        }
        if (!providerReady && client != null && status == ComponentHealthStatus.HEALTHY) {
            status = ComponentHealthStatus.WARNING;
            reason = "Provider reports limited availability";
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("configured", configured);
        details.put("fallbackEnabled", fallbackEnabled);
        details.put("provider", providerName);
        details.put("model", model);
        details.put("recentInvocationCount", total);
        details.put("recentSuccessCount", successful);
        details.put("recentFailureCount", failed);
        details.put("recentSuccessRatePct", roundOneDecimal(successRate));
        details.put("recentErrorRatePct", roundOneDecimal(failureRate));
        details.put("recentLatencyMs", latencyMs < 0 ? null : latencyMs);
        details.put("lastStatus", latest.getStatus());
        details.put("lastSuccessAt", lastSuccessLog == null ? null : lastSuccessLog.getCreatedAt());
        details.put("lastFailureAt", lastFailureLog == null ? null : lastFailureLog.getCreatedAt());
        details.put("providerReady", providerReady);
        details.put("providerDiagnostic", client == null ? null : safe(client.availabilityDiagnostic()));

        return new ComponentHealthResponse(
                component,
                status,
                reason,
                now,
                lastSuccessLog == null || lastSuccessLog.getCreatedAt() == null ? null : lastSuccessLog.getCreatedAt().toInstant(),
                lastFailureLog == null || lastFailureLog.getCreatedAt() == null ? null : lastFailureLog.getCreatedAt().toInstant(),
                latencyMs < 0 ? null : latencyMs,
                details
        );
    }

    private PlatformOperationsAiSummaryResponse aiSummary(Instant now) {
        Instant from = now.minus(AI_RECENT_WINDOW);
        OffsetDateTime fromTs = OffsetDateTime.ofInstant(from, ZoneOffset.UTC);
        OffsetDateTime toTs = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        List<AiInvocationLogEntity> recentLogs = aiInvocationLogRepository.findByCreatedAtBetween(fromTs, toTs);
        if (recentLogs.isEmpty()) {
            return new PlatformOperationsAiSummaryResponse(0, 0, 0, null, Map.of(), Map.of());
        }

        long total = recentLogs.size();
        long successful = recentLogs.stream().filter(this::isAiSuccess).count();
        long failed = recentLogs.stream().filter(log -> !isAiSuccess(log)).count();
        Instant lastActivityAt = recentLogs.stream()
                .map(AiInvocationLogEntity::getCreatedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(OffsetDateTime::toInstant)
                .orElse(null);

        Map<String, Long> callsByProvider = recentLogs.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        log -> normalizeProviderName(log.getProviderName()),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.counting()));
        Map<String, Long> callsByStatus = recentLogs.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        log -> safe(log.getStatus()),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.counting()));
        return new PlatformOperationsAiSummaryResponse(total, successful, failed, lastActivityAt, callsByProvider, callsByStatus);
    }

    private boolean isRequiredCoreComponent(String component) {
        return Set.of("API", "DB", "Keycloak", "Redis", "MinIO").contains(component);
    }

    private boolean isAiSuccess(AiInvocationLogEntity log) {
        if (log == null) {
            return false;
        }
        String status = safe(log.getStatus()).toUpperCase(Locale.ROOT);
        return Set.of("SUCCESS", "COMPLETED", "SUCCEEDED", "OK").contains(status);
    }

    private boolean isDocumentAiSuccess(ClinicalDocumentEntity document) {
        if (document == null) {
            return false;
        }
        String status = safe(document.getAiExtractionStatus()).toUpperCase(Locale.ROOT);
        return Set.of("COMPLETED", "VERIFIED", "ACCEPTED", "APPROVED").contains(status);
    }

    private boolean isDocumentAiFailure(ClinicalDocumentEntity document) {
        if (document == null) {
            return false;
        }
        String status = safe(document.getAiExtractionStatus()).toUpperCase(Locale.ROOT);
        return "FAILED".equals(status);
    }

    private boolean isDocumentAiPending(ClinicalDocumentEntity document) {
        if (document == null) {
            return false;
        }
        String status = safe(document.getAiExtractionStatus()).toUpperCase(Locale.ROOT);
        return Set.of("QUEUED", "PROCESSING", "NOT_STARTED", "PENDING").contains(status);
    }

    private String resolveAiModel(String providerName, LlmClient client, AiInvocationLogEntity latestLog) {
        String latestModel = latestLog == null ? null : trimToNull(latestLog.getModelName());
        if (StringUtils.hasText(latestModel)) {
            return latestModel;
        }
        String propertyKey = switch (normalizeProviderName(providerName)) {
            case "GEMINI" -> firstText(
                    environment.getProperty("clinic.ai.gemini.model"),
                    environment.getProperty("clinic.llm.gemini.model"));
            case "GROQ" -> firstText(
                    environment.getProperty("clinic.ai.groq.model"),
                    environment.getProperty("groq.model"));
            default -> null;
        };
        if (StringUtils.hasText(propertyKey)) {
            return propertyKey;
        }
        return client == null ? null : normalizeProviderName(client.providerName());
    }

    private boolean isProviderReady(LlmClient client) {
        if (client == null) {
            return false;
        }
        try {
            return client.isAvailable();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String normalizeProviderName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (StringUtils.hasText(trimmed)) {
                return trimmed;
            }
        }
        return null;
    }

    private String sanitizeExtractionSummary(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String sanitized = safe(value);
        if (sanitized == null) {
            return null;
        }
        if (sanitized.length() <= 160) {
            return sanitized;
        }
        return sanitized.substring(0, 157) + "...";
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String buildReleaseReason(PlatformOperationsReleaseResponse release) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(release.releaseTag())) {
            parts.add(release.releaseTag());
        }
        if (StringUtils.hasText(release.gitCommit())) {
            parts.add(release.gitCommit());
        }
        if (StringUtils.hasText(release.environment())) {
            parts.add(release.environment());
        }
        if (parts.isEmpty()) {
            return "Build metadata not injected";
        }
        return String.join(" · ", parts);
    }

    private PlatformOperationsReleaseResponse releaseInfo(Instant now) {
        return new PlatformOperationsReleaseResponse(
                trimToNull(releaseProperties == null ? null : releaseProperties.releaseTag()),
                trimToNull(releaseProperties == null ? null : releaseProperties.gitCommit()),
                trimToNull(releaseProperties == null ? null : releaseProperties.environment()),
                parseInstant(releaseProperties == null ? null : releaseProperties.buildTimestamp()),
                parseInstant(releaseProperties == null ? null : releaseProperties.deploymentTimestamp()),
                trimToNull(resolveApiVersion())
        );
    }

    private PlatformOperationsRuntimeResponse runtimeInfo(Instant now) {
        var runtime = ManagementFactory.getRuntimeMXBean();
        long uptimeMs = runtime.getUptime();
        Instant startTime = Instant.ofEpochMilli(runtime.getStartTime());
        return new PlatformOperationsRuntimeResponse(true, "UP", uptimeMs, startTime, now);
    }

    private ComponentHealthResponse unknownComponent(String component, String reason) {
        Instant now = Instant.now();
        return new ComponentHealthResponse(component, ComponentHealthStatus.UNKNOWN, reason, now, null, null, null, Map.of());
    }

    private ComponentHealthResponse failureComponent(String component, String reason, Instant now, long latencyMs, Exception ex, Map<String, Object> details) {
        Map<String, Object> safeDetails = new LinkedHashMap<>(details);
        safeDetails.put("error", sanitize(ex == null ? null : ex.getMessage()));
        return new ComponentHealthResponse(component, ComponentHealthStatus.CRITICAL, reason, now, null, now, latencyMs, safeDetails);
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

    private String resolveApiVersion() {
        String packageVersion = PlatformOperationsOverviewService.class.getPackage() == null
                ? null
                : PlatformOperationsOverviewService.class.getPackage().getImplementationVersion();
        if (StringUtils.hasText(packageVersion)) {
            return packageVersion;
        }
        return trimToNull(environment.getProperty("jeevanam.api-version"));
    }

    private String resolveKeycloakBaseUrl() {
        String serverUrl = trimToNull(environment.getProperty("clinic.keycloak.admin.serverUrl"));
        if (StringUtils.hasText(serverUrl)) {
            return stripTrailingSlash(serverUrl);
        }
        String issuer = trimToNull(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"));
        if (!StringUtils.hasText(issuer)) {
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
        if (StringUtils.hasText(targetRealm)) {
            return targetRealm;
        }
        String issuer = trimToNull(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"));
        if (!StringUtils.hasText(issuer)) {
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

    private Instant parseInstant(String value) {
        String trimmed = trimToNull(value);
        if (!StringUtils.hasText(trimmed)) {
            return null;
        }
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ex) {
            try {
                return OffsetDateTime.parse(trimmed).toInstant();
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    private String formatDuration(Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + remainingSeconds + "s";
        }
        return remainingSeconds + "s";
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

    private String safe(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("[\\r\\n\\t]+", " ").trim();
    }

    private String sanitize(String value) {
        return safe(value);
    }
}
