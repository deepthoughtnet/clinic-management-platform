package com.deepthoughtnet.clinic.api.platform.commercial.entitlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.support.PostgresTestContainerSupport;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class CommercialEffectiveEntitlementMigrationTest extends PostgresTestContainerSupport {

    @Test
    void migrationCreatesEffectiveEntitlementTablesAndIndexesCleanly() throws Exception {
        try (ManagedSchema schema = createSchema()) {
            Flyway flyway = flyway(schema.name());
            flyway.migrate();
            flyway.migrate();

            try (Connection connection = connection()) {
                assertThat(tableExists(connection, schema.name(), "commercial_tenant_entitlement_overrides")).isTrue();
                assertThat(tableExists(connection, schema.name(), "commercial_effective_entitlement_snapshots")).isTrue();
                assertThat(tableExists(connection, schema.name(), "commercial_effective_entitlement_events")).isTrue();
                assertThat(columnType(connection, schema.name(), "commercial_tenant_entitlement_overrides", "submitted_at")).isEqualTo("timestamp with time zone");
                assertThat(columnType(connection, schema.name(), "commercial_tenant_entitlement_overrides", "reviewed_at")).isEqualTo("timestamp with time zone");
                assertThat(columnType(connection, schema.name(), "commercial_effective_entitlement_snapshots", "canonical_snapshot_json")).isEqualTo("jsonb");
                assertThat(columnType(connection, schema.name(), "commercial_effective_entitlement_events", "payload_json")).isEqualTo("jsonb");
                assertThat(indexExists(connection, schema.name(), "uq_commercial_effective_snapshots_current_per_tenant")).isTrue();
                assertThat(indexExists(connection, schema.name(), "uq_commercial_entitlement_overrides_active_window")).isTrue();
                assertThat(flyway.info().current()).isNotNull();
                assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo(latestResolvedVersion(flyway));
            }
        }
    }

    private static Flyway flyway(String schema) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl() + "?currentSchema=" + schema, POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .load();
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static ManagedSchema createSchema() throws Exception {
        String schema = "commercial_entitlements_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = connection();
             var statement = connection.createStatement()) {
            statement.execute("create schema if not exists " + schema);
        }
        return new ManagedSchema(schema);
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

    private static String columnType(Connection connection, String schema, String table, String column) throws Exception {
        try (var ps = connection.prepareStatement("""
                select data_type
                from information_schema.columns
                where table_schema = ?
                  and table_name = ?
                  and column_name = ?
                """)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, column);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return rs.getString(1);
            }
        }
    }

    private static boolean indexExists(Connection connection, String schema, String indexName) throws Exception {
        try (var ps = connection.prepareStatement("""
                select 1
                from pg_indexes
                where schemaname = ?
                  and indexname = ?
                """)) {
            ps.setString(1, schema);
            ps.setString(2, indexName);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String latestResolvedVersion(Flyway flyway) {
        return Arrays.stream(flyway.info().all())
                .map(MigrationInfo::getVersion)
                .filter(version -> version != null)
                .max(MigrationVersion::compareTo)
                .map(MigrationVersion::getVersion)
                .orElseThrow(() -> new IllegalStateException("No Flyway migrations were resolved"));
    }

    private record ManagedSchema(String name) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            try (Connection connection = connection();
                 var statement = connection.createStatement()) {
                statement.execute("drop schema if exists " + name + " cascade");
            }
        }
    }
}
