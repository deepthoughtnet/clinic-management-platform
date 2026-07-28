package com.deepthoughtnet.clinic.api.platform.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.testcontainers.DockerClientFactory;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class CommercialPricingMigrationTest {
    @Test
    void migrationCreatesCommercialPricingTablesAndAppliesCleanly() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker/Testcontainers is unavailable in this environment");

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                .withDatabaseName("clinic_management_test")
                .withUsername("clinic_test")
                .withPassword("clinic_test")) {
            postgres.start();
            try (ManagedSchema schema = createSchema(postgres)) {
                Flyway flyway = flyway(postgres, schema.name());
                flyway.migrate();
                flyway.migrate();

                try (Connection connection = connection(postgres)) {
                    assertThat(tableExists(connection, schema.name(), "commercial_plan_pricing")).isTrue();
                    assertThat(tableExists(connection, schema.name(), "commercial_plan_metered_rates")).isTrue();
                    assertThat(tableExists(connection, schema.name(), "commercial_plan_addon_pricing")).isTrue();
                    assertThat(tableExists(connection, schema.name(), "commercial_pricing_history")).isTrue();
                    assertThat(columnNullable(connection, schema.name(), "commercial_plan_pricing", "trial_days")).isTrue();
                    assertThat(countRows(connection, schema.name(), "commercial_plan_pricing")).isZero();
                    assertThat(flyway.info().current()).isNotNull();
                    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo(latestResolvedVersion(flyway));
                }
            }
        }
    }

    private static Flyway flyway(PostgreSQLContainer<?> postgres, String schema) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl() + "?currentSchema=" + schema, postgres.getUsername(), postgres.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .load();
    }

    private static Connection connection(PostgreSQLContainer<?> postgres) throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static ManagedSchema createSchema(PostgreSQLContainer<?> postgres) throws Exception {
        String schema = "commercial_pricing_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = connection(postgres);
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

    private static boolean columnNullable(Connection connection, String schema, String table, String column) throws Exception {
        try (var ps = connection.prepareStatement("""
                select is_nullable
                from information_schema.columns
                where table_schema = ?
                  and table_name = ?
                  and column_name = ?
                """)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, column);
            try (var rs = ps.executeQuery()) {
                return rs.next() && "YES".equalsIgnoreCase(rs.getString(1));
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
        public void close() {
        }
    }
}
