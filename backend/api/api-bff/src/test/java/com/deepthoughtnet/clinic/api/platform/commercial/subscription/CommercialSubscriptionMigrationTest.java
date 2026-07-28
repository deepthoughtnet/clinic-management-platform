package com.deepthoughtnet.clinic.api.platform.commercial.subscription;

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

class CommercialSubscriptionMigrationTest extends PostgresTestContainerSupport {
    @Test
    void migrationCreatesCommercialSubscriptionTablesAndAppliesCleanly() throws Exception {
        try (ManagedSchema schema = createSchema()) {
            Flyway flyway = flyway(schema.name());
            flyway.migrate();
            flyway.migrate();

            try (Connection connection = connection()) {
                assertThat(tableExists(connection, schema.name(), "commercial_tenant_subscriptions")).isTrue();
                assertThat(tableExists(connection, schema.name(), "commercial_subscription_events")).isTrue();
                assertThat(countRows(connection, schema.name(), "commercial_tenant_subscriptions")).isZero();
                assertThat(countRows(connection, schema.name(), "commercial_subscription_events")).isZero();
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
        String schema = "commercial_subscription_" + UUID.randomUUID().toString().replace("-", "");
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

    private static long countRows(Connection connection, String schema, String table) throws Exception {
        try (var ps = connection.prepareStatement("select count(*) from " + schema + "." + table)) {
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
