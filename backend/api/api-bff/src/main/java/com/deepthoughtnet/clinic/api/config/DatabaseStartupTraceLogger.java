package com.deepthoughtnet.clinic.api.config;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseStartupTraceLogger {
    private static final Logger log = LoggerFactory.getLogger(DatabaseStartupTraceLogger.class);

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    public DatabaseStartupTraceLogger(JdbcTemplate jdbcTemplate, Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logDatabaseState() {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap("""
                    select current_database() as database_name,
                           current_schema() as schema_name,
                           current_setting('search_path') as search_path
                    """);
            boolean flywayEnabled = environment.getProperty("spring.flyway.enabled", Boolean.class, Boolean.TRUE);
            String flywaySchema = environment.getProperty("spring.flyway.default-schema",
                    environment.getProperty("spring.flyway.schemas", "<default>"));
            log.info("[DB-STARTUP-TRACE] database={} schema={} flywayEnabled={} flywaySchema={} searchPath={}",
                    row.get("database_name"),
                    row.get("schema_name"),
                    flywayEnabled,
                    flywaySchema,
                    row.get("search_path"));
        } catch (Exception ex) {
            log.debug("Skipping database startup trace: {}", ex.toString());
        }
    }
}
