package com.deepthoughtnet.clinic.api.platform.commercialcatalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.support.PostgresTestContainerSupport;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class CommercialCatalogMigrationTest extends PostgresTestContainerSupport {

    @Test
    void migrationCreatesCatalogTablesAndSeedsRecords() throws Exception {
        try (ManagedSchema schema = createSchema()) {
            flyway(schema.name()).migrate();
            try (Connection connection = connection()) {
                assertThat(tableExists(connection, schema.name(), "commercial_capabilities")).isTrue();
                assertThat(tableExists(connection, schema.name(), "commercial_modules")).isTrue();
                assertThat(tableExists(connection, schema.name(), "commercial_features")).isTrue();
                assertThat(tableExists(connection, schema.name(), "commercial_limit_definitions")).isTrue();
                assertThat(tableExists(connection, schema.name(), "commercial_addon_offers")).isTrue();
                assertThat(countRows(connection, schema.name(), "commercial_modules", "code = 'APPOINTMENTS'")).isGreaterThan(0);
                assertThat(countRows(connection, schema.name(), "commercial_modules", "code = 'PATIENTS'")).isGreaterThan(0);
                assertThat(countRows(connection, schema.name(), "commercial_capabilities", "code = 'HEALTHCARE_CORE'")).isGreaterThan(0);
                assertThat(countRows(connection, schema.name(), "commercial_addon_offers", "code = 'PHARMACY_ADDON'")).isGreaterThan(0);
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
        String schema = "commercial_catalog_" + UUID.randomUUID().toString().replace("-", "");
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

    private static long countRows(Connection connection, String schema, String table, String whereClause) throws Exception {
        try (var ps = connection.prepareStatement("select count(*) from " + schema + "." + table + " where " + whereClause)) {
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return 0L;
                }
                return rs.getLong(1);
            }
        }
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
