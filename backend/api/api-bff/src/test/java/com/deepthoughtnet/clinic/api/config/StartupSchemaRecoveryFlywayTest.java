package com.deepthoughtnet.clinic.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.config.db.DatabaseSafetyGuard;
import com.deepthoughtnet.clinic.api.support.PostgresTestContainerSupport;
import java.util.Arrays;
import java.util.Objects;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class StartupSchemaRecoveryFlywayTest extends PostgresTestContainerSupport {
    private static final String BASE_URL = POSTGRES.getJdbcUrl();
    private static final String USER = POSTGRES.getUsername();
    private static final String PASSWORD = POSTGRES.getPassword();

    @Test
    void migrateFromEmptySchemaCreatesCriticalTables() throws Exception {
        try (ManagedSchema schema = createSchema()) {
            Flyway flyway = flyway(schema.name());
            flyway.migrate();

            try (Connection connection = connection()) {
                assertThat(tableExists(connection, schema.name(), "tenant_plans")).isTrue();
                assertThat(tableExists(connection, schema.name(), "tenants")).isTrue();
                assertThat(columnExists(connection, schema.name(), "tenants", "module_carepilot")).isTrue();
                assertThat(columnExists(connection, schema.name(), "tenants", "public_listing_enabled")).isTrue();
                assertThat(tableExists(connection, schema.name(), "app_users")).isTrue();
                assertThat(tableExists(connection, schema.name(), "clinical_ai_jobs")).isTrue();
                assertThat(tableExists(connection, schema.name(), "prescription_safety_reviews")).isTrue();
                assertThat(tableExists(connection, schema.name(), "help_pages")).isTrue();
                assertThat(tableExists(connection, schema.name(), "help_sections")).isTrue();
                assertThat(tableExists(connection, schema.name(), "help_content")).isTrue();
                assertThat(tableExists(connection, schema.name(), "help_attachments")).isTrue();
                assertThat(tableExists(connection, schema.name(), "notification_outbox")).isTrue();
                assertThat(columnExists(connection, schema.name(), "notification_outbox", "next_retry_at")).isTrue();
                assertThat(tableExists(connection, schema.name(), "module_business_events")).isTrue();
                assertThat(tableExists(connection, schema.name(), "module_business_event_listener_jobs")).isTrue();
            }
        }
    }

    @Test
    void migrateRepairsMissingTablesOnAnAlreadyMigratedSchema() throws Exception {
        try (ManagedSchema schema = createSchema()) {
            flyway(schema.name(), "103").migrate();
            dropTable(schema.name(), "help_pages");
            dropTable(schema.name(), "help_sections");
            dropTable(schema.name(), "help_content");
            dropTable(schema.name(), "help_attachments");

            Flyway repairFlyway = flyway(schema.name());
            repairFlyway.migrate();

            try (Connection connection = connection()) {
                assertThat(tableExists(connection, schema.name(), "help_pages")).isTrue();
                assertThat(tableExists(connection, schema.name(), "help_sections")).isTrue();
                assertThat(tableExists(connection, schema.name(), "help_content")).isTrue();
                assertThat(tableExists(connection, schema.name(), "help_attachments")).isTrue();
                assertThat(tableExists(connection, schema.name(), "clinical_ai_jobs")).isTrue();
                assertThat(tableExists(connection, schema.name(), "prescription_safety_reviews")).isTrue();
                assertThat(tableExists(connection, schema.name(), "notification_outbox")).isTrue();
                assertThat(columnExists(connection, schema.name(), "notification_outbox", "next_retry_at")).isTrue();
                assertThat(tableExists(connection, schema.name(), "module_business_events")).isTrue();
                assertThat(tableExists(connection, schema.name(), "module_business_event_listener_jobs")).isTrue();
                assertThat(repairFlyway.info().current()).isNotNull();
                assertThat(repairFlyway.info().current().getVersion().getVersion()).isEqualTo(latestResolvedVersion(repairFlyway));
            }
        }
    }

    @Test
    void migrateRepairsPartialHelpSchemaOnAnAlreadyMigratedSchema() throws Exception {
        try (ManagedSchema schema = createSchema()) {
            flyway(schema.name(), "103").migrate();
            dropTable(schema.name(), "help_content");
            dropTable(schema.name(), "help_attachments");

            Flyway repairFlyway = flyway(schema.name());
            repairFlyway.migrate();

            try (Connection connection = connection()) {
                assertThat(tableExists(connection, schema.name(), "help_pages")).isTrue();
                assertThat(tableExists(connection, schema.name(), "help_sections")).isTrue();
                assertThat(tableExists(connection, schema.name(), "help_content")).isTrue();
                assertThat(tableExists(connection, schema.name(), "help_attachments")).isTrue();
                assertThat(tableExists(connection, schema.name(), "notification_outbox")).isTrue();
                assertThat(columnExists(connection, schema.name(), "notification_outbox", "next_retry_at")).isTrue();
                assertThat(tableExists(connection, schema.name(), "module_business_events")).isTrue();
                assertThat(tableExists(connection, schema.name(), "module_business_event_listener_jobs")).isTrue();
                assertThat(repairFlyway.info().current()).isNotNull();
                assertThat(repairFlyway.info().current().getVersion().getVersion()).isEqualTo(latestResolvedVersion(repairFlyway));
            }
        }
    }

    @Test
    void startupContextSchemaIsRemovedAfterHelperCloses() throws Exception {
        String createdSchema;
        try (ManagedSchema schema = createSchema()) {
            createdSchema = schema.name();
            try (Connection connection = connection()) {
                assertThat(schemaExists(connection, createdSchema)).isTrue();
            }
        }

        try (Connection connection = connection()) {
            assertThat(schemaExists(connection, createdSchema)).isFalse();
        }
    }

    @AfterAll
    static void noStartupContextSchemasRemainAfterSuite() throws Exception {
        DatabaseSafetyGuard.assertTestDatabase(dataSource());
        try (Connection connection = connection()) {
            assertThat(countStartupContextSchemas(connection)).isZero();
        }
    }

    private static Flyway flyway(String schema) {
        return flyway(schema, null);
    }

    private static Flyway flyway(String schema, String target) {
        var configure = Flyway.configure()
                .dataSource(BASE_URL + "?currentSchema=" + schema, USER, PASSWORD)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration");
        if (target != null) {
            configure.target(target);
        }
        return configure.load();
    }

    private static ManagedSchema createSchema() throws Exception {
        DatabaseSafetyGuard.assertTestDatabase(dataSource());
        String schema = "startup_context_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute("create schema if not exists " + schema);
        }
        return new ManagedSchema(schema);
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(BASE_URL, USER, PASSWORD);
    }

    private static boolean tableExists(Connection connection, String schema, String table) throws Exception {
        try (var ps = connection.prepareStatement("""
                select 1
                from information_schema.tables
                where table_schema = ?
                  and table_name = ?
                """)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean columnExists(Connection connection, String schema, String table, String column) throws Exception {
        try (var ps = connection.prepareStatement("""
                select 1
                from information_schema.columns
                where table_schema = ?
                  and table_name = ?
                  and column_name = ?
                """)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, column);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void dropTable(String schema, String table) throws Exception {
        DatabaseSafetyGuard.assertTestDatabase(dataSource());
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists " + schema + "." + table + " cascade");
        }
    }

    private static boolean schemaExists(Connection connection, String schema) throws Exception {
        try (var ps = connection.prepareStatement("""
                select 1
                from information_schema.schemata
                where schema_name = ?
                """)) {
            ps.setString(1, schema);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static long countStartupContextSchemas(Connection connection) throws Exception {
        try (var ps = connection.prepareStatement("""
                select count(*)
                from information_schema.schemata
                where schema_name like 'startup_context_%'
                """)) {
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return 0L;
                }
                return rs.getLong(1);
            }
        }
    }

    private static String latestResolvedVersion(Flyway flyway) {
        return Arrays.stream(flyway.info().all())
                .map(MigrationInfo::getVersion)
                .filter(Objects::nonNull)
                .max(MigrationVersion::compareTo)
                .map(MigrationVersion::getVersion)
                .orElseThrow(() -> new IllegalStateException("No Flyway migrations were resolved"));
    }

        private record ManagedSchema(String name) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            DatabaseSafetyGuard.assertTestDatabase(StartupSchemaRecoveryFlywayTest.dataSource());
            try (Connection connection = StartupSchemaRecoveryFlywayTest.connection();
                 Statement statement = connection.createStatement()) {
                statement.execute("drop schema if exists " + name + " cascade");
            }
        }
    }
}
