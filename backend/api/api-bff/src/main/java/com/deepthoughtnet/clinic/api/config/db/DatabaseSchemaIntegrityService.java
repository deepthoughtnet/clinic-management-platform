package com.deepthoughtnet.clinic.api.config.db;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DatabaseSchemaIntegrityService {
    private static final List<String> CRITICAL_TABLES = List.of(
            "tenants",
            "app_users",
            "patients",
            "notification_outbox",
            "clinical_ai_jobs",
            "help_pages",
            "prescription_safety_reviews"
    );
    private static final Map<String, List<String>> CRITICAL_COLUMNS = Map.of(
            "notification_outbox", List.of("next_retry_at"),
            "tenants", List.of("id", "code"),
            "app_users", List.of("id", "tenant_id"),
            "patients", List.of("id", "tenant_id")
    );

    private final JdbcTemplate jdbcTemplate;
    private final AtomicReference<CachedReport> cachedReport = new AtomicReference<>();

    public DatabaseSchemaIntegrityService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DatabaseSchemaIntegrityReport inspect(boolean useCache) {
        if (useCache) {
            CachedReport cached = cachedReport.get();
            if (cached != null && !cached.isExpired()) {
                return cached.report();
            }
        }

        DatabaseSchemaIntegrityReport report = inspectNow();
        cachedReport.set(new CachedReport(report));
        return report;
    }

    public DatabaseSchemaIntegrityReport inspectNow() {
        List<String> missingTables = new ArrayList<>();
        for (String table : CRITICAL_TABLES) {
            if (!tableExists(table)) {
                missingTables.add(table);
            }
        }

        Map<String, List<String>> missingColumns = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : CRITICAL_COLUMNS.entrySet()) {
            String table = entry.getKey();
            if (!tableExists(table)) {
                continue;
            }
            List<String> missing = entry.getValue().stream()
                    .filter(column -> !columnExists(table, column))
                    .collect(Collectors.toList());
            if (!missing.isEmpty()) {
                missingColumns.put(table, missing);
            }
        }

        Long tenantCount = countIfPresent("tenants");
        Long appUserCount = countIfPresent("app_users");
        Long patientCount = countIfPresent("patients");
        Integer schemaVersion = currentFlywayVersion();

        boolean healthy = missingTables.isEmpty() && missingColumns.isEmpty();
        boolean matureButEmpty = schemaVersion != null
                && schemaVersion >= 104
                && safeCount(tenantCount) == 0L
                && safeCount(appUserCount) == 0L
                && safeCount(patientCount) == 0L;

        return new DatabaseSchemaIntegrityReport(
                healthy,
                matureButEmpty,
                schemaVersion,
                tenantCount,
                appUserCount,
                patientCount,
                missingTables,
                missingColumns
        );
    }

    private boolean tableExists(String tableName) {
        Integer value = jdbcTemplate.queryForObject("""
                select case when to_regclass('public.' || ?) is null then 0 else 1 end
                """, Integer.class, tableName);
        return value != null && value == 1;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer value = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = ?
                  and column_name = ?
                """, Integer.class, tableName, columnName);
        return value != null && value > 0;
    }

    private Long countIfPresent(String tableName) {
        if (!tableExists(tableName)) {
            return null;
        }
        return jdbcTemplate.queryForObject("select count(*) from public." + tableName, Long.class);
    }

    private Integer currentFlywayVersion() {
        if (!tableExists("flyway_schema_history")) {
            return null;
        }
        String version = jdbcTemplate.queryForObject("""
                select version
                from public.flyway_schema_history
                where success = true
                  and version is not null
                order by installed_rank desc
                limit 1
                """, String.class);
        if (!StringUtils.hasText(version)) {
            return null;
        }
        try {
            return Integer.parseInt(version.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private long safeCount(Long count) {
        return count == null ? -1L : count;
    }

    private record CachedReport(DatabaseSchemaIntegrityReport report, long expiresAtMillis) {
        private static final long TTL_MILLIS = 30_000L;

        private CachedReport(DatabaseSchemaIntegrityReport report) {
            this(report, System.currentTimeMillis() + TTL_MILLIS);
        }

        private boolean isExpired() {
            return System.currentTimeMillis() >= expiresAtMillis;
        }
    }
}
