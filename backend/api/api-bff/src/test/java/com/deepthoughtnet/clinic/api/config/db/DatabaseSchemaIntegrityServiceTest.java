package com.deepthoughtnet.clinic.api.config.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.support.PostgresTestContainerSupport;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseSchemaIntegrityServiceTest extends PostgresTestContainerSupport {
    private final DataSource dataSource = dataSource();
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    private final DatabaseSchemaIntegrityService service = new DatabaseSchemaIntegrityService(jdbcTemplate);

    @BeforeEach
    void resetDatabase() {
        DatabaseSafetyGuard.assertTestDatabase(dataSource);
        jdbcTemplate.execute("drop schema if exists public cascade");
        jdbcTemplate.execute("create schema public");
        flyway().migrate();
    }

    @Test
    void completeSchemaPassesReadinessCheck() {
        DatabaseSchemaIntegrityReport report = service.inspectNow();

        assertThat(report.healthy()).isTrue();
        assertThat(report.missingTables()).isEmpty();
        assertThat(report.missingColumns()).isEmpty();
    }

    @Test
    void missingPatientsTableFailsReadinessCheck() {
        jdbcTemplate.execute("drop table if exists public.patients cascade");

        DatabaseSchemaIntegrityReport report = service.inspectNow();

        assertThat(report.healthy()).isFalse();
        assertThat(report.missingTables()).contains("patients");
    }

    @Test
    void missingNextRetryAtColumnFailsReadinessCheck() {
        jdbcTemplate.execute("alter table if exists public.notification_outbox drop column if exists next_retry_at");

        DatabaseSchemaIntegrityReport report = service.inspectNow();

        assertThat(report.healthy()).isFalse();
        assertThat(report.missingColumns()).containsKey("notification_outbox");
        assertThat(report.missingColumns().get("notification_outbox")).contains("next_retry_at");
    }

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();
    }
}
