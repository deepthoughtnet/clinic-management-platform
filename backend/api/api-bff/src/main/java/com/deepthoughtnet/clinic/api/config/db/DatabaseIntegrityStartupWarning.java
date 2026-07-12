package com.deepthoughtnet.clinic.api.config.db;

import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class DatabaseIntegrityStartupWarning {
    private static final Logger log = LoggerFactory.getLogger(DatabaseIntegrityStartupWarning.class);

    private final DatabaseSchemaIntegrityService integrityService;
    private final Environment environment;

    public DatabaseIntegrityStartupWarning(DatabaseSchemaIntegrityService integrityService, Environment environment) {
        this.integrityService = integrityService;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logWarningIfNeeded() {
        DatabaseSchemaIntegrityReport report = integrityService.inspect(true);
        if (report.healthy() && !report.matureButEmpty()) {
            return;
        }

        String missingColumns = report.missingColumns().entrySet().stream()
                .map(entry -> entry.getKey() + "." + String.join(",", entry.getValue()))
                .collect(Collectors.joining(";"));
        log.warn("[DB-INTEGRITY-FAIL] profile={} schemaVersion={} tenantCount={} appUserCount={} patientCount={} missingTables={} missingColumns={} action=manual recovery required",
                String.join(",", environment.getActiveProfiles()),
                report.schemaVersion(),
                report.tenantCount(),
                report.appUserCount(),
                report.patientCount(),
                report.missingTables(),
                missingColumns);
    }
}
