package com.deepthoughtnet.clinic.api.config.db;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("databaseSchema")
public class DatabaseSchemaHealthIndicator implements HealthIndicator {
    private final DatabaseSchemaIntegrityService integrityService;

    public DatabaseSchemaHealthIndicator(DatabaseSchemaIntegrityService integrityService) {
        this.integrityService = integrityService;
    }

    @Override
    public Health health() {
        DatabaseSchemaIntegrityReport report = integrityService.inspect(true);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("schemaVersion", report.schemaVersion());
        details.put("tenantCount", report.tenantCount());
        details.put("appUserCount", report.appUserCount());
        details.put("patientCount", report.patientCount());
        details.put("missingTables", report.missingTables());
        details.put("missingColumns", report.missingColumns());
        details.put("matureButEmpty", report.matureButEmpty());
        details.put("healthy", report.healthy());

        if (report.healthy()) {
            return Health.up().withDetails(details).build();
        }
        return Health.down().withDetails(details).build();
    }
}
